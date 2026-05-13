package com.ovi.where.presentation.people

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.ovi.where.core.theme.Dimens
import com.ovi.where.presentation.common.WhereTopAppBar
import com.ovi.where.presentation.people.components.ProfileActions
import com.ovi.where.presentation.people.components.ProfileErrorState
import com.ovi.where.presentation.people.components.ProfileHeader
import com.ovi.where.presentation.people.components.ProfileNotFoundState
import com.ovi.where.presentation.people.components.ProfileSkeleton
import com.ovi.where.presentation.people.components.ProfileStats

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    userId: String,
    onNavigateBack: () -> Unit = {},
    onNavigateToChat: (String) -> Unit = {},
    viewModel: UserProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val navigateToChat by viewModel.navigateToChat.collectAsState()
    var showBlockDialog by remember { mutableStateOf(false) }

    LaunchedEffect(userId) { viewModel.loadUser(userId) }

    // Handle navigation to chat
    LaunchedEffect(navigateToChat) {
        navigateToChat?.let { conversationId ->
            onNavigateToChat(conversationId)
            viewModel.onChatNavigated()
        }
    }

    val topBarTitle = uiState.profile?.let { "@${it.username}" } ?: ""

    Scaffold(
        topBar = {
            WhereTopAppBar(
                title = topBarTitle,
                onNavigateBack = onNavigateBack
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(top = Dimens.spaceLarge)
        ) {
            when {
                uiState.isLoading -> {
                    ProfileSkeleton(
                        modifier = Modifier.padding(top = Dimens.spaceXLarge)
                    )
                }
                uiState.error != null && uiState.profile == null -> {
                    ProfileErrorState(
                        message = uiState.error ?: "Something went wrong",
                        onRetry = { viewModel.loadUser(userId) }
                    )
                }
                uiState.notFound -> {
                    ProfileNotFoundState(onBack = onNavigateBack)
                }
                uiState.profile != null -> {
                    val profile = uiState.profile!!

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = Dimens.spaceLarge)
                    ) {
                        ProfileHeader(
                            profile = profile,
                            isSharing = false
                        )

                        ProfileStats(
                            mutualCount = 0,
                            isSharing = false
                        )

                        Spacer(Modifier.height(Dimens.spaceLarge))

                        ProfileActions(
                            action = profile.friendshipAction,
                            onSendRequest = { viewModel.sendFriendRequest(userId) },
                            onCancelRequest = { viewModel.removeFriend(userId) },
                            onAccept = { viewModel.acceptFriendRequest(userId) },
                            onDecline = { viewModel.declineFriendRequest(userId) },
                            onMessage = { viewModel.openOrCreateDm(userId) },
                            onUnfriend = { viewModel.removeFriend(userId) },
                            onBlock = { showBlockDialog = true }
                        )

                        Spacer(Modifier.height(Dimens.spaceXLarge))
                    }
                }
            }
        }
    }

    // Block confirmation dialog
    if (showBlockDialog) {
        AlertDialog(
            onDismissRequest = { showBlockDialog = false },
            title = { Text("Block user?") },
            text = {
                Text(
                    "Blocking this user will remove them from your friends and prevent them from contacting you. This action can be undone later."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.blockUser(userId)
                        showBlockDialog = false
                    }
                ) {
                    Text(
                        "Block",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
