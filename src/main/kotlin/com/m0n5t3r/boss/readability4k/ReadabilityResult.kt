package com.m0n5t3r.boss.readability4k

data class ReadabilityResult(
    // article title
    val title: String?,
    // HTML string of processed article content
    val content: String,
    // text content of the article, with all the HTML tags removed
    val textContent: String,
    // length of an article, in characters
    val length: Int,
    // article description, or short excerpt from the content
    val excerpt: String?,
    // author metadata
    val byline: String?,
    // content direction
    val dir: String?,
    // name of the site
    val siteName: String?,
    // content language
    val lang: String?,
    // published time
    val publishedTime: String?,
)
