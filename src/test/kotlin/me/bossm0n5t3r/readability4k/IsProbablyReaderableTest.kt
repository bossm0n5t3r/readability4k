package me.bossm0n5t3r.readability4k

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup

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
    }) {
    companion object {
        private const val READERABLE = "readerable"
    }
}
