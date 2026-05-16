package com.ovi.where.presentation.chat

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
 *   or "Sharing location" if friend is sharing (Requirement 4.5)
 * - Group conversation: 40dp avatar, group name, member count + sharing count text
 *
 * Hides presence status if the other user is not in the caller's friends list (Requirement 8.4).
 * Hides sharing status for non-friends in 1:1 (Requirement 4.6).
 *
 * Shows animated location pulse icon when group has active sharing (Requirement 4.1).
 *
 * Admin users see an overflow menu with "Mute Member", "Group Settings", "Invite Link" (Requirement 15.1).
 * Non-admin users: overflow menu is hidden (Requirement 15.7).
 *
 * Navigation actions: back, profile/group info, group map.
 *
 * Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 15.1, 15.2, 15.4, 15.5, 15.7, 16.5, 16.6, 8.3, 8.4
 */
@OptIn(ExperimentalMaterial3Api::class)
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

                        // Subtitle: sharing/online status for 1:1, member count + sharing for groups
                        if (conversation.isGroup) {
                            // Requirement 4.3: "{memberCount} members · {sharingCount} sharing location"
                            // Requirement 4.4: "{memberCount} members" when no sharing
                            // Requirement 16.1: "{totalCount} members · {onlineCount} online"
                            // Requirement 16.3: Only "{totalCount} members" when no one online
                            val subtitle = when {
                                activeLocationSharingCount > 0 -> {
                                    "${conversation.memberCount} members · $activeLocationSharingCount sharing location"
                                }
                                onlineMemberCount > 0 -> {
                                    "${conversation.memberCount} members · $onlineMemberCount online"
                                }
                                else -> {
                                    "${conversation.memberCount} members"
                                }
                            }
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        } else if (isOtherUserFriend) {
                            // Requirement 4.5: 1:1 with friend sharing → "Sharing location" in tertiary
                            // Requirement 4.6: 1:1 with non-friend sharing → hide sharing status entirely
                            if (isOtherUserSharingLocation) {
                                Text(
                                    text = "Sharing location",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    maxLines = 1
                                )
                            } else {
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
                        // Requirement 4.6: If not a friend, hide sharing status entirely (no else branch)
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
                // Requirement 13.1: Search icon in ChatScreen header opens search bar
                IconButton(onClick = onSearchTap) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search messages",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Requirement 25.1: Media gallery action accessible from header (both 1:1 and group)
                IconButton(onClick = { onNavigateToMediaGallery(conversation.id) }) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = "Media gallery",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

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

                        // Requirement 4.1: Animated location pulse icon when active sharing exists
                        if (activeLocationSharingCount > 0) {
                            LocationPulseIcon()
                        }

                        // Group map action
                        // Requirement 4.2: On tap, navigate to Screen.GroupMap, viewport fits all active sharers
                        IconButton(onClick = { onNavigateToGroupMap(groupId) }) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = "Group map",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Requirement 15.1: Admin overflow menu with 3 actions
                        // Requirement 15.7: Non-admin users hide overflow menu
                        if (isCurrentUserAdmin) {
                            Box {
                                IconButton(onClick = onAdminOverflowToggle) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "Admin actions",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                DropdownMenu(
                                    expanded = showAdminOverflowMenu,
                                    onDismissRequest = onAdminOverflowDismiss
                                ) {
                                    // Requirement 15.2: Mute Member action
                                    DropdownMenuItem(
                                        text = { Text("Mute Member") },
                                        onClick = onMuteMemberTap
                                    )
                                    // Requirement 15.4: Group Settings action
                                    DropdownMenuItem(
                                        text = { Text("Group Settings") },
                                        onClick = {
                                            onAdminOverflowDismiss()
                                            onNavigateToEditGroup(groupId)
                                        }
                                    )
                                    // Requirement 15.5: Invite Link action
                                    DropdownMenuItem(
                                        text = { Text("Invite Link") },
                                        onClick = onInviteLinkTap
                                    )
                                }
                            }
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
 * Animated location pulse icon displayed in the chat header when a group has active location sharing.
 *
 * The pulse animation cycles opacity between 0.3 and 1.0 over a 1.5-second repeating interval.
 *
 * Requirement 4.1: Animated location pulse icon (opacity 0.3-1.0, 1.5s interval) next to map button.
 */
@Composable
private fun LocationPulseIcon() {
    val infiniteTransition = rememberInfiniteTransition(label = "locationPulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "locationPulseAlpha"
    )

    Icon(
        imageVector = Icons.Default.LocationOn,
        contentDescription = "Active location sharing",
        tint = MaterialTheme.colorScheme.tertiary,
        modifier = Modifier
            .size(20.dp)
            .alpha(alpha)
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
