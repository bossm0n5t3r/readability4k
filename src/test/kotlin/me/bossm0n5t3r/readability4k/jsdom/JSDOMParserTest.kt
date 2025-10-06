package me.bossm0n5t3r.readability4k.jsdom

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
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
        }
    })
