package com.ovi.where.core.theme

import androidx.compose.ui.graphics.Color

// ── Primary: #e80c88 (vivid pink) — M3 tonal palette ───────────────────────
val Pink10  = Color(0xFF3E0027)
val Pink20  = Color(0xFF65003F)
val Pink30  = Color(0xFF8C0059)
val Pink40  = Color(0xFFB5006F)   // primary (light)
val Pink80  = Color(0xFFFFB0D1)   // primary (dark)
val Pink90  = Color(0xFFFFD8EE)   // primaryContainer (light)
val Pink95  = Color(0xFFFFECF5)
val Pink99  = Color(0xFFFFF8F9)

// ── Secondary: #12823a (deep green) — M3 tonal palette ──────────────────────
val Green10  = Color(0xFF002109)
val Green20  = Color(0xFF00390F)
val Green30  = Color(0xFF005220)
val Green40  = Color(0xFF006E2C)   // secondary (light)
val Green80  = Color(0xFF83D98A)   // secondary (dark)
val Green90  = Color(0xFF9EF5A5)   // secondaryContainer (light)
val Green95  = Color(0xFFC5FFD1)

// ── Tertiary: purple — M3 tonal palette ─────────────────────────────────────
val Purple10  = Color(0xFF1E0058)
val Purple20  = Color(0xFF33008A)
val Purple30  = Color(0xFF4D00B6)
val Purple40  = Color(0xFF6950A1)  // tertiary (light)
val Purple80  = Color(0xFFCFBCFF)  // tertiary (dark)
val Purple90  = Color(0xFFEADDFF)  // tertiaryContainer (light)

// ── Neutral surfaces ─────────────────────────────────────────────────────────
val Neutral4   = Color(0xFF0F0D0E)
val Neutral6   = Color(0xFF161214)
val Neutral12  = Color(0xFF221E20)
val Neutral17  = Color(0xFF2D292B)
val Neutral22  = Color(0xFF383436)
val Neutral24  = Color(0xFF3C3840)
val Neutral87  = Color(0xFFDDD8DA)
val Neutral92  = Color(0xFFEBE6E8)
val Neutral94  = Color(0xFFF1ECF0)
val Neutral96  = Color(0xFFF7F1F5)
val Neutral99  = Color(0xFFFFF8F9)

// ── Neutral variant surfaces ─────────────────────────────────────────────────
val NeutralVar30  = Color(0xFF4D3F47)
val NeutralVar50  = Color(0xFF7F6B77)
val NeutralVar60  = Color(0xFF9A8490)
val NeutralVar80  = Color(0xFFD6BCCA)
val NeutralVar90  = Color(0xFFF3D9E5)

// ── Error ────────────────────────────────────────────────────────────────────
val Red10  = Color(0xFF410002)
val Red40  = Color(0xFFBA1A1A)
val Red80  = Color(0xFFFFB4AB)
val Red90  = Color(0xFFFFDAD6)

// ── Semantic status helpers (theme-agnostic names) ────────────────────────────
// Use MaterialTheme.colorScheme.tertiary for "active" state in composables
val LocationActive   = Color(0xFF006E2C)   // = Green40 — used in Canvas (non-composable)
val LocationInactive = Color(0xFF9A8490)   // = NeutralVar60

// ── Avatar palette — single source used by MapScreen + GroupDetailsScreen ───
val AvatarColors = listOf(
    Color(0xFFB5006F),  // Pink40 — primary
    Color(0xFF006E2C),  // Green40 — secondary
    Color(0xFF6950A1),  // Purple40 — tertiary
    Color(0xFF006A60),  // teal
    Color(0xFF8B4000),  // amber-brown
    Color(0xFF006491),  // blue
    Color(0xFF6B538C),  // mauve
    Color(0xFF1D6A2A),  // dark green
)

// ── Brand constants ──────────────────────────────────────────────────────────
val GoogleBlue = Color(0xFF4285F4)
