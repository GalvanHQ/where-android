package com.ovi.where.presentation.people.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ovi.where.core.theme.Dimens
import com.ovi.where.presentation.model.FriendUiModel
import com.ovi.where.presentation.model.OtherUserProfileUiModel

/**
 * Profile header: large avatar (96dp) with LiveRingAvatar, name, @username, bio.
 */
@Composable
fun ProfileHeader(
    profile: OtherUserProfileUiModel,
    isSharing: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Dimens.spaceLarge),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LiveRingAvatar(
            photoUrl = profile.photoUrl,
            displayName = profile.displayName,
            isLive = isSharing,
            size = Dimens.avatarSizeXLarge
        )

        Spacer(Modifier.height(Dimens.spaceLarge))

        Text(
            text = profile.displayName,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(Dimens.spaceSmall))

        Text(
            text = "@${profile.username}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        if (profile.bio.isNotBlank()) {
            Spacer(Modifier.height(Dimens.spaceMedium))
            Text(
                text = profile.bio,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Stats row showing mutual friends count and sharing status.
 */
@Composable
fun ProfileStats(
    mutualCount: Int,
    isSharing: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.spaceLarge, vertical = Dimens.spaceMedium),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.People,
            contentDescription = null,
            modifier = Modifier.size(Dimens.iconSizeSmall),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(Dimens.spaceSmall))
        Text(
            text = "$mutualCount mutual",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.width(Dimens.spaceLarge))

        Icon(
            imageVector = Icons.Default.LocationOn,
            contentDescription = null,
            modifier = Modifier.size(Dimens.iconSizeSmall),
            tint = if (isSharing) MaterialTheme.colorScheme.tertiary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(Dimens.spaceSmall))
        Text(
            text = if (isSharing) "Sharing" else "Not sharing",
            style = MaterialTheme.typography.labelMedium,
            color = if (isSharing) MaterialTheme.colorScheme.tertiary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Overlapping avatar stack showing mutual friends.
 */
@Composable
fun MutualFriendsSection(
    friends: List<FriendUiModel>,
    modifier: Modifier = Modifier
) {
    if (friends.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.spaceLarge)
    ) {
        Text(
            text = "Mutual Friends",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Dimens.spaceMedium))

        // Avatar stack (overlapping)
        Row {
            val displayCount = minOf(friends.size, 5)
            for (i in 0 until displayCount) {
                Box(
                    modifier = Modifier.offset(x = (-8 * i).dp)
                ) {
                    LiveRingAvatar(
                        photoUrl = friends[i].photoUrl,
                        displayName = friends[i].displayName,
                        isLive = false,
                        size = Dimens.avatarSizeSmall
                    )
                }
            }
            if (friends.size > 5) {
                Spacer(Modifier.width(Dimens.spaceSmall))
                Text(
                    text = "+${friends.size - 5}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }
        }
    }
}
