package com.ovi.where.presentation.people

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import com.ovi.where.core.theme.Dimens
import com.ovi.where.presentation.common.WhereTopAppBar
import com.ovi.where.presentation.model.FriendRequestUiModel
import com.ovi.where.presentation.people.components.LiveRingAvatar
import com.ovi.where.presentation.people.components.RequestsEmptyState
import com.ovi.where.presentation.people.components.RequestsSkeleton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendRequestsScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: FriendRequestsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }

    val tabs = listOf("Incoming", "Sent")

    Scaffold(
        topBar = {
            WhereTopAppBar(
                title = "Friend Requests",
                onNavigateBack = onNavigateBack
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Segmented tab control
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            // Content per tab
            when {
                uiState.isLoading -> {
                    RequestsSkeleton()
                }
                selectedTab == 0 -> {
                    // Incoming tab
                    if (uiState.requests.isEmpty()) {
                        RequestsEmptyState(tab = "incoming")
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(Dimens.spaceLarge),
                            verticalArrangement = Arrangement.spacedBy(Dimens.spaceMedium)
                        ) {
                            items(
                                items = uiState.requests,
                                key = { it.pairId }
                            ) { request ->
                                IncomingRequestRow(
                                    request = request,
                                    onAccept = { viewModel.acceptRequest(request.requesterId) },
                                    onDecline = { viewModel.declineRequest(request.requesterId) }
                                )
                            }
                        }
                    }
                }
                selectedTab == 1 -> {
                    // Sent tab
                    if (uiState.outgoingRequests.isEmpty()) {
                        RequestsEmptyState(tab = "outgoing")
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(Dimens.spaceLarge),
                            verticalArrangement = Arrangement.spacedBy(Dimens.spaceMedium)
                        ) {
                            items(
                                items = uiState.outgoingRequests,
                                key = { it.pairId }
                            ) { request ->
                                OutgoingRequestRow(
                                    request = request,
                                    onCancel = { viewModel.cancelRequest(request.requesterId) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IncomingRequestRow(
    request: FriendRequestUiModel,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.spaceSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LiveRingAvatar(
            photoUrl = request.photoUrl,
            displayName = request.displayName,
            isLive = false,
            size = Dimens.avatarSizeMedium
        )

        Spacer(Modifier.width(Dimens.spaceLarge))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = request.displayName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "@${request.username}" + formatTimestamp(request.sentAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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

@Composable
private fun OutgoingRequestRow(
    request: FriendRequestUiModel,
    onCancel: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.spaceSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LiveRingAvatar(
            photoUrl = request.photoUrl,
            displayName = request.displayName,
            isLive = false,
            size = Dimens.avatarSizeMedium
        )

        Spacer(Modifier.width(Dimens.spaceLarge))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = request.displayName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "@${request.username}" + formatTimestamp(request.sentAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.height(Dimens.buttonHeightSmall),
            shape = MaterialTheme.shapes.medium,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Dimens.spaceLarge)
        ) {
            Text("Cancel", style = MaterialTheme.typography.labelMedium)
        }
    }
}

private fun formatTimestamp(sentAt: Long): String {
    if (sentAt == 0L) return ""
    val relative = DateUtils.getRelativeTimeSpanString(
        sentAt,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE
    )
    return " · $relative"
}
