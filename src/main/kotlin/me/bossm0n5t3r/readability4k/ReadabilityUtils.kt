package me.bossm0n5t3r.readability4k

import org.jsoup.nodes.Document
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
        elements: Elements,
        filterFn: ((Node) -> Boolean)? = null,
    ) = removeNodes(elements.toList(), filterFn)

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

    fun removeFlag(
        flags: Int,
        flag: Int,
    ) = flags and flag.inv()

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
        val headingNodes = getAllNodesWithTag(element, listOf("h1", "h2"))
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
            getAllNodesWithTag(element, tags)
                .sumOf { getInnerText(it, true).length.toBigDecimal() }
        return childrenLength / textLength
    }

    fun isValidByLine(
        element: Element,
        matchString: String,
    ): Boolean {
        val rel = element.attr("rel")
        val itemprop = element.attr("itemprop")
        val bylineLength = element.text().trim().length

        val hasAuthorRel = rel == "author"
        val hasAuthorItemprop = itemprop.isNotEmpty() && itemprop.contains("author")
        val matchesPattern = matchString.isNotEmpty() && Regexps.BYLINE.containsMatchIn(matchString)

        val hasAuthorInfo = hasAuthorRel || hasAuthorItemprop || matchesPattern

        return hasAuthorInfo && bylineLength in 1..99
    }

    fun removeScripts(doc: Element) {
        removeNodes(getAllNodesWithTag(doc, listOf("script", "noscript")))
    }

    fun hasChildBlockElement(element: Element): Boolean =
        element.children().someNode {
            DIV_TO_P_ELEMS.contains(it.tagName()) || hasChildBlockElement(it)
        }

    fun isElementWithoutContent(element: Element): Boolean {
        if (element.text().trim().isNotEmpty()) return false

        val children = element.children()
        return children.isEmpty() || children.size == element.getElementsByTag("br").size + element.getElementsByTag("hr").size
    }

    fun isProbablyVisible(element: Element): Boolean {
        val style = element.attr("style")
        if (style.contains("visibility:hidden") || style.contains("visibility: hidden")) {
            return false
        }
        return isNodeVisible(element)
    }

    fun getNextNode(
        element: Element,
        ignoreSelfAndKids: Boolean = false,
    ): Element? {
        if (!ignoreSelfAndKids && element.children().isNotEmpty()) {
            return element.children().first()
        }

        element.nextElementSibling()?.let { return it }

        var parent = element.parent()
        while (parent != null && parent.nextElementSibling() == null) {
            parent = parent.parent()
        }

        return parent?.nextElementSibling()
    }

    fun removeAndGetNext(element: Element): Element? {
        val nextElement = getNextNode(element, true)
        element.remove()
        return nextElement
    }

    fun fixRelativeUris(
        document: Document,
        articleContent: Element,
    ) {
        val baseURI = document.baseUri()
        val documentURI = document.location()

        fun toAbsoluteURI(uri: String): String {
            if (baseURI == documentURI && uri.startsWith("#")) {
                return uri
            }

            try {
                return URI(baseURI).resolve(uri).toString()
            } catch (ex: Exception) {
            }
            return uri
        }

        val links = getAllNodesWithTag(articleContent, listOf("a"))
        links.forEach { link ->
            val href = link.attr("href")
            if (href.isNotBlank()) {
                if (href.startsWith("javascript:")) {
                    val childNodes = link.childNodes()
                    if (childNodes.size == 1 && childNodes.first() is TextNode) {
                        val textNode = TextNode(link.text())
                        link.replaceWith(textNode)
                    } else {
                        val container = Element("span")
                        val children = link.childNodes().toList()
                        for (child in children) {
                            child.remove()
                            container.appendChild(child)
                        }
                        link.replaceWith(container)
                    }
                } else {
                    link.attr("href", toAbsoluteURI(href))
                }
            }
        }

        val medias =
            getAllNodesWithTag(
                articleContent,
                listOf(
                    "img",
                    "picture",
                    "figure",
                    "video",
                    "audio",
                    "source",
                ),
            )

        medias.forEach { media ->
            val src = media.attr("src")
            val poster = media.attr("poster")
            val srcset = media.attr("srcset")

            if (src.isNotBlank()) {
                media.attr("src", toAbsoluteURI(src))
            }

            if (poster.isNotBlank()) {
                media.attr("poster", toAbsoluteURI(poster))
            }

            if (srcset.isNotBlank()) {
                val newSrcset =
                    srcset.replace(Regexps.SRCSET_URL) { matchResult ->
                        val url = matchResult.groupValues[1]
                        val descriptor = matchResult.groupValues[2]
                        val comma = matchResult.groupValues[3]
                        toAbsoluteURI(url) + descriptor + comma
                    }
                media.attr("srcset", newSrcset)
            }
        }
    }

    fun cleanStyles(element: Element?) {
        if (element == null || element.tagName().lowercase() == "svg") {
            return
        }

        PRESENTATIONAL_ATTRIBUTES.forEach { element.removeAttr(it) }

        if (DEPRECATED_SIZE_ATTRIBUTE_ELEMS.contains(element.tagName().lowercase())) {
            element.removeAttr("width")
            element.removeAttr("height")
        }

        element.children().forEach { cleanStyles(it) }
    }

    fun simplifyNestedElements(articleContent: Element) {
        var node = articleContent as Element?

        while (node != null) {
            val parent = node.parent()

            if (parent != null &&
                (node.tagName().equals("DIV", ignoreCase = true) || node.tagName().equals("SECTION", ignoreCase = true)) &&
                !(node.id().isNotEmpty() && node.id().startsWith("readability"))
            ) {
                if (isElementWithoutContent(node)) {
                    node = removeAndGetNext(node)
                    continue
                }
                if (
                    hasSingleTagInsideElement(node, "DIV") ||
                    hasSingleTagInsideElement(node, "SECTION")
                ) {
                    val childElement = node.children().firstOrNull()

                    if (childElement != null) {
                        node.attributes().forEach { attribute ->
                            childElement.attr(attribute.key, attribute.value)
                        }

                        // 부모 노드를 자식 노드로 교체
                        node.replaceWith(childElement)
                        node = childElement
                        continue
                    }
                }
            }

            node = getNextNode(node)
        }
    }

    fun cleanClasses(
        node: Element,
        classesToPreserve: Set<String>,
    ) {
        val preservedClasses =
            node
                .attr("class")
                .takeIf { it.isNotBlank() }
                ?.split(Regex("\\s+"))
                ?.filter { it.isNotBlank() && it in classesToPreserve }
                ?.joinToString(" ")
                ?.takeIf { it.isNotEmpty() }

        if (preservedClasses != null) {
            node.attr("class", preservedClasses)
        } else {
            node.removeAttr("class")
        }

        node.children().forEach { cleanClasses(it, classesToPreserve) }
    }

    fun postProcessContent(
        articleContent: Element,
        document: Document,
        keepClasses: Boolean,
        classesToPreserve: Set<String>,
    ) {
        fixRelativeUris(document, articleContent)
        simplifyNestedElements(articleContent)
        if (!keepClasses) {
            cleanClasses(articleContent, classesToPreserve)
        }
    }

    fun setNodeTag(
        node: Element,
        tag: String,
    ): Element {
        val replacement = Element(tag)

        val childrenToMove = node.children().toList()
        for (child in childrenToMove) {
            child.remove()
            replacement.appendChild(child)
        }

        val textNodes = node.textNodes()
        for (textNode in textNodes.toList()) {
            textNode.remove()
            replacement.appendChild(textNode)
        }

        for (attribute in node.attributes()) {
            replacement.attr(attribute.key, attribute.value)
        }

        if (node.hasAttr("data-readability")) {
            replacement.attr("data-readability", node.attr("data-readability"))
        }

        node.replaceWith(replacement)

        return replacement
    }

    fun replaceNodeTags(
        nodeList: Elements,
        newTagName: String,
    ) {
        val nodesCopy = nodeList.toList()
        for (node in nodesCopy) {
            setNodeTag(node, newTagName)
        }
    }

    fun replaceNodeTags(
        nodeList: List<Element>,
        newTagName: String,
    ) {
        for (node in nodeList) {
            setNodeTag(node, newTagName)
        }
    }

    fun nextNode(node: Node?): Node? {
        var next = node
        while (next != null &&
            next !is Element &&
            (next is TextNode && Regexps.WHITESPACE.containsMatchIn(next.text()))
        ) {
            next = next.nextSibling()
        }
        return next
    }

    fun replaceBrs(elem: Element) {
        val brElements = getAllNodesWithTag(elem, listOf("br"))

        brElements.forEach { br ->
            var next: Node? = br.nextSibling()
            var replaced = false

            while (true) {
                next = nextNode(next)
                if (next == null || next !is Element ||
                    !next.tagName().equals("BR", ignoreCase = true)
                ) {
                    break
                }

                replaced = true
                val brSibling = next.nextSibling()
                next.remove()
                next = brSibling
            }

            if (replaced) {
                val p = Element("p")
                br.replaceWith(p)

                next = p.nextSibling()
                while (next != null) {
                    if (next is Element && next.tagName().equals("BR", ignoreCase = true)) {
                        val nextElem = nextNode(next.nextSibling())
                        if (nextElem is Element && nextElem.tagName().equals("BR", ignoreCase = true)) {
                            break
                        }
                    }

                    if (!isPhrasingContent(next)) {
                        break
                    }

                    val sibling = next.nextSibling()
                    p.appendChild(next)
                    next = sibling
                }

                while (p.childNodeSize() > 0) {
                    val lastChild = p.childNode(p.childNodeSize() - 1)
                    if (isWhiteSpace(lastChild)) {
                        lastChild.remove()
                    } else {
                        break
                    }
                }

                p.parent()?.let { parent ->
                    if (parent.tagName().equals("P", ignoreCase = true)) {
                        setNodeTag(parent, "DIV")
                    }
                }
            }
        }
    }
}
