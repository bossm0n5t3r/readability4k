package com.m0n5t3r.boss.readability4k

import com.m0n5t3r.boss.readability4k.dom.Attribute
import com.m0n5t3r.boss.readability4k.dom.DOMParser
import com.m0n5t3r.boss.readability4k.dom.Document
import com.m0n5t3r.boss.readability4k.dom.Element
import com.m0n5t3r.boss.readability4k.dom.Node
import com.m0n5t3r.boss.readability4k.dom.NodeType
import com.m0n5t3r.boss.readability4k.dom.NodeUtils.innerHTMLOrNull
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeEmpty
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import java.math.BigDecimal
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonPrimitive

class ReadabilityTest :
    DescribeSpec({
        describe("Readability API") {
            describe("#constructor") {
                val doc = DOMParser().parse("<html><div>yo</div></html>")

                it("should accept a debug option") {
                    Readability(doc).p.debug shouldBe false
                    Readability(doc, ReadabilityOptions(debug = true)).p.debug shouldBe true
                }

                it("should accept a nbTopCandidates option") {
                    Readability(doc).p.nbTopCandidates shouldBe 5
                    Readability(doc, ReadabilityOptions(nbTopCandidates = 42))
                        .p
                        .nbTopCandidates shouldBe 42
                }

                it("should accept a maxElemsToParse option") {
                    Readability(doc).p.maxElemsToParse shouldBe 0
                    Readability(doc, ReadabilityOptions(maxElemsToParse = 42))
                        .p
                        .maxElemsToParse shouldBe 42
                }

                it("should accept a keepClasses option") {
                    Readability(doc).p.keepClasses shouldBe false
                    Readability(doc, ReadabilityOptions(keepClasses = true)).p.keepClasses shouldBe
                        true
                    Readability(doc, ReadabilityOptions(keepClasses = false)).p.keepClasses shouldBe
                        false
                }

                it("should accept a allowedVideoRegex option or default it") {
                    Readability(doc).p.allowedVideoRegex.pattern shouldBe Regexps.VIDEOS.pattern
                    val customRegex = Regex("""//mydomain\.com/.*'""")
                    Readability(doc, ReadabilityOptions(allowedVideoRegex = customRegex))
                        .p
                        .allowedVideoRegex
                        .pattern shouldBe customRegex.pattern
                }

                it("should honor constructor option defaults and overrides") {
                    val default = Readability(doc).p
                    default.debug shouldBe false
                    default.maxElemsToParse shouldBe 0
                    default.nbTopCandidates shouldBe 5
                    default.charThreshold shouldBe 500
                    default.classesToPreserve shouldBe setOf("page")
                    default.keepClasses shouldBe false
                    default.disableJSONLD shouldBe false
                    default.allowedVideoRegex.pattern shouldBe Regexps.VIDEOS.pattern
                    default.linkDensityModifier shouldBe BigDecimal.ZERO

                    val serializerDoc =
                        DOMParser().parse("<html><div>default serializer</div></html>")
                    val serializerElement = serializerDoc.documentElement!!
                    default.serializer(serializerElement) shouldBe serializerElement.innerHTML

                    val override =
                        Readability(
                                doc,
                                ReadabilityOptions(
                                    debug = true,
                                    maxElemsToParse = 42,
                                    nbTopCandidates = 42,
                                    charThreshold = 42,
                                    classesToPreserve = listOf("caption"),
                                    keepClasses = true,
                                    serializer = { "serialized" },
                                    disableJSONLD = true,
                                    allowedVideoRegex = Regex("custom-video"),
                                    linkDensityModifier = BigDecimal("0.25"),
                                ),
                            )
                            .p
                    override.debug shouldBe true
                    override.maxElemsToParse shouldBe 42
                    override.nbTopCandidates shouldBe 42
                    override.charThreshold shouldBe 42
                    override.classesToPreserve shouldBe setOf("page", "caption")
                    override.keepClasses shouldBe true
                    override.serializer(serializerElement) shouldBe "serialized"
                    override.disableJSONLD shouldBe true
                    override.allowedVideoRegex.pattern shouldBe "custom-video"
                    override.linkDensityModifier shouldBe BigDecimal("0.25")
                }
            }

            describe("#parse") {
                val exampleSource = Utils.getTestPages().first().source

                afterTest { unmockkObject(ReadabilityUtils) }

                it("shouldn't parse oversized documents as per configuration") {
                    val doc = DOMParser().parse("<html><div>yo</div></html>")
                    shouldThrow<IllegalArgumentException> {
                            Readability(doc, ReadabilityOptions(maxElemsToParse = 1)).parse()
                        }
                        .message shouldBe "Aborting parsing document; 2 elements found"
                }

                it("should run cleanClasses with default configuration") {
                    mockkObject(ReadabilityUtils)
                    every { ReadabilityUtils.cleanClasses(any(), any()) } just Runs

                    val doc = DOMParser().parse(exampleSource)
                    val parser = Readability(doc)
                    parser.parse()

                    verify(exactly = 1) { ReadabilityUtils.cleanClasses(any(), any()) }
                }

                it("should run cleanClasses when option keepClasses = false") {
                    mockkObject(ReadabilityUtils)
                    every { ReadabilityUtils.cleanClasses(any(), any()) } just Runs

                    val doc = DOMParser().parse(exampleSource)
                    val parser = Readability(doc, ReadabilityOptions(keepClasses = false))
                    parser.parse()

                    verify(exactly = 1) { ReadabilityUtils.cleanClasses(any(), any()) }
                }

                it("shouldn't run cleanClasses when option keepClasses = true") {
                    mockkObject(ReadabilityUtils)
                    every { ReadabilityUtils.cleanClasses(any(), any()) } just Runs

                    val doc = DOMParser().parse(exampleSource)
                    val parser = Readability(doc, ReadabilityOptions(keepClasses = true))
                    parser.parse()

                    verify(exactly = 0) { ReadabilityUtils.cleanClasses(any(), any()) }
                }

                it("should use custom content serializer sent as option") {
                    val doc = DOMParser().parse("<html><body>My cat: <img src=''></body></html>")
                    val expectedContent = "My cat: "
                    val content =
                        Readability(
                                doc,
                                ReadabilityOptions(
                                    serializer = { el -> el.firstChild?.textContent.orEmpty() }
                                ),
                            )
                            .parse()
                            ?.content
                    content shouldBe expectedContent
                }

                @Suppress("ktlint:standard:max-line-length")
                it("should use custom video regex sent as option") {
                    val html =
                        """<html><body><p>Lorem ipsum dolor sit amet, consectetur adipiscing elit. Nunc mollis leo lacus, vitae semper nisl ullamcorper ut.</p>""" +
                            """<iframe src="https://mycustomdomain.com/some-embeds"></iframe></body></html>"""
                    val doc = DOMParser().parse(html)
                    val expectedXhtml =
                        """<div id="readability-page-1" class="page">""" +
                            """<p>Lorem ipsum dolor sit amet, consectetur adipiscing elit. Nunc mollis leo lacus, vitae semper nisl ullamcorper ut.</p>""" +
                            """<iframe src="https://mycustomdomain.com/some-embeds"></iframe>""" +
                            """</div>"""
                    val content =
                        Readability(
                                doc,
                                ReadabilityOptions(
                                    charThreshold = 20,
                                    allowedVideoRegex = Regex(""".*mycustomdomain.com.*"""),
                                ),
                            )
                            .parse()
                            ?.content
                    content shouldBe expectedXhtml
                }

                it("should honor parse() return contract") {
                    val fixture = Utils.getTestPages().single { it.dir == "001" }
                    val doc = DOMParser().parse(fixture.source, "http://fakehost/test/page.html")
                    val result =
                        Readability(doc, ReadabilityOptions(classesToPreserve = listOf("caption")))
                            .parse()

                    result.shouldNotBeNull()
                    result.content.shouldNotBeEmpty()

                    val parsedContentDoc = DOMParser().parse(result.content)
                    val expectedTextContent = parsedContentDoc.textContent
                    result.textContent shouldBe expectedTextContent
                    result.length shouldBe expectedTextContent.length

                    result.title shouldBe
                        fixture.expectedMetadata["title"]
                            ?.takeIf { it != JsonNull }
                            ?.jsonPrimitive
                            ?.content
                    result.byline shouldBe
                        fixture.expectedMetadata["byline"]
                            ?.takeIf { it != JsonNull }
                            ?.jsonPrimitive
                            ?.content
                    result.excerpt shouldBe
                        fixture.expectedMetadata["excerpt"]
                            ?.takeIf { it != JsonNull }
                            ?.jsonPrimitive
                            ?.content
                    result.siteName shouldBe
                        fixture.expectedMetadata["siteName"]
                            ?.takeIf { it != JsonNull }
                            ?.jsonPrimitive
                            ?.content
                    result.dir shouldBe
                        fixture.expectedMetadata["dir"]
                            ?.takeIf { it != JsonNull }
                            ?.jsonPrimitive
                            ?.content
                    result.lang shouldBe
                        fixture.expectedMetadata["lang"]
                            ?.takeIf { it != JsonNull }
                            ?.jsonPrimitive
                            ?.content
                    result.publishedTime shouldBe
                        fixture.expectedMetadata["publishedTime"]
                            ?.takeIf { it != JsonNull }
                            ?.jsonPrimitive
                            ?.content
                }

                it("should normalize relative URIs that escape the origin root") {
                    val html =
                        """<html><body><div><a href="../../../index.html">link</a></div></body></html>"""
                    val doc = DOMParser().parse(html, "http://fakehost/test/page.html")
                    val div = doc.getElementsByTagName("div").first()
                    div.shouldNotBeNull()
                    ReadabilityUtils.fixRelativeUris(
                        div,
                        ReadabilityProperties(doc, ReadabilityOptions()),
                    )
                    val link = div.getElementsByTagName("a").first()
                    link.shouldNotBeNull()
                    link.getAttribute("href") shouldBe "http://fakehost/index.html"
                }

                it("should not call getJSONLD when disableJSONLD is true") {
                    mockkObject(ReadabilityUtils)
                    every { ReadabilityUtils.getJSONLD(any()) } returns emptyMap()

                    val doc = DOMParser().parse(exampleSource)
                    Readability(doc, ReadabilityOptions(disableJSONLD = true)).parse()

                    verify(exactly = 0) { ReadabilityUtils.getJSONLD(any()) }
                }

                it("should fall back to meta tags when JSON-LD values are blank") {
                    val html =
                        """
                        <html>
                        <head>
                            <meta property="og:title" content="Open &amp;amp; Graph" />
                            <meta name="author" content="Meta Author" />
                            <meta name="description" content="Meta excerpt" />
                            <meta property="og:site_name" content="Meta Site" />
                            <meta property="article:published_time" content="2024-01-02" />
                        </head>
                        <body></body>
                        </html>
                        """
                            .trimIndent()
                    val doc = DOMParser().parse(html)
                    val p = ReadabilityProperties(doc, ReadabilityOptions())
                    val metadata = ReadabilityUtils.getArticleMetadata(emptyMap(), p)

                    metadata["title"] shouldBe "Open & Graph"
                    metadata["byline"] shouldBe "Meta Author"
                    metadata["excerpt"] shouldBe "Meta excerpt"
                    metadata["siteName"] shouldBe "Meta Site"
                    metadata["publishedTime"] shouldBe "2024-01-02"
                }

                it("should pick the first article from @graph when root @type is empty") {
                    val html =
                        """
                        <html>
                        <head>
                            <script type="application/ld+json">
                            {
                                "@context": "https://schema.org",
                                "@type": "",
                                "@graph": [
                                    { "@type": "Article", "headline": "Graph title" }
                                ]
                            }
                            </script>
                        </head>
                        <body></body>
                        </html>
                        """
                            .trimIndent()
                    val doc = DOMParser().parse(html)
                    val p = ReadabilityProperties(doc, ReadabilityOptions())
                    val jsonld = ReadabilityUtils.getJSONLD(p)

                    jsonld["title"] shouldBe "Graph title"
                }

                it("should join JSON-LD author array names like the original") {
                    val noBylineHtml =
                        """
                        <html>
                        <head>
                            <script type="application/ld+json">
                            {
                                "@context": "https://schema.org",
                                "@type": "Article",
                                "author": [{}, {"name": "Alice"}]
                            }
                            </script>
                        </head>
                        <body></body>
                        </html>
                        """
                            .trimIndent()
                    val noBylineDoc = DOMParser().parse(noBylineHtml)
                    val noBylineP = ReadabilityProperties(noBylineDoc, ReadabilityOptions())
                    ReadabilityUtils.getJSONLD(noBylineP).containsKey("byline") shouldBe false

                    val withEmptyHtml =
                        """
                        <html>
                        <head>
                            <script type="application/ld+json">
                            {
                                "@context": "https://schema.org",
                                "@type": "Article",
                                "author": [{"name": ""}, {"name": "Alice"}]
                            }
                            </script>
                        </head>
                        <body></body>
                        </html>
                        """
                            .trimIndent()
                    val withEmptyDoc = DOMParser().parse(withEmptyHtml)
                    val withEmptyP = ReadabilityProperties(withEmptyDoc, ReadabilityOptions())
                    ReadabilityUtils.getJSONLD(withEmptyP)["byline"] shouldBe ", Alice"
                }

                it("should fall back to the first paragraph when no excerpt metadata exists") {
                    val paragraph =
                        "First paragraph is retained as the fallback excerpt. ".repeat(20).trim()
                    val html =
                        """
                        <html>
                        <head><title>Title</title></head>
                        <body>
                            <article><p>$paragraph</p></article>
                        </body>
                        </html>
                    """
                            .trimIndent()
                    val doc = DOMParser().parse(html)
                    val result = Readability(doc, ReadabilityOptions(charThreshold = 0)).parse()

                    result.shouldNotBeNull()
                    result.excerpt shouldBe paragraph
                }
            }
        }

        describe("Test pages") {
            val testPages = Utils.getTestPages()

            testPages.forEach { testPage ->
                describe(testPage.dir) {
                    val uri = "http://fakehost/test/page.html"

                    describe("DOMParser") {
                        timeout = 30000L

                        lateinit var result: ReadabilityResult

                        beforeTest {
                            val parser = DOMParser()
                            val doc = parser.parse(testPage.source, uri)
                            if (parser.errorState.isNotBlank()) {
                                error("Parsing this DOM caused errors: ${parser.errorState}")
                            }
                            val reader =
                                Readability(
                                    doc,
                                    ReadabilityOptions(classesToPreserve = listOf("caption")),
                                )
                            result = reader.parse() ?: error("Readability.parse() returned null")
                        }

                        it("${testPage.dir}: should return a result object") {
                            result.content.shouldNotBeEmpty()
                        }

                        it("${testPage.dir}: should extract expected content") {
                            val actualDoc = DOMParser().parse(Utils.prettyPrint(result.content))
                            val expectedDoc =
                                DOMParser().parse(Utils.prettyPrint(testPage.expectedContent))

                            traverseDOM(actualDoc, expectedDoc) { actualNode, expectedNode ->
                                if (actualNode != null && expectedNode != null) {
                                    val actualDesc = nodeStr(actualNode)
                                    val expectedDesc = nodeStr(expectedNode)

                                    if (actualDesc != expectedDesc) {
                                        withClue(findableNodeDesc(actualNode)) {
                                            actualDesc shouldBe expectedDesc
                                        }
                                        return@traverseDOM false
                                    }

                                    if (actualNode.nodeType == NodeType.TEXT_NODE) {
                                        val actualText = htmlTransform(actualNode.textContent)
                                        val expectedText = htmlTransform(expectedNode.textContent)
                                        withClue(findableNodeDesc(actualNode)) {
                                            actualText shouldBe expectedText
                                        }
                                        if (actualText != expectedText) {
                                            return@traverseDOM false
                                        }
                                    } else if (actualNode.nodeType == NodeType.ELEMENT_NODE) {
                                        val actualElement = actualNode as Element
                                        val expectedElement = expectedNode as Element
                                        val actualNodeAttributes = attributesForNode(actualElement)
                                        val expectedNodeAttributes =
                                            attributesForNode(expectedElement)

                                        val desc =
                                            "node ${nodeStr(actualElement)} attributes " +
                                                "(${actualNodeAttributes.joinToString(",")}) should match " +
                                                "(${expectedNodeAttributes.joinToString(",")}) 1"

                                        withClue(desc) {
                                            actualNodeAttributes.size shouldBe
                                                expectedNodeAttributes.size
                                        }

                                        for (i in actualNodeAttributes.indices) {
                                            val attr = actualNodeAttributes[i].name
                                            val actualValue = actualElement.getAttribute(attr)
                                            val expectedValue = expectedElement.getAttribute(attr)

                                            withClue(
                                                "node (${findableNodeDesc(actualNode)}) attribute $attr should match"
                                            ) {
                                                actualValue shouldBe expectedValue
                                            }
                                        }
                                    }
                                } else {
                                    withClue("Should have a node from both DOMs") {
                                        nodeStr(actualNode) shouldBe nodeStr(expectedNode)
                                    }
                                    return@traverseDOM false
                                }
                                true
                            }
                        }

                        it("${testPage.dir}: should extract expected title") {
                            result.title shouldBe
                                testPage.expectedMetadata["title"]
                                    ?.takeIf { it != JsonNull }
                                    ?.jsonPrimitive
                                    ?.content
                        }

                        it("${testPage.dir}: should extract expected byline") {
                            result.byline shouldBe
                                testPage.expectedMetadata["byline"]
                                    ?.takeIf { it != JsonNull }
                                    ?.jsonPrimitive
                                    ?.content
                        }

                        it("${testPage.dir}: should extract expected excerpt") {
                            result.excerpt shouldBe
                                testPage.expectedMetadata["excerpt"]
                                    ?.takeIf { it != JsonNull }
                                    ?.jsonPrimitive
                                    ?.content
                        }

                        it("${testPage.dir}: should extract expected site name") {
                            result.siteName shouldBe
                                testPage.expectedMetadata["siteName"]
                                    ?.takeIf { it != JsonNull }
                                    ?.jsonPrimitive
                                    ?.content
                        }

                        testPage.expectedMetadata["dir"]
                            ?.takeIf { it != JsonNull }
                            ?.let { expectedDir ->
                                it("${testPage.dir}: should extract expected direction") {
                                    result.dir shouldBe expectedDir.jsonPrimitive.content
                                }
                            }

                        testPage.expectedMetadata["lang"]
                            ?.takeIf { it != JsonNull }
                            ?.let { expectedLang ->
                                it("${testPage.dir}: should extract expected language") {
                                    result.lang shouldBe expectedLang.jsonPrimitive.content
                                }
                            }

                        testPage.expectedMetadata["publishedTime"]
                            ?.takeIf { it != JsonNull }
                            ?.let { publishedTime ->
                                it("${testPage.dir}: should extract expected published time") {
                                    result.publishedTime shouldBe
                                        publishedTime.jsonPrimitive.content
                                }
                            }
                    }
                }
            }
        }
    }) {
    companion object {
        private fun htmlTransform(str: String): String = str.replace(Regex("\\s+"), " ")

        private fun nodeStr(node: Node?): String {
            if (node == null) return "(no node)"
            if (node.nodeType == NodeType.TEXT_NODE) {
                return "#text(${htmlTransform(node.textContent)})"
            }
            if (node.nodeType != NodeType.ELEMENT_NODE) {
                return "some other node type: ${node.nodeType} with data ${node.textContent}"
            }
            val element = node as Element
            var rv = element.localName
            if (element.id.isNotEmpty()) rv += "#${element.id}"
            if (element.className.isNotEmpty()) rv += ".(${element.className})"
            return rv
        }

        private fun genPath(node: Node): String {
            if (node is Element && node.id.isNotEmpty()) return "#${node.id}"
            if (node is Element && node.tagName == "BODY") return "body"
            val parent = node.parentNode
            val parentPath = parent?.let { genPath(it) }.orEmpty()
            val index = parent?.childNodes?.indexOf(node)?.plus(1) ?: 0
            return "$parentPath > ${nodeStr(node)}:nth-child($index)"
        }

        private fun findableNodeDesc(node: Node) =
            "${genPath(node)}(in: ``${node.parentNode?.innerHTMLOrNull}``)"

        private fun attributesForNode(node: Node): List<Attribute> =
            (node as? Element)?.attributes.orEmpty()

        fun inOrderTraverse(fromNode: Node?): Node? {
            if (fromNode?.firstChild != null) {
                return fromNode.firstChild
            }

            var node: Node? = fromNode
            while (node != null && node.nextSibling == null) {
                node = node.parentNode
            }

            return node?.nextSibling
        }

        fun inOrderIgnoreEmptyTextNodes(fromNode: Node?): Node? {
            var node = fromNode

            do {
                node = inOrderTraverse(node)
            } while (
                node != null &&
                    node.nodeType == NodeType.TEXT_NODE &&
                    node.textContent.trim().isEmpty()
            )

            return node
        }

        private fun traverseDOM(
            actualDOM: Document,
            expectedDOM: Document,
            callback: (actualNode: Node?, expectedNode: Node?) -> Boolean,
        ) {
            var actualNode = actualDOM.documentElement ?: actualDOM.childNodes.firstOrNull()
            var expectedNode = expectedDOM.documentElement ?: expectedDOM.childNodes.firstOrNull()

            while (actualNode != null || expectedNode != null) {
                if (!callback(actualNode, expectedNode)) {
                    break
                }
                actualNode = inOrderIgnoreEmptyTextNodes(actualNode)
                expectedNode = inOrderIgnoreEmptyTextNodes(expectedNode)
            }
        }
    }
}
