package com.ovi.where.presentation.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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

/**
 * Mini-map overlay that slides down from the top of the chat screen.
 * Shows all active location sharers in the conversation with markers.
 * Includes a close button and an expand button to navigate to full map.
 *
 * Height: 220dp — compact enough to still see chat messages below.
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
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
            shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Box {
                // Google Map
                val cameraPositionState = rememberCameraPositionState {
                    position = CameraPosition.fromLatLngZoom(LatLng(0.0, 0.0), 14f)
                }

                // Fit camera to show all markers
                LaunchedEffect(locations) {
                    val validLocations = locations.filter { it.latitude != 0.0 && it.longitude != 0.0 }
                    if (validLocations.size == 1) {
                        val loc = validLocations.first()
                        cameraPositionState.animate(
                            CameraUpdateFactory.newLatLngZoom(
                                LatLng(loc.latitude, loc.longitude), 15f
                            )
                        )
                    } else if (validLocations.size > 1) {
                        val boundsBuilder = LatLngBounds.builder()
                        validLocations.forEach {
                            boundsBuilder.include(LatLng(it.latitude, it.longitude))
                        }
                        cameraPositionState.animate(
                            CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 60)
                        )
                    }
                }

                GoogleMap(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
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
                    // Render markers for each active sharer
                    locations.filter { it.latitude != 0.0 && it.longitude != 0.0 }.forEach { loc ->
                        val markerState = remember(loc.userId, loc.latitude, loc.longitude) {
                            MarkerState(position = LatLng(loc.latitude, loc.longitude))
                        }
                        MarkerComposable(
                            state = markerState,
                            title = loc.displayName.ifEmpty { loc.userId },
                            zIndex = 10f
                        ) {
                            // Custom marker: colored circle with initial
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = loc.displayName.firstOrNull()?.uppercase() ?: "?",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                // Top-right controls: expand + close
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    // Expand to full map
                    IconButton(
                        onClick = onExpandToFullMap,
                        modifier = Modifier.size(32.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
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
                    // Close
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(32.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
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

                // Bottom-left: active sharers count badge
                if (locations.isNotEmpty()) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        shadowElevation = 2.dp
                    ) {
                        Text(
                            text = "${locations.size} sharing",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Medium
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
