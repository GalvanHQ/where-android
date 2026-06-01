package com.ovi.where.presentation.people

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.RemoveCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.ovi.where.R
import com.ovi.where.core.constants.AppConstants.PULL_TO_REFRESH_TIMEOUT_MS
import com.ovi.where.core.theme.Dimens
import com.ovi.where.presentation.common.LIST_ITEM_ANIMATION_DURATION_MS
import com.ovi.where.presentation.common.search.SearchBarTapTarget
import com.ovi.where.presentation.model.FriendUiModel
import com.ovi.where.presentation.people.components.BlockedInboxCard
import com.ovi.where.presentation.people.components.ErrorInfoCard
import com.ovi.where.presentation.people.components.FriendRow
import com.ovi.where.presentation.people.components.FriendsSectionHeader
import com.ovi.where.presentation.people.components.LiveRingAvatar
import com.ovi.where.presentation.people.components.PeopleEmptyState
import com.ovi.where.presentation.people.components.PeopleSkeleton
import com.ovi.where.presentation.people.components.RequestsInboxCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleScreen(
    contentPadding: PaddingValues = PaddingValues(),
    onNavigateToUserProfile: (String) -> Unit = {},
    onNavigateToChat: (String) -> Unit = {},
    onNavigateToFriendRequests: () -> Unit = {},
    onNavigateToBlockedUsers: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    viewModel: PeopleViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val navigateToChat by viewModel.navigateToChat.collectAsState()

    // Handle navigation to chat
    LaunchedEffect(navigateToChat) {
        navigateToChat?.let { conversationId ->
            onNavigateToChat(conversationId)
            viewModel.onChatNavigated()
        }
    }

    // Bottom sheet state for long-press actions
    var selectedFriendForSheet by remember { mutableStateOf<FriendUiModel?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Block confirmation dialog state
    var showBlockDialog by remember { mutableStateOf(false) }
    var friendToBlock by remember { mutableStateOf<FriendUiModel?>(null) }

    // Pull-to-refresh state
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .statusBarsPadding()
    ) {
            // Search bar tap target — navigates to full-screen search
            SearchBarTapTarget(
                placeholderText = "Search friends...",
                onClick = onNavigateToSearch,
                modifier = Modifier.padding(horizontal = Dimens.spaceLarge)
            )

            Spacer(Modifier.height(Dimens.spaceMedium))

            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    isRefreshing = true
                    scope.launch {
                        viewModel.onRetry()
                        // 10s timeout for pull-to-refresh
                        delay(PULL_TO_REFRESH_TIMEOUT_MS)
                        isRefreshing = false
                    }
                },
                modifier = Modifier.fillMaxSize()
            ) {
                // Loading state
                if (uiState.isLoading && !isRefreshing) {
                    PeopleSkeleton()
                    return@PullToRefreshBox
                }

                // Stop refresh indicator when data arrives
                if (isRefreshing && !uiState.isLoading) {
                    LaunchedEffect(Unit) { isRefreshing = false }
                }

                // Error state
                if (uiState.error != null) {
                    ErrorInfoCard(
                        message = uiState.error ?: "Something went wrong",
                        onRetry = { viewModel.onRetry() },
                        modifier = Modifier.padding(
                            horizontal = Dimens.spaceLarge,
                            vertical = Dimens.spaceMedium
                        )
                    )
                }

                // Empty state: no friends and no pending requests
                if (!uiState.isLoading && uiState.friends.isEmpty() && uiState.pendingRequestCount == 0) {
                    PeopleEmptyState(onFindFriends = onNavigateToSearch)
                    return@PullToRefreshBox
                }

                // Content: LazyColumn with sections
                val activeFriends = uiState.friends.filter { it.isOnline }
                val allFriends = uiState.friends

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = Dimens.spaceLarge)
                ) {
                    // Requests inbox card
                    if (uiState.pendingRequestCount > 0) {
                        item(key = "requests_inbox") {
                            RequestsInboxCard(
                                count = uiState.pendingRequestCount,
                                onClick = onNavigateToFriendRequests,
                                modifier = Modifier
                                    .animateItem(
                                        fadeInSpec = tween(LIST_ITEM_ANIMATION_DURATION_MS),
                                        placementSpec = tween(LIST_ITEM_ANIMATION_DURATION_MS),
                                        fadeOutSpec = tween(LIST_ITEM_ANIMATION_DURATION_MS)
                                    )
                                    .padding(
                                        horizontal = Dimens.spaceLarge,
                                        vertical = Dimens.spaceMedium
                                    )
                            )
                        }
                    }

                    // Blocked users entry — only surfaced when the user
                    // has at least one block. Hidden by default to keep
                    // the People tab uncluttered for accounts who never
                    // use it. From inside Conversation Info or User
                    // Profile the affordance is always reachable.
                    if (uiState.blockedCount > 0) {
                        item(key = "blocked_inbox") {
                            BlockedInboxCard(
                                count = uiState.blockedCount,
                                onClick = onNavigateToBlockedUsers,
                                modifier = Modifier
                                    .animateItem(
                                        fadeInSpec = tween(LIST_ITEM_ANIMATION_DURATION_MS),
                                        placementSpec = tween(LIST_ITEM_ANIMATION_DURATION_MS),
                                        fadeOutSpec = tween(LIST_ITEM_ANIMATION_DURATION_MS)
                                    )
                                    .padding(
                                        horizontal = Dimens.spaceLarge,
                                        vertical = Dimens.spaceSmall
                                    )
                            )
                        }
                    }

                    // "Active now" section
                    if (activeFriends.isNotEmpty()) {
                        item(key = "active_header") {
                            FriendsSectionHeader(
                                title = "Active now",
                                count = activeFriends.size,
                                accentColor = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        items(
                            items = activeFriends,
                            key = { "active_${it.userId}" }
                        ) { friend ->
                            FriendRow(
                                friend = friend,
                                onTap = { onNavigateToUserProfile(friend.userId) },
                                onMessage = { viewModel.openOrCreateDm(friend.userId) },
                                onLongPress = { selectedFriendForSheet = friend },
                                modifier = Modifier.animateItem(
                                    fadeInSpec = tween(LIST_ITEM_ANIMATION_DURATION_MS),
                                    placementSpec = tween(LIST_ITEM_ANIMATION_DURATION_MS),
                                    fadeOutSpec = tween(LIST_ITEM_ANIMATION_DURATION_MS)
                                )
                            )
                        }
                    }

                    // "All friends" section
                    item(key = "all_friends_header") {
                        FriendsSectionHeader(
                            title = "All friends",
                            count = allFriends.size
                        )
                    }
                    items(
                        items = allFriends,
                        key = { it.userId }
                    ) { friend ->
                        FriendRow(
                            friend = friend,
                            onTap = { onNavigateToUserProfile(friend.userId) },
                            onMessage = { viewModel.openOrCreateDm(friend.userId) },
                            onLongPress = { selectedFriendForSheet = friend },
                            modifier = Modifier.animateItem(
                                fadeInSpec = tween(LIST_ITEM_ANIMATION_DURATION_MS),
                                placementSpec = tween(LIST_ITEM_ANIMATION_DURATION_MS),
                                fadeOutSpec = tween(LIST_ITEM_ANIMATION_DURATION_MS)
                            )
                        )
                    }

                    item(key = "bottom_spacer") {
                        Spacer(Modifier.height(Dimens.spaceLarge))
                    }
                }
        }
    }

    // Long-press bottom sheet with actions
    if (selectedFriendForSheet != null) {
        ModalBottomSheet(
            onDismissRequest = { selectedFriendForSheet = null },
            sheetState = sheetState
        ) {
            val friend = selectedFriendForSheet!!
            Column(
                modifier = Modifier.padding(
                    horizontal = Dimens.spaceLarge,
                    vertical = Dimens.spaceMedium
                )
            ) {
                // Header: Avatar + name + username
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Dimens.spaceMedium),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LiveRingAvatar(
                        photoUrl = friend.photoUrl,
                        displayName = friend.displayName,
                        isLive = friend.isOnline,
                        size = Dimens.avatarSizeMedium
                    )
                    Text(
                        text = friend.displayName,
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Spacer(Modifier.height(Dimens.spaceMedium))

                // Message action
                TextButton(
                    onClick = {
                        viewModel.openOrCreateDm(friend.userId)
                        selectedFriendForSheet = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = ImageVector.vectorResource(id = R.drawable.chat_filled),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(Dimens.iconSizeMedium)
                        )
                        Spacer(Modifier.width(Dimens.spaceLarge))
                        Text(
                            text = "Chat",
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Unfriend action
                TextButton(
                    onClick = {
                        viewModel.removeFriend(friend.userId)
                        selectedFriendForSheet = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Rounded.PersonRemove,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(Dimens.iconSizeMedium)
                        )
                        Spacer(Modifier.width(Dimens.spaceLarge))
                        Text(
                            text = "Unfriend",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                // Block action
                TextButton(
                    onClick = {
                        friendToBlock = friend
                        selectedFriendForSheet = null
                        showBlockDialog = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Rounded.RemoveCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(Dimens.iconSizeMedium)
                        )
                        Spacer(Modifier.width(Dimens.spaceLarge))
                        Text(
                            text = "Block",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                Spacer(Modifier.height(Dimens.spaceLarge))
            }
        }
    }

    // Block confirmation dialog
    if (showBlockDialog && friendToBlock != null) {
        val blockTarget = friendToBlock!!
        AlertDialog(
            onDismissRequest = {
                showBlockDialog = false
                friendToBlock = null
            },
            title = { Text("Block ${blockTarget.displayName}?") },
            text = {
                Text("Blocking will remove them from your friends and prevent them from contacting you.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.blockUser(blockTarget.userId)
                        showBlockDialog = false
                        friendToBlock = null
                    }
                ) {
                    Text(
                        "Block",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showBlockDialog = false
                        friendToBlock = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
