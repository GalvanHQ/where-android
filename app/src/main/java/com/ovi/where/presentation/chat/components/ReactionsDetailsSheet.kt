package com.ovi.where.presentation.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/**
 * One reactor entry shown inside [ReactionsDetailsSheet].
 *
 * @property userId Stable id of the user who reacted (used as the row key).
 * @property displayName Name to show; falls back to "Unknown" when blank.
 * @property photoUrl Avatar URL; null/blank renders an initials placeholder.
 * @property emoji The emoji this user reacted with.
 * @property isCurrentUser When true, the row is labelled "Tap to remove" so the user
 *   knows they can clear their own reaction.
 */
data class Reactor(
    val userId: String,
    val displayName: String,
    val photoUrl: String?,
    val emoji: String,
    val isCurrentUser: Boolean
)

/**
 * Messenger-style bottom sheet listing everyone who reacted to a message,
 * grouped by emoji with quick-filter tabs at the top.
 *
 * Tabs: "All" + one tab per distinct emoji (with count). Tapping a tab filters the list.
 * Tapping the current user's own row removes their reaction.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReactionsDetailsSheet(
    reactors: List<Reactor>,
    onDismiss: () -> Unit,
    onRemoveOwnReaction: (emoji: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Group by emoji, preserving original order.
    val grouped = remember(reactors) {
        reactors.groupBy { it.emoji }.toList()
    }
    val allCount = reactors.size

    var selectedEmoji by rememberSaveable { mutableStateOf<String?>(null) }
    val visibleReactors = remember(reactors, selectedEmoji) {
        if (selectedEmoji == null) reactors else reactors.filter { it.emoji == selectedEmoji }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            // Header.
            Text(
                text = "Reactions",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )

            Spacer(Modifier.height(4.dp))

            // Filter pills: "All" + one per emoji.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterPill(
                    label = "All  $allCount",
                    selected = selectedEmoji == null,
                    onClick = { selectedEmoji = null }
                )
                grouped.forEach { (emoji, group) ->
                    FilterPill(
                        label = "$emoji  ${group.size}",
                        selected = selectedEmoji == emoji,
                        onClick = { selectedEmoji = emoji }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Reactor list.
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
            ) {
                items(visibleReactors, key = { it.userId + it.emoji }) { reactor ->
                    ReactorRow(
                        reactor = reactor,
                        onClick = {
                            if (reactor.isCurrentUser) {
                                onRemoveOwnReaction(reactor.emoji)
                                onDismiss()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val fg = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            ),
            color = fg
        )
    }
}

@Composable
private fun ReactorRow(
    reactor: Reactor,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar.
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            if (!reactor.photoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = reactor.photoUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                )
            } else {
                Text(
                    text = computeInitials(reactor.displayName).take(2),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(Modifier.size(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = reactor.displayName.ifBlank { "Unknown" },
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (reactor.isCurrentUser) {
                Text(
                    text = "Tap to remove",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text(
            text = reactor.emoji,
            fontSize = 20.sp
        )
    }
}
