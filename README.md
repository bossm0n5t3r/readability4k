# readability4k

A Kotlin port of [Mozilla Readability](https://github.com/mozilla/readability) that extracts readable article content
from HTML pages using a self-contained DOM implementation.

This project is licensed under the Apache License 2.0. See the `LICENSE` file for the full license text and `NOTICE` for
attribution.

## Maven coordinates

```text
me.bossm0n5t3r:readability4k:1.0.0
```

Remote distribution is not configured yet.

## Usage

The library works with `me.bossm0n5t3r.readability4k.dom.Document`, not with Jsoup `Document`.

```kotlin
import me.bossm0n5t3r.readability4k.Readability
import me.bossm0n5t3r.readability4k.dom.DOMParser

// Supply the source URL so relative links and media URLs are resolved.
val document = DOMParser().parse(html, "https://example.com/article")
val article = Readability(document).parse()
    ?: error("No readable article found")

println(article.title)
println(article.content)
```

`DOMParser().parse(html, url)` parses the raw HTML into a `Document`. The optional URL is used as the document base URI
so that relative URLs are resolved to absolute URLs during processing.

`Readability(document).parse()` mutates the supplied document and returns `null` when no readable article could be
found. The returned `ReadabilityResult` contains:

- `title` — article title, or `null`
- `content` — processed article HTML
- `textContent` — plain text extracted from the article content
- `length` — character length of `textContent`
- `excerpt` — short excerpt, or `null`
- `byline`, `dir`, `lang`, `siteName`, `publishedTime` — extracted metadata
