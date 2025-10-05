package me.bossm0n5t3r.readability4k

object HtmlEntities {
    private val HTML_ENTITY_PATTERN = Regex("&(quot|amp|apos|lt|gt);")
    private val NUMERIC_ENTITY_PATTERN =
        Regex(
            "&#(?:x([0-9a-f]+)|([0-9]+));",
            RegexOption.IGNORE_CASE,
        )
    private val HTML_SPECIAL_CHARS_PATTERN = Regex("[&<>]")
    private val HTML_ESCAPABLE_CHARS_PATTERN = Regex("""[&<>'"]""")

    private val ENTITY_TABLE =
        mapOf(
            "lt" to "<",
            "gt" to ">",
            "amp" to "&",
            "quot" to "\"",
            "apos" to "'",
        )

    private val REVERSE_ENTITY_TABLE =
        mapOf(
            "<" to "&lt;",
            ">" to "&gt;",
            "&" to "&amp;",
            "\"" to "&quot;",
            "'" to "&apos;",
        )

    fun encodeTextContentHTML(s: String): String =
        s.replace(HTML_SPECIAL_CHARS_PATTERN) { matchResult ->
            REVERSE_ENTITY_TABLE[matchResult.value] ?: matchResult.value
        }

    fun encodeHTML(s: String): String =
        s.replace(HTML_ESCAPABLE_CHARS_PATTERN) { matchResult ->
            REVERSE_ENTITY_TABLE[matchResult.value] ?: matchResult.value
        }

    fun decodeHTML(str: String): String =
        str
            .replace(HTML_ENTITY_PATTERN) { match ->
                ENTITY_TABLE[match.groupValues[1]] ?: match.value
            }.replace(NUMERIC_ENTITY_PATTERN) { match ->
                val hex = match.groupValues[1]
                val dec = match.groupValues[2]
                val num =
                    if (hex.isNotEmpty()) {
                        hex.toInt(16)
                    } else {
                        dec.toInt()
                    }

                val validNum =
                    when {
                        num == 0 || num > 0x10FFFF || (num in 0xD800..0xDFFF) -> 0xFFFD
                        else -> num
                    }

                String(Character.toChars(validNum))
            }
}
