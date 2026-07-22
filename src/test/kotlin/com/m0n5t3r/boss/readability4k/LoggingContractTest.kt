package com.m0n5t3r.boss.readability4k

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.m0n5t3r.boss.readability4k.dom.DOMParser
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import org.slf4j.LoggerFactory

private class LogCapture(loggerClass: Class<*>, level: Level) : AutoCloseable {
    private val logger =
        LoggerFactory.getLogger(loggerClass) as? Logger
            ?: error("Expected a Logback logger for ${loggerClass.name}")
    private val previousLevel = logger.level
    private val appender = ListAppender<ILoggingEvent>()

    init {
        logger.level = level
        appender.start()
        logger.addAppender(appender)
    }

    fun events(): List<ILoggingEvent> = appender.list.toList()

    override fun close() {
        logger.detachAppender(appender)
        appender.stop()
        logger.level = previousLevel
    }
}

class LoggingContractTest :
    DescribeSpec({
        val articleHtml =
            "<html><head><title>Contract test</title></head><body><article><p>" +
                "SECRET_ARTICLE_HTML " +
                "Readable sentence. ".repeat(40) +
                "</p></article></body></html>"

        fun parseArticle() =
            Readability(DOMParser().parse(articleHtml), ReadabilityOptions(charThreshold = 20))
                .parse()

        describe("logging contracts") {
            it("does not emit article content or per-node diagnostics at INFO") {
                val events =
                    LogCapture(Readability::class.java, Level.INFO).use { readabilityCapture ->
                        LogCapture(ReadabilityUtils::class.java, Level.INFO).use { utilsCapture ->
                            parseArticle()
                            readabilityCapture.events() + utilsCapture.events()
                        }
                    }

                events.all {
                    "SECRET_ARTICLE_HTML" !in it.formattedMessage && "<" !in it.formattedMessage
                } shouldBe true
                events.any { it.level == Level.INFO } shouldBe false
            }

            it("emits scalar candidate scoring diagnostics at DEBUG") {
                val events =
                    LogCapture(ReadabilityUtils::class.java, Level.DEBUG).use { capture ->
                        parseArticle()
                        capture.events()
                    }

                events.any {
                    it.level == Level.DEBUG &&
                        "Candidate:" in it.formattedMessage &&
                        "score=" in it.formattedMessage
                } shouldBe true
                events.all { "<" !in it.formattedMessage } shouldBe true
            }

            it("emits malformed JSON-LD at WARN with its throwable") {
                val events =
                    LogCapture(ReadabilityUtils::class.java, Level.WARN).use { capture ->
                        Readability(
                                DOMParser()
                                    .parse(
                                        "<html><body><script type=\"application/ld+json\">{not-json}</script></body></html>"
                                    )
                            )
                            .parse()
                        capture.events()
                    }

                val event = events.single {
                    it.level == Level.WARN && it.formattedMessage == "Failed to parse JSON-LD"
                }
                event.throwableProxy?.className shouldBe
                    "kotlinx.serialization.json.JsonDecodingException"
            }

            it("emits recoverable parser diagnostics at WARN") {
                val parser = DOMParser()
                val events =
                    LogCapture(DOMParser::class.java, Level.WARN).use { capture ->
                        parser.parse("<script>unterminated")
                        capture.events()
                    }

                parser.errorState shouldBe "expected '</script>' but reached end of document\n"
                val warnings = events.filter { it.level == Level.WARN }
                warnings.size shouldBe 1
                warnings.single().formattedMessage shouldBe
                    "JSDOMParser error: expected '</script>' but reached end of document"
                events.any { it.level == Level.ERROR } shouldBe false
            }
        }
    })
