package me.bossm0n5t3r.readability4k

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.math.BigDecimal

data class ReadabilityProperties(
    val document: Document,
    val articleTitle: String? = null,
    val articleByline: String? = null,
    val articleLang: String,
    val articleDir: String? = null,
    val articleSiteName: String? = null,
    val attempts: MutableList<Attempt> = mutableListOf(),
    val metadata: Map<String, String> = mutableMapOf(),
    val debug: Boolean,
    val maxElemsToParse: String? = null,
    val nbTopCandidates: Int,
    val charThreshold: Int,
    val classesToPreserve: Set<String>,
    val keepClasses: Boolean,
    val serializer: String? = null,
    val disableJSONLD: String? = null,
    val allowedVideoRegex: Regex,
    val linkDensityModifier: BigDecimal,
    var flags: Int,
) {
    data class Attempt(
        val articleContent: Element,
        val textLength: Int,
    )
}
