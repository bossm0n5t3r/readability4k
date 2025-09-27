package me.bossm0n5t3r.readability4k

/**
 * Regular expressions used throughout the Readability implementation.
 * All regex patterns from the original JavaScript implementation.
 */
object Regexps {
    // Main content detection patterns
    val UNLIKELY_CANDIDATES =
        Regex(
            "-ad-|ai2html|banner|breadcrumbs|combx|comment|community|cover-wrap|disqus|extra|footer|gdpr|header|legends|menu|related|remark|replies|rss|shoutbox|sidebar|skyscraper|social|sponsor|supplemental|ad-break|agegate|pagination|pager|popup|yom-remote",
            RegexOption.IGNORE_CASE,
        )

    val OK_MAYBE_ITS_A_CANDIDATE =
        Regex(
            "and|article|body|column|content|main|mathjax|shadow",
            RegexOption.IGNORE_CASE,
        )

    val POSITIVE =
        Regex(
            "article|body|content|entry|hentry|h-entry|main|page|pagination|post|text|blog|story",
            RegexOption.IGNORE_CASE,
        )

    val NEGATIVE =
        Regex(
            "-ad-|hidden|^hid$| hid$| hid |^hid |banner|combx|comment|com-|contact|footer|gdpr|masthead|media|meta|outbrain|promo|related|scroll|share|shoutbox|sidebar|skyscraper|sponsor|shopping|tags|widget",
            RegexOption.IGNORE_CASE,
        )

    val EXTRANEOUS =
        Regex(
            "print|archive|comment|discuss|e[\\\\-]?mail|share|reply|all|login|sign|single|utility",
            RegexOption.IGNORE_CASE,
        )

    val BYLINE =
        Regex(
            "byline|author|dateline|writtenby|p-author",
            RegexOption.IGNORE_CASE,
        )

    // Text processing patterns
    val REPLACE_FONTS =
        Regex(
            "<(/?)font[^>]*>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
        )

    val NORMALIZE = Regex("\\s{2,}")

    // Video and media patterns
    val VIDEOS =
        Regex(
            "//(www\\.)?((dailymotion|youtube|youtube-nocookie|player\\.vimeo|v\\.qq|bilibili|live.bilibili)\\.com|(archive|upload\\.wikimedia)\\.org|player\\.twitch\\.tv)",
            RegexOption.IGNORE_CASE,
        )

    // Navigation patterns
    val NEXT_LINK =
        Regex(
            "(next|weiter|continue|>([^|]|$)|»([^|]|$))",
            RegexOption.IGNORE_CASE,
        )

    val PREV_LINK =
        Regex(
            "(prev|earl|old|new|<|«)",
            RegexOption.IGNORE_CASE,
        )

    // Content analysis patterns
    val SHARE_ELEMENTS =
        Regex(
            "(\\b|_)(share|sharedaddy)(\\b|_)",
            RegexOption.IGNORE_CASE,
        )

    val TOKENIZE = Regex("\\W+")

    val WHITESPACE = Regex("^\\s*$")

    val HAS_CONTENT = Regex("\\S$")

    val HASH_URL = Regex("^#.+")

    val SRCSET_URL = Regex("(\\S+)(\\s+[\\d.]+[xw])?(\\s*(?:,|$))")

    val B64_DATA_URL =
        Regex(
            "^data:\\s*([^\\s;,]+)\\s*;\\s*base64\\s*,",
            RegexOption.IGNORE_CASE,
        )

    // Commas as used in Latin, Sindhi, Chinese and various other scripts.
    // see: https://en.wikipedia.org/wiki/Comma#Comma_variants
    val COMMAS = Regex("[\\u002C\\u060C\\uFE50\\uFE10\\uFE11\\u2E41\\u2E34\\u2E32\\uFF0C]")

    // See: https://schema.org/Article
    val JSON_LD_ARTICLE_TYPES =
        Regex(
            "^Article|AdvertiserContentArticle|NewsArticle|AnalysisNewsArticle|AskPublicNewsArticle|BackgroundNewsArticle|OpinionNewsArticle|ReportageNewsArticle|ReviewNewsArticle|Report|SatiricalArticle|ScholarlyArticle|MedicalScholarlyArticle|SocialMediaPosting|BlogPosting|LiveBlogPosting|DiscussionForumPosting|TechArticle|APIReference$",
        )

    // Content filtering patterns
    val AD_WORDS =
        Regex(
            "^(ad(vertising|vertisement)?|pub(licité)?|werb(ung)?|广告|Реклама|Anuncio)$",
            RegexOption.IGNORE_CASE,
        )

    val LOADING_WORDS =
        Regex(
            "^((loading|正在加载|Загрузка|chargement|cargando)(…|\\.\\.\\.)?)$",
            RegexOption.IGNORE_CASE,
        )
}
