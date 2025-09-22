package me.bossm0n5t3r.readability4k

import org.jsoup.nodes.Element
import java.math.BigDecimal

/**
 * Utility functions for Readability processing.
 * Contains helper methods extracted from the original JavaScript implementation.
 */
object ReadabilityUtils {
    /**
     * Get the text content length of an element
     */
    fun getInnerText(
        element: Element,
        normalizeSpaces: Boolean = true,
    ): String =
        if (normalizeSpaces) {
            element.text().replace(Regexps.NORMALIZE, " ")
        } else {
            element.text()
        }

    /**
     * Calculate link density of an element
     */
    fun getLinkDensity(element: Element): BigDecimal {
        val textLength = getInnerText(element).length
        if (textLength == 0) return BigDecimal.ZERO
        val linkLength =
            element.getElementsByTag("a").sumOf { linkNode ->
                val href = linkNode.attr("href")
                val coefficient = if (href.isNotBlank() && href.matches(Regexps.HASH_URL)) BigDecimal.valueOf(0.3) else BigDecimal.ONE
                getInnerText(linkNode).length.toBigDecimal() * coefficient
            }
        return linkLength / textLength.toBigDecimal()
    }
}
