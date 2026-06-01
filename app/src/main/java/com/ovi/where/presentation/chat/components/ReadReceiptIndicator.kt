package com.ovi.where.presentation.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.ovi.where.presentation.model.BubbleDirection

/**
 * Messenger-style "seen" receipt: a single tiny reader avatar (or a compact stack of up to
 * [MAX_DISPLAYED_AVATARS] in groups) rendered to the trailing edge below the LAST sent
 * bubble in a group.
 *
 * Pure Messenger: no double-check icon, no labels. The avatar IS the seen indicator.
 * In groups, multiple avatars overlap with a thin surface ring so they read as a stack.
 * Beyond [MAX_DISPLAYED_AVATARS] readers, an unobtrusive "+N" tail is appended.
 */
@Composable
fun ReadReceiptIndicator(
    readBy: List<String>,
    readByPhotoUrls: List<String?>,
    direction: BubbleDirection,
    modifier: Modifier = Modifier,
    readByNames: List<String> = emptyList()
) {
    if (direction != BubbleDirection.SENT || readBy.isEmpty()) return

    val totalReaders = readBy.size
    // Iterate based on readBy (always populated) — photo and name lists are
    // optional and may be missing, in which case we fall back to a colored
    // placeholder circle. Without this, an empty readByPhotoUrls list (the
    // current MessageUiModel default) would render nothing at all.
    val displayCount = totalReaders.coerceAtMost(MAX_DISPLAYED_AVATARS)
    val overflow = totalReaders - displayCount

    val context = LocalContext.current
    val density = LocalDensity.current

    val avatarPixelSize = remember(density) {
        with(density) { READER_AVATAR_SIZE_DP.dp.roundToPx() }
    }

    val ringColor = MaterialTheme.colorScheme.surface

    Row(
        modifier = modifier
            .semantics {
                contentDescription =
                    "Seen by $totalReaders ${if (totalReaders == 1) "person" else "people"}"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        Box {
            repeat(displayCount) { index ->
                val photoUrl = readByPhotoUrls.getOrNull(index)
                val nameForFallback = readByNames.getOrNull(index).orEmpty()
                Box(
                    modifier = Modifier
                        .offset(x = (index * AVATAR_OVERLAP_OFFSET_DP).dp)
                        .size(READER_AVATAR_SIZE_DP.dp)
                        .border(1.5.dp, ringColor, CircleShape)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    if (!photoUrl.isNullOrBlank()) {
                        val avatarRequest = remember(photoUrl, avatarPixelSize) {
                            ImageRequest.Builder(context)
                                .data(photoUrl)
                                .crossfade(true)
                                .size(avatarPixelSize)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .networkCachePolicy(CachePolicy.ENABLED)
                                .memoryCacheKey(photoUrl)
                                .diskCacheKey(photoUrl)
                                .build()
                        }
                        AsyncImage(
                            model = avatarRequest,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(READER_AVATAR_SIZE_DP.dp)
                                .clip(CircleShape)
                        )
                    } else if (nameForFallback.isNotBlank()) {
                        // Tiny initials fallback when no photo is available.
                        Text(
                            text = computeInitials(nameForFallback).take(1),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 8.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    // Otherwise: solid primaryContainer circle stays as-is —
                    // still a visible "seen" cue.
                }
            }
        }

        if (overflow > 0) {
            Spacer(
                modifier = Modifier.width(
                    (((displayCount - 1).coerceAtLeast(0)) * AVATAR_OVERLAP_OFFSET_DP + 4).dp
                )
            )
            Text(
                text = "+$overflow",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private const val MAX_DISPLAYED_AVATARS = 3
private const val READER_AVATAR_SIZE_DP = 14
private const val AVATAR_OVERLAP_OFFSET_DP = 9
