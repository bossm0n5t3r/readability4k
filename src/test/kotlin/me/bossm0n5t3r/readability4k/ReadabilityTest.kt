package me.bossm0n5t3r.readability4k

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import me.bossm0n5t3r.readability4k.dom.DOMParser

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
    })
