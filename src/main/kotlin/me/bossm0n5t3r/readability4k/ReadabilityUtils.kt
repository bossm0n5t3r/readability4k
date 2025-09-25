package me.bossm0n5t3r.readability4k

import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.select.Elements
import java.math.BigDecimal
import java.net.URI

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

    fun Elements.everyNode(predicate: (Element) -> Boolean): Boolean = this.all(predicate)

    fun Elements.someNode(predicate: (Element) -> Boolean): Boolean = this.any(predicate)

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

    /**
     * Get the number of times a string s appears in the node e.
     */
    fun getCharCount(
        element: Element,
        delimiters: String = ",",
    ): Int = getInnerText(element).split(delimiters).size - 1

    fun isWhiteSpace(node: Node): Boolean {
        val element = node as? Element
        return (node is TextNode && node.text().trim().isEmpty()) || (element != null && element.tagName() == "BR")
    }

    fun isUrl(string: String) =
        try {
            URI(string).toURL()
            true
        } catch (_: Exception) {
            false
        }

    /**
     * Check if this node has only whitespace and a single element with a given tag
     * Returns false if the DIV node contains non-empty text nodes
     * or if it contains no element with a given tag or more than 1 element.
     */
    fun hasSingleTagInsideElement(
        element: Element,
        tag: String,
    ): Boolean {
        if (element.children().size != 1 || element
                .children()
                .firstOrNull()
                ?.tagName()
                ?.equals(tag, ignoreCase = true) != true
        ) {
            return false
        }
        return element.childNodes().none { it is TextNode && it.text().isNotBlank() }
    }

    fun getAllNodesWithTag(
        node: Element,
        tagNames: List<String>,
    ): Elements =
        if (tagNames.isNotEmpty()) {
            node.select(tagNames.joinToString(","))
        } else {
            Elements()
        }

    fun textSimilarity(
        textA: String,
        textB: String,
    ): BigDecimal {
        val tokensA =
            textA
                .lowercase()
                .split(Regexps.TOKENIZE)
                .filter { it.isNotBlank() }

        val tokensB =
            textB
                .lowercase()
                .split(Regexps.TOKENIZE)
                .filter { it.isNotBlank() }

        if (tokensA.isEmpty() || tokensB.isEmpty()) {
            return BigDecimal.ZERO
        }

        val uniqTokensB = tokensB.filter { token -> !tokensA.contains(token) }
        val distanceB = uniqTokensB.joinToString(" ").length.toBigDecimal() / tokensB.joinToString(" ").length.toBigDecimal()

        return BigDecimal.ONE - distanceB
    }

    fun headerDuplicatesTitle(
        element: Element,
        articleTitle: String,
    ): Boolean {
        if (element.tagName() != "H1" && element.tagName() != "H2") {
            return false
        }
        val heading = getInnerText(element, false)
        LOGGER.info("Evaluating similarity of header: {}, articleTitle: {}", heading, articleTitle)
        return textSimilarity(articleTitle, heading) > BigDecimal.valueOf(0.75)
    }

    fun removeNodes(
        nodeList: List<Node>,
        filterFn: ((Node) -> Boolean)? = null,
    ) {
        if (filterFn == null) {
            nodeList.forEach { it.remove() }
            return
        }
        for (i in nodeList.size - 1 downTo 0) {
            val node = nodeList[i]
            val parentNode = node.parent()
            if (parentNode != null) {
                if (filterFn(node)) {
                    node.remove()
                }
            }
        }
    }

    fun flagIsActive(
        flags: Int,
        flag: Int,
    ): Boolean = (flags and flag) > 0

    fun getClassWeight(
        element: Element,
        flags: Int,
    ): Int {
        if (flagIsActive(flags, FLAG_WEIGHT_CLASSES).not()) {
            return 0
        }

        var weight = 0

        if (element.className().isNotBlank()) {
            if (Regexps.NEGATIVE.containsMatchIn(element.className())) {
                weight -= 25
            }
            if (Regexps.POSITIVE.containsMatchIn(element.className())) {
                weight += 25
            }
        }

        if (element.id().isNotBlank()) {
            if (Regexps.NEGATIVE.containsMatchIn(element.id())) {
                weight -= 25
            }
            if (Regexps.POSITIVE.containsMatchIn(element.id())) {
                weight += 25
            }
        }

        return weight
    }

    fun cleanHeaders(
        element: Element,
        flags: Int,
    ) {
        val headingNodes = element.select("H1,H2")
        removeNodes(headingNodes) { node ->
            val shouldRemove = getClassWeight(node as Element, flags) < 0
            if (shouldRemove) {
                LOGGER.info("Removing header with low class weight: {}", node)
            }
            shouldRemove
        }
    }

    fun getTextDensity(
        element: Element,
        tags: List<String>,
    ): BigDecimal {
        val textLength = getInnerText(element, true).length.toBigDecimal()
        if (textLength == BigDecimal.ZERO) return BigDecimal.ZERO
        val childrenLength =
            element
                .select(tags.joinToString(","))
                .sumOf { getInnerText(it, true).length.toBigDecimal() }
        return childrenLength / textLength
    }
}
