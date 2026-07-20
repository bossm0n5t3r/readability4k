package com.m0n5t3r.boss.readability4k.dom

class DocumentFragment : Node() {
    override val nodeType = NodeType.DOCUMENT_FRAGMENT_NODE
    override val nodeName = "#document-fragment"

    val children: MutableList<Element> = mutableListOf()

    val firstElementChild: Element?
        get() = children.firstOrNull()

    val lastElementChild: Element?
        get() = children.lastOrNull()
}
