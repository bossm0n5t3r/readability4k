package com.m0n5t3r.boss.readability4k.dom

import java.net.URI

class Document(val documentURI: String) : Node() {
    override val nodeType = NodeType.DOCUMENT_NODE
    override val nodeName = "#document"

    var title: String = ""
    var documentElement: Element? = null
    var head: Element? = null
    var body: Element? = null

    val children: MutableList<Element> = mutableListOf()

    val firstElementChild: Element?
        get() = children.firstOrNull()

    val lastElementChild: Element?
        get() = children.lastOrNull()

    private var _baseURI: String? = null
    val baseURI: String
        get() {
            _baseURI?.let {
                return it
            }

            var result = documentURI

            getElementsByTagName("base").firstOrNull()?.getAttribute("href")?.let { href ->
                try {
                    result = URI(result).resolve(href).toString()
                } catch (e: Exception) {
                    // Just fall back to documentURI
                }
            }

            _baseURI = result
            return result
        }

    fun getElementsByTagName(tag: String): List<Element> = NodeUtils.getElementsByTagName(this, tag)

    fun querySelectorAll(selector: String): List<Element> =
        NodeUtils.querySelectorAll(this, selector)

    fun getElementById(id: String): Element? {
        fun getElem(node: Node): Element? {
            if (node is Element && node.id == id) {
                return node
            }
            for (child in node.childNodes) {
                val result = getElem(child)
                if (result != null) return result
            }
            return null
        }
        return getElem(this)
    }

    fun createElement(tag: String): Element = Element(tag)

    fun createTextNode(text: String): TextNode = TextNode().apply { textContent = text }

    fun createDocumentFragment(): DocumentFragment = DocumentFragment()
}
