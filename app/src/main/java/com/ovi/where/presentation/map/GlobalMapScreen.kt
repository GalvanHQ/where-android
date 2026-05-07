package com.ovi.where.presentation.map

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.ovi.where.R
import com.ovi.where.core.theme.AvatarColors
import com.ovi.where.core.theme.Dimens
import com.ovi.where.core.utils.showToast
import kotlinx.coroutines.launch
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.rememberCameraPositionState



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalMapScreen(
    contentPadding: PaddingValues = PaddingValues(),
    onNavigateToChat: (String) -> Unit = {},
    onNavigateToUserProfile: (String) -> Unit = {},
    onNavigateToGroupMap: (String) -> Unit = {},
    viewModel: GlobalMapViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val navigateToChat by viewModel.navigateToChat.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Handle navigation to chat
    LaunchedEffect(navigateToChat) {
        navigateToChat?.let { conversationId ->
            onNavigateToChat(conversationId)
            viewModel.onChatNavigated()
        }
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(0.0, 0.0), 2.0f)
    }

    // ── Permission launcher ───────────────────────────────────────────────────
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) viewModel.locateMe()
        else context.showToast(context.getString(R.string.toast_location_denied))
    }

    // ── Camera follows own location on first fix ──────────────────────────────
    LaunchedEffect(uiState.hasMyLocation, uiState.myLatitude, uiState.myLongitude) {
        if (uiState.hasMyLocation && uiState.myLatitude != 0.0 && uiState.myLongitude != 0.0) {
            cameraPositionState.position = CameraPosition.fromLatLngZoom(
                LatLng(uiState.myLatitude, uiState.myLongitude), 13.0f
            )
        }
    }

    // ── Snackbar events ───────────────────────────────────────────────────────
    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is com.ovi.where.core.common.UiEvent.ShowSnackbar ->
                    snackbarHostState.showSnackbar(event.message.asString(context))
                else -> Unit
            }
        }
    }

    // ── Sharing duration state (local) ────────────────────────────────────────
    var sharingDuration by remember { mutableStateOf(60f) }
    var pendingShareGroupId by remember { mutableStateOf<String?>(null) }

    // Duration dialog
    if (pendingShareGroupId != null) {
        DurationPickerDialog(
            currentDuration = sharingDuration,
            onDurationChange = { sharingDuration = it },
            onDismiss = { pendingShareGroupId = null },
            onConfirm = {
                viewModel.startSharing(pendingShareGroupId!!, sharingDuration.toLong())
                pendingShareGroupId = null
            }
        )
    }

    // ── Bottom sheet state ────────────────────────────────────────────────────
    val friendSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val groupPickerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val shareSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { scaffoldPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
                .padding(contentPadding)
        ) {
            // ── Map ───────────────────────────────────────────────────────────
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState
            )

            // ── Friend avatar markers ─────────────────────────────────────────
            val validFriends = uiState.friendLocations.filter {
                it.latitude != 0.0 && it.longitude != 0.0
            }
            if (validFriends.isNotEmpty()) {
                // Capture theme colours before DrawScope
                val shadowColor  = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
                val borderColor  = MaterialTheme.colorScheme.surface
                val textColor    = MaterialTheme.colorScheme.onPrimary
                val pulseColor   = MaterialTheme.colorScheme.tertiary

                AvatarMarkersOverlay(
                    markers = validFriends.map { friend ->
                        MapMarker(
                            id = friend.userId,
                            userId = friend.userId,
                            label = friend.displayName.take(1).uppercase(),
                            latitude = friend.latitude,
                            longitude = friend.longitude,
                            photoUrl = friend.photoUrl,
                            isPulsing = friend.isActive
                        )
                    },
                    cameraPosition = cameraPositionState.position,
                    shadowColor = shadowColor,
                    borderColor = borderColor,
                    textColor = textColor,
                    pulseColor = pulseColor,
                    onMarkerClick = { marker ->
                        val friend = validFriends.firstOrNull { it.userId == marker.userId }
                        if (friend != null) viewModel.selectFriend(friend)
                    }
                )
            }

            // ── Group filter pill ─────────────────────────────────────────────
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = Dimens.spaceMedium)
                    .clickable { viewModel.showGroupPicker(true) },
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = Dimens.spaceLarge, vertical = Dimens.spaceMedium),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.LocationOn, null,
                        modifier = Modifier.size(Dimens.iconSizeSmall),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(Dimens.spaceSmall))
                    Text(
                        text = uiState.activeGroupFilter?.name ?: "All Friends",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (uiState.friendLocations.isNotEmpty()) {
                        Spacer(Modifier.width(Dimens.spaceSmall))
                        Badge(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) {
                            Text(
                                "${uiState.friendLocations.size}",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    Spacer(Modifier.width(Dimens.spaceXSmall))
                    Icon(
                        Icons.Default.ArrowDropDown, null,
                        modifier = Modifier.size(Dimens.iconSizeMedium),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── Sharing status banner ─────────────────────────────────────────
            if (uiState.isSharing) {
                Card(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 64.dp, start = Dimens.spaceLarge, end = Dimens.spaceLarge),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = Dimens.spaceLarge, vertical = Dimens.spaceSmall),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SharingPulseDot()
                        Spacer(Modifier.width(Dimens.spaceSmall))
                        Text(
                            text = "Sharing in ${uiState.groups.firstOrNull { it.id == uiState.sharingGroupId }?.name ?: "group"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

            // ── Loading indicator ─────────────────────────────────────────────
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            // ── FABs (right side) ─────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = Dimens.spaceLarge, bottom = if (uiState.friendLocations.isNotEmpty()) 200.dp else Dimens.spaceLarge),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(Dimens.spaceMedium)
            ) {
                // Fit all friends
                SmallFloatingActionButton(
                    onClick = {
                        val valid = uiState.friendLocations.filter { it.latitude != 0.0 }
                        if (valid.size == 1) {
                            scope.launch {
                                cameraPositionState.position = CameraPosition.fromLatLngZoom(
                                        LatLng(valid[0].latitude, valid[0].longitude), 14.0f
                                    )
                            }
                        } else if (valid.size > 1) {
                            scope.launch {
                                cameraPositionState.position = CameraPosition.fromLatLngZoom(
                                        LatLng(
                                            valid.map { it.latitude }.average(),
                                            valid.map { it.longitude }.average()
                                        ), 10.0f
                                    )
                            }
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Default.CenterFocusStrong, contentDescription = "Fit all")
                }

                // My location
                SmallFloatingActionButton(
                    onClick = {
                        val hasPermission = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasPermission) viewModel.locateMe()
                        else permissionLauncher.launch(
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = stringResource(R.string.cd_my_location))
                }

                // Share / Stop sharing
                if (uiState.isSharing) {
                    FloatingActionButton(
                        onClick = { viewModel.stopSharing() },
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.large
                    ) {
                        Icon(
                            Icons.Default.Stop,
                            contentDescription = stringResource(R.string.cd_stop_sharing),
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                } else {
                    FloatingActionButton(
                        onClick = {
                            when {
                                uiState.groups.isEmpty() -> {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Join a group first to share your location")
                                    }
                                }
                                uiState.groups.size == 1 -> {
                                    // Only one group — go straight to duration picker
                                    pendingShareGroupId = uiState.groups[0].id
                                }
                                else -> viewModel.showShareSheet(true)
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        shape = MaterialTheme.shapes.large
                    ) {
                        Icon(
                            Icons.Default.NearMe,
                            contentDescription = stringResource(R.string.cd_share_location),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }

            // ── Active sharers bottom strip ───────────────────────────────────
            AnimatedVisibility(
                visible = uiState.friendLocations.isNotEmpty(),
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Dimens.spaceMedium)
                        .navigationBarsPadding(),
                    shape = MaterialTheme.shapes.large,
                    elevation = CardDefaults.cardElevation(Dimens.cardElevationHigh),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(Dimens.spaceLarge)) {
                        Text(
                            text = "${uiState.friendLocations.size} friend${if (uiState.friendLocations.size != 1) "s" else ""} sharing",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(Dimens.spaceMedium))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMedium)
                        ) {
                            items(items = uiState.friendLocations, key = { it.userId }) { friend ->
                                FriendAvatarChip(
                                    friend = friend,
                                    onClick = {
                                        viewModel.selectFriend(friend)
                                        if (friend.latitude != 0.0) {
                                            scope.launch {
                                                cameraPositionState.position = CameraPosition.fromLatLngZoom(
                                                    LatLng(friend.latitude, friend.longitude), 14.0f
                                                )
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Friend detail bottom sheet ────────────────────────────────────────────
    if (uiState.showFriendSheet && uiState.selectedFriend != null) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.dismissFriendSheet() },
            sheetState = friendSheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            FriendDetailSheet(
                friend = uiState.selectedFriend!!,
                onMessage = { viewModel.openOrCreateDm(uiState.selectedFriend!!.userId); viewModel.dismissFriendSheet() },
                onNavigateToUserProfile = onNavigateToUserProfile,
                onNavigateToGroupMap = onNavigateToGroupMap,
                onDismiss = { viewModel.dismissFriendSheet() }
            )
        }
    }

    // ── Group filter bottom sheet ─────────────────────────────────────────────
    if (uiState.showGroupPicker) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.showGroupPicker(false) },
            sheetState = groupPickerSheetState
        ) {
            GroupFilterSheet(
                groups = uiState.groups,
                activeFilter = uiState.activeGroupFilter,
                onSelect = { filter ->
                    viewModel.setGroupFilter(filter)
                    viewModel.showGroupPicker(false)
                }
            )
        }
    }

    // ── Share group picker sheet ──────────────────────────────────────────────
    if (uiState.showShareSheet) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.showShareSheet(false) },
            sheetState = shareSheetState
        ) {
            ShareGroupPickerSheet(
                groups = uiState.groups,
                onSelect = { groupId ->
                    viewModel.showShareSheet(false)
                    pendingShareGroupId = groupId
                }
            )
        }
    }
}

// ── Sub-composables ───────────────────────────────────────────────────────────

@Composable
private fun SharingPulseDot() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f, targetValue = 1.2f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "pulse_scale"
    )
    Box(
        modifier = Modifier
            .size(8.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.tertiary)
    )
}

@Composable
private fun FriendAvatarChip(
    friend: FriendLocationUiModel,
    onClick: () -> Unit
) {
    val color = AvatarColors[friend.userId.hashCode().and(0x7FFFFFFF) % AvatarColors.size]
    Column(
        modifier = Modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            if (!friend.photoUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = friend.photoUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(Dimens.avatarSizeMedium).clip(CircleShape)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(Dimens.avatarSizeMedium)
                        .clip(CircleShape)
                        .background(color),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = friend.displayName.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.surface
                    )
                }
            }
            if (friend.isActive) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiary)
                )
            }
        }
        Spacer(Modifier.height(Dimens.spaceXSmall))
        Text(
            text = friend.displayName.substringBefore(" "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = friend.timeAgo,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun FriendDetailSheet(
    friend: FriendLocationUiModel,
    onMessage: () -> Unit,
    onNavigateToUserProfile: (String) -> Unit,
    onNavigateToGroupMap: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val color = AvatarColors[friend.userId.hashCode().and(0x7FFFFFFF) % AvatarColors.size]

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Dimens.spaceXLarge)
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Avatar
        if (!friend.photoUrl.isNullOrEmpty()) {
            AsyncImage(
                model = friend.photoUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(Dimens.avatarSizeXLarge).clip(CircleShape)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(Dimens.avatarSizeXLarge)
                    .clip(CircleShape)
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = friend.displayName.take(1).uppercase(),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.surface
                )
            }
        }

        Spacer(Modifier.height(Dimens.spaceLarge))

        Text(text = friend.displayName, style = MaterialTheme.typography.headlineSmall)
        if (friend.username.isNotEmpty()) {
            Text(
                text = "@${friend.username}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(Dimens.spaceMedium))

        // Location status
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (friend.isActive) {
                SharingPulseDot()
                Spacer(Modifier.width(Dimens.spaceSmall))
                Text(
                    text = "Sharing in ${friend.groupName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            } else {
                Icon(Icons.Default.LocationOff, null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(Dimens.spaceSmall))
                Text(
                    text = if (friend.timeAgo.isNotEmpty()) "Last seen ${friend.timeAgo}" else "Location not available",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(Dimens.spaceXLarge))

        // Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMedium)
        ) {
            // Message button
            Button(
                onClick = onMessage,
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Default.ChatBubbleOutline, null, modifier = Modifier.size(Dimens.iconSizeMedium))
                Spacer(Modifier.width(Dimens.spaceSmall))
                Text("Message")
            }

            // View profile
            OutlinedButton(
                onClick = { onNavigateToUserProfile(friend.userId); onDismiss() },
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Default.Person, null, modifier = Modifier.size(Dimens.iconSizeMedium))
                Spacer(Modifier.width(Dimens.spaceSmall))
                Text("Profile")
            }
        }

        // View on group map
        FilledTonalButton(
            onClick = { onNavigateToGroupMap(friend.groupId); onDismiss() },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium
        ) {
            Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(Dimens.iconSizeMedium))
            Spacer(Modifier.width(Dimens.spaceSmall))
            Text("Open ${friend.groupName} Map")
        }

        Spacer(Modifier.height(Dimens.spaceLarge))
    }
}

@Composable
private fun GroupFilterSheet(
    groups: List<GroupFilter>,
    activeFilter: GroupFilter?,
    onSelect: (GroupFilter?) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Dimens.spaceXLarge)
            .navigationBarsPadding()
    ) {
        Text("Show locations from", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Dimens.spaceLarge))

        // All friends option
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelect(null) }
                .padding(vertical = Dimens.spaceMedium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = activeFilter == null, onClick = { onSelect(null) })
            Spacer(Modifier.width(Dimens.spaceMedium))
            Text("All Friends", style = MaterialTheme.typography.bodyLarge)
        }

        groups.forEach { group ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(group) }
                    .padding(vertical = Dimens.spaceMedium),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = activeFilter?.id == group.id, onClick = { onSelect(group) })
                Spacer(Modifier.width(Dimens.spaceMedium))
                Text(group.name, style = MaterialTheme.typography.bodyLarge)
            }
        }

        Spacer(Modifier.height(Dimens.spaceLarge))
    }
}

@Composable
private fun ShareGroupPickerSheet(
    groups: List<GroupFilter>,
    onSelect: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Dimens.spaceXLarge)
            .navigationBarsPadding()
    ) {
        Text("Share location in", style = MaterialTheme.typography.titleMedium)
        Text(
            "Choose a group to share your live location with",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Dimens.spaceLarge))

        groups.forEach { group ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(group.id) }
                    .padding(vertical = Dimens.spaceSmall),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(Dimens.spaceLarge),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.LocationOn, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(Dimens.spaceLarge))
                    Text(group.name, style = MaterialTheme.typography.bodyLarge)
                }
            }
            Spacer(Modifier.height(Dimens.spaceSmall))
        }

        Spacer(Modifier.height(Dimens.spaceLarge))
    }
}

// Reuse DurationPickerDialog from MapScreen (same implementation, local copy)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DurationPickerDialog(
    currentDuration: Float,
    onDurationChange: (Float) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.title_share_duration)) },
        text = {
            Column {
                Text(stringResource(R.string.msg_share_duration_instruction),
                    style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(Dimens.spaceMedium))
                Slider(value = currentDuration, onValueChange = onDurationChange, valueRange = 15f..480f, steps = 15)
                Text(
                    text = formatDurationLocal(context, currentDuration.toLong()),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_start_sharing)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

private fun formatDurationLocal(context: android.content.Context, minutes: Long): String {
    return when {
        minutes < 60      -> context.getString(R.string.duration_minutes, minutes)
        minutes == 60L    -> context.getString(R.string.duration_one_hour)
        minutes % 60 == 0L -> context.getString(R.string.duration_hours, minutes / 60)
        else              -> context.getString(R.string.duration_hours_minutes, minutes / 60, minutes % 60)
    }
}
