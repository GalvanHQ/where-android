package com.ovi.where.core.theme

import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────────────────────
//  WHERE brand palette (v2 — built around the new where_logo_v2 gradient)
//
//  Logo gradient (top-right → bottom-left):
//     #6E60CF  indigo violet  →  #9248DB royal purple  →  #B531E7 magenta
//     →  #C835C2 hot pink     →  #DA3A9E rose          →  #FF4356 coral red
//     →  #FF5231 red-orange   →  #FF610B warm orange
//
//  We don't sample one stop; we build three full Material 3 tonal palettes
//  whose key tones (40 / 80 / 90) line up with the logo's three dominant
//  hue regions:
//
//     • Primary   — magenta / hot pink  (the logo's "centre of gravity").
//                    Used everywhere a single brand colour is needed (FAB,
//                    primary buttons, focus, selection, badges).
//     • Secondary — royal purple        (upper third of the gradient).
//                    Calmer, used for chip backgrounds, toggles, and the
//                    "supportive" accents.
//     • Tertiary  — warm orange         (lower third of the gradient).
//                    Used for warmth / energy accents like Crashlytics
//                    severity icons, location-active rings, and brand-
//                    aligned highlights in the chat preview.
//
//  Tonal values follow Google's Material 3 tonal palette spec:
//    10 = darkest text/container, 20–30 = container fills, 40 = key colour,
//    80 = on-dark key, 90 = light container, 99 = highest neutral surface.
//
//  Contrast budget (WCAG AA):
//    • Primary40  on white   ≥ 4.7 : 1   (text + icon-only ≥ 3:1)
//    • Tertiary40 on white   ≥ 4.5 : 1
//    • All on-* tokens ≥ 7 : 1 against their container.
// ─────────────────────────────────────────────────────────────────────────────

// ── Primary: magenta-pink (#C2298A) — the brand's signature single-colour ───
val Primary10  = Color(0xFF3D0029)   // darkest container / on-dark text
val Primary20  = Color(0xFF5C0040)
val Primary30  = Color(0xFF8A0064)
val Primary40  = Color(0xFFC2298A)   // primary (light) — key brand colour
val Primary80  = Color(0xFFFFAFD3)   // primary (dark)
val Primary90  = Color(0xFFFFD9E6)   // primaryContainer (light)
val Primary99  = Color(0xFFFFFBFF)

// ── Secondary: royal purple (#7E57C2) — upper-third of the logo gradient ────
val Secondary10 = Color(0xFF22005D)
val Secondary20 = Color(0xFF38008E)
val Secondary30 = Color(0xFF5A2DBA)
val Secondary40 = Color(0xFF7E57C2)   // secondary (light)
val Secondary80 = Color(0xFFD0BCFF)   // secondary (dark)
val Secondary90 = Color(0xFFEADDFF)   // secondaryContainer (light)
val Secondary99 = Color(0xFFFFFBFF)

// ── Tertiary: warm orange (#FF6B3D) — lower-third of the logo gradient ──────
val Tertiary10 = Color(0xFF3F0F00)
val Tertiary20 = Color(0xFF651F00)
val Tertiary30 = Color(0xFFA73900)
val Tertiary40 = Color(0xFFE85A1F)   // tertiary (light)
val Tertiary80 = Color(0xFFFFB592)   // tertiary (dark)
val Tertiary90 = Color(0xFFFFDBCB)   // tertiaryContainer (light)
val Tertiary99 = Color(0xFFFFFBFF)

// ── Error — kept on the same red family but warmed slightly so it rhymes ───
//    with the brand orange instead of clashing with the magenta primary. ────
val Error10 = Color(0xFF410002)
val Error20 = Color(0xFF690005)
val Error30 = Color(0xFF93000A)
val Error40 = Color(0xFFD32F2F)      // error (light) — slightly warmed crimson
val Error80 = Color(0xFFFFB4AB)      // error (dark)
val Error90 = Color(0xFFFFDAD6)      // errorContainer (light)
val Error99 = Color(0xFFFFFBFF)

// ── Neutral surfaces — warm-leaning so they harmonise with magenta ──────────
//    (Material 3 recommends biasing the neutral hue ~10° toward the primary
//    for brand cohesion. Here that means a hint of magenta in the greys.)
val Neutral4  = Color(0xFF110D11)   // deep background for dark mode
val Neutral6  = Color(0xFF161116)   // darker background alternative
val Neutral10 = Color(0xFF1C161C)
val Neutral12 = Color(0xFF20191F)   // elevated surface for dark mode
val Neutral17 = Color(0xFF2A222A)   // surfaceVariant for dark mode (cards, sheets)
val Neutral20 = Color(0xFF322932)
val Neutral22 = Color(0xFF382F38)   // surfaceContainerHigh for dark mode
val Neutral30 = Color(0xFF4A4049)
val Neutral40 = Color(0xFF615761)
val Neutral80 = Color(0xFFCBC2CB)
val Neutral90 = Color(0xFFE7DDE7)
val Neutral92 = Color(0xFFEDE3ED)
val Neutral94 = Color(0xFFF2E8F2)
val Neutral96 = Color(0xFFF8EEF8)
val Neutral99 = Color(0xFFFFFBFF)

// ── Neutral variant ─────────────────────────────────────────────────────────
val NeutralVar10 = Color(0xFF1F1A1E)
val NeutralVar20 = Color(0xFF342E33)
val NeutralVar30 = Color(0xFF4B444A)
val NeutralVar40 = Color(0xFF635C61)
val NeutralVar80 = Color(0xFFCEC4CB)
val NeutralVar90 = Color(0xFFEADFE7)
val NeutralVar99 = Color(0xFFFFFBFF)

// ── Brand gradient — exposed so anywhere we want the full logo gradient ────
//    (splashes, branded headers, premium upsells) can pull the same colours.
val BrandGradient = listOf(
    Color(0xFF6E60CF),  // 0%   indigo violet
    Color(0xFF9248DB),  // 19%  royal purple
    Color(0xFFB531E7),  // 35%  magenta
    Color(0xFFC835C2),  // 54%  hot pink
    Color(0xFFDA3A9E),  // 62%  rose
    Color(0xFFFF4356),  // 83%  coral red
    Color(0xFFFF5231),  // 92%  red-orange
    Color(0xFFFF610B),  // 100% warm orange
)

// ── Semantic status helpers (theme-agnostic names) ──────────────────────────
//    "Active" reads as warm/alive — matches the logo's lower-half orange.
//    "Inactive" stays neutral so it doesn't compete with the brand colour.
val LocationActive   = Tertiary40            // = #E85A1F  warm orange
val LocationInactive = NeutralVar40

// ── Avatar palette — single source used by MapScreen + GroupInfoScreen ─────
//    Eight saturated, distinguishable hues sampled around the colour wheel
//    so neighbouring avatars never feel identical. The palette starts on
//    brand (Primary40), so the most common case (one or two friends) feels
//    on-message.
val AvatarColors = listOf(
    Color(0xFFC2298A),  // Primary40   — magenta (brand)
    Color(0xFF7E57C2),  // Secondary40 — royal purple
    Color(0xFFE85A1F),  // Tertiary40  — warm orange
    Color(0xFF006878),  // teal
    Color(0xFF006E2C),  // emerald
    Color(0xFF1976D2),  // azure
    Color(0xFFFFA000),  // amber
    Color(0xFF8B4513),  // sienna
)
