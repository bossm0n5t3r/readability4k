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
        }
    })
