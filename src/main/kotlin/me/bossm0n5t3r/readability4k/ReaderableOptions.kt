package me.bossm0n5t3r.readability4k

import me.bossm0n5t3r.readability4k.dom.Element
import java.math.BigDecimal

/**
 * Configuration options for isProbablyReaderable function.
 *
 * @param minContentLength The minimum node content length used to decide if the document is readerable.
 * @param minScore The minimum cumulated 'score' used to determine if the document is readerable.
 * @param visibilityChecker The function used to determine if a node is visible.
 */
data class ReaderableOptions(
    val minContentLength: Int = 140,
    val minScore: BigDecimal = BigDecimal.valueOf(20),
    val visibilityChecker: (Element) -> Boolean = ::isNodeVisible,
)
