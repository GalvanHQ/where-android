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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ovi.where.core.theme.Dimens
import com.ovi.where.presentation.model.BubbleDirection

/**
 * Read receipt indicator shown below sent messages.
 *
 * Displays:
 * - Double-tick icon (DoneAll) tinted with primary color
 * - Up to 3 reader avatars (overlapping)
 * - "+N" overflow text when more than 3 readers
 *
 * Only shown on sent messages when readBy has at least 1 non-sender user (Requirement 5.3).
 *
 * Requirements: 5.3, 5.6
 */
@Composable
fun ReadReceiptIndicator(
    readBy: List<String>,
    readByPhotoUrls: List<String?>,
    direction: BubbleDirection,
    modifier: Modifier = Modifier
) {
    // Only show on sent messages with at least 1 reader (Requirement 5.3)
    if (direction != BubbleDirection.SENT || readBy.isEmpty()) return

    val totalReaders = readBy.size
    val displayedAvatars = readByPhotoUrls.take(MAX_DISPLAYED_AVATARS)
    val overflow = totalReaders - MAX_DISPLAYED_AVATARS

    Row(
        modifier = modifier
            .padding(top = Dimens.spaceXSmall)
            .semantics {
                contentDescription = "Read by $totalReaders ${if (totalReaders == 1) "person" else "people"}"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSmall)
    ) {
        // Double-tick icon
        Icon(
            imageVector = Icons.Default.DoneAll,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp)
        )

        // Reader avatars (overlapping)
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
                        if (photoUrl != null) {
                            AsyncImage(
                                model = photoUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(READER_AVATAR_SIZE_DP.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            // Placeholder circle for users without photos
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

            // "+N" overflow indicator (Requirement 5.6)
            if (overflow > 0) {
                Text(
                    text = "+$overflow",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(
                        start = ((displayedAvatars.size * AVATAR_OVERLAP_OFFSET_DP) + READER_AVATAR_SIZE_DP).dp
                    )
                )
            }
        }
    }
}

/** Maximum number of reader avatars to display before showing "+N". */
private const val MAX_DISPLAYED_AVATARS = 3

/** Size of each reader avatar in dp. */
private const val READER_AVATAR_SIZE_DP = 16

/** Horizontal offset between overlapping avatars in dp. */
private const val AVATAR_OVERLAP_OFFSET_DP = 10
