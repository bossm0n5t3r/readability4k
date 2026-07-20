package com.m0n5t3r.boss.readability4k.dom

class TextNode : Node() {
    override val nodeType = NodeType.TEXT_NODE
    override val nodeName = "#text"

    private var _textContent: String? = null
    private var _innerHTML: String? = null

    override var textContent: String
        get() {
            if (_textContent == null) {
                _textContent = HtmlEntities.decodeHTML(_innerHTML ?: "")
            }
            return requireNotNull(_textContent) { "textContent is null" }
        }
        set(value) {
            _textContent = value
            _innerHTML = null
        }

    var innerHTML: String
        get() {
            if (_innerHTML == null) {
                _innerHTML = HtmlEntities.encodeTextContentHTML(_textContent ?: "")
            }
            return requireNotNull(_innerHTML) { "innerHTML is null" }
        }
        set(value) {
            _innerHTML = value
            _textContent = null
        }
}
