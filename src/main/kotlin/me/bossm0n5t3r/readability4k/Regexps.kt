package me.bossm0n5t3r.readability4k

object Regexps {
    val UNLIKELY_CANDIDATES =
        Regex(
            "-ad-|ai2html|banner|breadcrumbs|combx|comment|community|cover-wrap|disqus|extra|footer|gdpr|header|legends|menu|related|remark|replies|rss|shoutbox|sidebar|skyscraper|social|sponsor|supplemental|ad-break|agegate|pagination|pager|popup|yom-remote",
            RegexOption.IGNORE_CASE,
        )
    val OK_MAYBE_ITS_A_CANDIDATE = Regex("and|article|body|column|content|main|mathjax|shadow", RegexOption.IGNORE_CASE)
}
