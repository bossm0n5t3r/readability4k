package me.bossm0n5t3r.readability4k

data class TestPage(
    val dir: String,
    val source: String,
    val expectedContent: String,
    val expectedMetadata: Map<String, Any?>,
)
