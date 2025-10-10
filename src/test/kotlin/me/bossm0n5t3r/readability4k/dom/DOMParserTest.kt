package me.bossm0n5t3r.readability4k.dom

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import me.bossm0n5t3r.readability4k.LOGGER
import me.bossm0n5t3r.readability4k.dom.NodeUtils.innerHTMLOrNull
import me.bossm0n5t3r.readability4k.dom.NodeUtils.textContentOrNull

@Suppress("SpellCheckingInspection")
class DOMParserTest :
    DescribeSpec({
        val baseTestCase =
            """<html><body><p>Some text and <a class="someclass" href="#">a link</a></p>""" +
                """<div id="foo">With a <script>With &lt; fancy " characters in it because""" +
                """</script> that is fun.<span>And another node to make it harder</span></div><form><input type="text"/><input type="number"/>Here's a form</form></body></html>"""

        val baseDoc = DOMParser().parse(baseTestCase, "http://fakehost/")

        describe("Test JSDOM functionality") {
            fun nodeExpect(
                actual: Node?,
                expected: Node?,
            ) {
                if (actual == null && expected == null) {
                    LOGGER.debug("Both are null")
                }
                actual shouldBe expected
            }

            it("should work for basic operations using the parent child hierarchy and innerHTML") {
                baseDoc.childNodes.size shouldBe 1
                baseDoc.getElementsByTagName("*").size shouldBe 10

                val foo = baseDoc.getElementById("foo")
                foo?.parentNode?.localName shouldBe "body"
                nodeExpect(baseDoc.body, foo?.parentNode)
                nodeExpect(baseDoc.body?.parentNode, baseDoc.documentElement)
                baseDoc.body?.childNodes?.size shouldBe 3

                val generatedHTML = baseDoc.getElementsByTagName("p")[0].innerHTML
                generatedHTML shouldBe """Some text and <a class="someclass" href="#">a link</a>"""

                val scriptNode = baseDoc.getElementsByTagName("script")[0]
                val scriptHTML = scriptNode.innerHTML
                scriptHTML shouldBe """With &lt; fancy " characters in it because"""
                scriptNode.textContent shouldBe """With < fancy " characters in it because"""
            }

            it("should have basic URI information") {
                baseDoc.documentURI shouldBe "http://fakehost/"
                baseDoc.baseURI shouldBe "http://fakehost/"
            }

            it("should deal with script tags") {
                val scripts = baseDoc.getElementsByTagName("script")
                scripts.size shouldBe 1
                scripts[0].textContent shouldBe """With < fancy " characters in it because"""
            }

            it("should have working sibling/first+lastChild properties") {
                val foo = baseDoc.getElementById("foo")

                nodeExpect(foo?.previousSibling?.nextSibling, foo)
                nodeExpect(foo?.nextSibling?.previousSibling, foo)
                nodeExpect(foo?.nextSibling, foo?.nextElementSibling)
                nodeExpect(foo?.previousSibling, foo?.previousElementSibling)

                val beforeFoo = foo?.previousSibling
                val afterFoo = foo?.nextSibling

                nodeExpect(baseDoc.body?.lastChild, afterFoo)
                nodeExpect(baseDoc.body?.firstChild, beforeFoo)
            }

            it("should have working removeChild and appendChild functionality") {
                val foo = baseDoc.getElementById("foo")
                foo.shouldNotBeNull()

                val beforeFoo = foo.previousSibling as? Element
                val afterFoo = foo.nextSibling as? Element

                val removedFoo = foo.parentNode?.removeChild(foo)
                nodeExpect(foo, removedFoo)
                foo.parentNode.shouldBeNull()
                foo.previousSibling.shouldBeNull()
                foo.nextSibling.shouldBeNull()
                foo.previousElementSibling.shouldBeNull()
                foo.nextElementSibling.shouldBeNull()

                beforeFoo?.localName shouldBe "p"
                nodeExpect(beforeFoo?.nextSibling, afterFoo)
                nodeExpect(afterFoo?.previousSibling, beforeFoo)
                nodeExpect(beforeFoo?.nextElementSibling, afterFoo)
                nodeExpect(afterFoo?.previousElementSibling, beforeFoo)

                baseDoc.body?.childNodes?.size shouldBe 2

                baseDoc.body?.appendChild(foo)

                baseDoc.body?.childNodes?.size shouldBe 3
                nodeExpect(afterFoo?.nextSibling, foo)
                nodeExpect(foo.previousSibling, afterFoo)
                nodeExpect(afterFoo?.nextElementSibling, foo)
                nodeExpect(foo.previousElementSibling, afterFoo)

                afterFoo.shouldNotBeNull()
                baseDoc.body?.appendChild(afterFoo)
                nodeExpect(foo.previousSibling, beforeFoo)
                nodeExpect(foo.nextSibling, afterFoo)
                nodeExpect(foo.previousElementSibling, beforeFoo)
                nodeExpect(foo.nextElementSibling, afterFoo)

                nodeExpect(foo.previousSibling?.nextSibling, foo)
                nodeExpect(foo.nextSibling?.previousSibling, foo)
                nodeExpect(foo.nextSibling, foo.nextElementSibling)
                nodeExpect(foo.previousSibling, foo.previousElementSibling)
            }

            it("should handle attributes") {
                val link = baseDoc.getElementsByTagName("a")[0]
                link.getAttribute("href") shouldBe "#"
                link.getAttribute("class") shouldBe link.className
                val foo = baseDoc.getElementById("foo")
                foo.shouldNotBeNull()
                foo.id shouldBe foo.getAttribute("id")
            }

            it("should have a working replaceChild") {
                val parent = baseDoc.getElementsByTagName("div")[0]
                val p = baseDoc.createElement("p")
                p.setAttribute("id", "my-replaced-kid")
                val childCount = parent.childNodes.size
                val childElCount = parent.children.size

                for (i in 0 until childCount) {
                    val replacedNode = parent.childNodes[i]
                    val replacedAnElement = replacedNode.nodeType == NodeType.ELEMENT_NODE
                    val oldNext = replacedNode.nextSibling
                    val oldNextEl = (replacedNode as? Element)?.nextElementSibling
                    val oldPrev = replacedNode.previousSibling
                    val oldPrevEl = (replacedNode as? Element)?.previousElementSibling

                    parent.replaceChild(p, replacedNode)

                    nodeExpect(p.nextSibling, oldNext)
                    nodeExpect(p.previousSibling, oldPrev)
                    nodeExpect(p.parentNode, parent)

                    replacedNode.parentNode.shouldBeNull()
                    replacedNode.nextSibling.shouldBeNull()
                    replacedNode.previousSibling.shouldBeNull()
                    if (replacedAnElement) {
                        (replacedNode as? Element)?.nextElementSibling.shouldBeNull()
                        (replacedNode as? Element)?.previousElementSibling.shouldBeNull()
                    }

                    if (oldNext != null) {
                        nodeExpect(oldNext.previousSibling, p)
                    }
                    if (oldPrev != null) {
                        nodeExpect(oldPrev.nextSibling, p)
                    }

                    nodeExpect(parent.childNodes[i], p)

                    val kidElementIndex = parent.children.indexOf(p)
                    kidElementIndex shouldNotBe -1

                    if (kidElementIndex > 0) {
                        nodeExpect(parent.children[kidElementIndex - 1], p.previousElementSibling)
                        nodeExpect(p.previousElementSibling?.nextElementSibling, p)
                    } else {
                        p.previousElementSibling.shouldBeNull()
                    }
                    if (kidElementIndex < parent.children.size - 1) {
                        nodeExpect(parent.children[kidElementIndex + 1], p.nextElementSibling)
                        nodeExpect(p.nextElementSibling?.previousElementSibling, p)
                    } else {
                        p.nextElementSibling.shouldBeNull()
                    }

                    if (replacedAnElement) {
                        nodeExpect(oldNextEl, p.nextElementSibling)
                        nodeExpect(oldPrevEl, p.previousElementSibling)
                    }

                    parent.childNodes.size shouldBe childCount
                    parent.children.size shouldBe if (replacedAnElement) childElCount else childElCount + 1

                    parent.replaceChild(replacedNode, p)

                    nodeExpect(oldNext, replacedNode.nextSibling)
                    nodeExpect(oldNextEl, (replacedNode as? Element)?.nextElementSibling)
                    nodeExpect(oldPrev, replacedNode.previousSibling)
                    nodeExpect(oldPrevEl, (replacedNode as? Element)?.previousElementSibling)
                    if (replacedNode.nextSibling != null) {
                        nodeExpect(replacedNode.nextSibling?.previousSibling, replacedNode)
                    }
                    if (replacedNode.previousSibling != null) {
                        nodeExpect(replacedNode.previousSibling?.nextSibling, replacedNode)
                    }
                    if (replacedAnElement) {
                        if ((replacedNode as? Element)?.previousElementSibling != null) {
                            nodeExpect(
                                replacedNode.previousElementSibling?.nextElementSibling,
                                replacedNode,
                            )
                        }
                        if ((replacedNode as? Element)?.nextElementSibling != null) {
                            nodeExpect(
                                replacedNode.nextElementSibling?.previousElementSibling,
                                replacedNode,
                            )
                        }
                    }
                }
            }

            it("should have a working insertBefore") {
                val doc = DOMParser().parse(baseTestCase)
                val body = doc.body
                val foo = doc.getElementById("foo")
                val p = doc.getElementsByTagName("p")[0]
                val form = doc.getElementsByTagName("form")[0]

                val newEl = doc.createElement("hr")
                body?.insertBefore(newEl, foo)
                nodeExpect(p.nextSibling, newEl)
                nodeExpect(newEl.nextSibling, foo)
                nodeExpect(foo?.previousSibling, newEl)
                nodeExpect(newEl.previousSibling, p)
                nodeExpect(p.nextElementSibling, newEl)
                nodeExpect(newEl.nextElementSibling, foo)
                nodeExpect(foo?.previousElementSibling, newEl)
                nodeExpect(newEl.previousElementSibling, p)
                body?.childNodes?.size shouldBe 4
                body?.children?.size shouldBe 4

                val newEl2 = doc.createElement("hr")
                body?.insertBefore(newEl2, null)
                nodeExpect(body?.lastChild, newEl2)
                nodeExpect(form.nextSibling, newEl2)
                nodeExpect(newEl2.previousSibling, form)
                body?.childNodes?.size shouldBe 5
                body?.children?.size shouldBe 5

                val newEl3 = doc.createElement("hr")
                body?.insertBefore(newEl3, p)
                nodeExpect(body?.firstChild, newEl3)
                nodeExpect(newEl3.nextSibling, p)
                nodeExpect(p.previousSibling, newEl3)
                body?.childNodes?.size shouldBe 6
                body?.children?.size shouldBe 6
            }

            it("should correctly handle mixed element/text siblings on insertBefore") {
                val html1 = """<div><p>A</p>Some Text<span>B</span></div>"""
                val doc1 = DOMParser().parse(html1)
                val div1 = doc1.getElementsByTagName("div")[0]
                val pA1 = doc1.getElementsByTagName("p")[0]
                val textNode1 = div1.childNodes[1]
                val spanB1 = doc1.getElementsByTagName("span")[0]
                val newEl1 = doc1.createElement("hr")
                div1.insertBefore(newEl1, spanB1)
                nodeExpect(newEl1.previousSibling, textNode1)
                nodeExpect(newEl1.previousElementSibling, pA1)
                nodeExpect(newEl1.nextSibling, spanB1)
                nodeExpect(newEl1.nextElementSibling, spanB1)
                nodeExpect(pA1.nextElementSibling, newEl1)
                nodeExpect(spanB1.previousElementSibling, newEl1)

                val html2 = """<div><p>A</p><span>B</span>Some Text</div>"""
                val doc2 = DOMParser().parse(html2)
                val div2 = doc2.getElementsByTagName("div")[0]
                val pA2 = doc2.getElementsByTagName("p")[0]
                val spanB2 = doc2.getElementsByTagName("span")[0]
                val textNode2 = div2.childNodes[2]
                val newEl2 = doc2.createElement("hr")
                div2.insertBefore(newEl2, textNode2)
                nodeExpect(newEl2.previousSibling, spanB2)
                nodeExpect(newEl2.previousElementSibling, spanB2)
                nodeExpect(newEl2.nextSibling, textNode2)
                newEl2.nextElementSibling.shouldBeNull()
                nodeExpect(pA2.nextElementSibling, spanB2)
                nodeExpect(spanB2.nextElementSibling, newEl2)
            }

            it("should throw an error when inserting before a non-child") {
                val doc = DOMParser().parse("""<div><p>A</p></div>""")
                val div = doc.getElementsByTagName("div")[0]
                val p = doc.createElement("p")
                val unconnected = doc.createElement("span")

                shouldThrow<IllegalArgumentException> {
                    div.insertBefore(p, unconnected)
                }.message shouldBe "insertBefore: reference node not found"
            }

            it("should have a working createDocumentFragment") {
                val doc = DOMParser().parse(baseTestCase)
                val body = doc.body
                val fragment = doc.createDocumentFragment()
                body.shouldNotBeNull()
                fragment.nodeType shouldBe NodeType.DOCUMENT_FRAGMENT_NODE
                fragment.nodeName shouldBe "#document-fragment"

                val p = doc.getElementsByTagName("p")[0]
                val foo = doc.getElementById("foo")
                foo.shouldNotBeNull()

                fragment.appendChild(p)
                fragment.appendChild(foo)

                p.parentNode shouldBe fragment
                foo.parentNode shouldBe fragment
                fragment.childNodes.size shouldBe 2
                fragment.children.size shouldBe 2
                body.childNodes.size shouldBe 1

                body.appendChild(fragment)
                body.childNodes.size shouldBe 3
                p.parentNode shouldBe body
                foo.parentNode shouldBe body
                fragment.childNodes.size shouldBe 0
            }

            it("should handle moving an existing child with insertBefore") {
                val doc = DOMParser().parse("""<div><p>A</p><p>B</p><p>C</p></div>""")
                val div = doc.getElementsByTagName("div")[0]
                val pA = div.children[0]
                val pB = div.children[1]
                val pC = div.children[2]

                div.insertBefore(pC, pB)

                div.children.size shouldBe 3
                nodeExpect(div.children[0], pA)
                nodeExpect(div.children[1], pC)
                nodeExpect(div.children[2], pB)

                pA.previousSibling.shouldBeNull()
                nodeExpect(pA.nextSibling, pC)

                nodeExpect(pC.previousSibling, pA)
                nodeExpect(pC.nextSibling, pB)

                nodeExpect(pB.previousSibling, pC)
                pB.nextSibling.shouldBeNull()
            }

            it("should handle inserting a node before itself as a no-op") {
                val doc = DOMParser().parse("""<div><p>A</p><p>B</p></div>""")
                val div = doc.getElementsByTagName("div")[0]
                val pA = div.children[0]
                val pB = div.children[1]

                div.insertBefore(pB, pB)

                div.children.size shouldBe 2
                nodeExpect(div.children[0], pA)
                nodeExpect(div.children[1], pB)
                nodeExpect(pA.nextSibling, pB)
                nodeExpect(pB.previousSibling, pA)
            }

            it("should handle replacing a node with itself as a no-op") {
                val doc = DOMParser().parse("""<div><p>A</p><p>B</p></div>""")
                val div = doc.getElementsByTagName("div")[0]
                val pA = div.children[0]
                val pB = div.children[1]

                div.replaceChild(pB, pB)

                div.children.size shouldBe 2
                nodeExpect(div.children[0], pA)
                nodeExpect(div.children[1], pB)
                nodeExpect(pA.nextSibling, pB)
                nodeExpect(pB.previousSibling, pA)
            }

            it("should correctly handle sibling pointers on remove()") {
                val doc = DOMParser().parse("""<div><p>A</p>Some text<p>B</p></div>""")
                val div = doc.getElementsByTagName("div")[0]
                val pA = div.children[0]
                val textNode = div.childNodes[1]
                val pB = div.children[1]

                nodeExpect(pA.nextElementSibling, pB)
                nodeExpect(pB.previousElementSibling, pA)

                textNode.remove()

                nodeExpect(pA.nextElementSibling, pB)
                nodeExpect(pB.previousElementSibling, pA)

                textNode.parentNode.shouldBeNull()
                textNode.nextSibling.shouldBeNull()
                textNode.previousSibling.shouldBeNull()
            }
        }

        describe("Test HTML escaping") {
            val baseStr =
                """<p>Hello, everyone &amp; all their friends, &lt;this&gt; is a &quot; test with &apos; quotes.</p>"""
            val doc = DOMParser().parse(baseStr)
            val p = doc.getElementsByTagName("p")[0]
            val txtNode = p.firstChild

            it("should handle encoding HTML correctly") {
                "<p>${p.innerHTML}</p>" shouldBe baseStr
                "<p>${txtNode?.innerHTMLOrNull}</p>" shouldBe baseStr
            }

            it("should have decoded correctly") {
                p.textContent shouldBe """Hello, everyone & all their friends, <this> is a " test with ' quotes."""
                txtNode?.textContent shouldBe """Hello, everyone & all their friends, <this> is a " test with ' quotes."""
            }

            it("should handle updates via textContent correctly") {
                txtNode?.textContentOrNull = txtNode.textContentOrNull + " "
                txtNode?.textContentOrNull = txtNode.textContentOrNull?.trim()
                val expectedHTML = baseStr.replace("&quot;", "\"").replace("&apos;", "'")
                "<p>${txtNode?.innerHTMLOrNull}</p>" shouldBe expectedHTML
                "<p>${p.innerHTML}</p>" shouldBe expectedHTML
            }

            it("should handle decimal and hex escape sequences") {
                val parsedDoc = DOMParser().parse("""<p>&#32;&#x20;</p>""")
                parsedDoc.getElementsByTagName("p")[0].textContent shouldBe "  "
            }
        }

        describe("Script parsing") {
            it("should strip ?-based comments within script tags") {
                val html = """<script><?Silly test <img src="test"></script>"""
                val doc = DOMParser().parse(html)

                val docFirstChild = doc.firstChild as Element

                docFirstChild.tagName shouldBe "SCRIPT"
                docFirstChild.textContent shouldBe ""
                docFirstChild.children.size shouldBe 0
                docFirstChild.childNodes.size shouldBe 0
            }

            it("should strip !-based comments within script tags") {
                val html = """<script><!--Silly test > <script src="foo.js"></script>--></script>"""
                val doc = DOMParser().parse(html)

                doc.firstChild.shouldBeInstanceOf<Element>()
                val docFirstChild = doc.firstChild as Element

                docFirstChild.tagName shouldBe "SCRIPT"
                docFirstChild.textContent shouldBe ""
                docFirstChild.children.size shouldBe 0
                docFirstChild.childNodes.size shouldBe 0
            }

            it("should strip any other nodes within script tags") {
                val html = """<script>&lt;div>Hello, I'm not really in a &lt;/div></script>"""
                val doc = DOMParser().parse(html)

                doc.firstChild.shouldBeInstanceOf<Element>()
                val docFirstChild = doc.firstChild as Element

                docFirstChild.tagName shouldBe "SCRIPT"
                docFirstChild.textContent shouldBe """<div>Hello, I'm not really in a </div>"""
                docFirstChild.children.size shouldBe 0
                docFirstChild.childNodes.size shouldBe 1
            }

            it("should strip any other invalid script nodes within script tags") {
                val html = """<script>&lt;script src="foo.js">&lt;/script></script>"""
                val doc = DOMParser().parse(html)

                doc.firstChild.shouldBeInstanceOf<Element>()
                val docFirstChild = doc.firstChild as Element

                docFirstChild.tagName shouldBe "SCRIPT"
                docFirstChild.textContent shouldBe """<script src="foo.js"></script>"""
                docFirstChild.children.size shouldBe 0
                docFirstChild.childNodes.size shouldBe 1
            }

            it("should not be confused by partial closing tags") {
                val html = """<script>var x = '&lt;script>Hi&lt;' + '/script>';</script>"""
                val doc = DOMParser().parse(html)

                doc.firstChild.shouldBeInstanceOf<Element>()
                val docFirstChild = doc.firstChild as Element

                docFirstChild.tagName shouldBe "SCRIPT"
                docFirstChild.textContent shouldBe """var x = '<script>Hi<' + '/script>';"""
                docFirstChild.children.size shouldBe 0
                docFirstChild.childNodes.size shouldBe 1
            }
        }

        describe("Tag local name case handling") {
            it("should lowercase tag names") {
                val html = """<DIV><svG><clippath/></svG></DIV>"""
                val doc = DOMParser().parse(html)

                doc.firstChild.shouldBeInstanceOf<Element>()
                val docFirstChild = doc.firstChild as Element

                docFirstChild.tagName shouldBe "DIV"
                docFirstChild.localName shouldBe "div"

                docFirstChild.firstChild.shouldBeInstanceOf<Element>()
                val docFirstChildFirstChild = docFirstChild.firstChild as Element

                docFirstChildFirstChild.tagName shouldBe "SVG"
                docFirstChildFirstChild.localName shouldBe "svg"

                docFirstChildFirstChild.firstChild.shouldBeInstanceOf<Element>()
                val docFirstChildFirstChildFirstChild = docFirstChildFirstChild.firstChild as Element

                docFirstChildFirstChildFirstChild.tagName shouldBe "CLIPPATH"
                docFirstChildFirstChildFirstChild.localName shouldBe "clippath"
            }
        }

        describe("Recovery from self-closing tags that have close tags") {
            it("should handle delayed closing of a tag") {
                val html = """<div><input><p>I'm in an input</p></input></div>"""
                val doc = DOMParser().parse(html)

                doc.firstChild.shouldBeInstanceOf<Element>()
                val docFirstChild = doc.firstChild as Element

                docFirstChild.localName shouldBe "div"
                docFirstChild.childNodes.size shouldBe 1

                docFirstChild.firstChild.shouldBeInstanceOf<Element>()
                val docFirstChildFirstChild = docFirstChild.firstChild as Element

                docFirstChildFirstChild.localName shouldBe "input"
                docFirstChildFirstChild.childNodes.size shouldBe 1

                docFirstChildFirstChild.firstChild.shouldBeInstanceOf<Element>()
                val docFirstChildFirstChildFirstChild = docFirstChildFirstChild.firstChild as Element

                docFirstChildFirstChildFirstChild.localName shouldBe "p"
            }
        }

        describe("baseURI parsing") {
            it("should handle various types of relative and absolute base URIs") {
                fun checkBase(
                    base: String,
                    expectedResult: String,
                ) {
                    val html = """<html><head><base href='$base'></base></head><body/></html>"""
                    val doc = DOMParser().parse(html, "http://fakehost/some/dir/")
                    doc.baseURI shouldBe expectedResult
                }

                checkBase("relative/path", "http://fakehost/some/dir/relative/path")
                checkBase("/path", "http://fakehost/path")
                checkBase("http://absolute/", "http://absolute/")
                checkBase("//absolute/path", "http://absolute/path")
            }
        }

        describe("namespace workarounds") {
            it("should handle random namespace information in the serialized DOM") {
                val html =
                    """<a0:html><a0:body><a0:DIV><a0:svG><a0:clippath/></a0:svG></a0:DIV></a0:body></a0:html>"""
                val doc = DOMParser().parse(html)
                val div = doc.getElementsByTagName("div")[0]
                div.tagName shouldBe "DIV"
                div.localName shouldBe "div"

                div.firstChild.shouldBeInstanceOf<Element>()
                val divFirstChild = div.firstChild as Element

                divFirstChild.tagName shouldBe "SVG"
                divFirstChild.localName shouldBe "svg"

                divFirstChild.firstChild.shouldBeInstanceOf<Element>()
                val divFirstChildFirstChild = divFirstChild.firstChild as Element

                divFirstChildFirstChild.tagName shouldBe "CLIPPATH"
                divFirstChildFirstChild.localName shouldBe "clippath"
                doc.documentElement shouldBe doc.firstChild
                doc.body shouldBe doc.documentElement?.firstChild
            }
        }
    })
