package com.ovi.where.presentation.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.ovi.where.core.theme.AvatarColors

/**
 * Circular avatar composable with online indicator overlay.
 *
 * Displays a remote image (via Coil) when [photoUrl] is available,
 * or falls back to a colored circle with computed initials from [name].
 * An online indicator (green dot with white border) is shown at the
 * bottom-right corner when [isOnline] is true.
 *
 * Requirements: 3.1, 3.2, 3.3, 3.4, 6.1, 6.6
 */
@Composable
fun ConversationAvatar(
    name: String,
    photoUrl: String?,
    isOnline: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    indicatorSize: Dp = 14.dp,
    indicatorBorderWidth: Dp = 2.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = "$name avatar" },
        contentAlignment = Alignment.Center
    ) {
        // Avatar content: remote image or initials fallback
        if (!photoUrl.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(photoUrl)
                    .crossfade(true)
                    .memoryCacheKey(photoUrl)
                    .diskCacheKey(photoUrl)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .size(128)
                    .build(),
                contentDescription = name,
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            val initials = computeInitials(name)
            val backgroundColor = avatarBackgroundColor(name)

            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(backgroundColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = Color.White
                )
            }
        }

        // Online indicator: green dot with white border at bottom-right
        if (isOnline) {
            val indicatorOffset = size * 0.03f // slight inward offset
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = -indicatorOffset, y = -indicatorOffset)
                    .size(indicatorSize + indicatorBorderWidth * 2)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(indicatorSize)
                        .clip(CircleShape)
                        .background(Color(0xFF44B700))
                )
            }
        }
    }
}

/**
 * Computes initials from a display name.
 *
 * Rules:
 * - Blank/empty name → "?"
 * - Single word → first character, uppercased
 * - Multiple words → first character of first word + first character of second word, uppercased
 *
 * This function is `internal` for testability (property-tested in task 2.2).
 */
internal fun computeInitials(name: String): String {
    val trimmed = name.trim()
    if (trimmed.isBlank()) return "?"

    val words = trimmed.split("\\s+".toRegex())
    return when {
        words.size >= 2 -> "${words[0].first().uppercaseChar()}${words[1].first().uppercaseChar()}"
        else -> words[0].first().uppercaseChar().toString()
    }
}

/**
 * Picks a deterministic avatar background color based on the name string.
 * Uses the [AvatarColors] palette for visual variety.
 */
private fun avatarBackgroundColor(name: String): Color {
    val index = name.hashCode().and(0x7FFFFFFF) % AvatarColors.size
    return AvatarColors[index]
}
