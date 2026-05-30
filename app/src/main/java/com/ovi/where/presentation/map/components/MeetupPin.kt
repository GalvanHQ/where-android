package com.ovi.where.presentation.map.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Canonical meetup pin — a circular badge with a white border and a flag
 * glyph in the center.
 *
 * The accent defaults to the brand's tertiary (teal) — the meetup
 * "destination" concept gets its own cool accent, kept distinct from the
 * yellow primary (brand / buttons) and the warm orange "live" cue. The
 * previous hard-coded Color.Green was off-brand.
 *
 * This circular form mirrors the Life360 friend pins and renders reliably
 * at any size (the old Canvas teardrop didn't).
 */
@Composable
fun MeetupPin(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    accentColor: Color = MaterialTheme.colorScheme.tertiary
) {
    Box(
        modifier = modifier
            .size(size)
            .shadow(elevation = 6.dp, shape = CircleShape)
            .clip(CircleShape)
            .background(accentColor)
            .border(width = 2.dp, color = Color.White, shape = CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Flag,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(size * 0.45f)
        )
    }
}
