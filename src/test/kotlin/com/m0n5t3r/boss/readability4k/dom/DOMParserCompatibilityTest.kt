package com.m0n5t3r.boss.readability4k.dom

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class DOMParserCompatibilityTest :
    DescribeSpec({
        describe("HTML compatibility") {
            it("should treat markup-like script content as text") {
                val html =
                    """
                    <html><head><script type="application/configuration">{"intro":"<a href=\"https://automattic.com\">WordPress</a>"}</script></head>
                    <body><article><p>Article text.</p></article></body></html>
                    """
                        .trimIndent()
                val parser = DOMParser()
                val doc = parser.parse(html)

                doc.body.shouldNotBeNull()
                doc.getElementsByTagName("script").single().textContent shouldBe
                    """{"intro":"<a href=\"https://automattic.com\">WordPress</a>"}"""
                parser.errorState shouldBe ""
            }

            it("should not require closing tags for void elements") {
                val parser = DOMParser()
                val doc =
                    parser.parse(
                        """<html><head><link rel="stylesheet"><meta charset="UTF-8"></head>""" +
                            """<body><img src="hero.jpg"><p>Article text.</p><br>After</body></html>"""
                    )

                doc.head.shouldNotBeNull().children.map { it.localName } shouldBe
                    listOf("link", "meta")
                doc.body.shouldNotBeNull().textContent shouldBe "Article text.After"
                parser.errorState shouldBe ""
            }

            it("should parse unquoted and boolean attributes") {
                val parser = DOMParser()
                val doc =
                    parser.parse(
                        """<div data-liked=comment-not-liked hidden><a href=https://example.com/path>link</a></div>"""
                    )
                val div = doc.getElementsByTagName("div").single()
                val link = div.getElementsByTagName("a").single()

                div.getAttribute("data-liked") shouldBe "comment-not-liked"
                div.getAttribute("hidden") shouldBe ""
                link.href shouldBe "https://example.com/path"
                parser.errorState shouldBe ""
            }

            it("should advance past malformed attribute separators") {
                val parser = DOMParser()
                val doc = parser.parse("""<div =broken data-id=42>content</div>""")
                val div = doc.getElementsByTagName("div").single()

                div.getAttribute("data-id") shouldBe "42"
                div.textContent shouldBe "content"
                parser.errorState shouldBe ""
            }

            it("should implicitly close inline elements before a list item closes") {
                val parser = DOMParser()
                val doc = parser.parse("""<ul><li><a href="#"><span>First item</li></ul>""")
                val listItem = doc.getElementsByTagName("li").single()
                val link = listItem.getElementsByTagName("a").single()

                listItem.textContent shouldBe "First item"
                link.firstElementChild?.localName shouldBe "span"
                parser.errorState shouldBe ""
            }
        }
    })
