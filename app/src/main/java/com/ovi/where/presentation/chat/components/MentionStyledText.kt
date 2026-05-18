package com.ovi.where.presentation.chat.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

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
    primaryColor: androidx.compose.ui.graphics.Color
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
