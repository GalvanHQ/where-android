package com.ovi.where.presentation.chat.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ovi.where.core.utils.LocalReducedMotion
import com.ovi.where.domain.model.MessageStatus
import com.ovi.where.presentation.model.BubbleDirection

/**
 * Message delivery status indicator displayed after the timestamp on sent messages.
 *
 * Shows:
 * - PENDING: clock icon, white/60%
 * - SENT: single tick, white/70%
 * - DELIVERED: double tick, white/70%
 * - READ: double tick, light blue accent (#34B7F1)
 * - FAILED: error icon, error color
 *
 * Transitions between states use a 150ms crossfade animation (Requirement 23.5).
 * Reduced motion: apply instantly with no transition (Requirement 23.7).
 * Only displayed on messages with BubbleDirection.SENT.
 *
 * Requirements: 10.1, 10.2, 10.3, 10.4, 10.5, 10.6, 10.7, 10.8, 23.5, 23.7
 */
@Composable
fun MessageStatusIndicator(
    status: MessageStatus,
    direction: BubbleDirection,
    modifier: Modifier = Modifier
) {
    // Requirement 10.6: Only display on sent messages
    if (direction != BubbleDirection.SENT) return

    val reducedMotion = LocalReducedMotion.current

    val accessibilityDescription = when (status) {
        MessageStatus.PENDING -> "Message pending"
        MessageStatus.SENT -> "Message sent"
        MessageStatus.DELIVERED -> "Message delivered"
        MessageStatus.READ -> "Message read"
        MessageStatus.FAILED -> "Message failed"
    }

    // Requirement 23.5: 150ms crossfade transition between status states
    // Requirement 23.7: Skip animation when reduced motion is enabled
    Crossfade(
        targetState = status,
        animationSpec = if (reducedMotion) snap() else tween(durationMillis = CROSSFADE_DURATION_MS),
        label = "MessageStatusCrossfade",
        modifier = modifier
            .semantics { contentDescription = accessibilityDescription }
    ) { currentStatus ->
        when (currentStatus) {
            // PENDING — clock icon, subtle white
            MessageStatus.PENDING -> Icon(
                imageVector = Icons.Filled.AccessTime,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(STATUS_ICON_SIZE)
            )
            // SENT — single tick, white
            MessageStatus.SENT -> Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(STATUS_ICON_SIZE)
            )
            // DELIVERED — double tick, white
            MessageStatus.DELIVERED -> Icon(
                imageVector = Icons.Filled.DoneAll,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(STATUS_ICON_SIZE)
            )
            // READ — double tick, light blue accent for clear "read" feedback
            MessageStatus.READ -> Icon(
                imageVector = Icons.Filled.DoneAll,
                contentDescription = null,
                tint = READ_TICK_COLOR,
                modifier = Modifier.size(STATUS_ICON_SIZE)
            )
            // FAILED — error icon, error color
            MessageStatus.FAILED -> Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(STATUS_ICON_SIZE)
            )
        }
    }
}

/** Icon size for all status indicators (13dp for compact inline display). */
private val STATUS_ICON_SIZE = 13.dp

/** Light blue color for READ status ticks — high contrast against primary bubble. */
private val READ_TICK_COLOR = Color(0xFF34B7F1)

/** Crossfade animation duration in milliseconds (150ms per Requirement 23.5). */
private const val CROSSFADE_DURATION_MS = 150
