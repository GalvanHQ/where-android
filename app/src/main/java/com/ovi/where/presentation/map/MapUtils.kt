package com.ovi.where.presentation.map

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
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

/** Pick a deterministic avatar colour for a given userId. */
fun avatarColorFor(userId: String): Color =
    AvatarColors[userId.hashCode().and(0x7FFFFFFF) % AvatarColors.size]

// ── Avatar Marker Overlay ─────────────────────────────────────────────────────

/**
 * Immutable data needed to draw one map marker.
 *
 * @param id        Unique key for this marker.
 * @param userId    Used for colour hashing.
 * @param label     1-character initial displayed inside the circle.
 * @param latitude  WGS-84 latitude.
 * @param longitude WGS-84 longitude.
 * @param photoUrl  If non-null, Coil image loaded as avatar (not yet wired — uses initial as fallback).
 * @param isPulsing When true, a green pulsing ring is drawn (active sharing).
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
 * Canvas overlay that draws coloured avatar circle markers on top of a MapLibre map.
 * Drawn entirely in Compose Canvas — no native map annotation API required.
 *
 * @param markers         List of markers to draw.
 * @param cameraPosition  Current camera position (used for projection).
 * @param shadowColor     Captured from `colorScheme.onSurface.copy(0.18f)` before Canvas.
 * @param borderColor     Captured from `colorScheme.surface`.
 * @param textColor       Captured from `colorScheme.onPrimary`.
 * @param markerRadius    Radius in dp; default `Dimens.markerRadius`.
 * @param onMarkerClick   Called when the user taps anywhere in the overlay.
 *                        Use the closest visible marker in production; here we fire
 *                        the first one as a simplification (fine for <20 friends).
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

        markers.forEachIndexed { index, marker ->
            val pos = latLngToPixel(
                marker.latitude, marker.longitude,
                centerLat, centerLng,
                zoom, widthPx, heightPx
            )
            val fillColor = AvatarColors[index % AvatarColors.size]
            val markerSize = markerRadius * 2

            if (marker.isPulsing) {
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (pos.x - radiusPx * 2.1f).toInt(),
                                (pos.y - radiusPx * 2.1f).toInt()
                            )
                        }
                        .size(markerRadius * 4.2f)
                        .clip(CircleShape)
                        .background(pulseColor.copy(alpha = 0.14f))
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
    }
}
