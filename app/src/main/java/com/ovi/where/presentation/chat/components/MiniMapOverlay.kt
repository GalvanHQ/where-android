package com.ovi.where.presentation.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.ShareLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.ovi.where.domain.model.SharedLocation

// Marker colors for different users
private val MarkerColors = listOf(
    Color(0xFF4CAF50), // Green
    Color(0xFF2196F3), // Blue
    Color(0xFFFF9800), // Orange
    Color(0xFF9C27B0), // Purple
    Color(0xFFE91E63), // Pink
    Color(0xFF00BCD4), // Cyan
    Color(0xFFFF5722), // Deep Orange
    Color(0xFF3F51B5), // Indigo
)

/**
 * Enhanced mini-map overlay that slides down from the top of the chat screen.
 * Shows all active location sharers with colored avatar markers and a
 * horizontal "who's sharing" strip below the map.
 *
 * UX improvements over basic version:
 * - Colored markers per user (consistent color assignment)
 * - Pulsing ring animation on markers to indicate live updates
 * - "Who's sharing" horizontal list with names below the map
 * - Empty state when no one is sharing
 * - Smooth camera animation when locations change
 */
@Composable
fun MiniMapOverlay(
    visible: Boolean,
    locations: List<SharedLocation>,
    onClose: () -> Unit,
    onExpandToFullMap: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
        exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column {
                // ── Map area ──────────────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                ) {
                    val validLocations = locations.filter { it.latitude != 0.0 && it.longitude != 0.0 }

                    if (validLocations.isEmpty()) {
                        // Empty state
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .background(MaterialTheme.colorScheme.surfaceContainerLow),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Filled.ShareLocation,
                                    contentDescription = null,
                                    modifier = Modifier.size(36.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "No one is sharing location",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    } else {
                        // Google Map with markers
                        val cameraPositionState = rememberCameraPositionState {
                            position = CameraPosition.fromLatLngZoom(LatLng(0.0, 0.0), 14f)
                        }

                        LaunchedEffect(validLocations) {
                            if (validLocations.size == 1) {
                                val loc = validLocations.first()
                                cameraPositionState.animate(
                                    CameraUpdateFactory.newLatLngZoom(
                                        LatLng(loc.latitude, loc.longitude), 15f
                                    ),
                                    durationMs = 600
                                )
                            } else if (validLocations.size > 1) {
                                val boundsBuilder = LatLngBounds.builder()
                                validLocations.forEach {
                                    boundsBuilder.include(LatLng(it.latitude, it.longitude))
                                }
                                cameraPositionState.animate(
                                    CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 80),
                                    durationMs = 600
                                )
                            }
                        }

                        GoogleMap(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)),
                            cameraPositionState = cameraPositionState,
                            properties = MapProperties(isMyLocationEnabled = false),
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
                            validLocations.forEachIndexed { index, loc ->
                                val markerState = remember(loc.userId, loc.latitude, loc.longitude) {
                                    MarkerState(position = LatLng(loc.latitude, loc.longitude))
                                }
                                val color = MarkerColors[index % MarkerColors.size]

                                MarkerComposable(
                                    state = markerState,
                                    title = loc.displayName.ifEmpty { loc.userId },
                                    zIndex = 10f
                                ) {
                                    PulsingMarker(
                                        initial = loc.displayName.firstOrNull()?.uppercase() ?: "?",
                                        color = color
                                    )
                                }
                            }
                        }
                    }

                    // Top-right controls
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    ) {
                        IconButton(
                            onClick = onExpandToFullMap,
                            modifier = Modifier.size(34.dp),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                            )
                        ) {
                            Icon(
                                Icons.Filled.OpenInFull,
                                contentDescription = "Open full map",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier.size(34.dp),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                            )
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Close map",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // ── Who's sharing strip ───────────────────────────────────────
                val validLocations = locations.filter { it.latitude != 0.0 && it.longitude != 0.0 }
                if (validLocations.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(validLocations, key = { it.userId }) { loc ->
                            val index = validLocations.indexOf(loc)
                            val color = MarkerColors[index % MarkerColors.size]
                            SharerChip(
                                name = loc.displayName.ifEmpty { loc.userId },
                                color = color
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Pulsing marker with a ring animation to indicate live location updates.
 */
@Composable
private fun PulsingMarker(
    initial: String,
    color: Color
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Box(contentAlignment = Alignment.Center) {
        // Pulsing ring
        Box(
            modifier = Modifier
                .size(40.dp)
                .scale(pulseScale)
                .clip(CircleShape)
                .background(color.copy(alpha = pulseAlpha))
        )
        // Solid marker
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(color)
                .border(2.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initial,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                ),
                color = Color.White
            )
        }
    }
}

/**
 * Compact chip showing a sharer's name with their assigned color dot.
 */
@Composable
private fun SharerChip(
    name: String,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(
                color = color.copy(alpha = 0.08f),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = name.split(" ").firstOrNull() ?: name,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
