package com.ovi.where.presentation.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ovi.where.core.theme.Dimens

/**
 * Animated 3-dot typing indicator displayed below the last message, above the input.
 *
 * Shows:
 * - "{name} is typing…" for 1:1 conversations (Requirement 7.2)
 * - "{name1}, {name2} +N are typing…" for groups with max 2 names (Requirement 7.6)
 * - Animated bouncing dots
 *
 * Requirements: 7.2, 7.6
 */
@Composable
fun TypingIndicator(
    typingText: String?,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = typingText != null,
        enter = fadeIn() + slideInVertically { it },
        exit = fadeOut() + slideOutVertically { it }
    ) {
        Surface(
            modifier = modifier
                .padding(horizontal = Dimens.spaceLarge, vertical = Dimens.spaceSmall)
                .semantics { contentDescription = typingText ?: "" },
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(Dimens.cornerMedium)
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = Dimens.spaceLarge,
                    vertical = Dimens.spaceMedium
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Animated 3-dot indicator
                BouncingDots()

                Spacer(Modifier.width(Dimens.spaceMedium))

                // Typing text
                Text(
                    text = typingText ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Three dots that bounce in sequence to indicate typing activity.
 * Each dot bounces with a staggered delay for a wave effect.
 */
@Composable
private fun BouncingDots(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "typingDots")

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(DOT_COUNT) { index ->
            val offsetY by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = ANIMATION_DURATION_MS
                        0f at 0
                        -DOT_BOUNCE_HEIGHT at (ANIMATION_DURATION_MS / 4) + (index * DOT_STAGGER_DELAY_MS)
                        0f at (ANIMATION_DURATION_MS / 2) + (index * DOT_STAGGER_DELAY_MS)
                    },
                    repeatMode = RepeatMode.Restart
                ),
                label = "dot_$index"
            )

            Box(
                modifier = Modifier
                    .size(DOT_SIZE_DP.dp)
                    .offset { IntOffset(0, offsetY.dp.roundToPx()) }
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            )
        }
    }
}

private const val DOT_COUNT = 3
private const val DOT_SIZE_DP = 6
private const val DOT_BOUNCE_HEIGHT = 4f
private const val ANIMATION_DURATION_MS = 1200
private const val DOT_STAGGER_DELAY_MS = 150
