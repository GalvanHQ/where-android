package com.ovi.where.presentation.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ovi.where.presentation.model.ConversationUiModel

/**
 * Custom chat header composable for the ChatScreen.
 *
 * Displays differently for 1:1 vs group conversations per DESIGN.md spec:
 * - 1:1 conversation: 40dp avatar, contact name, online status text ("Online"/"Offline")
 * - Group conversation: 40dp avatar, group name, member count text
 *
 * Hides presence status if the other user is not in the caller's friends list (Requirement 8.4).
 *
 * Navigation actions: back, profile/group info, group map.
 *
 * Requirements: 16.5, 16.6, 8.3, 8.4
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatHeader(
    conversation: ConversationUiModel?,
    isOtherUserFriend: Boolean,
    onNavigateBack: () -> Unit,
    onNavigateToUserProfile: (String) -> Unit,
    onNavigateToGroupInfo: (String) -> Unit,
    onNavigateToGroupMap: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        modifier = modifier,
        title = {
            if (conversation != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        // Tap on header navigates to profile/group info
                        if (conversation.isGroup) {
                            conversation.groupId?.let { onNavigateToGroupInfo(it) }
                        } else {
                            conversation.otherUserId?.let { onNavigateToUserProfile(it) }
                        }
                    }
                ) {
                    // 40dp avatar
                    ChatHeaderAvatar(
                        photoUrl = conversation.photoUrl,
                        title = conversation.title
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    // Name and status/member count
                    Column {
                        Text(
                            text = conversation.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        // Subtitle: online status for 1:1, member count for groups
                        if (conversation.isGroup) {
                            Text(
                                text = "${conversation.memberCount} members",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        } else if (isOtherUserFriend) {
                            // Requirement 8.4: Only show presence if other user is in friends list
                            val statusText = if (conversation.isOtherUserOnline) "Online" else "Offline"
                            val statusColor = if (conversation.isOtherUserOnline) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.bodySmall,
                                color = statusColor,
                                maxLines = 1
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = "Chat",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        actions = {
            if (conversation != null) {
                if (conversation.isGroup) {
                    // Group info action
                    conversation.groupId?.let { groupId ->
                        IconButton(onClick = { onNavigateToGroupInfo(groupId) }) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Group info",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        // Group map action
                        IconButton(onClick = { onNavigateToGroupMap(groupId) }) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = "Group map",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                } else {
                    // 1:1 conversation: profile info action
                    conversation.otherUserId?.let { userId ->
                        IconButton(onClick = { onNavigateToUserProfile(userId) }) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "User profile",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}

/**
 * 40dp circular avatar for the chat header.
 * Shows the conversation photo if available, otherwise shows a letter placeholder.
 */
@Composable
private fun ChatHeaderAvatar(
    photoUrl: String?,
    title: String,
    modifier: Modifier = Modifier
) {
    val avatarSize = 40.dp

    if (!photoUrl.isNullOrEmpty()) {
        AsyncImage(
            model = photoUrl,
            contentDescription = "$title avatar",
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(avatarSize)
                .clip(CircleShape)
        )
    } else {
        Box(
            modifier = modifier
                .size(avatarSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .semantics { contentDescription = "$title avatar" },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
