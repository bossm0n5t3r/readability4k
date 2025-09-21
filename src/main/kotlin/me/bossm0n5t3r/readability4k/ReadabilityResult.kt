package me.bossm0n5t3r.readability4k

data class ReadabilityResult(
    val title: String?,
    val byline: String?,
    val dir: String?,
    val lang: String?,
    val content: String,
    val textContent: String,
    val length: Long,
    val excerpt: String?,
    val siteName: String?,
    val publishedTime: String?,
)
