package com.m0n5t3r.boss.readability4k

import com.m0n5t3r.boss.readability4k.dom.Element
import com.m0n5t3r.boss.readability4k.dom.Node
import com.m0n5t3r.boss.readability4k.dom.TextNode
import java.math.BigDecimal

data class ReadabilityOptions(
    val debug: Boolean = false,
    val maxElemsToParse: Int = DEFAULT_MAX_ELEMS_TO_PARSE,
    val nbTopCandidates: Int = DEFAULT_N_TOP_CANDIDATES,
    val charThreshold: Int = DEFAULT_CHAR_THRESHOLD,
    val classesToPreserve: List<String> = emptyList(),
    val keepClasses: Boolean = false,
    val serializer: ((Node) -> String) = { node ->
        when (node) {
            is Element -> node.innerHTML
            is TextNode -> node.innerHTML
            else -> ""
        }
    },
    val disableJSONLD: Boolean = false,
    val allowedVideoRegex: Regex = Regexps.VIDEOS,
    val linkDensityModifier: BigDecimal = BigDecimal.ZERO,
)
