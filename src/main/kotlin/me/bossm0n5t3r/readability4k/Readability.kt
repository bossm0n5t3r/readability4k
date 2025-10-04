package me.bossm0n5t3r.readability4k

import me.bossm0n5t3r.readability4k.ReadabilityUtils.getArticleMetadata
import me.bossm0n5t3r.readability4k.ReadabilityUtils.getJSONLD
import me.bossm0n5t3r.readability4k.ReadabilityUtils.grabArticle
import me.bossm0n5t3r.readability4k.ReadabilityUtils.postProcessContent
import me.bossm0n5t3r.readability4k.ReadabilityUtils.prepDocument
import me.bossm0n5t3r.readability4k.ReadabilityUtils.removeScripts
import me.bossm0n5t3r.readability4k.ReadabilityUtils.unwrapNoscriptImages
import org.jsoup.nodes.Document

class Readability(
    doc: Document,
    options: ReadabilityOptions = ReadabilityOptions(),
) {
    val p = ReadabilityProperties(doc, options)

    fun parse(): ReadabilityResult? {
        if (p.maxElemsToParse > 0) {
            val numTags = p.document.allElements.size
            require(numTags <= p.maxElemsToParse) {
                "Aborting parsing document; $numTags elements found"
            }
        }

        unwrapNoscriptImages(p.document)

        val jsonLd = if (p.disableJSONLD) mapOf() else getJSONLD(p)

        removeScripts(p.document)
        prepDocument(p.document)

        p.metadata = getArticleMetadata(jsonLd, p)
        p.articleTitle = p.metadata["title"]

        val articleContent = grabArticle(page = null, p = p) ?: return null
        LOGGER.info("Grabbed: ${articleContent.html()}")

        postProcessContent(articleContent, p)

        if (!p.metadata.containsKey("excerpt")) {
            val excerpt =
                articleContent
                    .select("p")
                    .firstOrNull()
                    ?.text()
                    ?.trim()
            if (excerpt != null) {
                p.metadata["excerpt"] = excerpt
            }
        }

        val textContent = articleContent.text()
        return ReadabilityResult(
            title = p.articleTitle,
            byline = p.metadata["byline"] ?: p.articleByline,
            dir = p.articleDir,
            lang = p.articleLang,
            content = p.serializer(articleContent),
            textContent = textContent,
            length = textContent.length,
            excerpt = p.metadata["excerpt"],
            siteName = p.metadata["siteName"] ?: p.articleSiteName,
            publishedTime = p.metadata["publishedTime"],
        )
    }
}
