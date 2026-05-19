package com.ovi.where.presentation.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest

/**
 * Data class holding the minimal user info needed for the avatar-tap bottom sheet.
 */
data class SheetUserDetails(
    val userId: String,
    val displayName: String,
    val photoUrl: String?,
    val username: String? = null,
    val isOnline: Boolean = false,
    /**
     * Friendship state between the current user and this user:
     *   - ACCEPTED → friends (show "Message")
     *   - PENDING_INCOMING → they sent you a request (show "Accept")
     *   - PENDING_OUTGOING → you already sent them a request (show "Requested")
     *   - NONE → no relationship (show "Add Friend")
     */
    val friendshipStatus: FriendshipAction = FriendshipAction.NONE
)

enum class FriendshipAction {
    ACCEPTED,
    PENDING_INCOMING,
    PENDING_OUTGOING,
    NONE
}

/**
 * Messenger-style bottom sheet shown when tapping a sender's avatar in a group chat.
 *
 * Shows:
 *   - Large avatar (80dp)
 *   - Display name (bold)
 *   - Username / subtitle (if available)
 *   - Online status dot
 *   - Action buttons: "Message" and "View Profile"
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserDetailsSheet(
    user: SheetUserDetails,
    onDismiss: () -> Unit,
    onViewProfile: (String) -> Unit,
    onMessage: (String) -> Unit = {},
    onAddFriend: (String) -> Unit = {},
    onAcceptRequest: (String) -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(8.dp))

            // Avatar (80dp)
            Box(contentAlignment = Alignment.BottomEnd) {
                val context = LocalContext.current
                val density = LocalDensity.current

                val avatarPixelSize = remember(density) {
                    with(density) { 80.dp.roundToPx() }
                }

                val avatarRequest = remember(user.photoUrl, avatarPixelSize) {
                    if (user.photoUrl.isNullOrBlank()) {
                        null
                    } else {
                        ImageRequest.Builder(context)
                            .data(user.photoUrl)
                            .crossfade(true)
                            .size(avatarPixelSize)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .networkCachePolicy(CachePolicy.ENABLED)
                            .memoryCacheKey(user.photoUrl)
                            .diskCacheKey(user.photoUrl)
                            .build()
                    }
                }

                if (avatarRequest != null) {
                    AsyncImage(
                        model = avatarRequest,
                        contentDescription = "${user.displayName} avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = computeInitials(user.displayName),
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                // Online dot
                if (user.isOnline) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(Color_Online)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Display name
            Text(
                text = user.displayName.ifBlank { "Unknown" },
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            // Username / subtitle
            if (!user.username.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "@${user.username}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            // Online status text
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (user.isOnline) "Active now" else "",
                style = MaterialTheme.typography.labelSmall,
                color = Color_Online
            )

            Spacer(Modifier.height(20.dp))

            // Action buttons row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // View Profile — always shown
                ActionButton(
                    icon = { Icon(Icons.Rounded.Person, contentDescription = "View Profile") },
                    label = "Profile",
                    onClick = { onViewProfile(user.userId) }
                )

                // Second button depends on friendship status
                when (user.friendshipStatus) {
                    FriendshipAction.ACCEPTED -> {
                        ActionButton(
                            icon = { Icon(Icons.AutoMirrored.Rounded.Chat, contentDescription = "Message") },
                            label = "Message",
                            onClick = { onMessage(user.userId) }
                        )
                    }
                    FriendshipAction.PENDING_INCOMING -> {
                        ActionButton(
                            icon = { Icon(Icons.Rounded.PersonAdd, contentDescription = "Accept") },
                            label = "Accept",
                            onClick = { onAcceptRequest(user.userId) }
                        )
                    }
                    FriendshipAction.PENDING_OUTGOING -> {
                        ActionButton(
                            icon = { Icon(Icons.Rounded.Person, contentDescription = "Requested") },
                            label = "Requested",
                            onClick = { } // No action — already sent
                        )
                    }
                    FriendshipAction.NONE -> {
                        ActionButton(
                            icon = { Icon(Icons.Rounded.PersonAdd, contentDescription = "Add Friend") },
                            label = "Add Friend",
                            onClick = { onAddFriend(user.userId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionButton(
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(48.dp),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            icon()
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private val Color_Online = androidx.compose.ui.graphics.Color(0xFF44B700)
