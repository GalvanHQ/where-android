package com.ovi.where.presentation.map.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.ComposeMapColorScheme
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.ovi.where.core.theme.Dimens
import com.ovi.where.presentation.map.components.MeetupPin
import kotlinx.coroutines.launch

/**
 * Full-screen in-app navigation view to the active meetup destination.
 *
 * Replaces the previous Google Maps Intent handoff. Renders:
 *  • A `GoogleMap` with the route polyline drawn from the user's
 *    location to the meetup pin.
 *  • A top status bar with the destination name, live distance + ETA.
 *  • A recenter FAB that fits the camera around origin + destination.
 *  • A close button that pops back to the map screen.
 *
 * The camera auto-fits to show both endpoints on the first successful
 * route fetch, then stays put — the user can pan/zoom freely. The
 * recenter FAB always re-fits to the current bounds.
 */
@Composable
fun MeetupNavigationScreen(
    onNavigateBack: () -> Unit,
    viewModel: MeetupNavigationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(0.0, 0.0), 2f)
    }

    // ── First-fix camera placement ────────────────────────────────────────
    // Once we have both endpoints, fit the camera to show them together.
    // We only do this once (controlled by `hasFitted` below) so the user's
    // pan/zoom isn't overridden on every state update.
    val origin = uiState.origin
    val destination = uiState.destination
    var hasFittedOnce by remember { mutableStateOf(false) }
    LaunchedEffect(origin, destination) {
        if (!hasFittedOnce && origin != null && destination != null && destination.hasValidLocation) {
            val destLatLng = LatLng(destination.latitude, destination.longitude)
            fitCamera(origin, destLatLng, scope, cameraPositionState)
            hasFittedOnce = true
        }
    }

    // ── Auto-pop when destination is cleared upstream ─────────────────────
    LaunchedEffect(destination) {
        // After the first observation has resolved, a null destination
        // means the meetup was cleared while we were driving. Pop back.
        if (!uiState.isLoading && destination == null) {
            onNavigateBack()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // ── Map ───────────────────────────────────────────────────────────
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            mapColorScheme = ComposeMapColorScheme.FOLLOW_SYSTEM,
            properties = MapProperties(
                mapType = MapType.NORMAL,
                isMyLocationEnabled = false
            ),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                myLocationButtonEnabled = false,
                mapToolbarEnabled = false,
                compassEnabled = false
            )
        ) {
            // Route polyline
            uiState.route?.points?.takeIf { it.size >= 2 }?.let { points ->
                Polyline(
                    points = points,
                    color = MaterialTheme.colorScheme.primary,
                    width = 14f,
                    geodesic = false,
                    zIndex = 1f
                )
            }

            // Destination pin
            destination?.takeIf { it.hasValidLocation }?.let { dest ->
                val target = remember(dest.latitude, dest.longitude) {
                    LatLng(dest.latitude, dest.longitude)
                }
                val markerState = remember(target) { MarkerState(position = target) }
                MarkerComposable(
                    state = markerState,
                    title = dest.name.ifBlank { "Meetup point" },
                    zIndex = 4f
                ) {
                    MeetupPin(size = 50.dp)
                }
            }

            // User location dot (small primary-tinted circle so it reads as
            // "you" without depending on the system blue dot, which we
            // don't enable here — that needs a runtime permission flow).
            origin?.let { o ->
                val youState = remember(o) { MarkerState(position = o) }
                MarkerComposable(
                    state = youState,
                    title = "You",
                    zIndex = 3f
                ) {
                    YouDot()
                }
            }
        }

        // ── Top status card ───────────────────────────────────────────────
        TopStatusCard(
            destinationName = destination?.name?.ifBlank { "Meetup point" } ?: "Loading…",
            address = destination?.address?.takeIf { it.isNotBlank() },
            distanceLabel = uiState.distanceLabel,
            etaLabel = uiState.etaLabel,
            isLoading = uiState.isLoading && uiState.route == null,
            errorMessage = uiState.errorMessage,
            onClose = onNavigateBack,
            onRetry = { viewModel.refreshRoute() },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = Dimens.spaceMedium, vertical = Dimens.spaceSmall)
        )

        // ── Recenter FAB ──────────────────────────────────────────────────
        if (origin != null && destination != null && destination.hasValidLocation) {
            SmallFloatingActionButton(
                onClick = {
                    val destLatLng = LatLng(destination.latitude, destination.longitude)
                    fitCamera(origin, destLatLng, scope, cameraPositionState)
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = Dimens.spaceLarge, bottom = Dimens.spaceLarge),
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Rounded.MyLocation, contentDescription = "Recenter")
            }
        }
    }
}

/** Convenience: fit the camera around origin + destination with padding. */
private fun fitCamera(
    origin: LatLng,
    destination: LatLng,
    scope: kotlinx.coroutines.CoroutineScope,
    cameraPositionState: com.google.maps.android.compose.CameraPositionState
) {
    scope.launch {
        val bounds = LatLngBounds.builder()
            .include(origin)
            .include(destination)
            .build()
        cameraPositionState.animate(
            update = CameraUpdateFactory.newLatLngBounds(bounds, 180),
            durationMs = 600
        )
    }
}

@Composable
private fun TopStatusCard(
    destinationName: String,
    address: String?,
    distanceLabel: String?,
    etaLabel: String?,
    isLoading: Boolean,
    errorMessage: String?,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(
                start = 8.dp,
                end = 8.dp,
                top = 8.dp,
                bottom = 12.dp
            )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "NAVIGATING TO",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = destinationName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!address.isNullOrBlank()) {
                        Text(
                            text = address,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                } else if (errorMessage != null) {
                    IconButton(onClick = onRetry) {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = "Retry",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // ── Metric row: distance + ETA ────────────────────────────
            if (distanceLabel != null || etaLabel != null || errorMessage != null) {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (distanceLabel != null) {
                        MetricChip(
                            icon = Icons.Rounded.NearMe,
                            value = distanceLabel,
                            label = "Distance",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (etaLabel != null) {
                        MetricChip(
                            icon = Icons.Rounded.Schedule,
                            value = etaLabel,
                            label = "ETA",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                if (errorMessage != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/** "You are here" dot — primary-tinted with a white ring for contrast on dark tiles. */
@Composable
private fun YouDot() {
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}
