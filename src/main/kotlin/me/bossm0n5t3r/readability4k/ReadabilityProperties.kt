package me.bossm0n5t3r.readability4k

import me.bossm0n5t3r.readability4k.jsdom.Document
import me.bossm0n5t3r.readability4k.jsdom.Element
import java.math.BigDecimal

data class ReadabilityProperties(
    val document: Document,
    var articleTitle: String? = null,
    var articleByline: String? = null,
    var articleLang: String? = null,
    var articleDir: String? = null,
    var articleSiteName: String? = null,
    val attempts: MutableList<Attempt> = mutableListOf(),
    var metadata: MutableMap<String, String?> = mutableMapOf(),
    val debug: Boolean,
    val maxElemsToParse: Int,
    val nbTopCandidates: Int,
    val charThreshold: Int,
    val classesToPreserve: Set<String>,
    val keepClasses: Boolean,
    val serializer: (Element) -> String,
    val disableJSONLD: Boolean,
    val allowedVideoRegex: Regex,
    val linkDensityModifier: BigDecimal,
    var flags: Int,
) {
    constructor(document: Document, options: ReadabilityOptions) : this(
        document = document,
        articleTitle = null,
        articleByline = null,
        articleLang = null,
        articleDir = null,
        articleSiteName = null,
        attempts = mutableListOf<Attempt>(),
        metadata = mutableMapOf<String, String?>(),
        debug = options.debug,
        maxElemsToParse = options.maxElemsToParse,
        nbTopCandidates = options.nbTopCandidates,
        charThreshold = options.charThreshold,
        classesToPreserve = CLASSES_TO_PRESERVE + options.classesToPreserve,
        keepClasses = options.keepClasses,
        serializer = options.serializer,
        disableJSONLD = options.disableJSONLD,
        allowedVideoRegex = options.allowedVideoRegex,
        linkDensityModifier = options.linkDensityModifier,
        flags = FLAG_STRIP_UNLIKELYS or FLAG_WEIGHT_CLASSES or FLAG_CLEAN_CONDITIONALLY,
    )

    data class Attempt(
        val articleContent: Element,
        val textLength: Int,
    )
}
