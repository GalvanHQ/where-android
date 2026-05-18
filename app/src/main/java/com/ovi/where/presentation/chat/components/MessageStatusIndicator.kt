package com.ovi.where.presentation.chat.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ovi.where.core.utils.LocalReducedMotion
import com.ovi.where.domain.model.MessageStatus
import com.ovi.where.presentation.model.BubbleDirection

/**
 * Messenger-style circular delivery status indicator.
 *
 * Visual language (matches Messenger's "circle of state" precisely):
 *   - PENDING   : hollow circle (outlined ring, no fill)
 *   - SENT      : hollow circle with a check inside (outlined ring + outlined check)
 *   - DELIVERED : filled primary circle with a white check inside
 *   - READ      : nothing rendered here — the reader's avatar (via [ReadReceiptIndicator])
 *                 takes over as the "seen" cue
 *   - FAILED    : red filled circle with a white exclamation
 *
 * The icon only appears on sent messages, transitions between states with a 150ms crossfade,
 * and is sized to live cleanly next to the small timestamp text outside the bubble.
 */
@Composable
fun MessageStatusIndicator(
    status: MessageStatus,
    direction: BubbleDirection,
    modifier: Modifier = Modifier
) {
    if (direction != BubbleDirection.SENT) return
    // READ is rendered as the reader's avatar by ReadReceiptIndicator, so suppress here.
    if (status == MessageStatus.READ) return

    val reducedMotion = LocalReducedMotion.current

    val accessibilityDescription = when (status) {
        MessageStatus.PENDING -> "Message pending"
        MessageStatus.SENT -> "Message sent"
        MessageStatus.DELIVERED -> "Message delivered"
        MessageStatus.READ -> "Message read"
        MessageStatus.FAILED -> "Message failed"
    }

    Crossfade(
        targetState = status,
        animationSpec = if (reducedMotion) snap() else tween(durationMillis = CROSSFADE_DURATION_MS),
        label = "MessageStatusCrossfade",
        modifier = modifier.semantics { contentDescription = accessibilityDescription }
    ) { currentStatus ->
        when (currentStatus) {
            MessageStatus.PENDING -> StatusRing(filled = false, showCheck = false)
            MessageStatus.SENT -> StatusRing(filled = false, showCheck = true)
            MessageStatus.DELIVERED -> StatusRing(filled = true, showCheck = true)
            MessageStatus.FAILED -> FailedBadge()
            MessageStatus.READ -> Box {} // handled elsewhere
        }
    }
}

/**
 * Hollow or filled primary-colored circular ring with an optional check glyph inside.
 *
 * @param filled When true, renders a solid primary fill with a white check (DELIVERED).
 *               When false, renders just an outlined ring; if [showCheck] is also true an
 *               outlined check is drawn over the ring (SENT). With both flags false you get
 *               a plain hollow ring (PENDING).
 */
@Composable
private fun StatusRing(filled: Boolean, showCheck: Boolean) {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
    val ringSize = STATUS_BADGE_SIZE
    val checkSize = STATUS_CHECK_SIZE

    if (filled) {
        Box(
            modifier = Modifier
                .size(ringSize)
                .clip(CircleShape)
                .background(tint),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.surface,
                modifier = Modifier.size(checkSize)
            )
        }
    } else {
        Box(
            modifier = Modifier
                .size(ringSize)
                .border(width = 1.2.dp, color = tint, shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (showCheck) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(checkSize)
                )
            }
        }
    }
}

/**
 * Failed-state badge: solid error-colored circle with a white exclamation glyph.
 */
@Composable
private fun FailedBadge() {
    Box(
        modifier = Modifier
            .size(STATUS_BADGE_SIZE)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.error),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.PriorityHigh,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(STATUS_CHECK_SIZE)
        )
    }
}

private val STATUS_BADGE_SIZE = 14.dp
private val STATUS_CHECK_SIZE = 9.dp
private const val CROSSFADE_DURATION_MS = 150
