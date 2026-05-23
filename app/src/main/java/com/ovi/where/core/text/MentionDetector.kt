package com.ovi.where.core.text

/**
 * Canonical regex + helpers for `@mention` tokens in chat content.
 *
 * Why centralize: the same `Regex("""@\w+""")` was inlined in
 * `ChatBubble.kt` for rendering and described in a comment in
 * `MentionStyledText.kt`. Server-side validation lives elsewhere; if we
 * ever tweak the rules (e.g. allow `.` or `_`, or require a leading
 * boundary), every consumer should pick the change up automatically.
 *
 * Conventions:
 *   • `@\w+` — `\w` is `[A-Za-z0-9_]` in Kotlin's regex, matching the
 *     username constraints we accept on signup.
 *   • Mentions are trailing-bounded by anything that's not `\w`, so
 *     `Hello @alice!` captures `@alice` cleanly.
 */
object MentionDetector {

    /**
     * The single canonical mention pattern. Lazy + `val` so the regex
     * engine compiles once across the whole process.
     */
    val PATTERN: Regex = Regex("""@\w+""")

    /**
     * Returns the [IntRange] (inclusive end, matching [MatchResult.range])
     * of every mention token in [text], in source order.
     *
     * Empty input → empty list.
     */
    fun ranges(text: String): List<IntRange> {
        if (text.isEmpty()) return emptyList()
        return PATTERN.findAll(text).map { it.range }.toList()
    }
}
