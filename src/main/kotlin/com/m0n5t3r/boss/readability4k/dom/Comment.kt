package com.m0n5t3r.boss.readability4k.dom

class Comment : Node() {
    override val nodeType = NodeType.COMMENT_NODE
    override val nodeName = "#comment"
}
