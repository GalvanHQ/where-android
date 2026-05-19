package com.ovi.where.presentation.chat.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ovi.where.core.utils.LocalReducedMotion
import com.ovi.where.domain.model.MessageStatus
import com.ovi.where.presentation.model.BubbleDirection

/**
 * WhatsApp-style double-tick delivery status indicator.
 *
 * Visual states (no text labels — just the icon):
 *   - PENDING   → single gray clock (small circle outline)
 *   - SENT      → single gray tick ✓
 *   - DELIVERED → double gray ticks ✓✓
 *   - READ      → double blue ticks ✓✓
 *   - FAILED    → single red "!" exclamation
 *
 * Size: 16×12dp (wider than tall to fit double ticks).
 * Transitions: 150ms crossfade between states.
 */
@Composable
fun MessageStatusIndicator(
    status: MessageStatus,
    direction: BubbleDirection,
    modifier: Modifier = Modifier
) {
    if (direction != BubbleDirection.SENT) return

    val reducedMotion = LocalReducedMotion.current

    val description = when (status) {
        MessageStatus.PENDING -> "Message sending"
        MessageStatus.SENT -> "Message sent"
        MessageStatus.DELIVERED -> "Message delivered"
        MessageStatus.READ -> "Message read"
        MessageStatus.FAILED -> "Message failed"
    }

    Crossfade(
        targetState = status,
        animationSpec = if (reducedMotion) snap() else tween(durationMillis = 150),
        label = "WhatsAppStatusCrossfade",
        modifier = modifier.semantics { contentDescription = description }
    ) { current ->
        val grayTick = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        val blueTick = Color(0xFF53BDEB) // WhatsApp blue
        val errorColor = MaterialTheme.colorScheme.error

        Canvas(modifier = Modifier.size(width = ICON_WIDTH, height = ICON_HEIGHT)) {
            val h = size.height
            val w = size.width
            val stroke = 1.6.dp.toPx()

            when (current) {
                MessageStatus.PENDING -> {
                    // Small clock circle
                    val cx = w / 2f
                    val cy = h / 2f
                    val r = h * 0.35f
                    drawCircle(
                        color = grayTick,
                        radius = r,
                        center = Offset(cx, cy),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)
                    )
                    // Clock hands
                    drawLine(
                        color = grayTick,
                        start = Offset(cx, cy),
                        end = Offset(cx, cy - r * 0.6f),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = grayTick,
                        start = Offset(cx, cy),
                        end = Offset(cx + r * 0.5f, cy),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                }
                MessageStatus.SENT -> {
                    // Single tick ✓
                    drawTick(
                        color = grayTick,
                        strokeWidth = stroke,
                        offsetX = w * 0.25f,
                        height = h
                    )
                }
                MessageStatus.DELIVERED -> {
                    // Double tick ✓✓ (gray)
                    drawTick(
                        color = grayTick,
                        strokeWidth = stroke,
                        offsetX = w * 0.1f,
                        height = h
                    )
                    drawTick(
                        color = grayTick,
                        strokeWidth = stroke,
                        offsetX = w * 0.35f,
                        height = h
                    )
                }
                MessageStatus.READ -> {
                    // Double tick ✓✓ (blue)
                    drawTick(
                        color = blueTick,
                        strokeWidth = stroke,
                        offsetX = w * 0.1f,
                        height = h
                    )
                    drawTick(
                        color = blueTick,
                        strokeWidth = stroke,
                        offsetX = w * 0.35f,
                        height = h
                    )
                }
                MessageStatus.FAILED -> {
                    // Red exclamation "!"
                    val cx = w / 2f
                    val topY = h * 0.15f
                    val bottomY = h * 0.6f
                    drawLine(
                        color = errorColor,
                        start = Offset(cx, topY),
                        end = Offset(cx, bottomY),
                        strokeWidth = stroke * 1.2f,
                        cap = StrokeCap.Round
                    )
                    drawCircle(
                        color = errorColor,
                        radius = stroke * 0.8f,
                        center = Offset(cx, h * 0.8f)
                    )
                }
            }
        }
    }
}

/**
 * Draws a single WhatsApp-style tick (check mark) at the given horizontal offset.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTick(
    color: Color,
    strokeWidth: Float,
    offsetX: Float,
    height: Float
) {
    // Tick proportions relative to the canvas height
    val startX = offsetX
    val startY = height * 0.5f
    val midX = offsetX + height * 0.2f
    val midY = height * 0.75f
    val endX = offsetX + height * 0.55f
    val endY = height * 0.25f

    drawLine(
        color = color,
        start = Offset(startX, startY),
        end = Offset(midX, midY),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )
    drawLine(
        color = color,
        start = Offset(midX, midY),
        end = Offset(endX, endY),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )
}

private val ICON_WIDTH = 18.dp
private val ICON_HEIGHT = 12.dp
