package com.ovi.where.core.links

/**
 * Centralized URL / link detection used by chat bubbles, the notification
 * inbox, and anywhere else the app renders user-supplied text.
 *
 * Why a dedicated module?
 *   The previous implementation lived inline in `LinkPreviewCard.kt`,
 *   used a too-greedy regex (`https?://[^\s]+`) and only handled the
 *   `https?://` schemes. That caused two real bugs:
 *
 *     1. **Trailing punctuation got swallowed**:
 *        `Check out https://example.com/page.` → the regex captured
 *        `https://example.com/page.` and `Intent.ACTION_VIEW` opened a
 *        404 page on most servers.
 *     2. **Bare domains weren't tappable**:
 *        Users paste `example.com`, `www.example.com`, or `foo.io/bar`
 *        all the time. Those weren't detected.
 *     3. **`where://` URIs shared in chat opened the system browser**
 *        because `Intent.ACTION_VIEW` doesn't differentiate, and the
 *        browser doesn't know what `where://chat/abc` means.
 *
 * This parser owns the canonical regex set and the categorization of
 * each match, so the rest of the app can be opinionated about how to
 * handle internal vs. external links without re-implementing detection.
 *
 * Limits:
 *   • Not a full URI grammar — we accept what's pragmatically useful for
 *     a chat app and reject obviously broken inputs. Don't use this to
 *     validate URLs you'll be POST-ing somewhere security-sensitive.
 */
object LinkParser {

    /** A detected link in source text plus its category and span. */
    data class Match(
        /** The portion of the source text that matched, with trailing punctuation trimmed. */
        val displayText: String,
        /** The fully-qualified URL we'll hand to the OS or the in-app router. */
        val targetUrl: String,
        /** Char offset (inclusive) of [displayText] in the original source. */
        val start: Int,
        /** Char offset (exclusive) of [displayText] in the original source. */
        val end: Int,
        val kind: Kind,
    )

    enum class Kind {
        /** http(s) URL or bare domain. Opened in the system browser. */
        EXTERNAL,
        /** `where://` URI — routed through DeepLinkManager. */
        INTERNAL,
        /** `mailto:` URI — opened with the email app. */
        EMAIL,
        /** `tel:` URI — opened with the dialer. */
        PHONE
    }

    // ── Regex set ───────────────────────────────────────────────────────────
    //
    // Order matters: the dispatcher tries them in sequence and picks the
    // first non-overlapping match. `where://` must come before the generic
    // URL regex because both would otherwise match `where://chat/abc`.

    /** `where://host/path` — internal deep links shared in chat. */
    private val WHERE_URI = Regex("""where://[A-Za-z0-9_]+(?:/[\w.\-/%&?=#~+]*)?""")

    /** `mailto:user@example.com` */
    private val MAILTO = Regex("""mailto:[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}""")

    /** `tel:+1-555-0100` (loose — accept anything the dialer can handle). */
    private val TEL = Regex("""tel:\+?[0-9 .\-()]{4,}""")

    /** `http(s)://host[/path]` */
    private val HTTP_URL =
        Regex("""https?://(?:[A-Za-z0-9\-._~%]+(?::[A-Za-z0-9\-._~%]*)?@)?(?:[A-Za-z0-9\-._~%]+(?:\.[A-Za-z]{2,}))(?::\d{1,5})?(?:[/?#][^\s<>"]*)?""")

    /**
     * `www.host.tld` or `host.tld[/path]` — bare domains that we'll
     * promote to `https://` so they open as expected.
     *
     * Word boundary at the start prevents matching the trailing half of
     * an email address (`user@example.com` — the `example.com` part).
     * Restricting the TLD to two-or-more letters avoids matching
     * filenames like `foo.bar1`.
     */
    private val BARE_DOMAIN =
        Regex("""(?<![@./])\b(?:www\.)?[A-Za-z][A-Za-z0-9\-]*(?:\.[A-Za-z][A-Za-z0-9\-]*)*\.(?:com|net|org|io|app|dev|me|co|gov|edu|mil|info|biz|tv|ai|io|to|xyz|us|uk|de|fr|jp|cn|in|ca|au|nz|ru|br|it|es|nl|pl|se|no|fi|dk|cz|ch|at|be|gr|tr|kr|sg|hk|tw|mx|ar|cl|za|eg|ng|bd|pk|vn|th|id|my|ph|il|sa|ae|qa|kw|om|jo|lb|ma|tn|dz|ke|gh|ug|tz|et|sd)\b(?:/[\w.\-/%&?=#~+@:!*'()]*)?""")

    /**
     * Trailing characters that are almost always punctuation, never part
     * of the URL. Stripped from the display + target span.
     *
     * Keep `)` symmetric: only strip if there's no matching `(` left
     * inside the URL — `https://en.wikipedia.org/wiki/Foo_(bar)` should
     * stay intact.
     */
    private val TRAILING_PUNCTUATION = setOf('.', ',', ';', ':', '!', '?', '"', '\'', '>', ']', '}')

    // ── Public API ──────────────────────────────────────────────────────────

    /**
     * Finds every link in [text] in source order, with overlapping matches
     * resolved in favor of the more specific category.
     *
     * Empty input → empty list.
     */
    fun findAll(text: String): List<Match> {
        if (text.isEmpty()) return emptyList()
        val out = mutableListOf<Match>()

        // Sweep through each pattern. Same-position matches keep the
        // higher-priority kind (WHERE > EMAIL > TEL > HTTP > BARE).
        val candidates = mutableListOf<Triple<IntRange, Kind, String>>()
        WHERE_URI.findAll(text).forEach { candidates += Triple(it.range, Kind.INTERNAL, it.value) }
        MAILTO.findAll(text).forEach { candidates += Triple(it.range, Kind.EMAIL, it.value) }
        TEL.findAll(text).forEach { candidates += Triple(it.range, Kind.PHONE, it.value) }
        HTTP_URL.findAll(text).forEach { candidates += Triple(it.range, Kind.EXTERNAL, it.value) }
        BARE_DOMAIN.findAll(text).forEach { candidates += Triple(it.range, Kind.EXTERNAL, it.value) }

        // Sort by start, drop any candidate that overlaps an earlier-kept one.
        candidates.sortWith(compareBy({ it.first.first }, { kindRank(it.second) }))
        var lastEnd = -1
        for ((range, kind, raw) in candidates) {
            if (range.first <= lastEnd) continue

            val (trimmedDisplay, trimmedRange) = trimTrailingPunctuation(raw, range)
            if (trimmedDisplay.isBlank()) continue

            val target = when (kind) {
                Kind.INTERNAL, Kind.EMAIL, Kind.PHONE -> trimmedDisplay
                Kind.EXTERNAL ->
                    if (trimmedDisplay.startsWith("http://") ||
                        trimmedDisplay.startsWith("https://")
                    ) trimmedDisplay
                    else "https://$trimmedDisplay"
            }

            out += Match(
                displayText = trimmedDisplay,
                targetUrl = target,
                start = trimmedRange.first,
                end = trimmedRange.last + 1,
                kind = kind,
            )
            lastEnd = trimmedRange.last
        }
        return out
    }

    /**
     * Convenience: extracts only the first http(s) URL — used by the link
     * preview pipeline which can only render one preview per message.
     *
     * Skips internal / mail / tel matches because we never preview those.
     */
    fun firstExternalUrl(text: String): String? =
        findAll(text).firstOrNull { it.kind == Kind.EXTERNAL }?.targetUrl

    // ── Internals ──────────────────────────────────────────────────────────

    /**
     * Trims trailing punctuation that's almost certainly not part of the
     * URL. Closing parens are special-cased: keep them when they balance
     * an opening paren inside the URL.
     */
    private fun trimTrailingPunctuation(raw: String, range: IntRange): Pair<String, IntRange> {
        var endExclusive = range.last + 1
        var s = raw

        while (s.isNotEmpty()) {
            val last = s.last()
            when {
                last in TRAILING_PUNCTUATION -> {
                    s = s.dropLast(1)
                    endExclusive--
                }
                last == ')' -> {
                    val opens = s.count { it == '(' }
                    val closes = s.count { it == ')' }
                    if (closes > opens) {
                        s = s.dropLast(1)
                        endExclusive--
                    } else break
                }
                else -> break
            }
        }
        return s to (range.first until endExclusive)
    }

    private fun kindRank(kind: Kind): Int = when (kind) {
        Kind.INTERNAL -> 0
        Kind.EMAIL -> 1
        Kind.PHONE -> 2
        Kind.EXTERNAL -> 3
    }
}
