package com.ovi.where.presentation.people

import android.text.format.DateUtils
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ovi.where.core.theme.Dimens
import com.ovi.where.core.constants.AppConstants.PULL_TO_REFRESH_TIMEOUT_MS
import com.ovi.where.presentation.common.LIST_ITEM_ANIMATION_DURATION_MS
import com.ovi.where.presentation.model.FriendRequestUiModel
import com.ovi.where.presentation.people.components.LiveRingAvatar
import com.ovi.where.presentation.people.components.RequestsEmptyState
import com.ovi.where.presentation.people.components.RequestsSkeleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Premium friend requests screen — single scrollable list with section headers.
 * No tabs, no segmented buttons. Clean and minimal.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendRequestsScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: FriendRequestsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Requests",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                scope.launch {
                    viewModel.refresh()
                    delay(PULL_TO_REFRESH_TIMEOUT_MS)
                    isRefreshing = false
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isRefreshing && !uiState.isLoading) {
                LaunchedEffect(Unit) { isRefreshing = false }
            }

            when {
                uiState.isLoading && !isRefreshing -> RequestsSkeleton()

                uiState.requests.isEmpty() && uiState.outgoingRequests.isEmpty() -> {
                    RequestsEmptyState(tab = "incoming")
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = Dimens.space3XLarge)
                    ) {
                        // ── Incoming section ─────────────────────────────
                        if (uiState.requests.isNotEmpty()) {
                            item(key = "incoming_header") {
                                SectionHeader(
                                    title = "Received",
                                    count = uiState.requests.size
                                )
                            }

                            items(
                                items = uiState.requests,
                                key = { "in_${it.pairId}" }
                            ) { request ->
                                IncomingRequestItem(
                                    request = request,
                                    onAccept = { viewModel.acceptRequest(request.requesterId) },
                                    onDecline = { viewModel.declineRequest(request.requesterId) },
                                    modifier = Modifier.animateItem(
                                        fadeInSpec = tween(LIST_ITEM_ANIMATION_DURATION_MS),
                                        placementSpec = tween(LIST_ITEM_ANIMATION_DURATION_MS),
                                        fadeOutSpec = tween(LIST_ITEM_ANIMATION_DURATION_MS)
                                    )
                                )
                            }
                        }

                        // ── Sent section ─────────────────────────────────
                        if (uiState.outgoingRequests.isNotEmpty()) {
                            item(key = "sent_header") {
                                SectionHeader(
                                    title = "Sent",
                                    count = uiState.outgoingRequests.size
                                )
                            }

                            items(
                                items = uiState.outgoingRequests,
                                key = { "out_${it.pairId}" }
                            ) { request ->
                                OutgoingRequestItem(
                                    request = request,
                                    onCancel = { viewModel.cancelRequest(request.requesterId) },
                                    modifier = Modifier.animateItem(
                                        fadeInSpec = tween(LIST_ITEM_ANIMATION_DURATION_MS),
                                        placementSpec = tween(LIST_ITEM_ANIMATION_DURATION_MS),
                                        fadeOutSpec = tween(LIST_ITEM_ANIMATION_DURATION_MS)
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Section Header ───────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = Dimens.spaceLarge,
                end = Dimens.spaceLarge,
                top = Dimens.spaceXLarge,
                bottom = Dimens.spaceMedium
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.width(Dimens.spaceMedium))
        Text(
            text = "$count",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── Incoming Request Item ────────────────────────────────────────────────────

@Composable
private fun IncomingRequestItem(
    request: FriendRequestUiModel,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.spaceLarge, vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        LiveRingAvatar(
            photoUrl = request.photoUrl,
            displayName = request.displayName,
            isLive = false,
            size = 52.dp
        )

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = request.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = formatTime(request.sentAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = "@${request.username}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onAccept,
                    modifier = Modifier.weight(1f).height(34.dp),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("Accept", style = MaterialTheme.typography.labelLarge)
                }
                OutlinedButton(
                    onClick = onDecline,
                    modifier = Modifier.weight(1f).height(34.dp),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text("Decline", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

// ── Outgoing Request Item ────────────────────────────────────────────────────

@Composable
private fun OutgoingRequestItem(
    request: FriendRequestUiModel,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.spaceLarge, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LiveRingAvatar(
            photoUrl = request.photoUrl,
            displayName = request.displayName,
            isLive = false,
            size = 52.dp
        )

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = request.displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "@${request.username} · ${formatTime(request.sentAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        OutlinedButton(
            onClick = onCancel,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.height(34.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Text("Withdraw", style = MaterialTheme.typography.labelMedium)
        }
    }
}

// ── Helper ───────────────────────────────────────────────────────────────────

private fun formatTime(sentAt: Long): String {
    if (sentAt == 0L) return ""
    return DateUtils.getRelativeTimeSpanString(
        sentAt, System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE
    ).toString()
}
