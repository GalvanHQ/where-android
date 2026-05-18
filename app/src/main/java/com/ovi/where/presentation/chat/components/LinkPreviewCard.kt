package com.ovi.where.presentation.chat.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.ui.graphics.Color

/**
 * Renders a link preview card below a message bubble.
 *
 * Displays:
 * - Thumbnail image (max 160dp height) if available from Open Graph metadata
 * - Title (pre-truncated to 80 chars with ellipsis by the mapper)
 * - Domain name
 *
 * On tap: opens the URL in the system browser via implicit intent.
 *
 * Requirements: 12.1, 12.4, 12.5, 12.6
 */
@Composable
fun LinkPreviewCard(
    url: String,
    title: String,
    description: String?,
    imageUrl: String?,
    domain: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                // Requirement 12.4: Open URL in system browser via implicit intent
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                context.startActivity(intent)
            },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column {
            // Thumbnail image with max height of 180dp
            if (!imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = "Link preview thumbnail for $domain",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 180.dp)
                        .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                )
            }

            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                // Domain name — uppercase + tracked for clean preview header look
                Text(
                    text = domain.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 0.5.sp,
                        fontSize = 10.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Title (already truncated to 80 chars with ellipsis by mapper)
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp)
                )

                // Description (omitted if title was missing in metadata per Req 12.6)
                if (!description.isNullOrBlank()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
            }
        }
    }
}


/**
 * Renders text with URLs detected and styled as tappable hyperlinks.
 *
 * Used when a message contains URLs but no link preview metadata
 * (e.g., when the link preview API timed out or failed).
 *
 * Requirement 12.3: Render URL as tappable hyperlink without a preview card.
 * Requirement 12.4: On tap, open URL in system browser via implicit intent.
 */
@Composable
fun LinkableText(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    color: Color,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val urlRegex = Regex("""https?://[^\s]+""")
    val matches = urlRegex.findAll(text).toList()

    if (matches.isEmpty()) {
        Text(
            text = text,
            style = style,
            color = color,
            modifier = modifier
        )
        return
    }

    val linkColor = MaterialTheme.colorScheme.tertiary
    val annotatedString = buildAnnotatedString {
        var lastIndex = 0
        matches.forEach { match ->
            // Append text before the URL
            if (match.range.first > lastIndex) {
                withStyle(SpanStyle(color = color)) {
                    append(text.substring(lastIndex, match.range.first))
                }
            }
            // Append the URL with link styling
            pushStringAnnotation(tag = "URL", annotation = match.value)
            withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                append(match.value)
            }
            pop()
            lastIndex = match.range.last + 1
        }
        // Append remaining text after last URL
        if (lastIndex < text.length) {
            withStyle(SpanStyle(color = color)) {
                append(text.substring(lastIndex))
            }
        }
    }

    ClickableText(
        text = annotatedString,
        style = style,
        modifier = modifier,
        onClick = { offset ->
            annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                .firstOrNull()?.let { annotation ->
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(annotation.item))
                    context.startActivity(intent)
                }
        }
    )
}
