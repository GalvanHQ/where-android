package com.ovi.where.presentation.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ovi.where.presentation.chat.components.ConversationAvatar
import com.ovi.where.presentation.model.ConversationUiModel

/**
 * Messenger-style compact chat header composable for the ChatScreen.
 *
 * Layout: back arrow (24dp) → 8dp → Avatar (36dp) with 10dp online indicator → 8dp → title column
 * Trailing actions: info icon (24dp, onSurfaceVariant).
 *
 * Displays differently for 1:1 vs group conversations:
 * - 1:1 online: subtitle "Active now" in green/tertiary
 * - 1:1 offline: subtitle "Offline" in onSurfaceVariant
 * - Group: subtitle "{N} members" in onSurfaceVariant
 *
 * Tapping the avatar/title area navigates to the info screen.
 *
 * Requirements: 2.1, 2.5, 6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 6.7, 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 7.7, 7.8, 7.9, 7.10, 7.11
 */
@Composable
fun ChatHeader(
    conversation: ConversationUiModel?,
    isOtherUserFriend: Boolean,
    activeLocationSharingCount: Int = 0,
    isOtherUserSharingLocation: Boolean = false,
    onlineMemberCount: Int = 0,
    isCurrentUserAdmin: Boolean = false,
    showAdminOverflowMenu: Boolean = false,
    onNavigateBack: () -> Unit,
    onNavigateToUserProfile: (String) -> Unit,
    onNavigateToGroupInfo: (String) -> Unit,
    onNavigateToConversationInfo: (String) -> Unit = {},
    onNavigateToGroupMap: (String) -> Unit,
    onNavigateToEditGroup: (String) -> Unit = {},
    onNavigateToMediaGallery: (String) -> Unit = {},
    onSearchTap: () -> Unit = {},
    onAdminOverflowToggle: () -> Unit = {},
    onAdminOverflowDismiss: () -> Unit = {},
    onMuteMemberTap: () -> Unit = {},
    onGroupSettingsTap: () -> Unit = {},
    onInviteLinkTap: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp
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
                        isOnline = !conversation.isGroup && conversation.isOtherUserOnline,
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

                        // Subtitle based on conversation type and online status
                        val subtitleText: String
                        val subtitleColor = when {
                            conversation.isGroup -> {
                                subtitleText = "${conversation.memberCount} members"
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            conversation.isOtherUserOnline -> {
                                subtitleText = "Active now"
                                MaterialTheme.colorScheme.tertiary
                            }
                            else -> {
                                subtitleText = "Offline"
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        }

                        Text(
                            text = subtitleText,
                            style = MaterialTheme.typography.labelSmall,
                            color = subtitleColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Trailing action icon: info only (call buttons removed)
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
