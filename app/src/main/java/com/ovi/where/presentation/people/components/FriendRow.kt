package com.ovi.where.presentation.people.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ovi.where.core.theme.Dimens
import com.ovi.where.presentation.model.FriendUiModel

/**
 * A row displaying a friend with avatar (StatusDot overlay), name, username,
 * message icon button, and long-press support.
 * Row height: 64dp. Merged semantics with Role.Button.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FriendRow(
    friend: FriendUiModel,
    onTap: () -> Unit,
    onMessage: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val onlineState = if (friend.isOnline) "Online" else "Offline"
    val description = "${friend.displayName}, $onlineState"

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .combinedClickable(
                onClick = onTap,
                onLongClick = onLongPress
            )
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = description
            }
            .padding(horizontal = Dimens.spaceLarge),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar with StatusDot overlay
        Box {
            LiveRingAvatar(
                photoUrl = friend.photoUrl,
                displayName = friend.displayName,
                isLive = friend.isOnline,
                size = Dimens.avatarSizeMedium
            )
            StatusDot(
                isOnline = friend.isOnline,
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }

        Spacer(Modifier.width(Dimens.spaceLarge))

        // Name and username
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = friend.displayName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "@${friend.username}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Message icon button (48dp touch target)
        IconButton(
            onClick = onMessage,
            modifier = Modifier.size(Dimens.buttonHeightSmall)
        ) {
            Icon(
                imageVector = Icons.Outlined.ChatBubbleOutline,
                contentDescription = "Message ${friend.displayName}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Dimens.iconSizeMedium)
            )
        }
    }
}
