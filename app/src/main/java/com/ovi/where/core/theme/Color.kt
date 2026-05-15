package com.ovi.where.core.theme

import androidx.compose.ui.graphics.Color

// ── Primary: #5170FF (indigo-blue) — M3 tonal palette ───────────────────────
val Primary10 = Color(0xFF001452)
val Primary20 = Color(0xFF002984)
val Primary30 = Color(0xFF003DB8)
val Primary40 = Color(0xFF5170FF)   // primary (light)
val Primary80 = Color(0xFFB6C4FF)   // primary (dark)
val Primary90 = Color(0xFFDCE1FF)   // primaryContainer (light)
val Primary99 = Color(0xFFFEFBFF)

// ── Secondary: desaturated teal-blue — M3 tonal palette ─────────────────────
val Secondary10 = Color(0xFF001F26)
val Secondary20 = Color(0xFF00363F)
val Secondary30 = Color(0xFF004E5A)
val Secondary40 = Color(0xFF006878)   // secondary (light)
val Secondary80 = Color(0xFF5DD5F0)   // secondary (dark)
val Secondary90 = Color(0xFFB2EBFA)   // secondaryContainer (light)
val Secondary99 = Color(0xFFF5FDFF)

// ── Tertiary: warm rose-violet — M3 tonal palette ───────────────────────────
val Tertiary10 = Color(0xFF3B0037)
val Tertiary20 = Color(0xFF560057)
val Tertiary30 = Color(0xFF731B72)
val Tertiary40 = Color(0xFF8E3A8C)   // tertiary (light)
val Tertiary80 = Color(0xFFEBB0E8)   // tertiary (dark)
val Tertiary90 = Color(0xFFFFD6F9)   // tertiaryContainer (light)
val Tertiary99 = Color(0xFFFFFBFF)

// ── Error — M3 tonal palette ─────────────────────────────────────────────────
val Error10 = Color(0xFF410002)
val Error20 = Color(0xFF690005)
val Error30 = Color(0xFF93000A)
val Error40 = Color(0xFFBA1A1A)      // error (light)
val Error80 = Color(0xFFFFB4AB)      // error (dark)
val Error90 = Color(0xFFFFDAD6)      // errorContainer (light)
val Error99 = Color(0xFFFFFBFF)

// ── Neutral surfaces ─────────────────────────────────────────────────────────
val Neutral4  = Color(0xFF0F0F12)   // deep background for dark mode
val Neutral6  = Color(0xFF141417)   // darker background alternative
val Neutral10 = Color(0xFF1B1B1F)
val Neutral12 = Color(0xFF1F1F23)   // elevated surface for dark mode
val Neutral17 = Color(0xFF282830)   // surfaceVariant for dark mode (cards, sheets)
val Neutral20 = Color(0xFF303034)
val Neutral22 = Color(0xFF353539)   // surfaceContainerHigh for dark mode
val Neutral30 = Color(0xFF47464A)
val Neutral40 = Color(0xFF5F5E62)
val Neutral80 = Color(0xFFC8C6CA)
val Neutral90 = Color(0xFFE4E1E6)
val Neutral92 = Color(0xFFEAE7EC)
val Neutral94 = Color(0xFFF0EDF1)
val Neutral96 = Color(0xFFF6F2F7)
val Neutral99 = Color(0xFFFEFBFF)

// ── Neutral variant ──────────────────────────────────────────────────────────
val NeutralVar10 = Color(0xFF1B1B22)
val NeutralVar20 = Color(0xFF303038)
val NeutralVar30 = Color(0xFF46464F)
val NeutralVar40 = Color(0xFF5E5D67)
val NeutralVar80 = Color(0xFFC7C5D0)
val NeutralVar90 = Color(0xFFE3E1EC)
val NeutralVar99 = Color(0xFFFEFBFF)

// ── Semantic status helpers (theme-agnostic names) ────────────────────────────
val LocationActive   = Color(0xFF006878)   // = Secondary40 — active location indicator
val LocationInactive = Color(0xFF5E5D67)   // = NeutralVar40 — inactive location indicator

// ── Avatar palette — single source used by MapScreen + GroupDetailsScreen ───
// 8 entries with distributed hues at 40-tone level for visual distinguishability
val AvatarColors = listOf(
    Color(0xFF5170FF),  // Primary40 — indigo-blue (hue ~228°)
    Color(0xFF006878),  // Secondary40 — teal (hue ~190°)
    Color(0xFF8E3A8C),  // Tertiary40 — rose-violet (hue ~301°)
    Color(0xFF6B5E00),  // olive-gold (hue ~52°)
    Color(0xFF8B4513),  // warm brown-orange (hue ~28°)
    Color(0xFF006E2C),  // green (hue ~150°)
    Color(0xFFBA1A1A),  // red (hue ~0°)
    Color(0xFF006491),  // cerulean blue (hue ~210°)
)
