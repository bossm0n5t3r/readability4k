package me.bossm0n5t3r.readability4k

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

@Suppress("SpellCheckingInspection")
class IsProbablyReaderableTest :
    DescribeSpec({
        describe("isProbablyReaderable - test pages") {
            val testPages = Utils.getTestPages()

            testPages.forEach {
                describe(it.dir) {
                    val expected = it.expectedMetadata[Companion.READERABLE]?.jsonPrimitive?.boolean
                    requireNotNull(expected) { "Expected ${Companion.READERABLE} metadata property in test page" }
                    val document = Jsoup.parse(it.source)
                    val result = isProbablyReaderable(document)
                    it("The result should ${if (expected) "" else "not "}be readerable") {
                        result shouldBe expected
                    }
                }
            }
        }

        describe("isProbablyReaderable") {
            val makeDoc = { html: String -> Jsoup.parse(html) }

            // content length: 11
            val verySmallDoc = makeDoc("<html><p id=\"main\">hello there</p></html>")
            // content length: 132
            val smallDoc = makeDoc("<html><p id=\"main\">${"hello there ".repeat(11)}</p></html>")
            // content length: 144
            val largeDoc = makeDoc("<html><p id=\"main\">${"hello there ".repeat(12)}</p></html>")
            // content length: 600
            val veryLargeDoc = makeDoc("<html><p id=\"main\">${"hello there ".repeat(50)}</p></html>")

            it("should only declare large documents as readerable when default options") {
                isProbablyReaderable(verySmallDoc) shouldBe false // score: 0
                isProbablyReaderable(smallDoc) shouldBe false // score: 0
                isProbablyReaderable(largeDoc) shouldBe false // score: ~1.7
                isProbablyReaderable(veryLargeDoc) shouldBe true // score: ~21.4
            }

            it("should declare small and large documents as readerable when lower minContentLength") {
                val options = ReaderableOptions(minContentLength = 120, minScore = 0.toBigDecimal())
                isProbablyReaderable(verySmallDoc, options) shouldBe false
                isProbablyReaderable(smallDoc, options) shouldBe true
                isProbablyReaderable(largeDoc, options) shouldBe true
                isProbablyReaderable(veryLargeDoc, options) shouldBe true
            }

            it("should only declare largest document as readerable when higher minContentLength") {
                val options = ReaderableOptions(minContentLength = 200, minScore = 0.toBigDecimal())
                isProbablyReaderable(verySmallDoc, options) shouldBe false
                isProbablyReaderable(smallDoc, options) shouldBe false
                isProbablyReaderable(largeDoc, options) shouldBe false
                isProbablyReaderable(veryLargeDoc, options) shouldBe true
            }

            it("should declare small and large documents as readerable when lower minScore") {
                val options = ReaderableOptions(minContentLength = 0, minScore = 4.toBigDecimal())
                isProbablyReaderable(verySmallDoc, options) shouldBe false // score: ~3.3
                isProbablyReaderable(smallDoc, options) shouldBe true // score: ~11.4
                isProbablyReaderable(largeDoc, options) shouldBe true // score: ~11.9
                isProbablyReaderable(veryLargeDoc, options) shouldBe true // score: ~24.4
            }

            it("should declare large documents as readerable when higher minScore") {
                val options = ReaderableOptions(minContentLength = 0, minScore = 11.5.toBigDecimal())
                isProbablyReaderable(verySmallDoc, options) shouldBe false // score: ~3.3
                isProbablyReaderable(smallDoc, options) shouldBe false // score: ~11.4
                isProbablyReaderable(largeDoc, options) shouldBe true // score: ~11.9
                isProbablyReaderable(veryLargeDoc, options) shouldBe true // score: ~24.4
            }

            it("should use node visibility checker provided as option - not visible") {
                var called = false
                val options =
                    ReaderableOptions(visibilityChecker = {
                        called = true
                        false
                    })
                isProbablyReaderable(veryLargeDoc, options) shouldBe false
                called shouldBe true
            }

            it("should use node visibility checker provided as option - visible") {
                var called = false
                val options =
                    ReaderableOptions(visibilityChecker = {
                        called = true
                        true
                    })
                isProbablyReaderable(veryLargeDoc, options) shouldBe true
                called shouldBe true
            }

            it("should use node visibility checker provided as parameter - not visible") {
                var called = false
                val visibilityChecker = { _: Element ->
                    called = true
                    false
                }
                isProbablyReaderable(veryLargeDoc, visibilityChecker) shouldBe false
                called shouldBe true
            }
        }
    }) {
    companion object {
        private const val READERABLE = "readerable"
    }
}
