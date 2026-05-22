package com.ovi.where.presentation.map.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ovi.where.domain.model.MeetupDestination

/**
 * Top-of-map labeled chip for the meetup destination.
 *
 * Shares the same chip language as the group filter chip: 40dp height, 50%
 * radius, 28dp leading icon/avatar, single-line label. Two states:
 *
 *  • **Idle** — outlined surface chip labeled `Meetup point` with a flag
 *    icon. Same border, elevation, and typography as the filter chip.
 *  • **Active** — same chip frame but filled primary, label becomes
 *    `name · distance`. Filling instead of restyling keeps the two chips
 *    visually identical in size and rhythm.
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

/**
 * Idle chip — outlined surface, identical to the group filter chip in
 * height, padding, border, and elevation. Only the leading bubble color
 * (primary tint) and label differ.
 */
@Composable
private fun IdleChip(onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(start = 6.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 28dp leading bubble — same size as the filter chip's avatar
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Flag,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(15.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Meetup point",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Active chip — same frame as the idle chip, filled primary instead of
 * outlined. Container height + padding match the filter chip exactly so
 * the two chips line up as a coordinated strip.
 */
@Composable
private fun ActiveChip(
    name: String,
    distanceText: String?,
    onClick: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    Surface(
        modifier = Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(50),
        color = primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shadowElevation = 4.dp,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(start = 6.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // White-on-tint inset bubble — visually equivalent to the idle
            // chip's primary-tinted bubble, just colored for the filled chip.
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.20f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Flag,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(15.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            // Cap the name so the chip stays compact even with long places.
            Text(
                text = name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 90.dp)
            )
            if (!distanceText.isNullOrBlank()) {
                Text(
                    text = " · $distanceText",
                    style = MaterialTheme.typography.labelLarge,
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
