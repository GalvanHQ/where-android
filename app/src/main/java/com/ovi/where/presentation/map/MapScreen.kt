package com.ovi.where.presentation.map

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.imageLoader
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.ovi.where.R
import com.ovi.where.core.theme.AvatarColors
import com.ovi.where.core.theme.Dimens
import com.ovi.where.core.utils.showToast
import com.ovi.where.presentation.common.WhereTopAppBar
import com.ovi.where.presentation.model.MemberLocationUiModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    groupId: String,
    onNavigateBack: () -> Unit,
    viewModel: MapViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showDurationDialog by remember { mutableStateOf(false) }
    var selectedDuration by remember { mutableFloatStateOf(60f) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(0.0, 0.0), 2.0f)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) viewModel.locateMe()
        else context.showToast(context.getString(R.string.toast_location_denied))
    }

    LaunchedEffect(groupId) { viewModel.observeLocations(groupId) }

    LaunchedEffect(uiState.hasMyLocation, uiState.myLatitude, uiState.myLongitude) {
        if (uiState.hasMyLocation && uiState.myLatitude != 0.0 && uiState.myLongitude != 0.0) {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(uiState.myLatitude, uiState.myLongitude), 14f
                ),
                durationMs = 800
            )
        }
    }

    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is com.ovi.where.core.common.UiEvent.ShowSnackbar ->
                    snackbarHostState.showSnackbar(event.message.asString(context))

                else -> Unit
            }
        }
    }

    if (showDurationDialog) {
        DurationPickerDialog(
            currentDuration = selectedDuration,
            onDurationChange = { selectedDuration = it },
            onDismiss = { showDurationDialog = false },
            onConfirm = {
                showDurationDialog = false
                viewModel.onStartSharing(groupId, selectedDuration.toLong())
            }
        )
    }

    Scaffold(
        topBar = {
            WhereTopAppBar(
                title = stringResource(R.string.title_group_map),
                onNavigateBack = onNavigateBack
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                SmallFloatingActionButton(
                    onClick = {
                        if (uiState.locations.isNotEmpty()) {
                            val valid = uiState.locations.filter { it.hasValidLocation }
                            if (valid.size == 1) {
                                scope.launch {
                                    cameraPositionState.animate(
                                        CameraUpdateFactory.newLatLngZoom(
                                            LatLng(valid[0].latitude, valid[0].longitude), 14f
                                        ),
                                        durationMs = 600
                                    )
                                }
                            } else if (valid.size > 1) {
                                scope.launch {
                                    cameraPositionState.animate(
                                        CameraUpdateFactory.newLatLngZoom(
                                            LatLng(
                                                valid.map { it.latitude }.average(),
                                                valid.map { it.longitude }.average()
                                            ), 11f
                                        ),
                                        durationMs = 600
                                    )
                                }
                            }
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(
                        Icons.Default.CenterFocusStrong,
                        contentDescription = stringResource(R.string.cd_fit_all)
                    )
                }
                Spacer(Modifier.height(Dimens.spaceSmall))
                SmallFloatingActionButton(
                    onClick = {
                        val hasPermission = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasPermission) viewModel.locateMe()
                        else permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(
                        Icons.Default.MyLocation,
                        contentDescription = stringResource(R.string.cd_my_location)
                    )
                }
                Spacer(Modifier.height(Dimens.spaceMedium))
                if (uiState.isSharing) {
                    FloatingActionButton(
                        onClick = { viewModel.onStopSharing(groupId) },
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
                        onClick = { showDurationDialog = true },
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
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {

            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(
                    isMyLocationEnabled = false
                ),
                uiSettings = MapUiSettings(
                    myLocationButtonEnabled = false,
                    zoomControlsEnabled = false
                )
            ) {
                // Member markers — geo-anchored via MarkerComposable
                val validLocations = uiState.locations.filter { it.hasValidLocation }
                validLocations.forEach { location ->
                    // ⚡ Bolt: Optimize map marker state updates
                    // Reusing the same MarkerState instance by only using userId as remember key.
                    // Instead of recreating the marker on every location change (which causes flickering and GC churn),
                    // we update the position of the existing state.
                    val markerState =
                        remember(location.userId) {
                            MarkerState(position = LatLng(location.latitude, location.longitude))
                        }.apply {
                            position = LatLng(location.latitude, location.longitude)
                        }
                    val avatarColor =
                        AvatarColors[location.userId.hashCode().and(0x7FFFFFFF) % AvatarColors.size]
                    MarkerComposable(
                        state = markerState,
                        title = location.displayName,
                        snippet = location.timeAgo,
                        zIndex = 5f,
                        onClick = {
                            scope.launch {
                                cameraPositionState.animate(
                                    CameraUpdateFactory.newLatLngZoom(
                                        LatLng(location.latitude, location.longitude), 15f
                                    ),
                                    durationMs = 600
                                )
                            }
                            true
                        }
                    ) {
                        // Life360-style pin marker
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .shadow(6.dp, CircleShape)
                                    .clip(CircleShape)
                                    .background(avatarColor)
                                    .border(3.dp, MaterialTheme.colorScheme.surface, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                var bitmap by remember(location.photoUrl) {
                                    mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(
                                        null
                                    )
                                }
                                val context = LocalContext.current

                                LaunchedEffect(location.photoUrl) {
                                    if (!location.photoUrl.isNullOrEmpty()) {
                                        val request = coil.request.ImageRequest.Builder(context)
                                            .data(location.photoUrl)
                                            .size(coil.size.Size.ORIGINAL)
                                            .allowHardware(false)
                                            .build()
                                        val result = context.imageLoader.execute(request)
                                        if (result is coil.request.SuccessResult) {
                                            val drawable = result.drawable
                                            val b =
                                                (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                                                    ?: drawable.toBitmap()
                                            bitmap = b.asImageBitmap()
                                        }
                                    }
                                }

                                if (bitmap != null) {
                                    androidx.compose.foundation.Image(
                                        bitmap = bitmap!!,
                                        contentDescription = location.displayName,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Text(
                                        text = location.displayName.take(1).uppercase(),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        // Triangle tail
                        val tailColor = MaterialTheme.colorScheme.surface
                        Box(
                            modifier = Modifier
                                .width(14.dp)
                                .height(10.dp)
                                .drawBehind {
                                    val path = Path().apply {
                                        moveTo(0f, 0f)
                                        lineTo(size.width, 0f)
                                        lineTo(size.width / 2f, size.height)
                                        close()
                                    }
                                    drawPath(path, color = tailColor)
                                }
                        )
                    }
                }
            }

            // Sharing status banner
            if (uiState.isSharing) {
                Card(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(horizontal = Dimens.spaceLarge, vertical = Dimens.spaceMedium),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(
                            horizontal = Dimens.spaceLarge,
                            vertical = Dimens.spaceSmall
                        ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.LocationOn, null,
                            modifier = Modifier.size(Dimens.iconSizeSmall),
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(Modifier.width(Dimens.spaceSmall))
                        Text(
                            "Sharing location",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            uiState.error?.let { error ->
                Card(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(Dimens.spaceMedium),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(Dimens.spaceMedium),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            // Bottom member list
            if (uiState.locations.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(Dimens.spaceMedium),
                    elevation = CardDefaults.cardElevation(defaultElevation = Dimens.cardElevationHigh),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(Dimens.spaceMedium)) {
                        Text(
                            text = stringResource(R.string.title_members),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(Dimens.spaceSmall))
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(Dimens.memberListHeight),
                            verticalArrangement = Arrangement.spacedBy(Dimens.spaceSmall)
                        ) {
                            items(items = uiState.locations, key = { it.id }) { location ->
                                MemberLocationItem(
                                    location = location,
                                    onClick = {
                                        if (location.hasValidLocation) {
                                            scope.launch {
                                                cameraPositionState.animate(
                                                    CameraUpdateFactory.newLatLngZoom(
                                                        LatLng(
                                                            location.latitude,
                                                            location.longitude
                                                        ),
                                                        15f
                                                    ),
                                                    durationMs = 600
                                                )
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
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
                Text(
                    text = stringResource(R.string.msg_share_duration_instruction),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(Dimens.spaceMedium))
                Slider(
                    value = currentDuration,
                    onValueChange = onDurationChange,
                    valueRange = 15f..480f,
                    steps = 15
                )
                Text(
                    text = formatDuration(context, currentDuration.toLong()),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_start_sharing)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

private fun formatDuration(context: android.content.Context, minutes: Long): String {
    return when {
        minutes < 60 -> context.getString(R.string.duration_minutes, minutes)
        minutes == 60L -> context.getString(R.string.duration_one_hour)
        minutes % 60 == 0L -> context.getString(R.string.duration_hours, minutes / 60)
        else -> context.getString(R.string.duration_hours_minutes, minutes / 60, minutes % 60)
    }
}

@Composable
fun MemberLocationItem(
    location: MemberLocationUiModel,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = Dimens.spaceSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val color = AvatarColors[location.userId.hashCode().and(0x7FFFFFFF) % AvatarColors.size]
        if (!location.photoUrl.isNullOrEmpty()) {
            AsyncImage(
                model = location.photoUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(Dimens.avatarSizeSmall)
                    .clip(CircleShape)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(Dimens.avatarSizeSmall)
                    .clip(CircleShape)
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = location.displayName.take(1).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.surface
                )
            }
        }

        Spacer(Modifier.width(Dimens.spaceMedium))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = location.displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (location.hasValidLocation) location.timeAgo
                else stringResource(R.string.msg_location_not_available),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (location.isActive) {
            Icon(
                Icons.Default.LocationOn, null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(Dimens.iconSizeSmall)
            )
        }
    }
}
