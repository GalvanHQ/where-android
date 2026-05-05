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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.ovi.where.core.theme.Dimens
import com.ovi.where.presentation.model.FriendshipActionUiModel
import com.ovi.where.presentation.model.SearchUserUiModel
import com.ovi.where.presentation.common.WhereTopAppBar

@androidx.compose.material3.ExperimentalMaterial3Api
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
            // Search field
            OutlinedTextField(
                value = uiState.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.spaceLarge, vertical = Dimens.spaceMedium),
                placeholder = { Text("Search by name or @username") },
                shape = MaterialTheme.shapes.extraLarge,
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )

            if (uiState.results.isEmpty() && uiState.query.length >= 2 && !uiState.isSearching) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No users found for '${uiState.query}'",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Dimens.spaceLarge),
                    verticalArrangement = Arrangement.spacedBy(Dimens.spaceSmall)
                ) {
                    items(items = uiState.results, key = { it.userId }) { user ->
                        SearchUserRow(
                            user        = user,
                            onAddFriend = { viewModel.sendFriendRequest(user.userId) },
                            onTap = { onNavigateToUserProfile(user.userId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchUserRow(
    user: SearchUserUiModel,
    onAddFriend: () -> Unit,
    onTap: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.spaceMedium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!user.photoUrl.isNullOrEmpty()) {
            AsyncImage(
                model = user.photoUrl,
                contentDescription = null,
                modifier = Modifier.size(Dimens.avatarSizeMedium).clip(CircleShape)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(Dimens.avatarSizeMedium)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text  = user.avatarInitial,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(Modifier.width(Dimens.spaceLarge))

        Column(modifier = Modifier.weight(1f)) {
            Text(text = user.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (user.username.isNotEmpty()) {
                Text(text = "@${user.username}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Action button driven by pre-computed FriendshipActionUiModel — no domain enum in composable
        when (user.friendshipAction) {
            FriendshipActionUiModel.FRIENDS -> {
                FilledTonalButton(onClick = {}, shape = MaterialTheme.shapes.medium, enabled = false) {
                    Text("Friends", style = MaterialTheme.typography.labelSmall)
                }
            }
            FriendshipActionUiModel.PENDING -> {
                FilledTonalButton(onClick = {}, shape = MaterialTheme.shapes.medium, enabled = false) {
                    Icon(Icons.Default.Schedule, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Pending", style = MaterialTheme.typography.labelSmall)
                }
            }
            FriendshipActionUiModel.ADD -> {
                Button(onClick = onAddFriend, shape = MaterialTheme.shapes.medium) {
                    Icon(Icons.Default.PersonAdd, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
