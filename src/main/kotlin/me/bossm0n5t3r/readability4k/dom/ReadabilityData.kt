package me.bossm0n5t3r.readability4k.dom

data class ReadabilityData(
    var contentScore: Double,
    var isDataTable: Boolean = false,
    var hasContentScore: Boolean = false,
)
