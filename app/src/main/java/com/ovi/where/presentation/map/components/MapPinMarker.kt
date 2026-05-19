package com.ovi.where.presentation.map.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// ── Pin dimensions ────────────────────────────────────────────────────────────
val PIN_BODY_SIZE = 52.dp
val PIN_BORDER_WIDTH = 3.dp
val PIN_TAIL_WIDTH = 20.dp
val PIN_TAIL_HEIGHT = 12.dp
val PIN_INNER_PADDING = 3.5.dp

/**
 * Premium Life360-style map pin marker with avatar, colored accent ring,
 * and smooth bezier tail pointer. Used across all map screens for consistency.
 *
 * @param avatarBitmap Pre-loaded avatar bitmap (null shows fallback initial)
 * @param fallbackLabel Text to show when no avatar (e.g., "J" for John)
 * @param accentColor The colored ring around the avatar
 * @param borderColor Outer border color (defaults to surface/white)
 */
@Composable
fun Life360PinMarker(
    avatarBitmap: Bitmap?,
    fallbackLabel: String,
    accentColor: Color,
    borderColor: Color = Color.Unspecified
) {
    val resolvedBorderColor = if (borderColor == Color.Unspecified) {
        MaterialTheme.colorScheme.surface
    } else {
        borderColor
    }
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Circular avatar body ──────────────────────────────────────────
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(PIN_BODY_SIZE)
                .shadow(
                    elevation = 10.dp,
                    shape = CircleShape,
                    ambientColor = accentColor.copy(alpha = 0.25f),
                    spotColor = accentColor.copy(alpha = 0.35f)
                )
                .background(resolvedBorderColor, CircleShape)
                .padding(PIN_BORDER_WIDTH)
                .clip(CircleShape)
                .background(accentColor)
                .padding(PIN_INNER_PADDING)
                .clip(CircleShape)
                .background(surfaceVariant)
        ) {
            if (avatarBitmap != null) {
                Image(
                    painter = BitmapPainter(avatarBitmap.asImageBitmap()),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = fallbackLabel,
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        // ── Smooth curved tail pointer ────────────────────────────────────
        Box(
            modifier = Modifier
                .offset(y = (-2).dp)
                .size(width = PIN_TAIL_WIDTH, height = PIN_TAIL_HEIGHT)
                .drawBehind {
                    val path = Path().apply {
                        moveTo(0f, 0f)
                        lineTo(size.width, 0f)
                        cubicTo(
                            size.width * 0.75f, size.height * 0.15f,
                            size.width * 0.6f, size.height * 0.85f,
                            size.width / 2f, size.height
                        )
                        cubicTo(
                            size.width * 0.4f, size.height * 0.85f,
                            size.width * 0.25f, size.height * 0.15f,
                            0f, 0f
                        )
                        close()
                    }
                    drawPath(path, color = resolvedBorderColor)
                }
        )
    }
}

/** Consistent avatar color assignment based on userId hash. */
val AvatarMarkerColors = listOf(
    Color(0xFF6366F1), // Indigo
    Color(0xFF8B5CF6), // Violet
    Color(0xFFEC4899), // Pink
    Color(0xFFF59E0B), // Amber
    Color(0xFF10B981), // Emerald
    Color(0xFF06B6D4), // Cyan
    Color(0xFFEF4444), // Red
    Color(0xFF3B82F6), // Blue
)

fun avatarColorForUser(userId: String): Color {
    return AvatarMarkerColors[userId.hashCode().and(0x7FFFFFFF) % AvatarMarkerColors.size]
}
