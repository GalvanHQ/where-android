package com.ovi.where.presentation.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ovi.where.R
import com.ovi.where.core.theme.Dimens
import com.ovi.where.core.theme.LocationActive
import com.ovi.where.core.theme.LocationInactive
import com.ovi.where.core.theme.Primary
import com.ovi.where.presentation.common.ShimmerGroupList
import com.ovi.where.presentation.model.GroupUiModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToMap: (String) -> Unit,
    onNavigateToCreateGroup: () -> Unit,
    onNavigateToJoinGroup: () -> Unit = {},
    onNavigateToGroupDetails: (String) -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(),
    showMapHint: Boolean = false,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToCreateGroup,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_create_group))
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = Dimens.spaceLarge,
                            end = Dimens.spaceMedium,
                            top = Dimens.spaceLarge,
                            bottom = Dimens.spaceSmall
                        ),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.title_my_groups),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    IconButton(onClick = onNavigateToJoinGroup) {
                        Icon(
                            imageVector = Icons.Default.GroupAdd,
                            contentDescription = stringResource(R.string.cd_join_group),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                AnimatedVisibility(visible = showMapHint) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Dimens.spaceLarge, vertical = Dimens.spaceSmall),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(Dimens.spaceMedium),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Map, null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(Dimens.iconSizeMedium)
                            )
                            Spacer(Modifier.width(Dimens.spaceMedium))
                            Text(
                                text = "Tap a group below to open the map",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }

                when {
                    uiState.isLoading && uiState.groups.isEmpty() -> {
                        ShimmerGroupList()
                    }

                    uiState.error != null && uiState.groups.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.Group, null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                                )
                                Spacer(Modifier.height(Dimens.spaceMedium))
                                Text(
                                    text = stringResource(R.string.error_loading_groups),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(Modifier.height(Dimens.spaceMedium))
                                TextButton(onClick = viewModel::loadGroups) {
                                    Text(stringResource(R.string.action_retry))
                                }
                            }
                        }
                    }

                    uiState.groups.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(Dimens.spaceXLarge)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(120.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Group, null,
                                        modifier = Modifier.size(60.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(Modifier.height(Dimens.spaceLarge))
                                Text(
                                    text = stringResource(R.string.msg_no_groups_title),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(Modifier.height(Dimens.spaceSmall))
                                Text(
                                    text = stringResource(R.string.msg_no_groups_subtitle),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = Dimens.spaceLarge,
                                end = Dimens.spaceLarge,
                                top = Dimens.spaceSmall,
                                bottom = Dimens.space3XLarge
                            ),
                            verticalArrangement = Arrangement.spacedBy(Dimens.spaceMedium)
                        ) {
                            items(items = uiState.groups, key = { it.id }) { group ->
                                GroupCard(
                                    group = group,
                                    onClick = { onNavigateToMap(group.id) },
                                    onDetailsClick = { onNavigateToGroupDetails(group.id) }
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
fun GroupCard(
    group: GroupUiModel,
    onClick: () -> Unit,
    onDetailsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.spaceLarge),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Group avatar circle
            Box(
                modifier = Modifier
                    .size(Dimens.avatarSizeMedium)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = group.name.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.width(Dimens.spaceMedium))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (group.description.isNotBlank()) {
                    Text(
                        text = group.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(Dimens.spaceSmall))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Group, null,
                        modifier = Modifier.size(Dimens.iconSizeSmall),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(Dimens.spaceXSmall))
                    Text(
                        text = group.memberCountText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.width(Dimens.spaceSmall))

            // Details button
            IconButton(onClick = onDetailsClick) {
                Icon(
                    Icons.Default.Group,
                    contentDescription = stringResource(R.string.title_group_details),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(Dimens.iconSizeMedium)
                )
            }
        }
    }
}
