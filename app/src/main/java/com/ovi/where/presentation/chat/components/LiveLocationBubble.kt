package com.ovi.where.presentation.chat.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.ovi.where.domain.model.SharedLocation
import kotlinx.coroutines.delay

/**
 * LiveLocationBubble — Displays a real-time updating mini-map preview showing
 * the sender's live location within a chat conversation.
 *
 * Requirements: 1.6, 1.7, 1.9, 2.1, 2.2, 2.3, 2.5, 2.6
 *
 * Behavior:
 * - Renders a 200dp x 120dp mini-map with the sender's position marker.
 * - Updates marker position every 15 seconds while session is active (throttle).
 * - Displays "Updated {N}s ago" below the mini-map, refreshed every 10 seconds.
 * - If no update received within 60s: shows "Waiting for location..." with fade animation (0.4-1.0 opacity).
 * - On session end: shows "Location sharing ended - {duration}" with frozen final position.
 * - On tap in group conversation: navigates to Screen.GroupMap centered on sharer.
 *
 * @param sharedLocation The current shared location data for this session.
 * @param isSessionActive Whether the location sharing session is still active.
 * @param senderDisplayName The display name of the user sharing their location.
 * @param isGroupConversation Whether this bubble is displayed in a group conversation.
 * @param onTapNavigateToMap Callback invoked when the bubble is tapped in a group conversation.
 * @param modifier Optional modifier for the root composable.
 */
@Composable
fun LiveLocationBubble(
    sharedLocation: SharedLocation,
    isSessionActive: Boolean,
    senderDisplayName: String,
    isGroupConversation: Boolean,
    onTapNavigateToMap: () -> Unit,
    modifier: Modifier = Modifier
) {
    // ── Throttled marker position (max 1 update per 15s) ──────────────────────
    var displayedLatitude by remember { mutableStateOf(sharedLocation.latitude) }
    var displayedLongitude by remember { mutableStateOf(sharedLocation.longitude) }
    var lastMarkerUpdateTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Throttle: only update displayed position if 15s have elapsed since last update
    LaunchedEffect(sharedLocation.latitude, sharedLocation.longitude) {
        if (isSessionActive) {
            val now = System.currentTimeMillis()
            val elapsed = now - lastMarkerUpdateTime
            if (elapsed >= MARKER_UPDATE_THROTTLE_MS) {
                displayedLatitude = sharedLocation.latitude
                displayedLongitude = sharedLocation.longitude
                lastMarkerUpdateTime = now
            } else {
                // Wait for the remaining time then update
                delay(MARKER_UPDATE_THROTTLE_MS - elapsed)
                displayedLatitude = sharedLocation.latitude
                displayedLongitude = sharedLocation.longitude
                lastMarkerUpdateTime = System.currentTimeMillis()
            }
        } else {
            // Session ended — freeze at final position
            displayedLatitude = sharedLocation.latitude
            displayedLongitude = sharedLocation.longitude
        }
    }

    // ── "Updated Ns ago" timer — refreshes every 10 seconds ───────────────────
    var secondsAgo by remember { mutableLongStateOf(computeSecondsAgo(sharedLocation.timestamp)) }

    LaunchedEffect(sharedLocation.timestamp, isSessionActive) {
        if (isSessionActive) {
            while (true) {
                secondsAgo = computeSecondsAgo(sharedLocation.timestamp)
                delay(TIME_AGO_REFRESH_INTERVAL_MS)
            }
        }
    }

    // ── Stale detection: no update within 60s ─────────────────────────────────
    val isStale = isSessionActive && secondsAgo >= STALE_THRESHOLD_SECONDS

    // ── Fade animation for "Waiting for location..." (0.4 to 1.0 opacity) ─────
    val fadeAlpha = if (isStale) {
        val infiniteTransition = rememberInfiniteTransition(label = "stale_fade")
        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.4f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1500),
                repeatMode = RepeatMode.Reverse
            ),
            label = "stale_alpha"
        )
        alpha
    } else {
        1f
    }

    // ── Session duration formatting ───────────────────────────────────────────
    val sessionDurationText = if (!isSessionActive && sharedLocation.sharingStartedAt > 0L) {
        val durationMinutes = ((sharedLocation.timestamp - sharedLocation.sharingStartedAt) / 60_000L)
            .coerceAtLeast(1L)
        formatDuration(durationMinutes)
    } else null

    // ── Accessibility description ─────────────────────────────────────────────
    val accessibilityDescription = buildString {
        append("$senderDisplayName ")
        if (isSessionActive) {
            if (isStale) append("waiting for location update")
            else append("sharing live location, updated ${secondsAgo}s ago")
        } else {
            append("location sharing ended")
            if (sessionDurationText != null) append(", duration $sessionDurationText")
        }
    }

    // ── Camera position for mini-map ──────────────────────────────────────────
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            LatLng(displayedLatitude, displayedLongitude),
            MINI_MAP_ZOOM_LEVEL
        )
    }

    // Update camera when displayed position changes
    LaunchedEffect(displayedLatitude, displayedLongitude) {
        cameraPositionState.position = CameraPosition.fromLatLngZoom(
            LatLng(displayedLatitude, displayedLongitude),
            MINI_MAP_ZOOM_LEVEL
        )
    }

    Column(
        modifier = modifier
            .semantics { contentDescription = accessibilityDescription }
    ) {
        Card(
            modifier = Modifier
                .size(width = MINI_MAP_WIDTH, height = MINI_MAP_HEIGHT)
                .then(
                    if (isGroupConversation) Modifier.clickable { onTapNavigateToMap() }
                    else Modifier
                ),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Mini Google Map
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    properties = MapProperties(
                        isMyLocationEnabled = false
                    ),
                    uiSettings = MapUiSettings(
                        zoomControlsEnabled = false,
                        zoomGesturesEnabled = false,
                        scrollGesturesEnabled = false,
                        tiltGesturesEnabled = false,
                        rotationGesturesEnabled = false,
                        myLocationButtonEnabled = false,
                        mapToolbarEnabled = false,
                        compassEnabled = false
                    )
                ) {
                    // Position marker
                    val markerState = remember(displayedLatitude, displayedLongitude) {
                        MarkerState(position = LatLng(displayedLatitude, displayedLongitude))
                    }
                    MarkerComposable(
                        state = markerState,
                        title = senderDisplayName,
                        zIndex = 10f
                    ) {
                        Box(
                            modifier = Modifier
                                .size(MARKER_SIZE)
                                .alpha(fadeAlpha)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Status overlay at bottom
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = when {
                            !isSessionActive -> "Location sharing ended"
                            isStale -> "Waiting for location..."
                            else -> "Sharing live location"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isStale) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = if (isStale) Modifier.alpha(fadeAlpha) else Modifier
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // ── Subtitle below mini-map ───────────────────────────────────────────
        when {
            !isSessionActive && sessionDurationText != null -> {
                // Requirement 2.6: "Location sharing ended - {duration}"
                Text(
                    text = "Location sharing ended - $sessionDurationText",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            isStale -> {
                // Requirement 2.5: "Waiting for location..." with fade
                Text(
                    text = "Waiting for location...",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.alpha(fadeAlpha)
                )
            }
            isSessionActive -> {
                // Requirement 2.3: "Updated {N}s ago"
                Text(
                    text = "Updated ${secondsAgo}s ago",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── Constants ─────────────────────────────────────────────────────────────────

/** Mini-map dimensions as specified in the task (200dp x 120dp). */
private val MINI_MAP_WIDTH = 200.dp
private val MINI_MAP_HEIGHT = 120.dp

/** Zoom level for the mini-map preview. */
private const val MINI_MAP_ZOOM_LEVEL = 15f

/** Marker size on the mini-map. */
private val MARKER_SIZE = 24.dp

/** Throttle interval for marker position updates (15 seconds). Requirement 2.2. */
private const val MARKER_UPDATE_THROTTLE_MS = 15_000L

/** Refresh interval for the "Updated Ns ago" text (10 seconds). Requirement 2.3. */
private const val TIME_AGO_REFRESH_INTERVAL_MS = 10_000L

/** Threshold in seconds after which the location is considered stale (60s). Requirement 2.5. */
private const val STALE_THRESHOLD_SECONDS = 60L

// ── Helper functions ──────────────────────────────────────────────────────────

/**
 * Computes the number of seconds elapsed since the given timestamp.
 */
private fun computeSecondsAgo(timestamp: Long): Long {
    if (timestamp <= 0L) return 0L
    return ((System.currentTimeMillis() - timestamp) / 1000L).coerceAtLeast(0L)
}

/**
 * Formats a duration in minutes to a human-readable string.
 * - >= 60 minutes: "{H}h {M}m"
 * - < 60 minutes: "{M}m"
 *
 * Requirement 2.6: Duration formatted as "{H}h {M}m" if 60 minutes or longer, "{M}m" if under.
 */
internal fun formatDuration(durationMinutes: Long): String {
    return if (durationMinutes >= 60) {
        val hours = durationMinutes / 60
        val minutes = durationMinutes % 60
        if (minutes > 0) "${hours}h ${minutes}m" else "${hours}h"
    } else {
        "${durationMinutes}m"
    }
}
