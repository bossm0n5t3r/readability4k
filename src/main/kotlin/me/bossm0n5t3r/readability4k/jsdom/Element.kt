package me.bossm0n5t3r.readability4k.jsdom

class Element(
    tag: String,
) : Node() {
    override val nodeType = NodeType.ELEMENT_NODE

    val matchingTag: String = tag
    var tagName: String

    init {
        val lastColonIndex = tag.lastIndexOf(':')
        val actualTag =
            if (lastColonIndex != -1) {
                tag.substring(lastColonIndex + 1)
            } else {
                tag
            }
        localName = actualTag.lowercase()
        tagName = actualTag.uppercase()
    }

    override val nodeName: String
        get() = tagName

    val attributes: MutableList<Attribute> = mutableListOf()
    val children: MutableList<Element> = mutableListOf()
    val style: Style = Style(this)

    var previousElementSibling: Element? = null
    var nextElementSibling: Element? = null

    val firstElementChild: Element?
        get() = children.firstOrNull()

    val lastElementChild: Element?
        get() = children.lastOrNull()

    var className: String
        get() = getAttribute("class") ?: ""
        set(value) = setAttribute("class", value)

    var id: String
        get() = getAttribute("id") ?: ""
        set(value) = setAttribute("id", value)

    var href: String
        get() = getAttribute("href") ?: ""
        set(value) = setAttribute("href", value)

    var src: String
        get() = getAttribute("src") ?: ""
        set(value) = setAttribute("src", value)

    var srcset: String
        get() = getAttribute("srcset") ?: ""
        set(value) = setAttribute("srcset", value)

    fun hasClass(className: String): Boolean {
        val classAttr = this.className.trim()
        if (classAttr.isEmpty()) return false
        val classes = classAttr.split(Regex("\\s+"))
        return className in classes
    }

    fun querySelectorAll(selector: String): List<Element> = NodeUtils.querySelectorAll(this, selector)

    fun getAttribute(name: String): String? = attributes.findLast { it.name == name }?.value

    fun setAttribute(
        name: String,
        value: String,
    ) {
        val existing = attributes.findLast { it.name == name }
        if (existing != null) {
            existing.value = value
        } else {
            attributes.add(Attribute(name, value))
        }
    }

    fun removeAttribute(name: String) {
        for (i in attributes.size - 1 downTo 0) {
            val attr = attributes[i]
            if (attr.name == name) {
                attributes.removeAt(i)
                break
            }
        }
    }

    fun hasAttribute(name: String): Boolean = attributes.any { it.name == name }

    fun setAttributeNode(node: Attribute) {
        setAttribute(node.name, node.value)
    }

    fun getElementsByTagName(tag: String): List<Element> = NodeUtils.getElementsByTagName(this, tag)

    var innerHTML: String
        get() {
            val builder = StringBuilder()

            fun getHTML(node: Node) {
                for (child in node.childNodes) {
                    when (child) {
                        is Element -> {
                            builder.append("<${child.localName}")

                            for (attr in child.attributes) {
                                val value = attr.getEncodedValue()
                                val quote = if (!value.contains('"')) '"' else '\''
                                builder.append(" ${attr.name}=$quote$value$quote")
                            }

                            if (child.localName in VOID_ELEMENTS && child.childNodes.isEmpty()) {
                                builder.append("/>")
                            } else {
                                builder.append(">")
                                getHTML(child)
                                builder.append("</${child.localName}>")
                            }
                        }
                        is TextNode -> {
                            builder.append(child.innerHTML)
                        }
                    }
                }
            }

            getHTML(this)
            return builder.toString()
        }
        set(html) {
            val parser = DOMParser()
            val node = parser.parse(html)

            childNodes.forEach { it.parentNode = null }
            childNodes.clear()
            children.clear()

            childNodes.addAll(node.childNodes)
            children.addAll(node.children)

            childNodes.forEach { it.parentNode = this }
        }

    override var textContent: String
        get() = super.textContent
        set(text) {
            childNodes.forEach { it.parentNode = null }
            childNodes.clear()
            children.clear()

            val node = TextNode()
            node.textContent = text
            node.parentNode = this
            childNodes.add(node)
        }

    companion object {
        private val VOID_ELEMENTS =
            setOf(
                "area",
                "base",
                "br",
                "col",
                "command",
                "embed",
                "hr",
                "img",
                "input",
                "link",
                "meta",
                "param",
                "source",
                "wbr",
            )
    }
}
