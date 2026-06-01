package com.ovi.where.presentation.common

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.ovi.where.core.theme.AvatarColors

/** Key used for the user's own marker in the display-positions map. */
const val MY_MARKER_KEY = "__my_location__"

// ── Pin dimensions ────────────────────────────────────────────────────────────
private val PIN_BODY_SIZE = 62.dp        // Rounded-square avatar area
private val PIN_BORDER_WIDTH = 3.5.dp      // White border ring
private val PIN_CORNER_RADIUS = 26.dp    // Rounded corners (squircle feel)
private val PIN_TAIL_WIDTH = 14.dp       // Tail width
private val PIN_TAIL_HEIGHT = 10.dp      // Tail height

/**
 * Map pin marker — a speech-bubble shape: a rounded square (squircle) with
 * the avatar clipped inside, plus a small triangular pointer at the bottom
 * center. White border ring, soft shadow. Matches the Life360 / reference
 * design where the pin is NOT a circle but a rounded rectangle.
 */
@Composable
fun Life360PinMarker(
    avatarBitmap: Bitmap?,
    fallbackLabel: String,
    accentColor: Color,
    borderColor: Color = Color.Unspecified,
    modifier: Modifier = Modifier
) {
    val resolvedBorderColor = if (borderColor == Color.Unspecified) {
        MaterialTheme.colorScheme.surface
    } else {
        borderColor
    }

    val avatarImageBitmap = remember(avatarBitmap) { avatarBitmap?.asImageBitmap() }
    val innerClipShape = RoundedCornerShape(PIN_CORNER_RADIUS - PIN_BORDER_WIDTH)

    val markerShape = remember {
        MapMarkerShape(
            cornerRadius = PIN_CORNER_RADIUS,
            tailWidth = PIN_TAIL_WIDTH,
            tailHeight = PIN_TAIL_HEIGHT
        )
    }

    // 1. Outer wrapper prevents the drop shadow from being clipped by the Map Bitmap
    Box(
        modifier = modifier.padding(
            start = 16.dp,
            end = 16.dp,
            top = 16.dp,
            bottom = 4.dp // Kept small so the map pin anchor stays locked to the tail tip
        )
    ) {
        // 2. The Pin Shape
        Box(
            modifier = Modifier
                .size(
                    width = PIN_BODY_SIZE,
                    height = PIN_BODY_SIZE + PIN_TAIL_HEIGHT
                )
                .drawBehind {
                    // FIX: Draw the drop shadow ONLY for the squircle body, ignoring the tail
                    val paint = Paint()
                    val frameworkPaint = paint.asFrameworkPaint()
                    frameworkPaint.color = android.graphics.Color.BLACK
                    frameworkPaint.setShadowLayer(
                        12.dp.toPx(), // Blur
                        0f,           // Offset X
                        8.dp.toPx(),  // Offset Y
                        android.graphics.Color.argb(70, 0, 0, 0) // ~28% alpha black
                    )

                    drawIntoCanvas { canvas ->
                        canvas.drawRoundRect(
                            left = 0f,
                            top = 0f,
                            right = size.width,
                            bottom = size.height - PIN_TAIL_HEIGHT.toPx(),
                            radiusX = PIN_CORNER_RADIUS.toPx(), // ⬅️ Changed from rx
                            radiusY = PIN_CORNER_RADIUS.toPx(), // ⬅️ Changed from ry
                            paint = paint
                        )
                    }
                }
                .background(resolvedBorderColor, markerShape)
                .padding(bottom = PIN_TAIL_HEIGHT)
                .padding(PIN_BORDER_WIDTH)
        ) {
            // ── Inner Avatar ────────────────────────────────────────────
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(innerClipShape)
                    .background(accentColor)
            ) {
                if (avatarImageBitmap != null) {
                    Image(
                        painter = BitmapPainter(avatarImageBitmap),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = fallbackLabel.trim().take(1).uppercase().ifEmpty { "?" },
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * A custom shape that merges the rounded rectangular body and the triangular pointer.
 * This guarantees the shadow drops evenly behind the *entire* pin, including the tail.
 */
private class MapMarkerShape(
    private val cornerRadius: Dp,
    private val tailWidth: Dp,
    private val tailHeight: Dp
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline = with(density) {
        val cr = cornerRadius.toPx()
        val tw = tailWidth.toPx()
        val th = tailHeight.toPx()

        val bodyHeight = size.height - th
        val center = size.width / 2f
        val halfTail = tw / 2f

        val path = Path().apply {
            // 1. Draw the main squircle body
            addRoundRect(
                RoundRect(
                    left = 0f,
                    top = 0f,
                    right = size.width,
                    bottom = bodyHeight,
                    cornerRadius = CornerRadius(cr, cr)
                )
            )

            // 2. Draw the tail seamlessly appended to the bottom
            // Drawn clockwise to match addRoundRect's winding direction for a solid fill
            moveTo(center - halfTail, bodyHeight)
            lineTo(center + halfTail, bodyHeight) // Cross over to the right side of the tail base

            // Curve down to the bottom tip
            cubicTo(
                center + halfTail * 0.7f, bodyHeight + th * 0.2f,
                center + halfTail * 0.4f, bodyHeight + th * 0.8f,
                center, size.height
            )

            // Curve back up to the left side
            cubicTo(
                center - halfTail * 0.4f, bodyHeight + th * 0.8f,
                center - halfTail * 0.7f, bodyHeight + th * 0.2f,
                center - halfTail, bodyHeight
            )
            close()
        }

        Outline.Generic(path)
    }
}

/** Consistent avatar color assignment based on userId hash.
 *  Mirrors the canonical [AvatarColors] so map
 *  pins, chat sender colors, and any other avatar use site stay in lockstep
 *  with the brand palette. */
val AvatarMarkerColors = AvatarColors

fun avatarColorForUser(userId: String): Color {
    return AvatarMarkerColors[userId.hashCode().and(0x7FFFFFFF) % AvatarMarkerColors.size]
}
