package me.bossm0n5t3r.readability4k

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UtilsTest {
    @Test
    fun testGetTestPages() {
        val testPages = Utils.getTestPages()
        assertTrue(testPages.isNotEmpty(), "Should have test pages")

        val firstPage = testPages.first()
        assertNotNull(firstPage.dir, "Directory name should not be null")
        assertNotNull(firstPage.source, "Source HTML should not be null")
        assertNotNull(firstPage.expectedContent, "Expected content should not be null")
        assertNotNull(firstPage.expectedMetadata, "Expected metadata should not be null")

        LOGGER.debug("Found {} test pages", testPages.size)
        LOGGER.debug("First page directory: {}", firstPage.dir)
    }

    @Test
    fun testPrettyPrint() {
        val html = "<div><p>Hello</p></div>"
        val prettyHtml = Utils.prettyPrint(html)
        assertTrue(prettyHtml.contains("    "), "Should contain indentation")
        LOGGER.debug("Pretty printed HTML: {}", prettyHtml)
    }
}
