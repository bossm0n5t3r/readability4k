@file:Suppress("SpellCheckingInspection")

package me.bossm0n5t3r.readability4k

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import kotlin.math.sqrt

/**
 * Checks if a DOM node is visible.
 *
 * @param node The DOM element to check for visibility.
 * @return true if the node is visible, false otherwise.
 */
fun isNodeVisible(node: Element): Boolean {
    // Check for display: none style
    val style = node.attr("style")
    if (style.contains("display:none") || style.contains("display: none")) {
        return false
    }

    // Check for hidden attribute
    if (node.hasAttr("hidden")) {
        return false
    }

    // Check for aria-hidden attribute
    if (node.hasAttr("aria-hidden")) {
        val ariaHidden = node.attr("aria-hidden")
        if (ariaHidden == "true") {
            // Allow fallback-image for wikimedia math images
            val className = node.className()
            if (!className.contains("fallback-image")) {
                return false
            }
        }
    }

    return true
}

/**
 * Decides whether or not the document is reader-able without parsing the whole thing.
 *
 * @param doc The document to analyze.
 * @param options Configuration object with options for readability detection.
 * @return Whether or not we suspect Readability.parse() will succeed at returning an article object.
 */
fun isProbablyReaderable(
    doc: Document,
    options: ReaderableOptions = ReaderableOptions(),
): Boolean {
    val nodes = doc.select("p, pre, article")

    // Get <div> nodes which have <br> node(s) and append them into the nodes collection.
    // Some articles' DOM structures might look like
    // <div>
    //   Sentences<br>
    //   <br>
    //   Sentences<br>
    // </div>
    val brNodes = doc.select("div > br")
    val nodeSet = nodes.toSet() + brNodes.mapNotNull { it.parent() }.toSet()

    var score = 0.0

    // This is a little cheeky, we use the accumulator 'score' to decide what to return from
    // this callback:
    return nodeSet.any { node ->
        if (!options.visibilityChecker(node)) {
            return@any false
        }

        val matchString = "${node.className()} ${node.id()}"
        if (Regexps.UNLIKELY_CANDIDATES.containsMatchIn(matchString) &&
            !Regexps.OK_MAYBE_ITS_A_CANDIDATE.containsMatchIn(matchString)
        ) {
            return@any false
        }

        if (node.select("li p").isNotEmpty()) {
            return@any false
        }

        val htmlLength = node.wholeText().length
        if (htmlLength < options.minContentLength) {
            return@any false
        }

        val textContentLength = node.html().trim().length
        if (textContentLength < options.minContentLength) {
            return@any false
        }

        score += sqrt((textContentLength - options.minContentLength).toDouble())

        score > options.minScore
    }
}
