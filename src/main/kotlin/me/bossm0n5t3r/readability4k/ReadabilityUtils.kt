package me.bossm0n5t3r.readability4k

import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
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

    /**
     * Determine if a node qualifies as phrasing content.
     * https://developer.mozilla.org/en-US/docs/Web/Guide/HTML/Content_categories#Phrasing_content
     */
    fun isPhrasingContent(node: Node): Boolean {
        val element = node as? Element
        val tagName = element?.tagName()?.uppercase()
        return node is TextNode ||
            tagName in PHRASING_ELEMS ||
            ((tagName == "A" || tagName == "DEL" || tagName == "INS") && element.children().all { isPhrasingContent(it) })
    }
}
