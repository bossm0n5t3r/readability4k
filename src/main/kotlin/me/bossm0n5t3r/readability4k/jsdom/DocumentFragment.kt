package me.bossm0n5t3r.readability4k.jsdom

class DocumentFragment : Node() {
    override val nodeType = NodeType.DOCUMENT_FRAGMENT_NODE
    override val nodeName = "#document-fragment"

    val children: MutableList<Element> = mutableListOf()
}
