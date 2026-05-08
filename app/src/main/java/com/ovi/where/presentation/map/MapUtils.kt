package com.ovi.where.presentation.map

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import coil.compose.AsyncImage
import androidx.compose.ui.unit.Dp
import com.ovi.where.core.theme.AvatarColors
import com.ovi.where.core.theme.Dimens
import com.google.android.gms.maps.model.CameraPosition
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.tan

// ── Web Mercator Projection ───────────────────────────────────────────────────

/**
 * Converts a lat/lng coordinate to a screen pixel position using Web Mercator
 * projection, given the current camera centre and zoom level.
 */
fun latLngToPixel(
    lat: Double,
    lng: Double,
    centerLat: Double,
    centerLng: Double,
    zoom: Double,
    screenWidthPx: Float,
    screenHeightPx: Float
): Offset {
    fun mercX(lon: Double) = (lon + 180.0) / 360.0
    fun mercY(latDeg: Double): Double {
        val latRad = latDeg * PI / 180.0
        return (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0
    }
    val scale = 256.0 * 2.0.pow(zoom)
    val cx = mercX(centerLng) * scale
    val cy = mercY(centerLat) * scale
    val x = ((mercX(lng) * scale - cx) + screenWidthPx / 2).toFloat()
    val y = ((mercY(lat) * scale - cy) + screenHeightPx / 2).toFloat()
    return Offset(x, y)
}

/** Check if a projected pixel position is within the visible viewport (with padding). */
private fun isInViewport(pos: Offset, widthPx: Float, heightPx: Float, padding: Float = 100f): Boolean {
    return pos.x > -padding && pos.x < widthPx + padding &&
           pos.y > -padding && pos.y < heightPx + padding
}

/** Pick a deterministic avatar colour for a given userId. */
fun avatarColorFor(userId: String): Color =
    AvatarColors[userId.hashCode().and(0x7FFFFFFF) % AvatarColors.size]

// ── Avatar Marker Overlay ─────────────────────────────────────────────────────

/**
 * Immutable data needed to draw one map marker.
 */
data class MapMarker(
    val id: String,
    val userId: String,
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val photoUrl: String? = null,
    val isPulsing: Boolean = false
)

/**
 * Canvas overlay that draws coloured avatar circle markers on top of a Google Map.
 * Uses viewport culling to skip off-screen markers and per-marker `key()` for
 * fine-grained recomposition (only moved markers recompose).
 */
@Composable
fun AvatarMarkersOverlay(
    markers: List<MapMarker>,
    cameraPosition: CameraPosition,
    shadowColor: Color,
    borderColor: Color,
    textColor: Color,
    pulseColor: Color,
    markerRadius: Dp = Dimens.markerRadius,
    onMarkerClick: (MapMarker) -> Unit
) {
    if (markers.isEmpty()) return

    val density = LocalDensity.current

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val zoom = cameraPosition.zoom.toDouble()
        val centerLat = cameraPosition.target.latitude
        val centerLng = cameraPosition.target.longitude
        val radiusPx = with(density) { markerRadius.toPx() }

        // Cache projection results — only recalculate when camera or marker data changes
        val projections by remember(markers, zoom, centerLat, centerLng, widthPx, heightPx) {
            derivedStateOf {
                markers.map { marker ->
                    val pos = latLngToPixel(
                        marker.latitude, marker.longitude,
                        centerLat, centerLng,
                        zoom, widthPx, heightPx
                    )
                    marker to pos
                }.filter { (_, pos) -> isInViewport(pos, widthPx, heightPx) }
            }
        }

        projections.forEachIndexed { index, (marker, pos) ->
            // Per-marker key → only the marker that moved recomposes
            key(marker.id) {
                SingleMarker(
                    marker = marker,
                    pos = pos,
                    radiusPx = radiusPx,
                    markerRadius = markerRadius,
                    fillColor = AvatarColors[index % AvatarColors.size],
                    borderColor = borderColor,
                    textColor = textColor,
                    pulseColor = pulseColor,
                    onMarkerClick = onMarkerClick
                )
            }
        }
    }
}

/**
 * Single marker composable — isolated for per-marker recomposition.
 */
@Composable
private fun SingleMarker(
    marker: MapMarker,
    pos: Offset,
    radiusPx: Float,
    markerRadius: Dp,
    fillColor: Color,
    borderColor: Color,
    textColor: Color,
    pulseColor: Color,
    onMarkerClick: (MapMarker) -> Unit
) {
    val markerSize = markerRadius * 2

    // Animated pulse ring for active sharing
    if (marker.isPulsing) {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse_${marker.id}")
        val pulseScale by infiniteTransition.animateFloat(
            initialValue = 0.8f,
            targetValue = 1.3f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200),
                repeatMode = RepeatMode.Reverse
            ),
            label = "scale_${marker.id}"
        )
        val pulseAlpha by infiniteTransition.animateFloat(
            initialValue = 0.2f,
            targetValue = 0.05f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200),
                repeatMode = RepeatMode.Reverse
            ),
            label = "alpha_${marker.id}"
        )

        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        (pos.x - radiusPx * 2.1f).toInt(),
                        (pos.y - radiusPx * 2.1f).toInt()
                    )
                }
                .size(markerRadius * 4.2f)
                .scale(pulseScale)
                .clip(CircleShape)
                .background(pulseColor.copy(alpha = pulseAlpha))
        )
    }

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (pos.x - radiusPx).toInt(),
                    (pos.y - radiusPx).toInt()
                )
            }
            .size(markerSize)
            .clip(CircleShape)
            .background(fillColor)
            .border(3f.dp, borderColor, CircleShape)
            .clickable { onMarkerClick(marker) },
        contentAlignment = Alignment.Center
    ) {
        if (!marker.photoUrl.isNullOrEmpty()) {
            AsyncImage(
                model = marker.photoUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = marker.label,
                color = textColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
