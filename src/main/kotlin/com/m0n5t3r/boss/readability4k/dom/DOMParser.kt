package com.m0n5t3r.boss.readability4k.dom

import com.m0n5t3r.boss.readability4k.LOGGER

class DOMParser {
    private var html: String = ""
    private var currentChar: Int = 0
    private val strBuf: StringBuilder = StringBuilder()
    private val retPair: Array<Any?> = arrayOfNulls(2)
    var errorState: String = ""
    private lateinit var doc: Document

    companion object {
        private val WHITESPACE = setOf(' ', '\t', '\n', '\r')
        private val INLINE_ELEMENTS = setOf("a", "span")
        private val LEGACY_CONTAINER_VOID_ELEMENTS = setOf("base", "input")
    }

    fun parse(html: String, url: String = ""): Document {
        this.html = html
        this.currentChar = 0
        this.doc = Document(url)

        readChildren(doc)

        doc.documentElement?.let { docElem ->
            doc.childNodes.toList().forEach { child ->
                if (child !== docElem) {
                    child.remove()
                }
            }
        }

        return doc
    }

    private fun error(message: String) {
        LOGGER.warn("JSDOMParser error: {}", message)
        errorState += "$message\n"
    }

    private fun peekNext(): Char? = html.getOrNull(currentChar)

    private fun nextChar(): Char? = html.getOrNull(currentChar++)

    private fun readString(quote: Char): String? {
        val n = html.indexOf(quote, currentChar)
        return if (n == -1) {
            currentChar = html.length
            null
        } else {
            val str = html.substring(currentChar, n)
            currentChar = n + 1
            str
        }
    }

    private fun readAttribute(node: Element) {
        val nameStart = currentChar
        while (true) {
            val c = peekNext() ?: break
            if (c in WHITESPACE || c == '=' || c == '>' || c == '/') break
            currentChar++
        }

        val name = html.substring(nameStart, currentChar)
        if (name.isEmpty()) {
            currentChar++
            return
        }

        while (peekNext() in WHITESPACE) {
            currentChar++
        }

        var value = ""
        if (peekNext() == '=') {
            currentChar++
            while (peekNext() in WHITESPACE) {
                currentChar++
            }

            val quote = peekNext()
            value =
                if (quote == '"' || quote == '\'') {
                    currentChar++
                    readString(quote) ?: return
                } else {
                    val valueStart = currentChar
                    while (true) {
                        val c = peekNext() ?: break
                        if (
                            c in WHITESPACE ||
                                c == '>' ||
                                (c == '/' && html.getOrNull(currentChar + 1) == '>')
                        ) {
                            break
                        }
                        currentChar++
                    }
                    html.substring(valueStart, currentChar)
                }
        }

        node.attributes.add(Attribute(name, HtmlEntities.decodeHTML(value)))
    }

    private fun makeElementNode(retPair: Array<Any?>): Boolean {
        var c: Char? = nextChar() ?: return false

        strBuf.clear()
        while (c !in WHITESPACE && c != '>' && c != '/') {
            strBuf.append(c)
            c = nextChar() ?: return false
        }

        val tag = strBuf.toString()
        if (tag.isEmpty()) return false

        val node = Element(tag)

        while (c != '/' && c != '>') {
            while (html.getOrNull(currentChar) in WHITESPACE) {
                currentChar++
            }
            c = nextChar() ?: return false

            if (c != '/' && c != '>') {
                currentChar--
                readAttribute(node)
            }
        }

        var closed = false
        if (c == '/') {
            closed = true
            c = nextChar()
            if (c != '>') {
                error("expected '>' to close $tag")
                return false
            }
        }

        retPair[0] = node
        retPair[1] = closed
        return true
    }

    private fun match(str: String): Boolean =
        if (html.startsWith(str, currentChar, ignoreCase = true)) {
            currentChar += str.length
            true
        } else {
            false
        }

    @Suppress("SameParameterValue")
    private fun discardTo(str: String) {
        val index = html.indexOf(str, currentChar)
        currentChar =
            if (index == -1) {
                html.length
            } else {
                index + str.length
            }
    }

    private fun readChildren(node: Node) {
        while (true) {
            val child = readNode()
            if (child == null) {
                if (!discardIgnorableClosingTag(node)) return
                continue
            }

            if (child.nodeType != NodeType.COMMENT_NODE) {
                node.appendChild(child)
            }
        }
    }

    private fun discardIgnorableClosingTag(parent: Node): Boolean {
        if (!html.startsWith("</", currentChar)) return false

        val end = html.indexOf('>', currentChar + 2)
        if (end == -1) return false

        val tag = html.substring(currentChar + 2, end).trim().lowercase()
        if (parent is Element && tag.equals(parent.matchingTag, ignoreCase = true)) return false
        if (tag != "script" && tag !in Element.VOID_ELEMENTS) return false

        currentChar = end + 1
        return true
    }

    private fun discardNextComment(): Comment? {
        if (match("--")) {
            discardTo("-->")
        } else {
            var c: Char? = nextChar() ?: return null
            while (c != '>') {
                if (c == '"' || c == '\'') {
                    readString(c)
                }
                c = nextChar()
            }
        }
        return Comment()
    }

    private fun readRawText(node: Element): Boolean {
        val closingTag = "</${node.matchingTag}>"
        val end = html.indexOf(closingTag, currentChar, ignoreCase = true)
        if (end == -1) {
            error("expected '$closingTag' but reached end of document")
            return false
        }

        if (currentChar < end) {
            node.appendChild(TextNode().apply { innerHTML = html.substring(currentChar, end) })
        }
        currentChar = end + closingTag.length
        return true
    }

    private fun isImplicitlyClosedByListItem(node: Element): Boolean {
        if (node.localName !in INLINE_ELEMENTS || !html.startsWith("</", currentChar)) return false

        val end = html.indexOf('>', currentChar + 2)
        return end != -1 &&
            html.substring(currentChar + 2, end).trim().equals("li", ignoreCase = true)
    }

    private fun readNode(): Node? {
        var c: Char? = nextChar() ?: return null

        if (c != '<') {
            currentChar--
            val textNode = TextNode()
            val n = html.indexOf('<', currentChar)

            textNode.innerHTML =
                if (n == -1) {
                    val result = html.substring(currentChar)
                    currentChar = html.length
                    result
                } else {
                    val result = html.substring(currentChar, n)
                    currentChar = n
                    result
                }

            return textNode
        }

        if (match("![CDATA[")) {
            val endChar = html.indexOf("]]>", currentChar)
            if (endChar == -1) {
                error("unclosed CDATA section")
                return null
            }

            val textNode = TextNode()
            textNode.textContent = html.substring(currentChar, endChar)
            currentChar = endChar + "]]>".length
            return textNode
        }

        c = peekNext()

        if (c == '!' || c == '?') {
            currentChar++
            return discardNextComment()
        }

        if (c == '/') {
            currentChar--
            return null
        }

        if (!makeElementNode(retPair)) {
            return null
        }

        val node = retPair[0] as Element
        val closed = retPair[1] as Boolean

        val closingTag = "</${node.matchingTag}>"
        val hasExplicitClosingTag =
            node.localName in LEGACY_CONTAINER_VOID_ELEMENTS &&
                html.indexOf(closingTag, currentChar, ignoreCase = true) != -1
        if (!closed && (node.localName !in Element.VOID_ELEMENTS || hasExplicitClosingTag)) {
            if (
                node.localName == "script" &&
                    !html.startsWith("<?", currentChar) &&
                    !html.startsWith("<!--", currentChar)
            ) {
                if (!readRawText(node)) return null
            } else {
                readChildren(node)
                if (!match(closingTag)) {
                    if (isImplicitlyClosedByListItem(node)) return node
                    val errorMessage =
                        if (currentChar < 0 || currentChar >= html.length) {
                            ", but currentChar < 0 || currentChar >= html.length, $currentChar, ${html.length}"
                        } else {
                            "and got " +
                                html.substring(
                                    currentChar,
                                    (currentChar + closingTag.length).coerceAtMost(html.length),
                                )
                        }
                    error("expected '$closingTag' $errorMessage")
                    return null
                }
            }
        }

        when (node.localName) {
            "title" -> if (doc.title.isEmpty()) doc.title = node.textContent.trim()
            "head" -> doc.head = node
            "body" -> doc.body = node
            "html" -> doc.documentElement = node
        }

        return node
    }
}
