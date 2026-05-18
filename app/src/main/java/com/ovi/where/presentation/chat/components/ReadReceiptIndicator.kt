package com.ovi.where.presentation.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.ovi.where.core.theme.Dimens
import com.ovi.where.presentation.model.BubbleDirection

/**
 * Read receipt indicator shown below sent messages.
 *
 * Displays:
 * - Double-tick icon (DoneAll) tinted with primary color
 * - Up to 3 reader avatars (overlapping)
 * - "+N" overflow text when more than 3 readers
 */
@Composable
fun ReadReceiptIndicator(
    readBy: List<String>,
    readByPhotoUrls: List<String?>,
    direction: BubbleDirection,
    modifier: Modifier = Modifier
) {
    if (direction != BubbleDirection.SENT || readBy.isEmpty()) return

    val totalReaders = readBy.size
    val displayedAvatars = readByPhotoUrls.take(MAX_DISPLAYED_AVATARS)
    val overflow = totalReaders - MAX_DISPLAYED_AVATARS

    val context = LocalContext.current
    val density = LocalDensity.current

    val avatarPixelSize = remember(density) {
        with(density) { READER_AVATAR_SIZE_DP.dp.roundToPx() }
    }

    Row(
        modifier = modifier
            .padding(top = Dimens.spaceXSmall)
            .semantics {
                contentDescription =
                    "Read by $totalReaders ${if (totalReaders == 1) "person" else "people"}"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSmall)
    ) {
        Icon(
            imageVector = Icons.Default.DoneAll,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp)
        )

        if (displayedAvatars.isNotEmpty()) {
            Box {
                displayedAvatars.forEachIndexed { index, photoUrl ->
                    Box(
                        modifier = Modifier
                            .offset(x = (index * AVATAR_OVERLAP_OFFSET_DP).dp)
                            .size(READER_AVATAR_SIZE_DP.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
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
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(READER_AVATAR_SIZE_DP.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                            )
                        }
                    }
                }
            }

            if (overflow > 0) {
                Text(
                    text = "+$overflow",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(
                        start = ((displayedAvatars.size * AVATAR_OVERLAP_OFFSET_DP) +
                                READER_AVATAR_SIZE_DP).dp
                    )
                )
            }
        }
    }
}

private const val MAX_DISPLAYED_AVATARS = 3
private const val READER_AVATAR_SIZE_DP = 16
private const val AVATAR_OVERLAP_OFFSET_DP = 10