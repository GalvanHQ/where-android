package com.ovi.where.presentation.people.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * Avatar with an animated pulsing ring when [isLive] is true.
 * Falls back to displaying the first initial of [displayName] when [photoUrl] is null.
 */
@Composable
fun LiveRingAvatar(
    photoUrl: String?,
    displayName: String,
    isLive: Boolean,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier
) {
    val ringColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Pulsing ring
        if (isLive) {
            val infiniteTransition = rememberInfiniteTransition(label = "live_ring_pulse")
            val scale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.15f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1500),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "ring_scale"
            )
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.8f,
                targetValue = 0.3f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1500),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "ring_alpha"
            )
            Box(
                modifier = Modifier
                    .size(size + 8.dp)
                    .scale(scale)
                    .border(
                        width = 2.dp,
                        color = ringColor.copy(alpha = alpha),
                        shape = CircleShape
                    )
            )
        }

        // Avatar
        if (photoUrl != null) {
            AsyncImage(
                model = photoUrl,
                contentDescription = displayName,
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            // Initial fallback
            val initial = displayName.take(1).uppercase().ifEmpty { "?" }
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initial,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}
