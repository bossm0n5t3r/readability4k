package me.bossm0n5t3r.readability4k.jsdom

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

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
        }
    })
