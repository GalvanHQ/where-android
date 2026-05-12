package com.ovi.where.presentation.people.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ovi.where.core.theme.Dimens
import com.ovi.where.presentation.model.ProfileFriendshipAction

/**
 * Renders different button layouts based on the current [ProfileFriendshipAction] state.
 */
@Composable
fun ProfileActions(
    action: ProfileFriendshipAction,
    onSendRequest: () -> Unit,
    onCancelRequest: () -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onMessage: () -> Unit,
    onUnfriend: () -> Unit,
    onBlock: () -> Unit,
    onUnblock: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.spaceLarge)
    ) {
        when (action) {
            is ProfileFriendshipAction.AddFriend -> {
                Button(
                    onClick = onSendRequest,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("Add Friend")
                }
                Spacer(Modifier.height(Dimens.spaceMedium))
                TextButton(
                    onClick = onBlock,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Block",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            is ProfileFriendshipAction.RequestSent -> {
                OutlinedButton(
                    onClick = onCancelRequest,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("Cancel Request")
                }
                Spacer(Modifier.height(Dimens.spaceMedium))
                TextButton(
                    onClick = onBlock,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Block",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            is ProfileFriendshipAction.RequestReceived -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMedium)
                ) {
                    Button(
                        onClick = onAccept,
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text("Accept")
                    }
                    OutlinedButton(
                        onClick = onDecline,
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text("Decline")
                    }
                }
                Spacer(Modifier.height(Dimens.spaceMedium))
                TextButton(
                    onClick = onBlock,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Block",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            is ProfileFriendshipAction.AlreadyFriends -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMedium)
                ) {
                    FilledTonalButton(
                        onClick = onMessage,
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text("Message")
                    }
                    OutlinedButton(
                        onClick = onUnfriend,
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Unfriend")
                    }
                }
                Spacer(Modifier.height(Dimens.spaceMedium))
                TextButton(
                    onClick = onBlock,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Block",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            is ProfileFriendshipAction.Blocked -> {
                // Caller has blocked this user — show unblock option
                Button(
                    onClick = onUnblock,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Text("Unblock")
                }
            }

            is ProfileFriendshipAction.BlockedByThem -> {
                // This user has blocked the caller — no actions available
                Text(
                    text = "This user is not available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
