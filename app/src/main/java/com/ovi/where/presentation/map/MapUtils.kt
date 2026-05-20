package com.ovi.where.presentation.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.ovi.where.core.theme.AvatarColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.tan


/** Pick a deterministic avatar colour for a given userId. */
fun avatarColorFor(userId: String): Color =
    AvatarColors[userId.hashCode().and(0x7FFFFFFF) % AvatarColors.size]

// ── Avatar Marker Overlay ─────────────────────────────────────────────────────

