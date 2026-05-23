package com.ovi.where.presentation.chat.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import com.ovi.where.core.links.LinkParser

/**
 * Builds an AnnotatedString with mention styling for use in input fields.
 * Applies primary color and bold to mention token ranges.
 *
 * Requirement 14.2: Styled mention token (primary color, bold) in the input field.
 *
 * @param text The input text
 * @param mentionRanges Ranges of mention tokens in the text
 * @param primaryColor The primary color to use for mentions
 * @return AnnotatedString with mention styling applied
 */
fun buildMentionAnnotatedString(
    text: String,
    mentionRanges: List<IntRange>,
    primaryColor: Color
): AnnotatedString {
    if (mentionRanges.isEmpty()) return AnnotatedString(text)

    return buildAnnotatedString {
        var lastIndex = 0
        for (range in mentionRanges.sortedBy { it.first }) {
            val safeStart = range.first.coerceIn(0, text.length)
            val safeEnd = (range.last + 1).coerceIn(0, text.length)
            if (safeStart > lastIndex) {
                append(text.substring(lastIndex, safeStart))
            }
            if (safeEnd > safeStart) {
                withStyle(
                    SpanStyle(
                        color = primaryColor,
                        fontWeight = FontWeight.Bold
                    )
                ) {
                    append(text.substring(safeStart, safeEnd))
                }
            }
            lastIndex = safeEnd
        }
        if (lastIndex < text.length) {
            append(text.substring(lastIndex))
        }
    }
}

/**
 * Builds an AnnotatedString that styles BOTH mentions and URLs in a single
 * pass so chat bubbles render `@alice check https://example.com` correctly:
 * the `@alice` is bold + primary, and the URL is underlined + tappable.
 *
 * Mention ranges and link ranges in source text never overlap in practice
 * (the @ regex only captures `@\w+`, the URL regex starts with a scheme or
 * domain letter), so we merge them by sorted start index.
 *
 * @param text         The full message body.
 * @param mentionRanges Mention spans (typically extracted via `Regex("@\\w+")`).
 * @param baseColor    Color for non-styled text.
 * @param mentionColor Tint applied to mention spans (bold + this color).
 * @param linkColor    Tint applied to URL spans (underline + this color).
 * @param onLinkClick  Invoked when the user taps a parsed link.
 */
fun buildMentionAndLinkAnnotatedString(
    text: String,
    mentionRanges: List<IntRange>,
    baseColor: Color,
    mentionColor: Color,
    linkColor: Color,
    onLinkClick: (LinkParser.Match) -> Unit,
): AnnotatedString {
    val links = LinkParser.findAll(text)
    if (mentionRanges.isEmpty() && links.isEmpty()) {
        return AnnotatedString(text, SpanStyle(color = baseColor))
    }

    // Build a unified, sorted span list: each entry is (start, end, isMention, link?).
    data class Span(
        val start: Int,
        val end: Int,
        val isMention: Boolean,
        val link: LinkParser.Match?,
    )

    val spans = buildList {
        mentionRanges.forEach { r ->
            add(Span(start = r.first, end = r.last + 1, isMention = true, link = null))
        }
        links.forEach { m ->
            add(Span(start = m.start, end = m.end, isMention = false, link = m))
        }
    }.sortedBy { it.start }

    val linkStyles = TextLinkStyles(
        style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
    )

    return buildAnnotatedString {
        var cursor = 0
        for (span in spans) {
            // Defensive: skip spans that overlap something we already rendered.
            if (span.start < cursor) continue
            if (span.start > cursor) {
                withStyle(SpanStyle(color = baseColor)) {
                    append(text.substring(cursor, span.start))
                }
            }
            val safeEnd = span.end.coerceAtMost(text.length)
            if (span.isMention) {
                withStyle(
                    SpanStyle(
                        color = mentionColor,
                        fontWeight = FontWeight.Bold,
                    )
                ) {
                    append(text.substring(span.start, safeEnd))
                }
            } else {
                val match = span.link!!
                withLink(
                    LinkAnnotation.Url(
                        url = match.targetUrl,
                        styles = linkStyles,
                        linkInteractionListener = { onLinkClick(match) },
                    )
                ) {
                    append(match.displayText)
                }
            }
            cursor = safeEnd
        }
        if (cursor < text.length) {
            withStyle(SpanStyle(color = baseColor)) {
                append(text.substring(cursor))
            }
        }
    }
}
