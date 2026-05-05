package com.ovi.where.presentation.people

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.ovi.where.core.theme.Dimens
import com.ovi.where.presentation.model.ProfileFriendshipAction
import com.ovi.where.presentation.common.WhereTopAppBar

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun UserProfileScreen(
    userId: String,
    onNavigateBack: () -> Unit = {},
    onNavigateToChat: (String) -> Unit = {},
    viewModel: UserProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(userId) { viewModel.loadUser(userId) }

    Scaffold(
        topBar = {
            WhereTopAppBar(
                title = uiState.profile?.displayName ?: "Profile",
                onNavigateBack = onNavigateBack
            )
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
        } else {
            val profile = uiState.profile ?: return@Scaffold

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(Dimens.spaceXLarge),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(Dimens.spaceLarge))

                // Avatar — uses pre-computed avatarInitial from OtherUserProfileUiModel
                if (!profile.photoUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = profile.photoUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(Dimens.avatarSizeXLarge).clip(CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(Dimens.avatarSizeXLarge)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text  = profile.avatarInitial,
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(Modifier.height(Dimens.spaceLarge))

                Text(text = profile.displayName, style = MaterialTheme.typography.headlineSmall)

                if (profile.username.isNotEmpty()) {
                    Text(
                        text  = "@${profile.username}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (profile.bio.isNotEmpty()) {
                    Spacer(Modifier.height(Dimens.spaceMedium))
                    Text(
                        text      = profile.bio,
                        style     = MaterialTheme.typography.bodyMedium,
                        color     = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(Modifier.height(Dimens.spaceXLarge))

                // Action buttons driven by sealed ProfileFriendshipAction — no raw domain enum
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMedium, Alignment.CenterHorizontally)
                ) {
                    when (profile.friendshipAction) {
                        is ProfileFriendshipAction.AlreadyFriends -> {
                            Button(
                                onClick = { /* TODO: open/create DM */ },
                                shape   = MaterialTheme.shapes.medium
                            ) { Text("Message") }
                            OutlinedButton(
                                onClick = { viewModel.removeFriend(userId) },
                                shape   = MaterialTheme.shapes.medium
                            ) { Text("Unfriend") }
                        }
                        is ProfileFriendshipAction.RequestSent -> {
                            FilledTonalButton(
                                onClick  = {},
                                enabled  = false,
                                shape    = MaterialTheme.shapes.medium
                            ) { Text("Request Sent") }
                        }
                        is ProfileFriendshipAction.RequestReceived -> {
                            Button(
                                onClick = { /* TODO: accept */ },
                                shape   = MaterialTheme.shapes.medium
                            ) { Text("Accept Request") }
                            OutlinedButton(
                                onClick = { /* TODO: decline */ },
                                shape   = MaterialTheme.shapes.medium
                            ) { Text("Decline") }
                        }
                        is ProfileFriendshipAction.AddFriend -> {
                            Button(
                                onClick = { viewModel.sendFriendRequest(userId) },
                                shape   = MaterialTheme.shapes.medium
                            ) { Text("Add Friend") }
                        }
                    }
                }
            }
        }
    }
}
