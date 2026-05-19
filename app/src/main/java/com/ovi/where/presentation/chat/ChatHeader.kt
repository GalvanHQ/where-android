package com.ovi.where.presentation.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ovi.where.presentation.chat.components.ConversationAvatar
import com.ovi.where.presentation.model.ConversationUiModel
import com.ovi.where.presentation.model.LastActiveFormatter
import kotlinx.coroutines.delay

/**
 * Messenger-style compact chat header for ChatScreen.
 *
 * Layout: back arrow → avatar (with online dot) → title column (name + status) → info icon.
 *
 * Subtitle is Messenger-style:
 *   - 1:1 online             → green dot + "Active now"
 *   - 1:1 offline + lastSeen → "Active 5m ago" (no dot)
 *   - 1:1 offline (unknown)  → "Offline"
 *   - Group, n online        → green dot + "{n} of {total} active"
 *   - Group, none online     → "{total} members"
 *
 * Subtitle text re-renders every minute so "Active 5m ago" ticks forward without a
 * full state push from the ViewModel.
 */
@Composable
fun ChatHeader(
    conversation: ConversationUiModel?,
    onNavigateBack: () -> Unit,
    onNavigateToGroupInfo: (String) -> Unit,
    onNavigateToConversationInfo: (String) -> Unit = {},
    onMapTap: () -> Unit = {},
    onlineMemberCount: Int = 0,
    isOtherUserFriend: Boolean = true,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(start = 4.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back arrow (24dp icon inside IconButton)
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (conversation != null) {
                // Tappable avatar + title area → navigates to info screen
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            if (conversation.isGroup) {
                                conversation.groupId?.let { onNavigateToGroupInfo(it) }
                            } else {
                                onNavigateToConversationInfo(conversation.id)
                            }
                        }
                        .semantics { contentDescription = "View ${conversation.title} info" },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Avatar (36dp) with 10dp online indicator
                    ConversationAvatar(
                        name = conversation.title,
                        photoUrl = conversation.photoUrl,
                        isOnline = !conversation.isGroup
                            && isOtherUserFriend
                            && conversation.isOtherUserOnline,
                        size = 36.dp,
                        indicatorSize = 10.dp,
                        indicatorBorderWidth = 1.5.dp
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Title column: title + subtitle
                    Column(
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Title: titleSmall + FontWeight.SemiBold
                        Text(
                            text = conversation.title,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        ActiveStatusSubtitle(
                            conversation = conversation,
                            onlineMemberCount = onlineMemberCount,
                            isOtherUserFriend = isOtherUserFriend
                        )
                    }
                }

                // Trailing action icons: map + info
                IconButton(
                    onClick = onMapTap,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Map,
                        contentDescription = "Show map",
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = {
                        if (conversation.isGroup) {
                            conversation.groupId?.let { onNavigateToGroupInfo(it) }
                        } else {
                            onNavigateToConversationInfo(conversation.id)
                        }
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Conversation info",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // Fallback when conversation is null (loading state)
                Text(
                    text = "Chat",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Renders the active-status subtitle line (with the live green dot when applicable).
 *
 * The text is recomputed every 60s so "Active 5m ago" advances over time without
 * waiting for a state push. Skips re-rendering entirely when the user is online or
 * has no known last-seen timestamp.
 */
@Composable
private fun ActiveStatusSubtitle(
    conversation: ConversationUiModel,
    onlineMemberCount: Int,
    isOtherUserFriend: Boolean
) {
    // Tick every minute so the relative-time subtitle stays current.
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(conversation.id) {
        while (true) {
            delay(60_000L)
            nowMs = System.currentTimeMillis()
        }
    }

    when {
        conversation.isGroup -> {
            if (onlineMemberCount > 0) {
                StatusLine(
                    showDot = true,
                    text = "$onlineMemberCount of ${conversation.memberCount} active",
                    color = MaterialTheme.colorScheme.tertiary
                )
            } else {
                StatusLine(
                    showDot = false,
                    text = "${conversation.memberCount} members",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        // 1:1 — only show presence info if the other user is a friend.
        !isOtherUserFriend -> Unit
        conversation.isOtherUserOnline -> {
            StatusLine(
                showDot = true,
                text = "Active now",
                color = MaterialTheme.colorScheme.tertiary
            )
        }
        else -> {
            val text = LastActiveFormatter.format(
                isOnline = false,
                lastSeen = conversation.otherUserLastSeen,
                now = nowMs
            )
            StatusLine(
                showDot = false,
                text = text,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatusLine(showDot: Boolean, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (showDot) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiary)
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
