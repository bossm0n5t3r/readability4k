package me.bossm0n5t3r.readability4k

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.bossm0n5t3r.readability4k.ReadabilityUtils.everyNode
import me.bossm0n5t3r.readability4k.Regexps.IMAGE_EXTENSION_REGEX
import me.bossm0n5t3r.readability4k.Regexps.IMAGE_URL_REGEX
import me.bossm0n5t3r.readability4k.Regexps.SRCSET_CANDIDATE_REGEX
import me.bossm0n5t3r.readability4k.jsdom.Document
import me.bossm0n5t3r.readability4k.jsdom.Element
import me.bossm0n5t3r.readability4k.jsdom.Node
import me.bossm0n5t3r.readability4k.jsdom.NodeType
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
            element.textContent.replace(Regexps.NORMALIZE, " ")
        } else {
            element.textContent
        }

    /**
     * Calculate link density of an element
     */
    fun getLinkDensity(element: Element): BigDecimal {
        val textLength = getInnerText(element).length
        if (textLength == 0) return BigDecimal.ZERO
        val linkLength =
            element.getElementsByTagName("a").sumOf { linkNode ->
                val href = linkNode.getAttribute("href").orEmpty()
                val coefficient =
                    if (href.isNotBlank() && href.matches(Regexps.HASH_URL)) BigDecimal.valueOf(0.3) else BigDecimal.ONE
                getInnerText(linkNode).length.toBigDecimal() * coefficient
            }
        return linkLength / textLength.toBigDecimal()
    }

    fun List<Node>.everyNode(predicate: (Node) -> Boolean): Boolean = this.all(predicate)

    fun List<Element>.someNode(predicate: (Element) -> Boolean): Boolean = this.any(predicate)

    /**
     * Determine if a node qualifies as phrasing content.
     * https://developer.mozilla.org/en-US/docs/Web/Guide/HTML/Content_categories#Phrasing_content
     */
    fun isPhrasingContent(node: Node): Boolean {
        val element = node as? Element
        val tagName = element?.tagName?.lowercase()
        return node.nodeType == NodeType.TEXT_NODE ||
            tagName in PHRASING_ELEMS ||
            ((tagName == "a" || tagName == "del" || tagName == "ins") && element.children.all { isPhrasingContent(it) })
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
        return (
            node.nodeType == NodeType.TEXT_NODE &&
                node.textContent
                    .trim()
                    .isEmpty()
        ) || (node.nodeType == NodeType.ELEMENT_NODE && element != null && element.tagName.equals("br", ignoreCase = true))
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
        if (element.children.size != 1 || element.children
                .firstOrNull()
                ?.tagName
                ?.equals(tag, ignoreCase = true) != true
        ) {
            return false
        }
        return element.childNodes.none { it.nodeType == NodeType.TEXT_NODE && Regexps.HAS_CONTENT.containsMatchIn(it.textContent) }
    }

    fun getAllNodesWithTag(
        document: Document,
        tagNames: List<String>,
    ): List<Element> = tagNames.flatMap { document.getElementsByTagName(it) }

    fun getAllNodesWithTag(
        node: Element,
        tagNames: List<String>,
    ): List<Element> = tagNames.flatMap { node.getElementsByTagName(it) }

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
        val distanceB =
            uniqTokensB.joinToString(" ").length.toBigDecimal() / tokensB.joinToString(" ").length.toBigDecimal()

        return BigDecimal.ONE - distanceB
    }

    fun headerDuplicatesTitle(
        element: Element,
        p: ReadabilityProperties,
    ): Boolean {
        if (element.tagName.equals("h1", ignoreCase = true).not() && element.tagName.equals("h2", ignoreCase = true).not()) {
            return false
        }
        val heading = getInnerText(element, false)
        LOGGER.info("Evaluating similarity of header: {}, articleTitle: {}", heading, p.articleTitle)
        val articleTitleInProperties =
            requireNotNull(p.articleTitle) {
                "articleTitle should not be null"
            }
        return textSimilarity(articleTitleInProperties, heading) > BigDecimal.valueOf(0.75)
    }

    fun removeNodes(
        elements: List<Element>,
        filterFn: ((Element) -> Boolean)? = null,
    ) {
        val nodeList = elements.toList()
        if (filterFn == null) {
            nodeList.forEach { it.remove() }
            return
        }
        for (i in nodeList.size - 1 downTo 0) {
            val node = nodeList[i]
            val parentNode = node.parentNode
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

        if (element.className.isNotBlank()) {
            if (Regexps.NEGATIVE.containsMatchIn(element.className)) {
                weight -= 25
            }
            if (Regexps.POSITIVE.containsMatchIn(element.className)) {
                weight += 25
            }
        }

        if (element.id.isNotBlank()) {
            if (Regexps.NEGATIVE.containsMatchIn(element.id)) {
                weight -= 25
            }
            if (Regexps.POSITIVE.containsMatchIn(element.id)) {
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
        val rel = element.getAttribute("rel")
        val itemprop = element.getAttribute("itemprop").orEmpty()
        val bylineLength = element.textContent.trim().length

        val hasAuthorRel = rel == "author"
        val hasAuthorItemprop = itemprop.isNotEmpty() && itemprop.contains("author")
        val matchesPattern = matchString.isNotEmpty() && Regexps.BYLINE.containsMatchIn(matchString)

        val hasAuthorInfo = hasAuthorRel || hasAuthorItemprop || matchesPattern

        return hasAuthorInfo && bylineLength in 1..99
    }

    fun removeScripts(doc: Document) {
        removeNodes(getAllNodesWithTag(doc, listOf("script", "noscript")))
    }

    fun hasChildBlockElement(element: Element): Boolean =
        element.children.someNode {
            DIV_TO_P_ELEMS.contains(it.tagName) || hasChildBlockElement(it)
        }

    fun isElementWithoutContent(node: Node): Boolean {
        if (node.nodeType != NodeType.ELEMENT_NODE) return false
        val element = node as Element
        if (element.textContent.trim().isNotEmpty()) return false

        val children = element.children
        return children.isEmpty() || children.size == element.getElementsByTagName("br").size + element.getElementsByTagName("hr").size
    }

    fun isProbablyVisible(element: Element): Boolean {
        val style = element.getAttribute("style")
        if (style != null && (style.contains("visibility:hidden") || style.contains("visibility: hidden"))) {
            return false
        }
        return isNodeVisible(element)
    }

    fun getNextNode(
        element: Element,
        ignoreSelfAndKids: Boolean = false,
    ): Element? {
        if (!ignoreSelfAndKids && element.firstElementChild != null) {
            return element.firstElementChild
        }

        element.nextElementSibling?.let { return it }

        var parent = element.parentNode
        while (parent != null && parent is Element && parent.nextElementSibling == null) {
            parent = parent.parentNode
        }

        return (parent as? Element)?.nextElementSibling
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
        val baseURI = document.baseURI
        val documentURI = document.documentURI

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
            val href = link.getAttribute("href").orEmpty()
            if (href.isNotBlank()) {
                if (href.startsWith("javascript:")) {
                    val childNodes = link.childNodes
                    if (childNodes.size == 1 && childNodes.first().nodeType == NodeType.TEXT_NODE) {
                        val textNode = document.createTextNode(link.textContent)
                        link.parentNode?.replaceChild(textNode, link)
                    } else {
                        val container = document.createElement("span")
                        val children = link.childNodes.toList()
                        for (child in children) {
                            child.remove()
                            container.appendChild(child)
                        }
                        link.parentNode?.replaceChild(container, link)
                    }
                } else {
                    link.setAttribute("href", toAbsoluteURI(href))
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
            val src = media.getAttribute("src").orEmpty()
            val poster = media.getAttribute("poster").orEmpty()
            val srcset = media.getAttribute("srcset").orEmpty()

            if (src.isNotBlank()) {
                media.setAttribute("src", toAbsoluteURI(src))
            }

            if (poster.isNotBlank()) {
                media.setAttribute("poster", toAbsoluteURI(poster))
            }

            if (srcset.isNotBlank()) {
                val newSrcset =
                    srcset.replace(Regexps.SRCSET_URL) { matchResult ->
                        val url = matchResult.groupValues[1]
                        val descriptor = matchResult.groupValues[2]
                        val comma = matchResult.groupValues[3]
                        toAbsoluteURI(url) + descriptor + comma
                    }
                media.setAttribute("srcset", newSrcset)
            }
        }
    }

    fun cleanStyles(element: Element?) {
        if (element == null || element.tagName.lowercase() == "svg") {
            return
        }

        PRESENTATIONAL_ATTRIBUTES.forEach { element.removeAttribute(it) }

        if (DEPRECATED_SIZE_ATTRIBUTE_ELEMS.contains(element.tagName)) {
            element.removeAttribute("width")
            element.removeAttribute("height")
        }

        element.children.forEach { cleanStyles(it) }
    }

    fun simplifyNestedElements(articleContent: Element) {
        var node = articleContent as Element?

        while (node != null) {
            val parent = node.parentNode

            if (parent != null &&
                (
                    node.tagName.equals("DIV", ignoreCase = true) ||
                        node.tagName
                            .equals("SECTION", ignoreCase = true)
                ) &&
                !(node.id.isNotEmpty() && node.id.startsWith("readability"))
            ) {
                if (isElementWithoutContent(node)) {
                    node = removeAndGetNext(node)
                    continue
                }
                if (
                    hasSingleTagInsideElement(node, "DIV") ||
                    hasSingleTagInsideElement(node, "SECTION")
                ) {
                    val childElement = node.children.firstOrNull()

                    if (childElement != null) {
                        node.attributes.forEach { attribute ->
                            childElement.setAttribute(attribute.name, attribute.value)
                        }

                        // 부모 노드를 자식 노드로 교체
                        node.parentNode?.replaceChild(childElement, node)
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
                .getAttribute("class")
                .orEmpty()
                .takeIf { it.isNotBlank() }
                ?.split(Regex("\\s+"))
                ?.filter { it.isNotBlank() && it in p.classesToPreserve }
                ?.joinToString(" ")
                ?.takeIf { it.isNotEmpty() }

        if (preservedClasses != null) {
            node.setAttribute("class", preservedClasses)
        } else {
            node.removeAttribute("class")
        }

        node.children.forEach { cleanClasses(it, p) }
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
        node.localName = tag.lowercase()
        node.tagName = tag.uppercase()
        return node
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
            next.nodeType != NodeType.ELEMENT_NODE &&
            Regexps.WHITESPACE.containsMatchIn(next.textContent)
        ) {
            next = next.nextSibling
        }
        return next
    }

    fun replaceBrs(
        elem: Element,
        p: ReadabilityProperties,
    ) {
        val brElements = getAllNodesWithTag(elem, listOf("br"))

        brElements.forEach { br ->
            var next: Node? = br.nextSibling
            var replaced = false

            while (true) {
                next = nextNode(next)
                if (next == null || next !is Element || !next.tagName.equals("BR", ignoreCase = true)) {
                    break
                }

                replaced = true
                val brSibling = next.nextSibling
                next.remove()
                next = brSibling
            }

            if (replaced) {
                val pElement = p.document.createElement("p")
                br.parentNode?.replaceChild(pElement, br)

                next = pElement.nextSibling
                while (next != null) {
                    if (next is Element && next.tagName.equals("BR", ignoreCase = true)) {
                        val nextElem = nextNode(next.nextSibling) as? Element
                        if (nextElem != null && nextElem.tagName.equals("BR", ignoreCase = true)) {
                            break
                        }
                    }

                    if (!isPhrasingContent(next)) {
                        break
                    }

                    val sibling = next.nextSibling
                    pElement.appendChild(next)
                    next = sibling
                }

                var pElementLastChild = pElement.lastChild
                while (pElementLastChild != null && isWhiteSpace(pElementLastChild)) {
                    pElementLastChild.remove()
                    pElementLastChild = pElement.lastChild
                }

                pElement.parentNode?.let { parent ->
                    val element = parent as? Element
                    if (element != null && element.tagName.equals("P", ignoreCase = true)) {
                        setNodeTag(element, "DIV")
                    }
                }
            }
        }
    }

    fun isSingleImage(node: Element?): Boolean {
        var current = node

        while (current != null) {
            if (current.tagName.equals("IMG", ignoreCase = true)) {
                return true
            }

            if (current.children.size != 1 || current.textContent.trim().isNotEmpty()) {
                return false
            }

            current = current.children.firstOrNull()
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
            val classAndId = "${next.className} ${next.id}".trim()

            next =
                if (filter(next, classAndId)) {
                    removeAndGetNext(next)
                } else {
                    getNextNode(next)
                }
        }
    }

    fun getArticleTitle(p: ReadabilityProperties): String {
        val document = p.document

        fun wordCount(str: String): Int = if (str.trim().isEmpty()) 0 else str.split(Regex("\\s+")).size

        var curTitle = ""
        var origTitle = ""

        try {
            curTitle = document.title.trim()
            origTitle = curTitle

            if (curTitle.isEmpty()) {
                document.getElementsByTagName("title").firstOrNull()?.let { titleElement ->
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
                    heading.textContent.trim() == trimmedTitle
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
            val h1Elements = document.getElementsByTagName("h1")

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

    fun prepDocument(
        document: Document,
        p: ReadabilityProperties,
    ) {
        removeNodes(getAllNodesWithTag(document, listOf("style")))

        document.body?.let { replaceBrs(it, p) }

        replaceNodeTags(getAllNodesWithTag(document, listOf("font")), "SPAN")
    }

    fun getRowAndColumnCount(table: Element): Pair<Int, Int> {
        var rows = 0
        var columns = 0
        val trs = table.getElementsByTagName("tr")

        for (tr in trs) {
            val rowspan = tr.getAttribute("rowspan")?.toIntOrNull() ?: 1
            rows += rowspan

            var columnsInThisRow = 0
            val cells = tr.getElementsByTagName("td")

            for (cell in cells) {
                val colspan = cell.getAttribute("colspan")?.toIntOrNull() ?: 1
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

        while (currentNode?.parentNode != null) {
            if (maxDepth in 1..<depth) {
                return false
            }

            val parent = currentNode.parentNode as? Element
            if (parent?.tagName?.uppercase() == normalizedTagName && (filterFn == null || filterFn(parent))) {
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
                if (element.attributes.any { p.allowedVideoRegex.containsMatchIn(it.value) }) {
                    return@removeNodes false
                }

                if (element.tagName == "object" && p.allowedVideoRegex.containsMatchIn(element.innerHTML)) {
                    return@removeNodes false
                }
            }
            true
        }
    }

    fun getNodeAncestors(
        node: Node,
        maxDepth: Int = 0,
    ): List<Node> {
        var i = 0
        val ancestors = mutableListOf<Node>()
        var currentNode: Node? = node

        while (currentNode?.parentNode != null) {
            val parent = currentNode.parentNode
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
                        hex.toLong(16)
                    } else {
                        numStr.toLong(10)
                    }

                val validNum =
                    when {
                        num == 0L || num > 0x10FFFF || (num in 0xD800..0xDFFF) -> 0xFFFD
                        else -> num
                    }

                String(Character.toChars(validNum.toInt()))
            }
    }

    fun initializeNode(
        node: Element,
        p: ReadabilityProperties,
    ) {
        node.setContentScore()

        val tagName = node.tagName.uppercase()

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
        val tables = root.getElementsByTagName("table")

        for (table in tables) {
            val role = table.getAttribute("role")
            if (role == "presentation") {
                table.setAttribute("_readabilityDataTable", "false")
                continue
            }

            val datatable = table.getAttribute("datatable")
            if (datatable == "0") {
                table.setAttribute("_readabilityDataTable", "false")
                continue
            }

            val summary = table.getAttribute("summary").orEmpty()
            if (summary.isNotBlank()) {
                table.setAttribute("_readabilityDataTable", "true")
                continue
            }

            val caption = table.getElementsByTagName("caption").firstOrNull()
            if (caption != null && caption.childNodes.isNotEmpty()) {
                table.setAttribute("_readabilityDataTable", "true")
                continue
            }

            val dataTableDescendants = setOf("col", "colgroup", "tfoot", "thead", "th")
            val descendantExists = { tag: String ->
                table.getElementsByTagName(tag).isNotEmpty()
            }

            if (dataTableDescendants.any(descendantExists)) {
                table.setAttribute("_readabilityDataTable", "true")
                continue
            }

            if (table.getElementsByTagName("table").isNotEmpty()) {
                table.setAttribute("_readabilityDataTable", "false")
                continue
            }

            val (rows, columns) = getRowAndColumnCount(table)

            if (columns == 1 || rows == 1) {
                table.setAttribute("_readabilityDataTable", "false")
                continue
            }

            if (rows >= 10 || columns > 4) {
                table.setAttribute("_readabilityDataTable", "true")
                continue
            }

            table.setAttribute("_readabilityDataTable", (rows * columns > 10).toString())
        }
    }

    fun unwrapNoscriptImages(doc: Document) {
        val imageSourceAttributes = setOf("src", "srcset", "data-src", "data-srcset")
        doc.getElementsByTagName("img").toList().forEach { img ->
            for (attr in img.attributes) {
                if (attr.name in imageSourceAttributes) return@forEach
                if (IMAGE_EXTENSION_REGEX.containsMatchIn(attr.value)) return@forEach
            }
            img.remove()
        }

        doc.getElementsByTagName("noscript").toList().forEach { noscript ->
            if (!isSingleImage(noscript)) {
                return@forEach
            }

            val tmp = doc.createElement("div")
            tmp.innerHTML = noscript.innerHTML
            val prevElement = noscript.previousElementSibling

            if (prevElement != null && isSingleImage(prevElement)) {
                var prevImg = prevElement
                if (prevImg.tagName != "img") {
                    prevImg = prevElement.getElementsByTagName("img").firstOrNull()
                }
                requireNotNull(prevImg) { "Previous element should not be null" }

                val newImg = tmp.getElementsByTagName("img").firstOrNull()
                requireNotNull(newImg) { "New image should not be null" }

                for (attr in prevImg.attributes) {
                    if (attr.value.isEmpty()) {
                        continue
                    }

                    if (attr.name == "src" || attr.name == "srcset" || IMAGE_EXTENSION_REGEX.containsMatchIn(attr.value)) {
                        if (newImg.getAttribute(attr.name) == attr.value) {
                            continue
                        }

                        var attrName = attr.name
                        if (newImg.hasAttribute(attrName)) {
                            attrName = "data-old-$attrName"
                        }
                        newImg.setAttribute(attrName, attr.value)
                    }
                }

                tmp.firstElementChild?.let {
                    noscript.parentNode?.replaceChild(it, prevElement)
                }
            }
        }
    }

    fun fixLazyImages(
        root: Element,
        p: ReadabilityProperties,
    ) {
        getAllNodesWithTag(root, listOf("img", "picture", "figure")).forEach { elem ->
            val srcAttr = elem.getAttribute("src")
            if (srcAttr != null && Regexps.B64_DATA_URL.containsMatchIn(srcAttr)) {
                val parts = Regexps.B64_DATA_URL.find(srcAttr)

                if (parts?.groupValues[1] != "image/svg+xml") {
                    val srcCouldBeRemoved =
                        elem.attributes.any {
                            it.name != "src" && IMAGE_EXTENSION_REGEX.containsMatchIn(it.value)
                        }

                    if (srcCouldBeRemoved) {
                        val dataUrlHeaderLength = parts?.groupValues[0]?.length ?: 0
                        val b64length = srcAttr.length - dataUrlHeaderLength
                        if (b64length < 133) {
                            elem.removeAttribute("src")
                        }
                    }
                }
            }

            val srcsetAttr = elem.getAttribute("srcset")
            if ((elem.hasAttribute("src") || (srcsetAttr != null && srcsetAttr != "null")) &&
                !elem.className.contains("lazy", ignoreCase = true)
            ) {
                return@forEach
            }

            for (attr in elem.attributes) {
                if (attr.name in setOf("src", "srcset", "alt")) {
                    continue
                }

                val copyTo =
                    when {
                        SRCSET_CANDIDATE_REGEX.containsMatchIn(attr.value) -> "srcset"
                        IMAGE_URL_REGEX.matches(attr.value) -> "src"
                        else -> null
                    }

                if (copyTo != null) {
                    when (elem.tagName.lowercase()) {
                        "img", "picture" -> elem.setAttribute(copyTo, attr.value)
                        "figure" -> {
                            if (getAllNodesWithTag(elem, listOf("img", "picture")).isEmpty()) {
                                val img = p.document.createElement("img")
                                img.setAttribute(copyTo, attr.value)
                                elem.appendChild(img)
                            }
                        }
                    }
                }
            }
        }
    }

    fun getArticleMetadata(
        jsonld: Map<String, String>,
        p: ReadabilityProperties,
    ): MutableMap<String, String?> {
        val metadata = mutableMapOf<String, String?>()
        val values = mutableMapOf<String, String>()
        val metaElements = p.document.getElementsByTagName("meta")

        metaElements.forEach { element ->
            val content = element.getAttribute("content")
            if (content.isNullOrBlank()) {
                return@forEach
            }

            val elementProperty = element.getAttribute("property")
            val elementName = element.getAttribute("name")
            var matches: MatchResult? = null
            var name: String? = null

            if (elementProperty != null) {
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

            if (matches == null && elementName != null && Regexps.NAME_PATTERN.containsMatchIn(elementName)) {
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
                ?: getArticleTitle(p)

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
            element.hasAttribute("_readabilityDataTable")
        }

        removeNodes(getAllNodesWithTag(e, listOf(tag))) { element ->
            if (tag == "table" && isDataTable(element)) return@removeNodes false
            if (hasAncestorTag(element, "table", -1) { isDataTable(it) }) return@removeNodes false
            if (hasAncestorTag(element, "code")) return@removeNodes false
            if (element.getElementsByTagName("table").any { isDataTable(it) }) return@removeNodes false

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

                val pCount = element.getElementsByTagName("p").size
                val imgCount = element.getElementsByTagName("img").size
                val liCount = element.getElementsByTagName("li").size - 100
                val inputCount = element.getElementsByTagName("input").size
                val headingDensity = getTextDensity(element, listOf("h1", "h2", "h3", "h4", "h5", "h6"))
                val innerText = getInnerText(element)

                var embedCount = 0
                val embeds = getAllNodesWithTag(element, listOf("object", "embed", "iframe"))
                for (embed in embeds) {
                    val hasAllowedVideo =
                        embed.attributes.any { p.allowedVideoRegex.containsMatchIn(it.value) } ||
                            (embed.tagName == "object" && p.allowedVideoRegex.containsMatchIn(embed.innerHTML))
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
                        element.children.all { it.children.size <= 1 } &&
                            element.getElementsByTagName("li").size == imgCount
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

    fun getJSONLD(p: ReadabilityProperties): Map<String, String> {
        val doc = p.document
        val scripts = getAllNodesWithTag(doc, listOf("script"))

        var metadata: MutableMap<String, String>? = null

        for (jsonLdElement in scripts) {
            if (metadata != null) break
            if (jsonLdElement.getAttribute("type") != "application/ld+json") {
                continue
            }

            try {
                val content = jsonLdElement.textContent.replace(Regexps.CDATA_REGEX, "")

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
                            vocab is JsonPrimitive && vocab.contentOrNull?.let {
                                Regexps.SCHEMA_DOT_ORG_REGEX.containsMatchIn(
                                    it,
                                )
                            } == true
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
                    val title = getArticleTitle(p)
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
        fixLazyImages(articleContent, p)

        cleanConditionally(articleContent, "form", p)
        cleanConditionally(articleContent, "fieldset", p)
        clean(articleContent, "object", p)
        clean(articleContent, "embed", p)
        clean(articleContent, "footer", p)
        clean(articleContent, "link", p)
        clean(articleContent, "aside", p)

        val shareElementThreshold = DEFAULT_CHAR_THRESHOLD

        articleContent.children.forEach { topCandidate ->
            cleanMatchedNodes(topCandidate) { node, matchString ->
                Regexps.SHARE_ELEMENTS.containsMatchIn(matchString) &&
                    node.textContent.length < shareElementThreshold
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
            val next = nextNode(br.nextElementSibling)
            if (next != null && next is Element && next.tagName == "p") {
                br.remove()
            }
        }

        getAllNodesWithTag(articleContent, listOf("table")).forEach { table ->
            val tbody =
                if (hasSingleTagInsideElement(table, "tbody")) {
                    table.firstElementChild
                } else {
                    table
                } ?: return@forEach

            if (hasSingleTagInsideElement(tbody, "tr")) {
                val row = tbody.firstElementChild ?: return@forEach
                if (hasSingleTagInsideElement(row, "td")) {
                    val cell = row.firstElementChild ?: return@forEach
                    val newTag =
                        if (cell.childNodes.everyNode(::isPhrasingContent)) {
                            "p"
                        } else {
                            "div"
                        }
                    val replacementCell = setNodeTag(cell, newTag)
                    table.parentNode?.replaceChild(replacementCell, table)
                }
            }
        }
    }

    private const val DATA_READABILITY_CONTENT_SCORE = "data-readability-content-score"

    private fun Element.hasContentScore(): Boolean = this.hasAttribute(DATA_READABILITY_CONTENT_SCORE)

    private fun Element.getContentScore(): Double = this.getAttribute(DATA_READABILITY_CONTENT_SCORE)?.toDoubleOrNull() ?: 0.0

    private fun Element.setContentScore(score: Double = 0.0) {
        this.setAttribute(DATA_READABILITY_CONTENT_SCORE, score.toString())
    }

    fun grabArticle(
        page: Element? = null,
        p: ReadabilityProperties,
    ): Element? {
        LOGGER.info("**** grabArticle ****")
        val document = p.document
        val isPaging = page != null
        val currentPage = page ?: document.body

        if (currentPage == null) {
            LOGGER.info("No body found in document. Abort.")
            return null
        }

        val pageCacheHtml = currentPage.innerHTML

        while (true) {
            LOGGER.info("Starting grabArticle loop")
            val stripUnlikelyCandidates = flagIsActive(p, FLAG_STRIP_UNLIKELYS)

            val elementsToScore = mutableListOf<Element>()
            var node: Element? = document.documentElement
            var shouldRemoveTitleHeader = true

            while (node != null) {
                if (node.tagName == "html") {
                    p.articleLang = node.getAttribute("lang")
                }

                val matchString = "${node.className} ${node.id}"

                if (!isProbablyVisible(node)) {
                    LOGGER.info("Removing hidden node - {}", matchString)
                    node = removeAndGetNext(node)
                    continue
                }

                if (node.getAttribute("aria-modal") == "true" && node.getAttribute("role") == "dialog") {
                    node = removeAndGetNext(node)
                    continue
                }

                if (p.articleByline.isNullOrEmpty() && p.metadata["byline"].isNullOrEmpty() &&
                    isValidByLine(
                        node,
                        matchString,
                    )
                ) {
                    val endOfSearchMarkerNode = getNextNode(node, true)
                    var next = getNextNode(node)
                    var itemPropNameNode: Element? = null

                    while (next != null && next != endOfSearchMarkerNode) {
                        val itemprop = next.getAttribute("itemprop")
                        if (itemprop != null && itemprop.split(Regex("\\s+")).contains("name")) {
                            itemPropNameNode = next
                            break
                        }
                        next = getNextNode(next)
                    }

                    p.articleByline = (itemPropNameNode ?: node).textContent.trim()
                    node = removeAndGetNext(node)
                    continue
                }

                if (shouldRemoveTitleHeader && headerDuplicatesTitle(node, p)) {
                    LOGGER.info("Removing header: {}, {}", node.textContent.trim(), p.articleTitle?.trim())
                    shouldRemoveTitleHeader = false
                    node = removeAndGetNext(node)
                    continue
                }

                if (stripUnlikelyCandidates) {
                    if (Regexps.UNLIKELY_CANDIDATES.containsMatchIn(matchString) &&
                        !Regexps.OK_MAYBE_ITS_A_CANDIDATE.containsMatchIn(matchString) &&
                        !hasAncestorTag(node, "table") &&
                        !hasAncestorTag(node, "code") &&
                        node.tagName != "body" &&
                        node.tagName != "a"
                    ) {
                        LOGGER.info("Removing unlikely candidate - {}", matchString)
                        node = removeAndGetNext(node)
                        continue
                    }

                    if (node.getAttribute("role") in UNLIKELY_ROLES) {
                        LOGGER.info("Removing content with role {} - {}", node.getAttribute("role"), matchString)
                        node = removeAndGetNext(node)
                        continue
                    }
                }

                if (node.tagName in setOf("div", "section", "header", "h1", "h2", "h3", "h4", "h5", "h6") &&
                    isElementWithoutContent(node)
                ) {
                    node = removeAndGetNext(node)
                    continue
                }

                if (node.tagName in DEFAULT_TAGS_TO_SCORE) {
                    elementsToScore.add(node)
                }

                if (node.tagName == "div") {
                    var childNode: Node? = node.firstChild

                    while (childNode != null) {
                        val nextSibling = childNode.nextSibling

                        if (isPhrasingContent(childNode)) {
                            val fragment = document.createDocumentFragment()
                            var current: Node = childNode

                            do {
                                fragment.appendChild(current)
                                val next = current.nextSibling ?: break
                                if (!isPhrasingContent(next)) break
                                current = next
                            } while (true)

                            val fragmentFirstChild = fragment.firstChild
                            while (fragmentFirstChild != null && isWhiteSpace(fragmentFirstChild)) {
                                fragmentFirstChild.remove()
                            }
                            val fragmentLastChild = fragment.lastChild
                            while (fragmentLastChild != null && isWhiteSpace(fragmentLastChild)) {
                                fragmentLastChild.remove()
                            }

                            if (fragment.firstChild != null) {
                                val p = document.createElement("p")
                                p.appendChild(fragment)
                                node.insertBefore(p, nextSibling)
                            }
                            childNode = current.nextSibling
                        } else {
                            childNode = nextSibling
                        }
                    }

                    if (hasSingleTagInsideElement(node, "p") &&
                        getLinkDensity(node) < 0.25.toBigDecimal()
                    ) {
                        val newNode = node.children[0]
                        node.parentNode?.replaceChild(newNode, node)
                        node = newNode
                        elementsToScore.add(node)
                    } else if (!hasChildBlockElement(node)) {
                        node = setNodeTag(node, "p")
                        elementsToScore.add(node)
                    }
                }
                node = getNextNode(node)
            }

            val candidates = mutableListOf<Element>()

            elementsToScore.forEach { elementToScore ->
                val parent = elementToScore.parentNode as? Element
                if (parent == null || parent.tagName.isBlank()) {
                    return@forEach
                }

                val innerText = getInnerText(elementToScore)
                if (innerText.length < 25) {
                    return@forEach
                }

                val ancestors = getNodeAncestors(elementToScore, maxDepth = 5)
                if (ancestors.isEmpty()) {
                    return@forEach
                }

                var contentScore = 0.0
                contentScore += 1
                contentScore += innerText.split(Regexps.COMMAS).size
                contentScore += minOf(innerText.length / 100, 3)

                ancestors.forEachIndexed { level, ancestor ->
                    if (ancestor !is Element || ancestor.tagName.isEmpty() || ancestor.parentNode == null ||
                        (ancestor.parentNode as? Element)?.tagName.isNullOrBlank()
                    ) {
                        return@forEachIndexed
                    }

                    if (!ancestor.hasContentScore()) {
                        initializeNode(ancestor, p)
                        candidates.add(ancestor)
                    }

                    val scoreDivider =
                        when (level) {
                            0 -> 1.0
                            1 -> 2.0
                            else -> (level * 3).toDouble()
                        }

                    val currentScore = ancestor.getContentScore()
                    ancestor.setContentScore(currentScore + contentScore / scoreDivider)
                }
            }

            val topCandidates = mutableListOf<Element>()

            candidates.forEach { candidate ->
                val candidateScore = candidate.getContentScore() * (1 - getLinkDensity(candidate).toDouble())
                candidate.setContentScore(candidateScore)

                LOGGER.info("Candidate: {} with score {}", candidate, candidateScore)

                var inserted = false
                for (i in 0 until minOf(p.nbTopCandidates, topCandidates.size)) {
                    val topCandidate = topCandidates[i]
                    val topCandidateContentScore = topCandidate.getContentScore()
                    if (candidateScore > topCandidateContentScore) {
                        topCandidates.add(i, candidate)
                        inserted = true
                        break
                    }
                }
                if (!inserted && topCandidates.size < p.nbTopCandidates) {
                    topCandidates.add(candidate)
                }
                if (topCandidates.size > p.nbTopCandidates) {
                    topCandidates.removeLast()
                }
            }

            var topCandidate = topCandidates.firstOrNull()
            var neededToCreateTopCandidate = false
            var parentOfTopCandidate: Element?

            if (topCandidate == null || topCandidate.tagName == "body") {
                topCandidate = document.createElement("div")
                neededToCreateTopCandidate = true

                var currentPageFirstChild = currentPage.firstChild
                while (currentPageFirstChild != null) {
                    LOGGER.info("Moving child out: {}", currentPageFirstChild)
                    topCandidate.appendChild(currentPageFirstChild)
                    currentPageFirstChild = currentPage.firstChild
                }

                currentPage.appendChild(topCandidate)
                initializeNode(topCandidate, p)
            } else {
                val alternativeCandidateAncestors = mutableListOf<List<Node>>()
                val topCandidateContentScore = topCandidate.getContentScore()

                for (i in 1 until topCandidates.size) {
                    val ithTopCandidate = topCandidates[i]
                    val ithTopCandidateContentScore = ithTopCandidate.getContentScore()
                    if (ithTopCandidateContentScore / topCandidateContentScore >= 0.75) {
                        alternativeCandidateAncestors.add(getNodeAncestors(topCandidates[i]))
                    }
                }

                val minimumTopCandidates = 3
                if (alternativeCandidateAncestors.size >= minimumTopCandidates) {
                    parentOfTopCandidate = topCandidate.parentNode as? Element

                    while (parentOfTopCandidate != null && parentOfTopCandidate.tagName != "body") {
                        var listsContainingThisAncestor = 0

                        for (ancestors in alternativeCandidateAncestors) {
                            if (parentOfTopCandidate in ancestors) {
                                listsContainingThisAncestor++
                            }
                            if (listsContainingThisAncestor >= minimumTopCandidates) {
                                break
                            }
                        }

                        if (listsContainingThisAncestor >= minimumTopCandidates) {
                            topCandidate = parentOfTopCandidate
                            break
                        }
                        parentOfTopCandidate = parentOfTopCandidate.parentNode as? Element
                    }
                }

                if (topCandidate != null && !topCandidate.hasContentScore()) {
                    initializeNode(topCandidate, p)
                }

                parentOfTopCandidate = topCandidate?.parentNode as? Element
                var lastScore = topCandidate?.getContentScore() ?: 0.0
                val scoreThreshold = lastScore / 3

                while (parentOfTopCandidate != null && parentOfTopCandidate.tagName != "body") {
                    if (!parentOfTopCandidate.hasContentScore()) {
                        parentOfTopCandidate = parentOfTopCandidate.parentNode as? Element
                        continue
                    }

                    val parentScore = parentOfTopCandidate.getContentScore()
                    if (parentScore < scoreThreshold) {
                        break
                    }
                    if (parentScore > lastScore) {
                        topCandidate = parentOfTopCandidate
                        break
                    }
                    lastScore = parentOfTopCandidate.getContentScore()
                    parentOfTopCandidate = parentOfTopCandidate.parentNode as? Element
                }

                parentOfTopCandidate = topCandidate?.parentNode as? Element
                while (parentOfTopCandidate != null &&
                    parentOfTopCandidate.tagName != "body" &&
                    parentOfTopCandidate.children.size == 1
                ) {
                    topCandidate = parentOfTopCandidate
                    parentOfTopCandidate = topCandidate.parentNode as? Element
                }

                if (topCandidate != null && !topCandidate.hasContentScore()) {
                    initializeNode(topCandidate, p)
                }
            }

            var articleContent = document.createElement("div")
            if (isPaging) {
                articleContent.id = "readability-content"
            }

            val siblingScoreThreshold =
                maxOf(
                    10.0,
                    (topCandidate?.getContentScore() ?: 0.0) * 0.2,
                )
            parentOfTopCandidate = topCandidate?.parentNode as? Element

            val siblings = parentOfTopCandidate?.children?.toList() ?: emptyList()

            siblings.forEach { sibling ->
                var append = false

                LOGGER.info("Looking at sibling node: {}", sibling)
                val siblingContentScore = sibling.getContentScore()
                LOGGER.info("Sibling has score {}", siblingContentScore)

                if (sibling === topCandidate) {
                    append = true
                } else {
                    var contentBonus = 0.0

                    if (sibling.className == topCandidate?.className && topCandidate.className.isNotEmpty()) {
                        contentBonus += topCandidate.getContentScore() * 0.2
                    }

                    if (sibling.hasContentScore() && sibling.getContentScore() + contentBonus >= siblingScoreThreshold) {
                        append = true
                    } else if (sibling.tagName == "p") {
                        val linkDensity = getLinkDensity(sibling)
                        val nodeContent = getInnerText(sibling)
                        val nodeLength = nodeContent.length

                        if (nodeLength > 80 && linkDensity < 0.25.toBigDecimal()) {
                            append = true
                        } else if (nodeLength in 1..<80 && linkDensity == BigDecimal.ZERO &&
                            Regex("""\.\s|\.$""").containsMatchIn(nodeContent)
                        ) {
                            append = true
                        }
                    }
                }

                if (append) {
                    LOGGER.info("Appending node: {}", sibling)

                    val nodeToAppend =
                        if (sibling.tagName !in ALTER_TO_DIV_EXCEPTIONS) {
                            LOGGER.info("Altering sibling: {} to div.", sibling)
                            setNodeTag(sibling, "div")
                        } else {
                            sibling
                        }

                    nodeToAppend.remove()
                    articleContent.appendChild(nodeToAppend)
                }
            }

            if (p.debug) {
                LOGGER.info("Article content pre-prep: {}", articleContent.innerHTML)
            }

            prepArticle(articleContent, p)

            if (p.debug) {
                LOGGER.info("Article content post-prep: {}", articleContent.innerHTML)
            }

            if (neededToCreateTopCandidate) {
                topCandidate?.id = "readability-page-1"
                topCandidate?.className = "page"
            } else {
                val div = document.createElement("div")
                div.id = "readability-page-1"
                div.className = "page"

                var articleContentFirstChild = articleContent.firstChild
                while (articleContentFirstChild != null) {
                    div.appendChild(articleContentFirstChild)
                    articleContentFirstChild = articleContent.firstChild
                }
                articleContent.appendChild(div)
            }

            if (p.debug) {
                LOGGER.info("Article content after paging: {}", articleContent.innerHTML)
            }

            var parseSuccessful = true
            val textLength = getInnerText(articleContent, true).length

            if (textLength < p.charThreshold) {
                parseSuccessful = false
                currentPage.innerHTML = pageCacheHtml

                p.attempts.add(ReadabilityProperties.Attempt(articleContent, textLength))

                if (flagIsActive(p, FLAG_STRIP_UNLIKELYS)) {
                    removeFlag(p, FLAG_STRIP_UNLIKELYS)
                } else if (flagIsActive(p, FLAG_WEIGHT_CLASSES)) {
                    removeFlag(p, FLAG_WEIGHT_CLASSES)
                } else if (flagIsActive(p, FLAG_CLEAN_CONDITIONALLY)) {
                    removeFlag(p, FLAG_CLEAN_CONDITIONALLY)
                } else {
                    p.attempts.sortByDescending { it.textLength }

                    if (p.attempts.firstOrNull()?.textLength == 0) {
                        return null
                    }

                    articleContent = p.attempts.first().articleContent
                    parseSuccessful = true
                }
            }

            if (parseSuccessful) {
                val ancestors =
                    listOfNotNull(parentOfTopCandidate, topCandidate) +
                        (parentOfTopCandidate?.let { getNodeAncestors(it) } ?: emptyList())

                for (ancestor in ancestors) {
                    if ((ancestor as? Element)?.tagName.isNullOrEmpty()) {
                        continue
                    }
                    val dir = ancestor.getAttribute("dir")
                    if (dir != null) {
                        p.articleDir = dir
                        break
                    }
                }

                return articleContent
            }
        }
    }
}
