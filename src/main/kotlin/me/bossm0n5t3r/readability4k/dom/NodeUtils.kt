package me.bossm0n5t3r.readability4k.dom

object NodeUtils {
    val Node.innerHTMLOrNull: String?
        get() =
            when (this) {
                is Element -> this.innerHTML
                is TextNode -> this.innerHTML
                else -> null
            }

    var Node.textContentOrNull: String?
        get() =
            when (this) {
                is Element -> this.textContent
                is TextNode -> this.textContent
                else -> null
            }
        set(value) {
            if (value == null) return
            when (this) {
                is Element -> this.textContent = value
                is TextNode -> this.textContent = value
                else -> {}
            }
        }

    fun getElementsByTagName(
        root: Node,
        tag: String,
    ): List<Element> {
        val upperTag = tag.uppercase()
        val elems = mutableListOf<Element>()
        val allTags = tag == "*"

        fun getElems(node: Node) {
            for (child in node.childNodes) {
                if (child is Element) {
                    if (allTags || child.tagName == upperTag) {
                        elems.add(child)
                    }
                    getElems(child)
                }
            }
        }

        getElems(root)
        return elems
    }

    fun querySelectorAll(
        root: Node,
        selector: String,
    ): List<Element> {
        val selectorGroups = selector.split(",").map { it.trim() }
        val results = mutableSetOf<Element>()

        for (selectorGroup in selectorGroups) {
            results.addAll(querySelectorSingle(root, selectorGroup))
        }

        return results.toList()
    }

    private fun querySelectorSingle(
        root: Node,
        selector: String,
    ): List<Element> {
        val parts = parseCombinators(selector)

        if (parts.size == 1 && parts[0].combinator == null) {
            return findAllMatching(root, parseSimpleSelector(parts[0].selector))
        }

        return findWithCombinators(root, parts)
    }

    private fun parseCombinators(selector: String): List<SelectorPart> {
        val parts = mutableListOf<SelectorPart>()
        var current = StringBuilder()
        var i = 0

        while (i < selector.length) {
            when (selector[i]) {
                '>' -> {
                    if (current.isNotBlank()) {
                        parts.add(SelectorPart(current.toString().trim(), null))
                        current = StringBuilder()
                    }
                    parts.last().combinator = Combinator.CHILD
                    i++
                    while (i < selector.length && selector[i].isWhitespace()) i++
                    continue
                }
                ' ', '\t', '\n', '\r' -> {
                    if (current.isNotBlank()) {
                        var j = i
                        while (j < selector.length && selector[j].isWhitespace()) j++

                        if (j < selector.length && selector[j] != '>') {
                            parts.add(SelectorPart(current.toString().trim(), Combinator.DESCENDANT))
                            current = StringBuilder()
                        }
                    }
                    i++
                    continue
                }
                else -> {
                    current.append(selector[i])
                    i++
                }
            }
        }

        if (current.isNotBlank()) {
            parts.add(SelectorPart(current.toString().trim(), null))
        }

        return parts
    }

    private fun findAllMatching(
        root: Node,
        selector: SimpleSelector,
    ): List<Element> {
        val elems = mutableListOf<Element>()

        fun traverse(node: Node) {
            for (child in node.childNodes) {
                if (child is Element) {
                    if (matches(child, selector)) {
                        elems.add(child)
                    }
                    traverse(child)
                }
            }
        }

        traverse(root)
        return elems
    }

    private fun findWithCombinators(
        root: Node,
        parts: List<SelectorPart>,
    ): List<Element> {
        if (parts.isEmpty()) return emptyList()

        var candidates = findAllMatching(root, parseSimpleSelector(parts[0].selector))

        for (i in 0 until parts.size - 1) {
            val combinator = parts[i].combinator ?: continue
            val nextSelector = parseSimpleSelector(parts[i + 1].selector)
            val newCandidates = mutableListOf<Element>()

            for (parent in candidates) {
                when (combinator) {
                    Combinator.CHILD -> {
                        for (child in parent.childNodes) {
                            if (child is Element && matches(child, nextSelector)) {
                                newCandidates.add(child)
                            }
                        }
                    }
                    Combinator.DESCENDANT -> {
                        fun findDescendants(node: Node) {
                            for (child in node.childNodes) {
                                if (child is Element) {
                                    if (matches(child, nextSelector)) {
                                        newCandidates.add(child)
                                    }
                                    findDescendants(child)
                                }
                            }
                        }
                        findDescendants(parent)
                    }
                }
            }

            candidates = newCandidates
        }

        return candidates
    }

    private fun parseSimpleSelector(selector: String): SimpleSelector {
        val trimmed = selector.trim()

        return when {
            trimmed == "*" -> SimpleSelector.All
            trimmed.startsWith("#") -> SimpleSelector.Id(trimmed.substring(1))
            trimmed.startsWith(".") -> SimpleSelector.Class(trimmed.substring(1))
            trimmed.startsWith("[") && trimmed.endsWith("]") -> {
                parseAttributeSelector(trimmed.substring(1, trimmed.length - 1))
            }
            else -> SimpleSelector.Tag(trimmed.uppercase())
        }
    }

    private fun parseAttributeSelector(attr: String): SimpleSelector {
        val parts = attr.split("=", limit = 2)
        return if (parts.size == 2) {
            val value = parts[1].trim().removeSurrounding("\"", "'")
            SimpleSelector.AttributeWithValue(parts[0].trim(), value)
        } else {
            SimpleSelector.Attribute(attr.trim())
        }
    }

    private fun matches(
        element: Element,
        selector: SimpleSelector,
    ): Boolean =
        when (selector) {
            is SimpleSelector.All -> true
            is SimpleSelector.Tag -> element.tagName == selector.name
            is SimpleSelector.Id -> element.id == selector.id
            is SimpleSelector.Class -> element.hasClass(selector.className)
            is SimpleSelector.Attribute -> element.hasAttribute(selector.name)
            is SimpleSelector.AttributeWithValue -> element.getAttribute(selector.name) == selector.value
        }

    private data class SelectorPart(
        val selector: String,
        var combinator: Combinator?,
    )

    private enum class Combinator {
        CHILD, // >
        DESCENDANT, // 공백
    }

    private sealed class SimpleSelector {
        object All : SimpleSelector()

        data class Tag(
            val name: String,
        ) : SimpleSelector()

        data class Id(
            val id: String,
        ) : SimpleSelector()

        data class Class(
            val className: String,
        ) : SimpleSelector()

        data class Attribute(
            val name: String,
        ) : SimpleSelector()

        data class AttributeWithValue(
            val name: String,
            val value: String,
        ) : SimpleSelector()
    }
}
