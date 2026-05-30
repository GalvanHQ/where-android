package com.ovi.where.presentation.map.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ovi.where.R
import com.ovi.where.domain.model.MeetupDestination

/**
 * Top-of-map labeled chip for the meetup destination.
 *
 * Visual language (shared with the group-filter + notification chips):
 *  • 52dp height, full-pill radius, white `surface` with a soft shadow
 *    and NO border — the elevation alone lifts it off the map.
 *  • A BARE leading glyph (no tinted circle behind it) + a bold
 *    `titleMedium` label, with generous padding.
 *
 * States:
 *  • **Idle** — white chip, warm-gold flag glyph, "Meetup point" label.
 *  • **Active** — filled `primary` (brand gold), white flag + `name · dist`.
 */
@Composable
fun MeetupChip(
    destination: MeetupDestination?,
    distanceText: String?,
    onIdleClick: () -> Unit,
    onActiveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val active = destination != null && destination.isActive && destination.hasValidLocation

    AnimatedContent(
        targetState = active,
        transitionSpec = {
            (fadeIn(tween(180)) togetherWith fadeOut(tween(180)))
        },
        label = "meetupChipState",
        modifier = modifier
    ) { isActive ->
        if (isActive && destination != null) {
            ActiveChip(
                name = destination.name.ifBlank { "Meetup point" },
                distanceText = distanceText,
                onClick = onActiveClick
            )
        } else {
            IdleChip(onClick = onIdleClick)
        }
    }
}

/** Idle chip — white pill, soft shadow, bare warm-gold flag glyph. */
@Composable
private fun IdleChip(onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .height(CHIP_HEIGHT)
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 8.dp,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(id = R.drawable.golf_hole_outlined),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Meetup point",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Active chip — filled brand gold, bare white flag + `name · distance`. */
@Composable
private fun ActiveChip(
    name: String,
    distanceText: String?,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .height(CHIP_HEIGHT)
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shadowElevation = 8.dp,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(id = R.drawable.golf_hole_outlined),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(8.dp))
            // Cap the name so the chip stays compact even with long places.
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 92.dp)
            )
            if (!distanceText.isNullOrBlank()) {
                Text(
                    text = " · $distanceText",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 60.dp)
                )
            }
        }
    }
}

/** Shared chip height for the top-of-map strip. */
internal val CHIP_HEIGHT = 40.dp
