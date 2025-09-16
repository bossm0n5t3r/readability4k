package me.bossm0n5t3r.readability4k

import kotlinx.serialization.json.JsonElement

data class TestPage(
    val dir: String,
    val source: String,
    val expectedContent: String,
    val expectedMetadata: Map<String, JsonElement>,
)
