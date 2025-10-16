package me.bossm0n5t3r.readability4k

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
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonPrimitive
import me.bossm0n5t3r.readability4k.dom.Attribute
import me.bossm0n5t3r.readability4k.dom.DOMParser
import me.bossm0n5t3r.readability4k.dom.Document
import me.bossm0n5t3r.readability4k.dom.Element
import me.bossm0n5t3r.readability4k.dom.Node
import me.bossm0n5t3r.readability4k.dom.NodeType
import me.bossm0n5t3r.readability4k.dom.NodeUtils.innerHTMLOrNull

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
                    Readability(doc, ReadabilityOptions(nbTopCandidates = 42)).p.nbTopCandidates shouldBe 42
                }

                it("should accept a maxElemsToParse option") {
                    Readability(doc).p.maxElemsToParse shouldBe 0
                    Readability(doc, ReadabilityOptions(maxElemsToParse = 42)).p.maxElemsToParse shouldBe 42
                }

                it("should accept a keepClasses option") {
                    Readability(doc).p.keepClasses shouldBe false
                    Readability(doc, ReadabilityOptions(keepClasses = true)).p.keepClasses shouldBe true
                    Readability(doc, ReadabilityOptions(keepClasses = false)).p.keepClasses shouldBe false
                }

                it("should accept a allowedVideoRegex option or default it") {
                    Readability(doc).p.allowedVideoRegex.pattern shouldBe Regexps.VIDEOS.pattern
                    val customRegex = Regex("""//mydomain\.com/.*'""")
                    Readability(doc, ReadabilityOptions(allowedVideoRegex = customRegex))
                        .p.allowedVideoRegex.pattern shouldBe customRegex.pattern
                }
            }

            describe("#parse") {
                val exampleSource = Utils.getTestPages().first().source

                afterTest {
                    unmockkObject(ReadabilityUtils)
                }

                it("shouldn't parse oversized documents as per configuration") {
                    val doc = DOMParser().parse("<html><div>yo</div></html>")
                    shouldThrow<IllegalArgumentException> {
                        Readability(doc, ReadabilityOptions(maxElemsToParse = 1)).parse()
                    }.message shouldBe "Aborting parsing document; 2 elements found"
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

                xit("should use custom content serializer sent as option") {
                    val doc = DOMParser().parse("My cat: <img src=''>")
                    val expectedXhtml =
                        """<div xmlns="http://www.w3.org/1999/xhtml" id="readability-page-1" class="page">My cat: <img src="" /></div>"""
                    val content =
                        Readability(
                            doc,
                            ReadabilityOptions(
                                serializer = { el -> el.firstChild?.textContent.orEmpty() },
                            ),
                        ).parse()?.content
                    content shouldBe expectedXhtml
                }

                @Suppress("ktlint:standard:max-line-length")
                xit("should use custom video regex sent as option") {
                    val html =
                        """<p>Lorem ipsum dolor sit amet, consectetur adipiscing elit. Nunc mollis leo lacus, vitae semper nisl ullamcorper ut.</p>""" +
                            """<iframe src="https://mycustomdomain.com/some-embeds"></iframe>"""
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
                        ).parse()?.content
                    content shouldBe expectedXhtml
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
                            val reader = Readability(doc, ReadabilityOptions(classesToPreserve = listOf("caption")))
                            result = reader.parse() ?: error("Readability.parse() returned null")
                        }

                        xit("should return a result object") {
                            result.content.shouldNotBeEmpty()
                            result.title.shouldNotBeNull()
                            result.excerpt.shouldNotBeNull()
                            result.byline.shouldNotBeNull()
                        }

                        it("should extract expected content") {
                            val actualDoc = DOMParser().parse(result.content)
                            val expectedDoc = DOMParser().parse(testPage.expectedContent)

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
                                        val expectedNodeAttributes = attributesForNode(expectedElement)

                                        val desc =
                                            "node ${nodeStr(actualElement)} attributes " +
                                                "(${actualNodeAttributes.joinToString(",")}) should match " +
                                                "(${expectedNodeAttributes.joinToString(",")}) 1"

                                        withClue(desc) {
                                            actualNodeAttributes.size shouldBe expectedNodeAttributes.size
                                        }

                                        for (i in actualNodeAttributes.indices) {
                                            val attr = actualNodeAttributes[i].name
                                            val actualValue = actualElement.getAttribute(attr)
                                            val expectedValue = expectedElement.getAttribute(attr)

                                            withClue("node (${findableNodeDesc(actualNode)}) attribute $attr should match") {
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

                        it("should extract expected title") {
                            result.title shouldBe
                                testPage.expectedMetadata["title"]
                                    ?.takeIf { it != JsonNull }
                                    ?.jsonPrimitive
                                    ?.content
                        }

                        it("should extract expected byline") {
                            result.byline shouldBe
                                testPage.expectedMetadata["byline"]
                                    ?.takeIf { it != JsonNull }
                                    ?.jsonPrimitive
                                    ?.content
                        }

                        it("should extract expected excerpt") {
                            result.excerpt shouldBe
                                testPage.expectedMetadata["excerpt"]
                                    ?.takeIf { it != JsonNull }
                                    ?.jsonPrimitive
                                    ?.content
                        }

                        it("should extract expected site name") {
                            result.siteName shouldBe
                                testPage.expectedMetadata["siteName"]
                                    ?.takeIf { it != JsonNull }
                                    ?.jsonPrimitive
                                    ?.content
                        }

                        testPage.expectedMetadata["dir"]?.takeIf { it != JsonNull }?.let { expectedDir ->
                            it("should extract expected direction") {
                                result.dir shouldBe expectedDir.jsonPrimitive.content
                            }
                        }

                        testPage.expectedMetadata["lang"]?.takeIf { it != JsonNull }?.let { expectedLang ->
                            it("should extract expected language") {
                                result.lang shouldBe expectedLang.jsonPrimitive.content
                            }
                        }

                        testPage.expectedMetadata["publishedTime"]?.takeIf { it != JsonNull }?.let { publishedTime ->
                            it("should extract expected published time") {
                                result.publishedTime shouldBe publishedTime.jsonPrimitive.content
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

        private fun findableNodeDesc(node: Node) = "${genPath(node)}(in: ``${node.parentNode?.innerHTMLOrNull}``)"

        private fun attributesForNode(node: Node): List<Attribute> = (node as? Element)?.attributes.orEmpty()

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
            } while (node != null && node.nodeType == NodeType.TEXT_NODE && node.textContent.trim().isEmpty())

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
