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
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Animated typing indicator rendered as a tiny received-style bubble with bouncing dots,
 * plus an optional caption below ("{name} is typing…").
 *
 * Visual style mirrors a real received message bubble (left-aligned, surfaceContainerHigh,
 * rounded with a tight bottom-left corner) so the typing context is unmistakable.
 */
@Composable
fun TypingIndicator(
    typingText: String?,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = typingText != null,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 }
    ) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .semantics { contentDescription = typingText ?: "" },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            // Bubble with bouncing dots — same shape language as a received "first" bubble.
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 18.dp,
                    bottomEnd = 18.dp,
                    bottomStart = 6.dp
                )
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    BouncingDots()
                }
            }

            if (!typingText.isNullOrBlank()) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = typingText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/**
 * Three dots that bounce in sequence to indicate typing activity.
 */
@Composable
private fun BouncingDots(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "typingDots")

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
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
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            )
        }
    }
}

private const val DOT_COUNT = 3
private const val DOT_SIZE_DP = 7
private const val DOT_BOUNCE_HEIGHT = 4f
private const val ANIMATION_DURATION_MS = 1200
private const val DOT_STAGGER_DELAY_MS = 150
