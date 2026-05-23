package com.ovi.where.core.links

import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle

/**
 * Compose text component that renders any URLs inside [text] as tappable
 * hyperlinks while leaving the rest of the string untouched.
 *
 * Routes through [LinkRouter], so:
 *   • `where://chat/abc` shared in chat opens the chat screen in-app.
 *   • `https://example.com` opens in a Custom Tab (or browser).
 *   • `mailto:` and `tel:` go to the email app / dialer.
 *
 * Why not the deprecated `ClickableText`?
 *   `ClickableText` swallows accessibility roles and doesn't support text
 *   selection. Compose 1.7 introduced [LinkAnnotation] + [withLink] which
 *   lets the platform's text engine handle taps, long-press selection,
 *   and TalkBack link traversal natively.
 *
 * Performance: parsing happens once per `text` value via [remember]; the
 * annotated string itself is the only thing recomposed.
 */
@Composable
fun LinkText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = LocalContentColor.current,
    linkColor: Color = MaterialTheme.colorScheme.tertiary,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val context = LocalContext.current

    val annotated = remember(text, color, linkColor) {
        buildLinkAnnotatedString(
            text = text,
            color = color,
            linkColor = linkColor,
            onClick = { match -> LinkRouter.open(context, match) }
        )
    }

    BasicText(
        text = annotated,
        style = style.copy(color = color),
        modifier = modifier,
        maxLines = maxLines,
        overflow = overflow,
    )
}

/**
 * Pure helper that produces the annotated string. Exposed for callers
 * (like the chat bubble) that already have an [AnnotatedString] pipeline
 * for mentions and want to compose with this one.
 */
fun buildLinkAnnotatedString(
    text: String,
    color: Color,
    linkColor: Color,
    onClick: (LinkParser.Match) -> Unit,
): AnnotatedString {
    val matches = LinkParser.findAll(text)
    if (matches.isEmpty()) {
        return AnnotatedString(text, SpanStyle(color = color))
    }

    val linkStyle = TextLinkStyles(
        style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
    )
    return androidx.compose.ui.text.buildAnnotatedString {
        var cursor = 0
        for (match in matches) {
            if (match.start > cursor) {
                withStyle(SpanStyle(color = color)) {
                    append(text.substring(cursor, match.start))
                }
            }
            withLink(
                LinkAnnotation.Url(
                    url = match.targetUrl,
                    styles = linkStyle,
                    linkInteractionListener = { onClick(match) }
                )
            ) {
                append(match.displayText)
            }
            cursor = match.end
        }
        if (cursor < text.length) {
            withStyle(SpanStyle(color = color)) {
                append(text.substring(cursor))
            }
        }
    }
}
