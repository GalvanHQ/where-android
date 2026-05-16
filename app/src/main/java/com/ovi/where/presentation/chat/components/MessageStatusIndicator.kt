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
 * - PENDING: clock icon, onSurfaceVariant
 * - SENT: single tick, onSurfaceVariant
 * - DELIVERED: double tick, onSurfaceVariant
 * - READ: double tick, primary (accent) color
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
            .padding(start = STATUS_SPACING)
            .semantics { contentDescription = accessibilityDescription }
    ) { currentStatus ->
        when (currentStatus) {
            // Requirement 10.1: PENDING — clock icon, 14dp, onSurfaceVariant
            MessageStatus.PENDING -> Icon(
                imageVector = Icons.Filled.AccessTime,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(STATUS_ICON_SIZE)
            )
            // Requirement 10.2: SENT — single tick, 14dp, onSurfaceVariant
            MessageStatus.SENT -> Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(STATUS_ICON_SIZE)
            )
            // Requirement 10.3: DELIVERED — double tick, 14dp, onSurfaceVariant
            MessageStatus.DELIVERED -> Icon(
                imageVector = Icons.Filled.DoneAll,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(STATUS_ICON_SIZE)
            )
            // Requirement 10.4: READ — double tick, 14dp, primary (accent) color
            MessageStatus.READ -> Icon(
                imageVector = Icons.Filled.DoneAll,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(STATUS_ICON_SIZE)
            )
            // Requirement 10.7: FAILED — error icon, 14dp, error color
            MessageStatus.FAILED -> Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(STATUS_ICON_SIZE)
            )
        }
    }
}

/** Icon size for all status indicators (14dp per requirements). */
private val STATUS_ICON_SIZE = 14.dp

/** Horizontal spacing between timestamp and status icon (4dp per requirements). */
private val STATUS_SPACING = 4.dp

/** Crossfade animation duration in milliseconds (150ms per Requirement 23.5). */
private const val CROSSFADE_DURATION_MS = 150
