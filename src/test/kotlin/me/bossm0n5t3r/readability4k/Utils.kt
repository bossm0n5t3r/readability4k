package me.bossm0n5t3r.readability4k

import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import org.jsoup.Jsoup

object Utils {
    private fun readFile(filePath: Path): String = filePath.readText().trim()

    private fun readJSON(jsonPath: Path): Map<String, JsonElement> {
        val jsonContent = readFile(jsonPath)
        return Json.parseToJsonElement(jsonContent).jsonObject.toMap()
    }

    private val testPageRoot = Path("src/test/resources/test-pages")

    fun getTestPages(): List<TestPage> =
        testPageRoot
            .listDirectoryEntries()
            .filter { it.isDirectory() }
            .map { dir ->
                TestPage(
                    dir = dir.name,
                    source = readFile(dir / "source.html"),
                    expectedContent = readFile(dir / "expected.html"),
                    expectedMetadata = readJSON(dir / "expected-metadata.json"),
                )
            }

    fun prettyPrint(html: String): String =
        Jsoup.parseBodyFragment(html)
            .apply {
                outputSettings().apply {
                    indentAmount(4)
                    prettyPrint(true)
                }
            }
            .body()
            .html()
}
