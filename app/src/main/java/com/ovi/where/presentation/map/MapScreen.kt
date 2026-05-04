package com.ovi.where.presentation.map

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.ovi.where.R
import com.ovi.where.core.theme.Dimens
import com.ovi.where.core.theme.LocationActive
import com.ovi.where.core.theme.Primary
import com.ovi.where.core.utils.showToast
import com.ovi.where.presentation.model.MemberLocationUiModel
import kotlinx.coroutines.launch
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Position
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan

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
    var selectedDuration by remember { mutableStateOf(60f) }

    val cameraState = rememberCameraState(
        firstPosition = CameraPosition(zoom = 2.0, target = Position(0.0, 0.0))
    )

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
            cameraState.position = CameraPosition(
                target = Position(uiState.myLongitude, uiState.myLatitude),
                zoom = 14.0
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
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.title_group_map),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                SmallFloatingActionButton(
                    onClick = {
                        if (uiState.locations.isNotEmpty()) {
                            val validLocations = uiState.locations.filter { it.hasValidLocation }
                            if (validLocations.size == 1) {
                                scope.launch {
                                    cameraState.position = CameraPosition(
                                        target = Position(validLocations[0].longitude, validLocations[0].latitude),
                                        zoom = 14.0
                                    )
                                }
                            } else if (validLocations.size > 1) {
                                val centerLat = validLocations.map { it.latitude }.average()
                                val centerLng = validLocations.map { it.longitude }.average()
                                scope.launch {
                                    cameraState.position = CameraPosition(
                                        target = Position(centerLng, centerLat),
                                        zoom = 11.0
                                    )
                                }
                            }
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Icon(Icons.Default.FitScreen, contentDescription = stringResource(R.string.cd_fit_all))
                }
                Spacer(Modifier.height(Dimens.spaceSmall))
                SmallFloatingActionButton(
                    onClick = {
                        val hasPermission = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasPermission) viewModel.locateMe()
                        else permissionLauncher.launch(arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ))
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = stringResource(R.string.cd_my_location))
                }
                Spacer(Modifier.height(Dimens.spaceMedium))
                if (uiState.isSharing) {
                    FloatingActionButton(
                        onClick = { viewModel.onStopSharing(groupId) },
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = stringResource(R.string.cd_stop_sharing),
                            tint = MaterialTheme.colorScheme.onErrorContainer)
                    }
                } else {
                    FloatingActionButton(
                        onClick = { showDurationDialog = true },
                        containerColor = Primary
                    ) {
                        Icon(Icons.Default.Share, contentDescription = stringResource(R.string.cd_share_location),
                            tint = Color.White)
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier.fillMaxSize().padding(paddingValues)
        ) {
            // Map
            MaplibreMap(
                modifier = Modifier.fillMaxSize(),
                baseStyle = BaseStyle.Uri("https://tiles.openfreemap.org/styles/liberty"),
                cameraState = cameraState
            )

            // Member markers overlay
            val validLocations = uiState.locations.filter { it.hasValidLocation }
            if (validLocations.isNotEmpty()) {
                MemberMarkersOverlay(
                    locations = validLocations,
                    cameraPosition = cameraState.position,
                    onMarkerClick = { location ->
                        scope.launch {
                            cameraState.position = CameraPosition(
                                target = Position(location.longitude, location.latitude),
                                zoom = 15.0
                            )
                        }
                    }
                )
            }

            // Sharing status banner
            if (uiState.isSharing) {
                Card(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(horizontal = Dimens.spaceLarge, vertical = Dimens.spaceMedium),
                    colors = CardDefaults.cardColors(
                        containerColor = LocationActive.copy(alpha = 0.9f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = Dimens.spaceMedium, vertical = Dimens.spaceSmall),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.LocationOn, null,
                            modifier = Modifier.size(14.dp), tint = Color.White)
                        Spacer(Modifier.width(Dimens.spaceSmall))
                        Text(
                            "Sharing location",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }
            }

            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            uiState.error?.let { error ->
                Card(
                    modifier = Modifier.align(Alignment.TopCenter).padding(Dimens.spaceMedium),
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
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
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
                            modifier = Modifier.fillMaxWidth().height(160.dp),
                            verticalArrangement = Arrangement.spacedBy(Dimens.spaceSmall)
                        ) {
                            items(items = uiState.locations, key = { it.id }) { location ->
                                MemberLocationItem(
                                    location = location,
                                    onClick = {
                                        if (location.hasValidLocation) {
                                            scope.launch {
                                                cameraState.position = CameraPosition(
                                                    target = Position(location.longitude, location.latitude),
                                                    zoom = 15.0
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

// Converts lat/lng to screen pixel position using Web Mercator projection
private fun latLngToPixel(
    lat: Double, lng: Double,
    centerLat: Double, centerLng: Double,
    zoom: Double, screenWidthPx: Float, screenHeightPx: Float
): Offset {
    fun mercatorX(lon: Double) = (lon + 180.0) / 360.0
    fun mercatorY(latDeg: Double): Double {
        val latRad = latDeg * PI / 180.0
        return (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0
    }
    val scale = 256.0 * 2.0.pow(zoom)
    val cx = mercatorX(centerLng) * scale
    val cy = mercatorY(centerLat) * scale
    val x = ((mercatorX(lng) * scale - cx) + screenWidthPx / 2).toFloat()
    val y = ((mercatorY(lat) * scale - cy) + screenHeightPx / 2).toFloat()
    return Offset(x, y)
}

// Distinct colors for member markers
private val markerColors = listOf(
    Color(0xFF1E88E5), Color(0xFF00ACC1), Color(0xFF7C4DFF),
    Color(0xFF43A047), Color(0xFFE53935), Color(0xFFFF9800),
    Color(0xFF8D6E63), Color(0xFF546E7A)
)

@Composable
private fun MemberMarkersOverlay(
    locations: List<MemberLocationUiModel>,
    cameraPosition: CameraPosition,
    onMarkerClick: (MemberLocationUiModel) -> Unit
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val zoom = cameraPosition.zoom
        val centerLat = cameraPosition.target.latitude
        val centerLng = cameraPosition.target.longitude
        val markerRadiusPx = with(density) { 20.dp.toPx() }

        locations.forEachIndexed { index, location ->
            val pos = latLngToPixel(
                location.latitude, location.longitude,
                centerLat, centerLng, zoom, widthPx, heightPx
            )
            val color = markerColors[index % markerColors.size]
            val initial = location.displayName.take(1).uppercase()

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onMarkerClick(location) }
            ) {
                // Shadow
                drawCircle(
                    color = Color.Black.copy(alpha = 0.2f),
                    radius = markerRadiusPx + 2f,
                    center = pos.copy(y = pos.y + 2f)
                )
                // Filled circle
                drawCircle(color = color, radius = markerRadiusPx, center = pos)
                // White border
                drawCircle(
                    color = Color.White, radius = markerRadiusPx,
                    center = pos, style = Stroke(width = 3f)
                )
                // Initial text
                val measured = textMeasurer.measure(
                    initial,
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                drawText(
                    measured,
                    topLeft = Offset(
                        pos.x - measured.size.width / 2f,
                        pos.y - measured.size.height / 2f
                    )
                )
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
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
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
        val color = markerColors[location.userId.hashCode().and(0x7FFFFFFF) % markerColors.size]
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
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
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
                tint = LocationActive,
                modifier = Modifier.size(Dimens.iconSizeSmall)
            )
        }
    }
}
