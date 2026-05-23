package com.ovi.where.presentation.chat.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ovi.where.core.links.LinkRouter
import com.ovi.where.core.links.LinkText

/**
 * Renders a link preview card below a message bubble.
 *
 * Tap behaviour is delegated to [LinkRouter.openRaw] so the same pipeline
 * that handles URLs typed into chat text (Custom Tab, internal `where://`
 * deep-links, mailto/tel) drives this card too.
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
            .clickable { LinkRouter.openRaw(context, url) },
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
                    fontWeight = FontWeight.SemiBold,
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
 * Backwards-compatible shim: the chat bubble still calls `LinkableText`,
 * but it now defers to the centralized [LinkText] which handles bare
 * domains, `where://` deep-links, mailto/tel, and trailing punctuation.
 *
 * Kept in this file so existing imports keep resolving without a sweep.
 */
@Composable
fun LinkableText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier
) {
    LinkText(
        text = text,
        style = style,
        color = color,
        linkColor = MaterialTheme.colorScheme.tertiary,
        modifier = modifier,
    )
}
