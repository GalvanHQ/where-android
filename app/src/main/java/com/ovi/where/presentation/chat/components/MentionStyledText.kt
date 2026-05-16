package com.ovi.where.presentation.chat.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import com.ovi.where.presentation.chat.MentionEngine

/**
 * Renders message text with @mentions styled in primary color and bold.
 *
 * Requirement 14.4: Render mentioned names in primary color and bold weight
 * within the message bubble text.
 *
 * @param text The message text content
 * @param mentionedUserIds List of user IDs mentioned in this message
 * @param userDisplayNames Map of userId to displayName for resolving mention text
 * @param modifier Modifier for the Text composable
 */
@Composable
fun MentionStyledText(
    text: String,
    mentionedUserIds: List<String>,
    userDisplayNames: Map<String, String>,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val textStyle = MaterialTheme.typography.bodyLarge
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    val annotatedString = remember(text, mentionedUserIds, userDisplayNames) {
        if (mentionedUserIds.isEmpty()) {
            AnnotatedString(text)
        } else {
            val mentionRanges = MentionEngine.findMentionRangesInMessage(
                text = text,
                mentionedUserIds = mentionedUserIds,
                userDisplayNames = userDisplayNames
            )

            if (mentionRanges.isEmpty()) {
                AnnotatedString(text)
            } else {
                buildAnnotatedString {
                    var lastIndex = 0
                    for (range in mentionRanges) {
                        // Append text before this mention
                        if (range.first > lastIndex) {
                            append(text.substring(lastIndex, range.first))
                        }
                        // Append the mention with styling
                        withStyle(
                            SpanStyle(
                                color = primaryColor,
                                fontWeight = FontWeight.Bold
                            )
                        ) {
                            append(text.substring(range.first, range.last + 1))
                        }
                        lastIndex = range.last + 1
                    }
                    // Append remaining text after last mention
                    if (lastIndex < text.length) {
                        append(text.substring(lastIndex))
                    }
                }
            }
        }
    }

    Text(
        text = annotatedString,
        style = textStyle.copy(color = onSurfaceColor),
        modifier = modifier,
        maxLines = maxLines,
        overflow = overflow
    )
}

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
