@file:Suppress("SpellCheckingInspection")

package com.m0n5t3r.boss.readability4k

import com.m0n5t3r.boss.readability4k.dom.Document
import com.m0n5t3r.boss.readability4k.dom.Element
import java.math.BigDecimal
import kotlin.math.sqrt

/**
 * Checks if a DOM node is visible.
 *
 * @param node The DOM element to check for visibility.
 * @return true if the node is visible, false otherwise.
 */
fun isNodeVisible(node: Element): Boolean {
    // Check for display: none style
    val style = node.getAttribute("style").orEmpty()
    if (style.contains("display:none") || style.contains("display: none")) {
        return false
    }

    // Check for hidden attribute
    if (node.hasAttribute("hidden")) {
        return false
    }

    // Check for aria-hidden attribute
    if (node.hasAttribute("aria-hidden")) {
        val ariaHidden = node.getAttribute("aria-hidden").orEmpty()
        if (ariaHidden == "true") {
            // Allow fallback-image for wikimedia math images
            val className = node.className
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
 * @return Whether or not we suspect Readability.parse() will succeed at returning an article
 *   object.
 */
fun isProbablyReaderable(doc: Document, options: ReaderableOptions = ReaderableOptions()): Boolean {
    val nodes = doc.querySelectorAll("p, pre, article")

    // Get <div> nodes which have <br> node(s) and append them into the nodes collection.
    // Some articles' DOM structures might look like
    // <div>
    //   Sentences<br>
    //   <br>
    //   Sentences<br>
    // </div>
    val brNodes = doc.querySelectorAll("div > br")
    val nodeSet = nodes.toSet() + brNodes.mapNotNull { it.parentNode as? Element }.toSet()

    var score = BigDecimal.ZERO

    // This is a little cheeky, we use the accumulator 'score' to decide what to return from
    // this callback:
    return nodeSet.any { node ->
        if (!options.visibilityChecker(node)) {
            return@any false
        }

        val matchString = "${node.className} ${node.id}"
        if (
            Regexps.UNLIKELY_CANDIDATES.containsMatchIn(matchString) &&
                !Regexps.OK_MAYBE_ITS_A_CANDIDATE.containsMatchIn(matchString)
        ) {
            return@any false
        }

        if (node.querySelectorAll("li p").isNotEmpty()) {
            return@any false
        }

        val textContentLength = node.textContent.trim().length
        if (textContentLength < options.minContentLength) {
            return@any false
        }

        score += sqrt((textContentLength - options.minContentLength).toDouble()).toBigDecimal()

        score > options.minScore
    }
}

fun isProbablyReaderable(doc: Document, visibilityChecker: (Element) -> Boolean): Boolean {
    val options = ReaderableOptions(visibilityChecker = visibilityChecker)
    return isProbablyReaderable(doc, options)
}
