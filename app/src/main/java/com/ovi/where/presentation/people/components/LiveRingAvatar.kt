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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest

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
    val context = LocalContext.current
    val density = LocalDensity.current

    val avatarPixelSize = remember(size, density) {
        with(density) { size.roundToPx() }
    }

    val avatarImageRequest = remember(photoUrl, avatarPixelSize) {
        if (photoUrl.isNullOrBlank()) {
            null
        } else {
            ImageRequest.Builder(context)
                .data(photoUrl)
                .crossfade(true)
                .size(avatarPixelSize)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .networkCachePolicy(CachePolicy.ENABLED)
                .memoryCacheKey(photoUrl)
                .diskCacheKey(photoUrl)
                .build()
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
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

        if (avatarImageRequest != null) {
            AsyncImage(
                model = avatarImageRequest,
                contentDescription = displayName,
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            InitialAvatar(
                displayName = displayName,
                size = size
            )
        }
    }
}

@Composable
private fun InitialAvatar(
    displayName: String,
    size: Dp
) {
    val initial = remember(displayName) {
        displayName
            .trim()
            .take(1)
            .uppercase()
            .ifEmpty { "?" }
    }

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