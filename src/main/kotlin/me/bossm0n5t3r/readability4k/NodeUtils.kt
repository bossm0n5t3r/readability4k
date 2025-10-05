package me.bossm0n5t3r.readability4k

object NodeUtils {
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
}
