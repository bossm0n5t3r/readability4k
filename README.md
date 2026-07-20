# readability4k

[![Maven Central Version](https://img.shields.io/maven-central/v/com.m0n5t3r.boss/readability4k)](https://central.sonatype.com/artifact/com.m0n5t3r.boss/readability4k)

A Kotlin port of [Mozilla Readability](https://github.com/mozilla/readability) that extracts readable article content
from HTML pages using a self-contained DOM implementation.

This project is licensed under the Apache License 2.0. See the `LICENSE` file for the full license text and `NOTICE` for
attribution.

## Maven coordinates

```text
com.m0n5t3r.boss:readability4k:1.0.0
```

Maven Central Portal publication is configured. After verifying the `com.m0n5t3r.boss` namespace, run:

```sh
./deploy.sh
```

The script prompts for any missing PGP and Central Portal credentials without persisting them. It creates a signed
Central bundle and uploads it as `USER_MANAGED`; release it from the Central Portal only after its validation succeeds.

## Usage

The library works with `com.m0n5t3r.boss.readability4k.dom.Document`, not with Jsoup `Document`.

```kotlin
import com.m0n5t3r.boss.readability4k.Readability
import com.m0n5t3r.boss.readability4k.dom.DOMParser

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
