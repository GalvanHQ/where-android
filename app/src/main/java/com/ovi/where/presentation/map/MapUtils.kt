package com.ovi.where.presentation.map

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.ovi.where.core.theme.AvatarColors


/** Pick a deterministic avatar colour for a given userId. */
fun avatarColorFor(userId: String): Color =
    AvatarColors[userId.hashCode().and(0x7FFFFFFF) % AvatarColors.size]

/**
 * Readable text/icon color for content drawn *on top of* an avatar circle.
 *
 * Avatar backgrounds span a wide saturation/brightness range (deep gold,
 * azure, teal, magenta, …). Previously initials were drawn in
 * `colorScheme.surface`, which is near-black in dark mode and lost contrast
 * on the darker avatar hues. Picking white vs. near-black from the
 * background's perceptual luminance keeps initials legible on every hue in
 * both themes (WCAG-safe: all current avatar colors fall well below the
 * 0.5 luminance threshold, so they resolve to white).
 */
fun avatarContentColorFor(background: Color): Color =
    if (background.luminance() > 0.5f) Color(0xFF1A1A1A) else Color.White

