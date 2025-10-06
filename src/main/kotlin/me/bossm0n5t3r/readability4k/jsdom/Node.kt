package me.bossm0n5t3r.readability4k.jsdom

abstract class Node {
    abstract val nodeType: NodeType
    abstract val nodeName: String

    var parentNode: Node? = null
    var previousSibling: Node? = null
    var nextSibling: Node? = null

    val childNodes: MutableList<Node> = mutableListOf()
    var localName: String? = null

    open val textContent: String
        get() {
            val text = StringBuilder()

            fun collectText(node: Node) {
                for (child in node.childNodes) {
                    if (child.nodeType == NodeType.TEXT_NODE) {
                        text.append((child as TextNode).textContent)
                    } else {
                        collectText(child)
                    }
                }
            }
            collectText(this)
            return text.toString()
        }

    val firstChild: Node?
        get() = childNodes.firstOrNull()

    val lastChild: Node?
        get() = childNodes.lastOrNull()

    fun appendChild(child: Node): Node {
        val nodes =
            if (child.nodeType == NodeType.DOCUMENT_FRAGMENT_NODE) {
                child.childNodes.toList()
            } else {
                listOf(child)
            }
        insertNodesAtIndex(nodes, -1)
        return child
    }

    fun insertBefore(
        newNode: Node,
        referenceNode: Node?,
    ): Node {
        if (newNode === referenceNode) return newNode

        val nodes =
            if (newNode.nodeType == NodeType.DOCUMENT_FRAGMENT_NODE) {
                newNode.childNodes.toList()
            } else {
                listOf(newNode)
            }

        val index = if (referenceNode != null) childNodes.indexOf(referenceNode) else -1
        require(referenceNode == null || index != -1) { "insertBefore: reference node not found" }

        insertNodesAtIndex(nodes, index)
        return newNode
    }

    fun remove(): Node {
        val parent = parentNode ?: return this

        val childIndex = parent.childNodes.indexOf(this)
        require(childIndex != -1) { "removeChild: node not found" }

        this.parentNode = null

        val prev = previousSibling
        val next = nextSibling

        prev?.nextSibling = next
        next?.previousSibling = prev

        parent.childNodes.removeAt(childIndex)

        if (this is Element) {
            val prevElem = previousElementSibling
            val nextElem = nextElementSibling

            prevElem?.nextElementSibling = nextElem
            nextElem?.previousElementSibling = prevElem

            when (parent) {
                is Element -> parent.children.remove(this)
                is Document -> parent.children.remove(this)
                is DocumentFragment -> parent.children.remove(this)
            }

            previousElementSibling = null
            nextElementSibling = null
        }

        previousSibling = null
        nextSibling = null

        return this
    }

    fun removeChild(child: Node): Node = child.remove()

    fun replaceChild(
        newNode: Node,
        oldNode: Node,
    ): Node {
        if (newNode == oldNode) return oldNode
        require(oldNode.parentNode == this) { "replaceChild: node to be replaced is not a child of this node" }
        insertBefore(newNode, oldNode)
        oldNode.remove()
        return oldNode
    }

    private fun insertNodesAtIndex(
        nodes: List<Node>,
        index: Int,
    ) {
        if (nodes.isEmpty()) return

        for (node in nodes) {
            node.parentNode?.let { node.remove() }
        }

        val afterSibling = childNodes.getOrNull(index)
        val prevSibling = if (afterSibling != null) afterSibling.previousSibling else lastChild

        val insertionPoint = if (index == -1) childNodes.size else index
        childNodes.addAll(insertionPoint, nodes)

        var prev = prevSibling
        for (node in nodes) {
            node.parentNode = this
            node.previousSibling = prev
            prev?.nextSibling = node
            prev = node
        }

        val lastInsertedNode = nodes.last()
        lastInsertedNode.nextSibling = afterSibling
        afterSibling?.previousSibling = lastInsertedNode

        val elementsToInsert = nodes.filterIsInstance<Element>()
        if (elementsToInsert.isEmpty()) return

        var afterElem = afterSibling
        while (afterElem != null && afterElem !is Element) {
            afterElem = afterElem.nextSibling
        }

        val children =
            when (this) {
                is Element -> this.children
                is Document -> this.children
                is DocumentFragment -> this.children
                else -> mutableListOf()
            }
        val lastElementChild =
            when (this) {
                is Element -> this.lastElementChild
                is Document -> this.lastElementChild
                is DocumentFragment -> this.lastElementChild
                else -> null
            }

        val prevElem = if (afterElem != null) afterElem.previousElementSibling else lastElementChild

        val afterElemIndex = afterElem?.let { children.indexOf(it) } ?: -1
        val elemInsertionPoint = if (afterElemIndex == -1) children.size else afterElemIndex

        children.addAll(elemInsertionPoint, elementsToInsert)

        var prevElement = prevElem
        for (elem in elementsToInsert) {
            elem.previousElementSibling = prevElement
            prevElement?.nextElementSibling = elem
            prevElement = elem
        }

        val lastInsertedElem = elementsToInsert.last()
        lastInsertedElem.nextElementSibling = afterElem
        afterElem?.previousElementSibling = lastInsertedElem
    }
}
