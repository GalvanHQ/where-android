package com.ovi.where.presentation.people

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import com.ovi.where.core.theme.Dimens
import com.ovi.where.presentation.common.WhereTopAppBar
import com.ovi.where.presentation.model.FriendshipActionUiModel
import com.ovi.where.presentation.model.SearchUserUiModel
import com.ovi.where.presentation.people.components.ErrorInfoCard
import com.ovi.where.presentation.people.components.FriendshipActionPill
import com.ovi.where.presentation.people.components.LiveRingAvatar
import com.ovi.where.presentation.people.components.PeopleSearchBar
import com.ovi.where.presentation.people.components.SearchLoadingShimmer
import com.ovi.where.presentation.people.components.SearchNoResultsState
import com.ovi.where.presentation.people.components.SearchPreEmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchUsersScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToUserProfile: (String) -> Unit = {},
    onNavigateToChat: (String) -> Unit = {},
    viewModel: SearchUsersViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            WhereTopAppBar(
                title = "Find People",
                onNavigateBack = onNavigateBack
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search bar
            PeopleSearchBar(
                value = uiState.query,
                onValueChange = viewModel::onQueryChange,
                onClear = { viewModel.onQueryChange("") },
                onSearch = { viewModel.onSearchImmediate() },
                modifier = Modifier.padding(
                    horizontal = Dimens.spaceLarge,
                    vertical = Dimens.spaceMedium
                )
            )

            // Content states
            when {
                uiState.error != null -> {
                    ErrorInfoCard(
                        message = uiState.error ?: "Something went wrong",
                        onRetry = { viewModel.onQueryChange(uiState.query) },
                        modifier = Modifier.padding(horizontal = Dimens.spaceLarge)
                    )
                }
                uiState.query.length < 2 -> {
                    // Pre-search empty state
                    SearchPreEmptyState()
                }
                uiState.isSearching -> {
                    // Loading shimmer
                    SearchLoadingShimmer()
                }
                uiState.results.isEmpty() -> {
                    // No results
                    SearchNoResultsState(query = uiState.query)
                }
                else -> {
                    // Results list
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = Dimens.spaceLarge
                        ),
                        verticalArrangement = Arrangement.spacedBy(Dimens.spaceSmall)
                    ) {
                        items(
                            items = uiState.results,
                            key = { it.userId }
                        ) { user ->
                            SearchResultRow(
                                user = user,
                                onPillTap = {
                                    when (user.friendshipAction) {
                                        FriendshipActionUiModel.ADD -> {
                                            viewModel.sendFriendRequest(user.userId)
                                        }
                                        else -> { /* PENDING/FRIENDS: no-op or cancel flow */ }
                                    }
                                },
                                onRowTap = { onNavigateToUserProfile(user.userId) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    user: SearchUserUiModel,
    onPillTap: () -> Unit,
    onRowTap: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onRowTap)
            .padding(vertical = Dimens.spaceMedium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LiveRingAvatar(
            photoUrl = user.photoUrl,
            displayName = user.displayName,
            isLive = false,
            size = Dimens.avatarSizeMedium
        )

        Spacer(Modifier.width(Dimens.spaceLarge))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user.displayName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (user.username.isNotEmpty()) {
                Text(
                    text = "@${user.username}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        FriendshipActionPill(
            action = user.friendshipAction,
            onTap = onPillTap
        )
    }
}
