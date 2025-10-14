package me.bossm0n5t3r.readability4k

import me.bossm0n5t3r.readability4k.ReadabilityUtils.getArticleMetadata
import me.bossm0n5t3r.readability4k.ReadabilityUtils.getJSONLD
import me.bossm0n5t3r.readability4k.ReadabilityUtils.grabArticle
import me.bossm0n5t3r.readability4k.ReadabilityUtils.postProcessContent
import me.bossm0n5t3r.readability4k.ReadabilityUtils.prepDocument
import me.bossm0n5t3r.readability4k.ReadabilityUtils.removeScripts
import me.bossm0n5t3r.readability4k.ReadabilityUtils.unwrapNoscriptImages
import me.bossm0n5t3r.readability4k.dom.Document

class Readability(
    doc: Document,
    options: ReadabilityOptions = ReadabilityOptions(),
) {
    val p = ReadabilityProperties(doc, options)

    fun parse(): ReadabilityResult? {
        if (p.maxElemsToParse > 0) {
            val numTags = p.document.getElementsByTagName("*").size
            require(numTags <= p.maxElemsToParse) {
                "Aborting parsing document; $numTags elements found"
            }
        }

        unwrapNoscriptImages(p.document)

        val jsonLd = if (p.disableJSONLD) mapOf() else getJSONLD(p)

        removeScripts(p.document)
        prepDocument(p.document, p)

        p.metadata = getArticleMetadata(jsonLd, p)
        p.articleTitle = p.metadata["title"]

        val articleContent = grabArticle(page = null, p = p) ?: return null
        LOGGER.info("Grabbed: ${articleContent.innerHTML}")

        postProcessContent(articleContent, p)

        if (p.metadata["excerpt"].isNullOrBlank()) {
            val excerpt =
                articleContent
                    .getElementsByTagName("p")
                    .firstOrNull()
                    ?.textContent
                    ?.trim()
            if (excerpt != null) {
                p.metadata["excerpt"] = excerpt
            }
        }

        val textContent = articleContent.textContent
        return ReadabilityResult(
            title = p.articleTitle,
            byline = p.metadata["byline"]?.takeIf { it.isNotBlank() } ?: p.articleByline,
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
