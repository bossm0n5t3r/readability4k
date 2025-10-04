package me.bossm0n5t3r.readability4k

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.bossm0n5t3r.readability4k.Regexps.IMAGE_EXTENSION_REGEX
import me.bossm0n5t3r.readability4k.Regexps.IMAGE_URL_REGEX
import me.bossm0n5t3r.readability4k.Regexps.SRCSET_CANDIDATE_REGEX
import org.jsoup.Jsoup
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

    fun List<Node>.everyNode(predicate: (Node) -> Boolean): Boolean = this.all(predicate)

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
        p: ReadabilityProperties,
    ): Boolean {
        if (element.tagName() != "H1" && element.tagName() != "H2") {
            return false
        }
        val heading = getInnerText(element, false)
        LOGGER.info("Evaluating similarity of header: {}, articleTitle: {}", heading, p.articleTitle)
        return textSimilarity(p.articleTitle, heading) > BigDecimal.valueOf(0.75)
    }

    fun removeNodes(
        elements: Elements,
        filterFn: ((Element) -> Boolean)? = null,
    ) {
        val nodeList = elements.toList()
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
        p: ReadabilityProperties,
        flag: Int,
    ): Boolean = (p.flags and flag) > 0

    fun removeFlag(
        p: ReadabilityProperties,
        flag: Int,
    ) {
        p.flags = p.flags and flag.inv()
    }

    fun getClassWeight(
        element: Element,
        p: ReadabilityProperties,
    ): Int {
        if (flagIsActive(p, FLAG_WEIGHT_CLASSES).not()) {
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
        p: ReadabilityProperties,
    ) {
        val headingNodes = getAllNodesWithTag(element, listOf("h1", "h2"))
        removeNodes(headingNodes) { element ->
            val shouldRemove = getClassWeight(element, p) < 0
            if (shouldRemove) {
                LOGGER.info("Removing header with low class weight: {}", element)
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
        articleContent: Element,
        p: ReadabilityProperties,
    ) {
        val document = p.document
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

        if (DEPRECATED_SIZE_ATTRIBUTE_ELEMS.contains(element.tagName())) {
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
        p: ReadabilityProperties,
    ) {
        val preservedClasses =
            node
                .attr("class")
                .takeIf { it.isNotBlank() }
                ?.split(Regex("\\s+"))
                ?.filter { it.isNotBlank() && it in p.classesToPreserve }
                ?.joinToString(" ")
                ?.takeIf { it.isNotEmpty() }

        if (preservedClasses != null) {
            node.attr("class", preservedClasses)
        } else {
            node.removeAttr("class")
        }

        node.children().forEach { cleanClasses(it, p) }
    }

    fun postProcessContent(
        articleContent: Element,
        p: ReadabilityProperties,
    ) {
        fixRelativeUris(articleContent, p)
        simplifyNestedElements(articleContent)
        if (!p.keepClasses) {
            cleanClasses(articleContent, p)
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

    fun isSingleImage(node: Element?): Boolean {
        var current = node

        while (current != null) {
            if (current.tagName().equals("IMG", ignoreCase = true)) {
                return true
            }

            if (current.children().size != 1 || current.text().trim().isNotEmpty()) {
                return false
            }

            current = current.children().firstOrNull()
        }

        return false
    }

    fun cleanMatchedNodes(
        element: Element,
        filter: (Element, String) -> Boolean,
    ) {
        val endOfSearchMarkerNode = getNextNode(element, true)
        var next = getNextNode(element)

        while (next != null && next != endOfSearchMarkerNode) {
            val classAndId = "${next.className()} ${next.id()}".trim()

            next =
                if (filter(next, classAndId)) {
                    removeAndGetNext(next)
                } else {
                    getNextNode(next)
                }
        }
    }

    fun getArticleTitle(document: Document): String {
        fun wordCount(str: String): Int = if (str.trim().isEmpty()) 0 else str.split(Regex("\\s+")).size

        var curTitle = ""
        var origTitle = ""

        try {
            curTitle = document.title().trim()
            origTitle = curTitle

            if (curTitle.isEmpty()) {
                document.getElementsByTag("title").firstOrNull()?.let { titleElement ->
                    curTitle = getInnerText(titleElement)
                    origTitle = curTitle
                }
            }
        } catch (e: Exception) {
        }

        var titleHadHierarchicalSeparators = false

        val titleSeparators = """\|\-–—\\\/>»"""
        val separatorRegex = Regex("""\s[$titleSeparators]\s""")
        val hierarchicalSeparatorRegex = Regex("""\s[\\/>»]\s""")

        if (separatorRegex.containsMatchIn(curTitle)) {
            titleHadHierarchicalSeparators = hierarchicalSeparatorRegex.containsMatchIn(curTitle)

            val allSeparators = separatorRegex.findAll(origTitle).toList()
            if (allSeparators.isNotEmpty()) {
                val lastSeparator = allSeparators.last()
                curTitle = origTitle.take(lastSeparator.range.first)

                if (wordCount(curTitle) < 3) {
                    curTitle =
                        origTitle.replace(
                            Regex("""^[^$titleSeparators]*[$titleSeparators]""", RegexOption.IGNORE_CASE),
                            "",
                        )
                }
            }
        } else if (curTitle.contains(": ")) {
            val headings = getAllNodesWithTag(document, listOf("h1", "h2"))
            val trimmedTitle = curTitle.trim()
            val match =
                headings.someNode { heading ->
                    heading.text().trim() == trimmedTitle
                }

            if (!match) {
                val lastColonIndex = origTitle.lastIndexOf(":")
                if (lastColonIndex != -1) {
                    curTitle = origTitle.substring(lastColonIndex + 1).trim()
                    if (wordCount(curTitle) < 3) {
                        curTitle = origTitle.substring(origTitle.indexOf(":") + 1).trim()
                    } else {
                        val firstColonIndex = origTitle.indexOf(":")
                        if (firstColonIndex != -1 && wordCount(origTitle.take(firstColonIndex)) > 5) {
                            curTitle = origTitle
                        }
                    }
                }
            }
        } else if (curTitle.length !in 15..150) {
            val h1Elements = document.getElementsByTag("h1")

            h1Elements.singleOrNull()?.let {
                curTitle = getInnerText(it)
            }
        }

        curTitle = curTitle.trim().replace(Regexps.NORMALIZE, " ")

        val curTitleWordCount = wordCount(curTitle)
        if (curTitleWordCount <= 4 &&
            (
                !titleHadHierarchicalSeparators ||
                    curTitleWordCount != wordCount(origTitle.replace(separatorRegex, "")) - 1
            )
        ) {
            curTitle = origTitle
        }

        return curTitle
    }

    fun prepDocument(document: Document) {
        removeNodes(getAllNodesWithTag(document, listOf("style")))

        replaceBrs(document.body())

        replaceNodeTags(getAllNodesWithTag(document, listOf("font")), "SPAN")
    }

    fun getRowAndColumnCount(table: Element): Pair<Int, Int> {
        var rows = 0
        var columns = 0
        val trs = table.getElementsByTag("tr")

        for (tr in trs) {
            val rowspan = tr.attr("rowspan").toIntOrNull() ?: 1
            rows += rowspan

            var columnsInThisRow = 0
            val cells = tr.getElementsByTag("td")

            for (cell in cells) {
                val colspan = cell.attr("colspan").toIntOrNull() ?: 1
                columnsInThisRow += colspan
            }
            columns = maxOf(columns, columnsInThisRow)
        }

        return rows to columns
    }

    fun hasAncestorTag(
        node: Element,
        tagName: String,
        maxDepth: Int = 3,
        filterFn: ((Element) -> Boolean)? = null,
    ): Boolean {
        val normalizedTagName = tagName.uppercase()
        var depth = 0
        var currentNode: Element? = node

        while (currentNode?.parent() != null) {
            if (maxDepth in 1..<depth) {
                return false
            }

            val parent = currentNode.parent()
            if (parent?.tagName()?.uppercase() == normalizedTagName && (filterFn == null || filterFn(parent))) {
                return true
            }

            currentNode = parent
            depth++
        }

        return false
    }

    fun clean(
        e: Element,
        tag: String,
        p: ReadabilityProperties,
    ) {
        val isEmbed = tag in setOf("object", "embed", "iframe")

        removeNodes(getAllNodesWithTag(e, listOf(tag))) { element ->
            if (isEmbed) {
                if (element.attributes().any { p.allowedVideoRegex.containsMatchIn(it.value) }) {
                    return@removeNodes false
                }

                if (element.tagName() == "object" && p.allowedVideoRegex.containsMatchIn(element.html())) {
                    return@removeNodes false
                }
            }
            true
        }
    }

    fun getNodeAncestors(
        node: Element,
        maxDepth: Int = 0,
    ): List<Element> {
        var i = 0
        val ancestors = mutableListOf<Element>()
        var currentNode: Element? = node

        while (currentNode?.parent() != null) {
            val parent = currentNode.parent()
            requireNotNull(parent) { "Parent node should not be null" }
            ancestors.add(parent)

            if (maxDepth > 0 && ++i == maxDepth) {
                break
            }

            currentNode = parent
        }

        return ancestors
    }

    fun unescapeHtmlEntities(str: String?): String? {
        if (str.isNullOrBlank()) {
            return str
        }

        return str
            .replace(Regex("&(quot|amp|apos|lt|gt);")) { matchResult ->
                val tag = matchResult.groupValues[1]
                HTML_ESCAPE_MAP[tag] ?: matchResult.value
            }.replace(Regex("&#(?:x([0-9a-f]+)|([0-9]+));", RegexOption.IGNORE_CASE)) { matchResult ->
                val hex = matchResult.groupValues[1]
                val numStr = matchResult.groupValues[2]

                val num =
                    if (hex.isNotEmpty()) {
                        hex.toInt(16)
                    } else {
                        numStr.toInt(10)
                    }

                val validNum =
                    when {
                        num == 0 || num > 0x10FFFF || (num in 0xD800..0xDFFF) -> 0xFFFD
                        else -> num
                    }

                String(Character.toChars(validNum))
            }
    }

    fun initializeNode(
        node: Element,
        p: ReadabilityProperties,
    ) {
        node.setContentScore()

        val tagName = node.tagName().uppercase()

        val contentScore =
            when (tagName) {
                "DIV" -> 5
                "PRE", "TD", "BLOCKQUOTE" -> 3
                "ADDRESS", "OL", "UL", "DL", "DD", "DT", "LI", "FORM" -> -3
                "H1", "H2", "H3", "H4", "H5", "H6", "TH" -> -5
                else -> 0
            } + getClassWeight(node, p)

        node.setContentScore(contentScore.toDouble())
    }

    fun markDataTables(root: Element) {
        val tables = root.getElementsByTag("table")

        for (table in tables) {
            val role = table.attr("role")
            if (role == "presentation") {
                table.attr("_readabilityDataTable", "false")
                continue
            }

            val datatable = table.attr("datatable")
            if (datatable == "0") {
                table.attr("_readabilityDataTable", "false")
                continue
            }

            val summary = table.attr("summary")
            if (summary.isNotBlank()) {
                table.attr("_readabilityDataTable", "true")
                continue
            }

            val caption = table.getElementsByTag("caption").firstOrNull()
            if (caption != null && caption.childNodes().isNotEmpty()) {
                table.attr("_readabilityDataTable", "true")
                continue
            }

            val dataTableDescendants = setOf("col", "colgroup", "tfoot", "thead", "th")
            val descendantExists = { tag: String ->
                table.getElementsByTag(tag).isNotEmpty()
            }

            if (dataTableDescendants.any(descendantExists)) {
                table.attr("_readabilityDataTable", "true")
                continue
            }

            if (table.getElementsByTag("table").isNotEmpty()) {
                table.attr("_readabilityDataTable", "false")
                continue
            }

            val (rows, columns) = getRowAndColumnCount(table)

            if (columns == 1 || rows == 1) {
                table.attr("_readabilityDataTable", "false")
                continue
            }

            if (rows >= 10 || columns > 4) {
                table.attr("_readabilityDataTable", "true")
                continue
            }

            table.attr("_readabilityDataTable", (rows * columns > 10).toString())
        }
    }

    fun unwrapNoscriptImages(doc: Element) {
        val imageSourceAttributes = setOf("src", "srcset", "data-src", "data-srcset")
        doc.getElementsByTag("img").toList().forEach { img ->
            for (attr in img.attributes()) {
                if (attr.key in imageSourceAttributes) return@forEach
                if (IMAGE_EXTENSION_REGEX.containsMatchIn(attr.value)) return@forEach
            }
            img.remove()
        }

        doc.getElementsByTag("noscript").toList().forEach { noscript ->
            if (!isSingleImage(noscript)) {
                return@forEach
            }

            val tmp = Jsoup.parseBodyFragment(noscript.html())
            val prevElement = noscript.previousElementSibling()

            if (prevElement != null && isSingleImage(prevElement)) {
                var prevImg = prevElement
                if (prevImg.tagName() != "img") {
                    prevImg = prevElement.getElementsByTag("img").firstOrNull()
                }
                requireNotNull(prevImg) { "Previous element should not be null" }

                val newImg = tmp.getElementsByTag("img").firstOrNull()
                requireNotNull(newImg) { "New image should not be null" }

                for (attr in prevImg.attributes()) {
                    if (attr.value.isEmpty()) {
                        continue
                    }

                    if (attr.key == "src" || attr.key == "srcset" || IMAGE_EXTENSION_REGEX.containsMatchIn(attr.value)) {
                        if (newImg.attr(attr.key) == attr.value) {
                            continue
                        }

                        var attrName = attr.key
                        if (newImg.hasAttr(attrName)) {
                            attrName = "data-old-$attrName"
                        }
                        newImg.attr(attrName, attr.value)
                    }
                }

                prevElement.replaceWith(newImg)
                noscript.remove()
            }
        }
    }

    fun fixLazyImages(root: Element) {
        getAllNodesWithTag(root, listOf("img", "picture", "figure")).forEach { elem ->
            val srcAttr = elem.attr("src")
            if (Regexps.B64_DATA_URL.containsMatchIn(srcAttr)) {
                val parts = Regexps.B64_DATA_URL.find(srcAttr)

                if (parts?.groupValues[1] != "image/svg+xml") {
                    val srcCouldBeRemoved =
                        elem.attributes().any {
                            it.key != "src" && IMAGE_EXTENSION_REGEX.containsMatchIn(it.value)
                        }

                    if (srcCouldBeRemoved) {
                        val dataUrlHeaderLength = parts?.groupValues[0]?.length ?: 0
                        val b64length = srcAttr.length - dataUrlHeaderLength
                        if (b64length < 133) {
                            elem.removeAttr("src")
                        }
                    }
                }
            }

            val srcsetAttr = elem.attr("srcset")
            if ((elem.hasAttr("src") || (srcsetAttr.isNotBlank() && srcsetAttr != "null")) &&
                !elem.className().contains("lazy", ignoreCase = true)
            ) {
                return@forEach
            }

            for (attr in elem.attributes()) {
                if (attr.key in setOf("src", "srcset", "alt")) {
                    continue
                }

                val copyTo =
                    when {
                        SRCSET_CANDIDATE_REGEX.containsMatchIn(attr.value) -> "srcset"
                        IMAGE_URL_REGEX.matches(attr.value) -> "src"
                        else -> null
                    }

                if (copyTo != null) {
                    when (elem.tagName().lowercase()) {
                        "img", "picture" -> elem.attr(copyTo, attr.value)
                        "figure" -> {
                            if (getAllNodesWithTag(elem, listOf("img", "picture")).isEmpty()) {
                                val newImg = Element("img").attr(copyTo, attr.value)
                                elem.appendChild(newImg)
                            }
                        }
                    }
                }
            }
        }
    }

    fun getArticleMetadata(
        doc: Document,
        jsonld: Map<String, String>,
    ): Map<String, String?> {
        val metadata = mutableMapOf<String, String?>()
        val values = mutableMapOf<String, String>()
        val metaElements = doc.getElementsByTag("meta")

        metaElements.forEach { element ->
            val content = element.attr("content")
            if (content.isBlank()) {
                return@forEach
            }

            val elementProperty = element.attr("property")
            val elementName = element.attr("name")
            var matches: MatchResult? = null
            var name: String? = null

            if (elementProperty.isNotBlank()) {
                matches = Regexps.PROPERTY_PATTERN.find(elementProperty)
                if (matches != null) {
                    name =
                        matches.groupValues
                            .first()
                            .lowercase()
                            .replace("\\s".toRegex(), "")
                    values[name] = content.trim()
                }
            }

            if (matches == null && elementName.isNotBlank() && Regexps.NAME_PATTERN.containsMatchIn(elementName)) {
                name = elementName.lowercase().replace("\\s".toRegex(), "").replace(".", ":")
                values[name] = content.trim()
            }
        }

        val title =
            jsonld["title"]
                ?: values["dc:title"]
                ?: values["dcterm:title"]
                ?: values["og:title"]
                ?: values["weibo:article:title"]
                ?: values["weibo:webpage:title"]
                ?: values["title"]
                ?: values["twitter:title"]
                ?: values["parsely-title"]
                ?: getArticleTitle(doc)

        val articleAuthor = values["article:author"]?.takeIf { !isUrl(it) }

        val byline =
            jsonld["byline"]
                ?: values["dc:creator"]
                ?: values["dcterm:creator"]
                ?: values["author"]
                ?: values["parsely-author"]
                ?: articleAuthor

        val excerpt =
            jsonld["excerpt"]
                ?: values["dc:description"]
                ?: values["dcterm:description"]
                ?: values["og:description"]
                ?: values["weibo:article:description"]
                ?: values["weibo:webpage:description"]
                ?: values["description"]
                ?: values["twitter:description"]

        val siteName = jsonld["siteName"] ?: values["og:site_name"]

        val publishedTime =
            jsonld["datePublished"]
                ?: values["article:published_time"]
                ?: values["parsely-pub-date"]

        metadata["title"] = unescapeHtmlEntities(title)
        metadata["byline"] = unescapeHtmlEntities(byline)
        metadata["excerpt"] = unescapeHtmlEntities(excerpt)
        metadata["siteName"] = unescapeHtmlEntities(siteName)
        metadata["publishedTime"] = unescapeHtmlEntities(publishedTime)

        return metadata
    }

    fun cleanConditionally(
        e: Element,
        tag: String,
        p: ReadabilityProperties,
    ) {
        if (!flagIsActive(p, FLAG_CLEAN_CONDITIONALLY)) {
            return
        }

        val isDataTable: (Element) -> Boolean = { element ->
            element.hasAttr("_readabilityDataTable")
        }

        removeNodes(getAllNodesWithTag(e, listOf(tag))) { element ->
            if (tag == "table" && isDataTable(element)) return@removeNodes false
            if (hasAncestorTag(element, "table", -1) { isDataTable(it) }) return@removeNodes false
            if (hasAncestorTag(element, "code")) return@removeNodes false
            if (element.getElementsByTag("table").any { isDataTable(it) }) return@removeNodes false

            val weight = getClassWeight(element, p)
            if (weight < 0) {
                return@removeNodes true
            }

            if (getCharCount(element, ",") < 10) {
                var isList = tag == "ul" || tag == "ol"
                if (!isList) {
                    val listLength =
                        getAllNodesWithTag(element, listOf("ul", "ol"))
                            .sumOf { getInnerText(it).length }
                    val elementTextLength = getInnerText(element).length
                    if (elementTextLength > 0) {
                        isList = listLength.toDouble() / elementTextLength > 0.9
                    }
                }

                val pCount = element.getElementsByTag("p").size
                val imgCount = element.getElementsByTag("img").size
                val liCount = element.getElementsByTag("li").size - 100
                val inputCount = element.getElementsByTag("input").size
                val headingDensity = getTextDensity(element, listOf("h1", "h2", "h3", "h4", "h5", "h6"))
                val innerText = getInnerText(element)

                var embedCount = 0
                val embeds = getAllNodesWithTag(element, listOf("object", "embed", "iframe"))
                for (embed in embeds) {
                    val hasAllowedVideo =
                        embed.attributes().any { p.allowedVideoRegex.containsMatchIn(it.value) } ||
                            (embed.tagName() == "object" && p.allowedVideoRegex.containsMatchIn(embed.html()))
                    if (hasAllowedVideo) {
                        return@removeNodes false
                    }
                    embedCount++
                }

                if (Regexps.AD_WORDS.containsMatchIn(innerText) || Regexps.LOADING_WORDS.containsMatchIn(innerText)) {
                    return@removeNodes true
                }

                val contentLength = innerText.length
                val linkDensity = getLinkDensity(element)
                val textishTags = listOf("span", "li", "td") + DIV_TO_P_ELEMS
                val textDensity = getTextDensity(element, textishTags)
                val isFigureChild = hasAncestorTag(element, "figure")

                val shouldRemove =
                    run {
                        val errors = mutableListOf<String>()

                        if (!isFigureChild && imgCount > 1 && (pCount.toDouble() / imgCount) < 0.5) {
                            errors.add("Bad p to img ratio (img=$imgCount, p=$pCount)")
                        }
                        if (!isList && liCount > pCount) {
                            errors.add("Too many li's outside of a list. (li=$liCount > p=$pCount)")
                        }
                        if (inputCount > (pCount / 3)) {
                            errors.add("Too many inputs per p. (input=$inputCount, p=$pCount)")
                        }
                        if (!isList && !isFigureChild && headingDensity < 0.9.toBigDecimal() && contentLength < 25 &&
                            (imgCount == 0 || imgCount > 2) &&
                            linkDensity > BigDecimal.ZERO
                        ) {
                            errors.add("Suspiciously short. (headingDensity=$headingDensity, img=$imgCount, linkDensity=$linkDensity)")
                        }
                        if (!isList && weight < 25 && linkDensity > (0.2.toBigDecimal() + p.linkDensityModifier)) {
                            errors.add("Low weight and a little linky. (linkDensity=$linkDensity)")
                        }
                        if (weight >= 25 && linkDensity > (0.5.toBigDecimal() + p.linkDensityModifier)) {
                            errors.add("High weight and mostly links. (linkDensity=$linkDensity)")
                        }
                        if ((embedCount == 1 && contentLength < 75) || embedCount > 1) {
                            errors.add("Suspicious embed. (embedCount=$embedCount, contentLength=$contentLength)")
                        }
                        if (imgCount == 0 && textDensity == BigDecimal.ZERO) {
                            errors.add("No useful content. (img=0, textDensity=$textDensity)")
                        }

                        errors.isNotEmpty()
                    }

                if (shouldRemove && isList) {
                    val isSimpleImageList =
                        element.children().all { it.childrenSize() <= 1 } &&
                            element.getElementsByTag("li").size == imgCount
                    if (isSimpleImageList) {
                        return@removeNodes false
                    }
                }

                return@removeNodes shouldRemove
            }

            false
        }
    }

    val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    private val SIMILARITY_THRESHOLD = 0.75.toBigDecimal()

    fun getJSONLD(doc: Document): Map<String, String> {
        val scripts = getAllNodesWithTag(doc, listOf("script"))

        var metadata: MutableMap<String, String>? = null

        for (jsonLdElement in scripts) {
            if (metadata != null) break
            if (jsonLdElement.attr("type") != "application/ld+json") {
                continue
            }

            try {
                val content = jsonLdElement.data().replace(Regexps.CDATA_REGEX, "")

                var parsed = json.parseToJsonElement(content)

                if (parsed is JsonArray) {
                    parsed = parsed.firstOrNull { element ->
                        (element as? JsonObject)
                            ?.get("@type")
                            ?.let { type ->
                                (type as? JsonPrimitive)
                                    ?.contentOrNull
                                    ?.let { Regexps.JSON_LD_ARTICLE_TYPES.containsMatchIn(it) }
                            }
                            ?: false
                    } ?: continue
                }

                if (parsed !is JsonObject) continue

                val matches =
                    when (val context = parsed["@context"]) {
                        is JsonPrimitive -> context.contentOrNull?.let { Regexps.SCHEMA_DOT_ORG_REGEX.containsMatchIn(it) } == true
                        is JsonObject -> {
                            val vocab = context["@vocab"]
                            vocab is JsonPrimitive && vocab.contentOrNull?.let { Regexps.SCHEMA_DOT_ORG_REGEX.containsMatchIn(it) } == true
                        }
                        else -> false
                    }

                if (!matches) continue

                var finalParsed = parsed
                if (parsed["@type"] == null && parsed["@graph"] is JsonArray) {
                    finalParsed = (parsed["@graph"] as JsonArray)
                        .firstOrNull { element ->
                            val typeContentOrEmpty =
                                (element as? JsonObject)
                                    ?.get("@type")
                                    ?.let { it as? JsonPrimitive }
                                    ?.contentOrNull
                                    ?: ""
                            Regexps.JSON_LD_ARTICLE_TYPES.containsMatchIn(typeContentOrEmpty)
                        }?.jsonObject ?: continue
                }

                val typeMatches =
                    (finalParsed["@type"] as? JsonPrimitive)
                        ?.contentOrNull
                        ?.let { Regexps.JSON_LD_ARTICLE_TYPES.containsMatchIn(it) }
                        ?: false

                if (!typeMatches) continue

                metadata = mutableMapOf()

                val name = finalParsed["name"]?.jsonPrimitive?.contentOrNull
                val headline = finalParsed["headline"]?.jsonPrimitive?.contentOrNull

                if (name != null && headline != null && name != headline) {
                    val title = getArticleTitle(doc)
                    val nameMatches = textSimilarity(name, title) > SIMILARITY_THRESHOLD
                    val headlineMatches = textSimilarity(headline, title) > SIMILARITY_THRESHOLD

                    metadata["title"] =
                        when {
                            headlineMatches && !nameMatches -> headline
                            else -> name
                        }
                } else if (name != null) {
                    metadata["title"] = name.trim()
                } else if (headline != null) {
                    metadata["title"] = headline.trim()
                }

                when (val author = finalParsed["author"]) {
                    is JsonObject -> {
                        author["name"]?.jsonPrimitive?.contentOrNull?.let {
                            metadata["byline"] = it.trim()
                        }
                    }
                    is JsonArray -> {
                        val authors =
                            author
                                .mapNotNull {
                                    (it as? JsonObject)
                                        ?.get("name")
                                        ?.jsonPrimitive
                                        ?.contentOrNull
                                        ?.trim()
                                }.filter { it.isNotEmpty() }

                        if (authors.isNotEmpty()) {
                            metadata["byline"] = authors.joinToString(", ")
                        }
                    }

                    else -> {}
                }

                finalParsed["description"]?.jsonPrimitive?.contentOrNull?.let {
                    metadata["excerpt"] = it.trim()
                }

                val publisher = finalParsed["publisher"]
                if (publisher is JsonObject) {
                    publisher["name"]?.jsonPrimitive?.contentOrNull?.let {
                        metadata["siteName"] = it.trim()
                    }
                }

                finalParsed["datePublished"]?.jsonPrimitive?.contentOrNull?.let {
                    metadata["datePublished"] = it.trim()
                }
            } catch (e: Exception) {
                LOGGER.warn("Failed to parse JSON-LD: ${e.message}")
            }
        }

        return metadata ?: emptyMap()
    }

    fun prepArticle(
        articleContent: Element,
        p: ReadabilityProperties,
    ) {
        cleanStyles(articleContent)
        markDataTables(articleContent)
        fixLazyImages(articleContent)

        cleanConditionally(articleContent, "form", p)
        cleanConditionally(articleContent, "fieldset", p)
        clean(articleContent, "object", p)
        clean(articleContent, "embed", p)
        clean(articleContent, "footer", p)
        clean(articleContent, "link", p)
        clean(articleContent, "aside", p)

        val shareElementThreshold = DEFAULT_CHAR_THRESHOLD

        articleContent.children().forEach { topCandidate ->
            cleanMatchedNodes(topCandidate) { node, matchString ->
                Regexps.SHARE_ELEMENTS.containsMatchIn(matchString) &&
                    node.text().length < shareElementThreshold
            }
        }

        clean(articleContent, "iframe", p)
        clean(articleContent, "input", p)
        clean(articleContent, "textarea", p)
        clean(articleContent, "select", p)
        clean(articleContent, "button", p)
        cleanHeaders(articleContent, p)

        cleanConditionally(articleContent, "table", p)
        cleanConditionally(articleContent, "ul", p)
        cleanConditionally(articleContent, "div", p)

        replaceNodeTags(getAllNodesWithTag(articleContent, listOf("h1")), "h2")

        removeNodes(getAllNodesWithTag(articleContent, listOf("p"))) { paragraph ->
            val contentElementCount = getAllNodesWithTag(paragraph, listOf("img", "embed", "object", "iframe")).size
            contentElementCount == 0 && getInnerText(paragraph, normalizeSpaces = false).isEmpty()
        }

        getAllNodesWithTag(articleContent, listOf("br")).forEach { br ->
            val next = nextNode(br.nextElementSibling())
            if (next != null && next is Element && next.tagName() == "p") {
                br.remove()
            }
        }

        getAllNodesWithTag(articleContent, listOf("table")).forEach { table ->
            val tbody =
                if (hasSingleTagInsideElement(table, "tbody")) {
                    table.firstElementChild()
                } else {
                    table
                } ?: return@forEach

            if (hasSingleTagInsideElement(tbody, "tr")) {
                val row = tbody.firstElementChild() ?: return@forEach
                if (hasSingleTagInsideElement(row, "td")) {
                    val cell = row.firstElementChild() ?: return@forEach
                    val newTag =
                        if (cell.childNodes().everyNode(::isPhrasingContent)) {
                            "p"
                        } else {
                            "div"
                        }
                    val replacementCell = setNodeTag(cell, newTag)
                    table.replaceWith(replacementCell)
                }
            }
        }
    }

    private fun Element.getContentScore(): Double {
        return this.attr("data-readability-content-score").toDoubleOrNull() ?: 0.0
    }

    private fun Element.setContentScore(score: Double = 0.0) {
        this.attr("data-readability-content-score", score.toString())
    }
}
