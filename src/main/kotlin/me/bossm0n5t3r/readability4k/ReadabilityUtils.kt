package me.bossm0n5t3r.readability4k

import org.jsoup.nodes.Element

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
}
