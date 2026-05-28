package com.ovi.where.presentation.people

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.ovi.where.core.theme.Dimens

/**
 * Blocked Users screen — Manage every account the local user has blocked.
 *
 * Reachable from the People tab via [com.ovi.where.presentation.people.components.BlockedInboxCard].
 * Each row exposes an inline "Unblock" affordance — tap removes the
 * block (and its symmetric `friendships/{pairId}` doc) through
 * [BlockedUsersViewModel.unblock]. State flows back into Conversation
 * Info / chat header so the "Block" affordance flips to "Unblock"
 * everywhere within one snapshot.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedUsersScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: BlockedUsersViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.snackbarEvent.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Blocked",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            uiState.blockedUsers.isEmpty() -> {
                BlockedEmptyState(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(
                        top = Dimens.spaceMedium,
                        bottom = Dimens.spaceLarge
                    ),
                    verticalArrangement = Arrangement.spacedBy(Dimens.spaceSmall)
                ) {
                    item(key = "info") {
                        InfoCaption(modifier = Modifier.padding(horizontal = Dimens.spaceLarge))
                        Spacer(Modifier.height(Dimens.spaceSmall))
                    }
                    items(uiState.blockedUsers, key = { it.userId }) { row ->
                        BlockedUserRow(
                            row = row,
                            onUnblock = { viewModel.unblock(row.userId) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Helper text under the toolbar explaining what blocking means. Mirrors
 * Messenger / WhatsApp's privacy-first language so users understand the
 * effect of unblocking.
 */
@Composable
private fun InfoCaption(modifier: Modifier = Modifier) {
    Text(
        text = "Blocked people can't message you, see your shared updates, or invite you to groups. They aren't told they're blocked.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

@Composable
private fun BlockedEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = Dimens.spaceLarge),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Block,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(Dimens.spaceLarge))
        Text(
            text = "Nobody's blocked",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(Dimens.spaceSmall))
        Text(
            text = "Anyone you block from a chat or profile will show up here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun BlockedUserRow(
    row: BlockedUserUiModel,
    onUnblock: () -> Unit
) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.spaceLarge),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar — falls back to a Block-icon chip when no photo is
            // resolved yet (warm-up still in flight or user has no photo).
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                if (!row.photoUrl.isNullOrBlank()) {
                    val request = remember(row.photoUrl) {
                        ImageRequest.Builder(context)
                            .data(row.photoUrl)
                            .size(176)
                            .crossfade(true)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .memoryCacheKey(row.photoUrl)
                            .diskCacheKey(row.photoUrl)
                            .build()
                    }
                    AsyncImage(
                        model = request,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Block,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (row.username.isNotBlank()) {
                    Text(
                        text = "@${row.username}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            OutlinedButton(
                onClick = onUnblock,
                shape = RoundedCornerShape(50)
            ) {
                Text(
                    text = "Unblock",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
