package com.m0n5t3r.boss.readability4k.dom

data class Attribute(val name: String, var value: String) {
    fun getEncodedValue(): String = HtmlEntities.encodeHTML(value)

    fun cloneNode(): Attribute = copy()
}
