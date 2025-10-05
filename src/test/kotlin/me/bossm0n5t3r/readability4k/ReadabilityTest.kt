package me.bossm0n5t3r.readability4k

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import org.jsoup.Jsoup

class ReadabilityTest :
    DescribeSpec({
        describe("Readability API") {
            describe("#constructor") {
                val doc = Jsoup.parse("<html><div>yo</div></html>")

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
        }
    })
