package me.bossm0n5t3r.readability4k.jsdom

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import me.bossm0n5t3r.readability4k.LOGGER

@Suppress("SpellCheckingInspection")
class JSDOMParserTest :
    DescribeSpec({
        val baseTestCase =
            """<html><body><p>Some text and <a class="someclass" href="#">a link</a></p>""" +
                """<div id="foo">With a <script>With &lt; fancy " characters in it because""" +
                """</script> that is fun.<span>And another node to make it harder</span></div><form><input type="text"/><input type="number"/>Here's a form</form></body></html>"""

        val baseDoc = JSDOMParser().parse(baseTestCase, "http://fakehost/")

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
                val doc = JSDOMParser().parse(baseTestCase)
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
                val doc1 = JSDOMParser().parse(html1)
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
                val doc2 = JSDOMParser().parse(html2)
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
        }
    })
