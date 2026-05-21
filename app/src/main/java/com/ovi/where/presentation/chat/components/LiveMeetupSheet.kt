package com.ovi.where.presentation.chat.components

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.ovi.where.R
import com.ovi.where.domain.model.SharedLocation
import com.ovi.where.presentation.map.components.Life360PinMarker
import com.ovi.where.presentation.map.components.avatarColorForUser
import com.ovi.where.presentation.map.components.fanOutOverlappingMarkers

/**
 * Live meetup bottom sheet — a half-screen map showing everyone in this
 * conversation who is currently sharing their live location, plus a duration
 * picker and a primary action button (Share / Stop).
 *
 * Mirrors the share-target sheet on the global map screen but scoped to a
 * single conversation, so the user can quickly join the meetup without
 * leaving the chat.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveMeetupSheet(
    visible: Boolean,
    conversationTitle: String?,
    locations: List<SharedLocation>,
    isSharing: Boolean,
    sharingTimeRemaining: String?,
    selectedDurationMinutes: Long,
    onDurationSelected: (Long) -> Unit,
    onStartSharing: () -> Unit,
    onStopSharing: () -> Unit,
    onOpenFullMap: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showInfiniteConfirm by rememberSaveable { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 38.dp, topEnd = 38.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
        ) {
            // ── Title row ────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Let's Meetup",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val sharerCount = locations.count {
                        it.latitude != 0.0 && it.longitude != 0.0
                    }
                    val subtitle = when {
                        isSharing && sharerCount > 1 ->
                            "You and ${sharerCount - 1} ${if (sharerCount - 1 == 1) "other" else "others"} sharing"
                        isSharing ->
                            "Only you are sharing in ${conversationTitle ?: "this chat"}"
                        sharerCount > 0 ->
                            "$sharerCount ${if (sharerCount == 1) "person" else "people"} sharing in ${conversationTitle ?: "this chat"}"
                        else ->
                            "Share your location with ${conversationTitle ?: "this chat"}"
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (locations.any { it.latitude != 0.0 && it.longitude != 0.0 }) {
                    IconButton(
                        onClick = onOpenFullMap,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = ImageVector.vectorResource(id = R.drawable.map),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // ── Map preview (220dp, rounded) ─────────────────────────────
            MapPreview(
                locations = locations,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            )

            Spacer(Modifier.height(16.dp))

            // ── Duration picker (only shown when not currently sharing) ──
            if (!isSharing) {
                Text(
                    text = "HOW LONG?",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DurationSegment(
                        label = "15m",
                        selected = selectedDurationMinutes == 15L,
                        modifier = Modifier.weight(1f),
                        onClick = { onDurationSelected(15L) }
                    )
                    DurationSegment(
                        label = "1h",
                        selected = selectedDurationMinutes == 60L,
                        modifier = Modifier.weight(1f),
                        onClick = { onDurationSelected(60L) }
                    )
                    DurationSegment(
                        label = "4h",
                        selected = selectedDurationMinutes == 240L,
                        modifier = Modifier.weight(1f),
                        onClick = { onDurationSelected(240L) }
                    )
                    DurationSegment(
                        label = "∞",
                        selected = selectedDurationMinutes == 0L,
                        modifier = Modifier.weight(1f),
                        onClick = { showInfiniteConfirm = true }
                    )
                }

                if (showInfiniteConfirm) {
                    AlertDialog(
                        onDismissRequest = { showInfiniteConfirm = false },
                        icon = {
                            Icon(
                                Icons.Filled.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        title = {
                            Text(
                                "Share until you stop?",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        text = {
                            Column {
                                Text(
                                    "Your live location will keep streaming with no time limit. " +
                                        "It will only stop when you tap Stop or remove this share manually.",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(Modifier.height(12.dp))
                                InfiniteShareBullet("Drains more battery than a timed share")
                                InfiniteShareBullet(
                                    "${conversationTitle ?: "Recipients"} will keep seeing your location until you stop it"
                                )
                                InfiniteShareBullet("You can stop anytime from this chat or the map")
                            }
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    onDurationSelected(0L)
                                    showInfiniteConfirm = false
                                }
                            ) {
                                Text("Use no time limit", fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showInfiniteConfirm = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }

                Spacer(Modifier.height(16.dp))
            } else {
                // Sharing status pill
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val infiniteTransition = rememberInfiniteTransition(label = "sharing_dot")
                        val pulseScale by infiniteTransition.animateFloat(
                            initialValue = 0.85f, targetValue = 1.15f,
                            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
                            label = "sharing_dot_scale"
                        )
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .scale(pulseScale)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.tertiary)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "You're sharing live location",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                text = if (sharingTimeRemaining != null) "$sharingTimeRemaining remaining" else "Until you stop",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.75f)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // ── Primary action button ────────────────────────────────────
            if (isSharing) {
                Button(
                    onClick = onStopSharing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Icon(Icons.Filled.Stop, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Stop sharing",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Button(
                    onClick = onStartSharing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(imageVector = ImageVector.vectorResource(id = R.drawable.location_arrow), null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Share my location",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Mini Google Map preview rendered inside the bottom sheet, showing all active
 * sharers in the conversation. Auto-zooms to fit all markers. Shows an empty
 * state with a friendly message when no one is sharing yet.
 */
@Composable
private fun MapPreview(
    locations: List<SharedLocation>,
    modifier: Modifier = Modifier
) {
    val validLocations = remember(locations) {
        locations.filter { it.latitude != 0.0 && it.longitude != 0.0 }
    }

    // ── Night mode map style ──────────────────────────────────────────────
    // Mirrors GlobalMapScreen: auto-applies the bundled night JSON between
    // 7pm and 6am so the chat preview matches the full map's look.
    val context = LocalContext.current
    val nightMapStyle = remember {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val isNight = hour < 6 || hour >= 19
        if (isNight) {
            com.google.android.gms.maps.model.MapStyleOptions
                .loadRawResourceStyle(context, com.ovi.where.R.raw.map_style_night)
        } else null
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 1.dp
    ) {
        if (validLocations.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surfaceContainerLow,
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "No one is sharing yet",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Be the first to share your location",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            // Seed the camera at the sharers' bounds *immediately* — no animation,
            // no flash. Use a centroid + heuristic zoom for the initial position
            // so when GoogleMap composes for the first time the camera is already
            // framing the meetup. Then we still nudge it tightly via
            // newLatLngBounds in a LaunchedEffect (uses the actual viewport size
            // so the framing is exact).
            val initialPosition = remember(validLocations) {
                if (validLocations.size == 1) {
                    val only = validLocations.first()
                    CameraPosition.fromLatLngZoom(LatLng(only.latitude, only.longitude), 15f)
                } else {
                    val centroidLat = validLocations.sumOf { it.latitude } / validLocations.size
                    val centroidLng = validLocations.sumOf { it.longitude } / validLocations.size
                    // Pick a zoom that roughly fits the diagonal span. Refined
                    // by the bounds animation below once the map knows its size.
                    val maxLat = validLocations.maxOf { it.latitude }
                    val minLat = validLocations.minOf { it.latitude }
                    val maxLng = validLocations.maxOf { it.longitude }
                    val minLng = validLocations.minOf { it.longitude }
                    val span = maxOf(maxLat - minLat, maxLng - minLng)
                    val zoom = when {
                        span > 5.0 -> 5f
                        span > 1.0 -> 8f
                        span > 0.5 -> 9f
                        span > 0.1 -> 11f
                        span > 0.05 -> 12f
                        span > 0.01 -> 13f
                        else -> 14f
                    }
                    CameraPosition.fromLatLngZoom(LatLng(centroidLat, centroidLng), zoom)
                }
            }
            val cameraPositionState = rememberCameraPositionState { position = initialPosition }

            // Frame all sharers exactly when the sheet opens. No animation —
            // animations cause the visible "zoom in" the user complained about.
            // Re-runs only when the sharer SET changes (a sharer joins or leaves),
            // not on every GPS coordinate update.
            val sharerKey = remember(validLocations) {
                validLocations.map { it.userId }.sorted().joinToString(",")
            }
            LaunchedEffect(sharerKey) {
                if (validLocations.isEmpty()) return@LaunchedEffect
                val update = if (validLocations.size == 1) {
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(
                            validLocations.first().latitude,
                            validLocations.first().longitude
                        ),
                        15f
                    )
                } else {
                    val bounds = LatLngBounds.builder().apply {
                        validLocations.forEach { include(LatLng(it.latitude, it.longitude)) }
                    }.build()
                    CameraUpdateFactory.newLatLngBounds(bounds, 96)
                }
                cameraPositionState.move(update)
            }

            // Fan out markers that sit within ~15m of each other so overlapping
            // pins don't stack on top of each other. Same util the map screen uses.
            val displayPositions = remember(validLocations) {
                fanOutOverlappingMarkers(
                    validLocations.map { it.userId to LatLng(it.latitude, it.longitude) }
                )
            }

            GoogleMap(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp)),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(
                    isMyLocationEnabled = false,
                    mapType = MapType.NORMAL,
                    // Auto-apply the night map style from 7pm-6am, exactly like
                    // the global map screen, so the chat preview blends with
                    // the rest of the app instead of glaring white at night.
                    mapStyleOptions = nightMapStyle
                ),
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = false,
                    zoomGesturesEnabled = true,
                    scrollGesturesEnabled = true,
                    tiltGesturesEnabled = false,
                    rotationGesturesEnabled = false,
                    myLocationButtonEnabled = false,
                    mapToolbarEnabled = false,
                    compassEnabled = false
                )
            ) {
                validLocations.forEach { loc ->
                    val displayPos = displayPositions[loc.userId]
                        ?: LatLng(loc.latitude, loc.longitude)
                    val markerState = remember(loc.userId, displayPos.latitude, displayPos.longitude) {
                        MarkerState(position = displayPos)
                    }

                    // Pre-load avatar bitmap with the same Coil pattern the
                    // map screen uses — disables hardware bitmaps (required
                    // for software-rendered MarkerComposable canvas), reuses
                    // the disk + memory cache so we never re-download the
                    // image when the chat reopens.
                    var avatarBitmap by remember(loc.userId) {
                        mutableStateOf<Bitmap?>(null)
                    }
                    LaunchedEffect(loc.photoUrl) {
                        val url = loc.photoUrl
                        if (!url.isNullOrEmpty()) {
                            val request = ImageRequest.Builder(context)
                                .data(url)
                                .allowHardware(false)
                                .memoryCacheKey(url)
                                .diskCacheKey(url)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .size(128)
                                .build()
                            val result = context.imageLoader.execute(request)
                            if (result is SuccessResult) {
                                avatarBitmap = (result.drawable as? BitmapDrawable)?.bitmap
                            }
                        }
                    }

                    MarkerComposable(
                        keys = arrayOf(avatarBitmap ?: Unit),
                        state = markerState,
                        title = loc.displayName.ifEmpty { loc.userId },
                        zIndex = 5f
                    ) {
                        Life360PinMarker(
                            avatarBitmap = avatarBitmap,
                            fallbackLabel = loc.displayName.firstOrNull()?.uppercase() ?: "?",
                            accentColor = avatarColorForUser(loc.userId)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Duration chip identical to the one used by [GlobalMapScreen]'s share-target sheet
 * so the two surfaces feel like the same control.
 */
@Composable
private fun DurationSegment(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(vertical = 14.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1
            )
        }
    }
}


/**
 * Bullet row used by the "Share until you stop" confirmation dialog.
 * Matches the styling in [GlobalMapScreen]'s identical confirmation so the two
 * surfaces feel like the same flow.
 */
@Composable
private fun InfiniteShareBullet(text: String) {
    Row(
        modifier = Modifier.padding(top = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            "•",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
