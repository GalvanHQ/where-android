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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.ovi.where.core.theme.Dimens
import com.ovi.where.presentation.model.FriendRequestUiModel
import com.ovi.where.presentation.common.WhereTopAppBar

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun FriendRequestsScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: FriendRequestsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            WhereTopAppBar(
                title = "Friend Requests",
                onNavigateBack = onNavigateBack
            )
        }
    ) { paddingValues ->
        if (uiState.requests.isEmpty() && !uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No pending requests",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(Dimens.spaceLarge),
                verticalArrangement = Arrangement.spacedBy(Dimens.spaceMedium)
            ) {
                items(items = uiState.requests, key = { it.friendshipId }) { request ->
                    FriendRequestRow(
                        request   = request,
                        onAccept  = { viewModel.acceptRequest(request.friendshipId) },
                        onDecline = { viewModel.declineRequest(request.friendshipId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FriendRequestRow(
    request: FriendRequestUiModel,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.spaceSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!request.photoUrl.isNullOrEmpty()) {
            AsyncImage(
                model = request.photoUrl,
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
                    text  = request.avatarInitial,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(Modifier.width(Dimens.spaceLarge))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text     = request.displayName,
                style    = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (request.username.isNotEmpty()) {
                Text(
                    text  = "@${request.username}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Button(
            onClick = onAccept,
            modifier = Modifier.height(Dimens.buttonHeightSmall),
            shape = MaterialTheme.shapes.medium,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Dimens.spaceLarge)
        ) {
            Text("Accept", style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.width(Dimens.spaceMedium))
        FilledTonalButton(
            onClick = onDecline,
            modifier = Modifier.height(Dimens.buttonHeightSmall),
            shape = MaterialTheme.shapes.medium,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Dimens.spaceMedium)
        ) {
            Text("Decline", style = MaterialTheme.typography.labelMedium)
        }
    }
}
