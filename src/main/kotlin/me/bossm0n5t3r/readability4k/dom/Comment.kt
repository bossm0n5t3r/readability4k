package me.bossm0n5t3r.readability4k.dom

class Comment : Node() {
    override val nodeType = NodeType.COMMENT_NODE
    override val nodeName = "#comment"
}
