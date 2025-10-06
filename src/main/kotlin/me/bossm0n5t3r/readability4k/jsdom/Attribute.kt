package me.bossm0n5t3r.readability4k.jsdom

data class Attribute(
    val name: String,
    var value: String,
) {
    fun getEncodedValue(): String = HtmlEntities.encodeHTML(value)

    fun cloneNode(): Attribute = copy()
}
