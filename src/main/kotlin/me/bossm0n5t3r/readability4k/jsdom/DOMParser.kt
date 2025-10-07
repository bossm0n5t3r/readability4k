package me.bossm0n5t3r.readability4k.jsdom

import me.bossm0n5t3r.readability4k.LOGGER

class DOMParser {
    private var html: String = ""
    private var currentChar: Int = 0
    private val strBuf: StringBuilder = StringBuilder()
    private val retPair: Array<Any?> = arrayOfNulls(2)
    private var errorState: String = ""
    private lateinit var doc: Document

    companion object {
        private val WHITESPACE = setOf(' ', '\t', '\n', '\r')
    }

    fun parse(
        html: String,
        url: String = "",
    ): Document {
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
        LOGGER.error("JSDOMParser error: $message")
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
        val n = html.indexOf('=', currentChar)
        if (n == -1) {
            currentChar = html.length
            return
        }

        val name = html.substring(currentChar, n)
        currentChar = n + 1

        if (name.isEmpty()) return

        val c = nextChar()
        if (c != '"' && c != '\'') {
            error("Error reading attribute $name, expecting '\"'")
            return
        }

        val value = readString(c) ?: return
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

    private fun match(str: String): Boolean {
        val substr = html.substring(currentChar, (currentChar + str.length).coerceAtMost(html.length))
        return if (substr.equals(str, ignoreCase = true)) {
            currentChar += str.length
            true
        } else {
            false
        }
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
        var child = readNode()
        while (child != null) {
            if (child.nodeType != NodeType.COMMENT_NODE) {
                node.appendChild(child)
            }
            child = readNode()
        }
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

        if (!closed) {
            readChildren(node)
            val closingTag = "</${node.matchingTag}>"
            if (!match(closingTag)) {
                error(
                    "expected '$closingTag' and got ${html.substring(
                        currentChar,
                        (currentChar + closingTag.length).coerceAtMost(html.length),
                    )}",
                )
                return null
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
