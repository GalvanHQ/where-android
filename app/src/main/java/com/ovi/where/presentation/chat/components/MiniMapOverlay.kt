package com.ovi.where.presentation.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
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
import androidx.compose.ui.graphics.Brush
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
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.ovi.where.domain.model.SharedLocation

private val MarkerColors = listOf(
    Color(0xFF6366F1), Color(0xFF8B5CF6), Color(0xFFEC4899),
    Color(0xFFF59E0B), Color(0xFF10B981), Color(0xFF06B6D4),
    Color(0xFFEF4444), Color(0xFF3B82F6)
)

/**
 * Premium mini-map overlay for the chat screen.
 *
 * Features:
 * - Auto-zoom: camera automatically fits all markers with smooth animation
 * - Re-centers when locations update (tracks movement in real-time)
 * - Gradient scrim at top/bottom for premium look
 * - Pulsing markers with user initials
 * - "X sharing" badge + sharer chips below
 * - Full screen button navigates to the main map
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
        enter = expandVertically(expandFrom = Alignment.Top, animationSpec = spring(dampingRatio = 0.8f)) + fadeIn(),
        exit = shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = tween(200)) + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
            shadowElevation = 12.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                ) {
                    val validLocations = locations.filter { it.latitude != 0.0 && it.longitude != 0.0 }

                    if (validLocations.isEmpty()) {
                        // Premium empty state
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
                                    Icons.Filled.ShareLocation,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = "No one is sharing location",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        }
                    } else {
                        val cameraPositionState = rememberCameraPositionState {
                            position = CameraPosition.fromLatLngZoom(
                                LatLng(validLocations.first().latitude, validLocations.first().longitude), 15f
                            )
                        }

                        // Auto-zoom: re-fit camera whenever locations change
                        LaunchedEffect(validLocations.map { "${it.userId}:${it.latitude}:${it.longitude}" }) {
                            if (validLocations.size == 1) {
                                cameraPositionState.animate(
                                    CameraUpdateFactory.newLatLngZoom(
                                        LatLng(validLocations.first().latitude, validLocations.first().longitude), 16f
                                    ),
                                    durationMs = 800
                                )
                            } else {
                                val bounds = LatLngBounds.builder().apply {
                                    validLocations.forEach { include(LatLng(it.latitude, it.longitude)) }
                                }.build()
                                cameraPositionState.animate(
                                    CameraUpdateFactory.newLatLngBounds(bounds, 60),
                                    durationMs = 800
                                )
                            }
                        }

                        GoogleMap(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)),
                            cameraPositionState = cameraPositionState,
                            properties = MapProperties(
                                isMyLocationEnabled = false,
                                mapType = MapType.NORMAL
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
                            validLocations.forEachIndexed { index, loc ->
                                val markerState = remember(loc.userId, loc.latitude, loc.longitude) {
                                    MarkerState(position = LatLng(loc.latitude, loc.longitude))
                                }
                                MarkerComposable(
                                    state = markerState,
                                    title = loc.displayName.ifEmpty { loc.userId },
                                    zIndex = 10f
                                ) {
                                    PulsingMarker(
                                        initial = loc.displayName.firstOrNull()?.uppercase() ?: "?",
                                        color = MarkerColors[index % MarkerColors.size]
                                    )
                                }
                            }
                        }
                    }

                    // Top gradient scrim for controls visibility
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.3f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )

                    // Controls: full screen + close
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Full screen button
                        IconButton(
                            onClick = onExpandToFullMap,
                            modifier = Modifier.size(36.dp),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                            )
                        ) {
                            Icon(
                                Icons.Filled.Fullscreen,
                                contentDescription = "Full screen map",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        // Close button
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier.size(36.dp),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                            )
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Close map",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Bottom-left badge: "X sharing"
                    val validCount = locations.count { it.latitude != 0.0 && it.longitude != 0.0 }
                    if (validCount > 0) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(10.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary,
                            shadowElevation = 4.dp
                        ) {
                            Text(
                                text = "$validCount sharing",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }

                // Who's sharing strip
                val validLocations = locations.filter { it.latitude != 0.0 && it.longitude != 0.0 }
                if (validLocations.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(validLocations, key = { it.userId }) { loc ->
                            val index = validLocations.indexOf(loc)
                            SharerChip(
                                name = loc.displayName.ifEmpty { loc.userId },
                                color = MarkerColors[index % MarkerColors.size]
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PulsingMarker(initial: String, color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.5f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "scale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "alpha"
    )

    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .scale(pulseScale)
                .clip(CircleShape)
                .background(color.copy(alpha = pulseAlpha))
        )
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(color)
                .border(2.5.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initial,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold, fontSize = 14.sp
                ),
                color = Color.White
            )
        }
    }
}

@Composable
private fun SharerChip(name: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = color.copy(alpha = 0.1f)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = name.split(" ").firstOrNull() ?: name,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
