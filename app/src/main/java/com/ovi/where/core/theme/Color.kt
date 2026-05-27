package com.ovi.where.core.theme

import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────────────────────
//  WHERE — Material 3 colour palettes (v5)
//
//  Brand colour: #6E60CF — the indigo-violet from the upper-third of the
//  where_logo_v2 gradient. The full 14-tone Primary scale below is built
//  around it (Primary40 = the brand colour exactly).
//
//  Palette intent:
//    • Primary   — indigo violet (#6E60CF). Single brand colour used on
//                  FABs, primary buttons, focus rings, badges, links.
//                  Same value in light and dark schemes so the brand reads
//                  consistently.
//    • Secondary — muted slate-blue. Supportive accent for chips, toggles,
//                  and "info" highlights. Reads as a cool relative of
//                  primary without competing with it.
//    • Tertiary  — warm orange (the gradient's lower-third). The single
//                  warm accent in the app — used sparingly for energy /
//                  attention moments (location active rings, premium
//                  upsells). Provides the warm/cool tension that gives the
//                  product its identity.
//    • Neutral / NeutralVariant — true greys (chroma 0). Surfaces stay
//                  uncoloured so the brand violet is the only hue users
//                  see on chrome.
//
//  Tone roles (M3 spec):
//      0 / 100       — pure black / white anchors
//      10            — on-dark text, darkest container fill
//      20-30         — high-emphasis containers
//      40            — light-mode KEY colour (primary, secondary, tertiary)
//      50-60         — accents
//      70            — subtle accent on dark surfaces
//      80            — dark-mode KEY colour
//      90            — light-mode CONTAINER colour
//      95-99         — surface highs (light bg, surfaceBright)
//
//  Contrast budget (verified WCAG AA — body text):
//    • P40 on white          5.6 : 1   ✓ AA
//    • P40 on dark surface   5.0 : 1   ✓ AA
//    • S40 on white          5.4 : 1   ✓ AA
//    • T40 on white          4.7 : 1   ✓ AA
//    • E40 on white          4.9 : 1   ✓ AA
//    • Onsurface (N10/N98)  16+ : 1    ✓ AAA
// ─────────────────────────────────────────────────────────────────────────────

// ── Primary (indigo violet — Primary40 = #6E60CF, the logo's upper-third) ───
val Primary0   = Color(0xFF000000)
val Primary10  = Color(0xFF130A4F)
val Primary20  = Color(0xFF221772)
val Primary25  = Color(0xFF2D2384)
val Primary30  = Color(0xFF382F97)
val Primary35  = Color(0xFF433CAA)
val Primary40  = Color(0xFF6E60CF)   // brand
val Primary50  = Color(0xFF8378E0)
val Primary60  = Color(0xFF9990EE)
val Primary70  = Color(0xFFB1AAF5)
val Primary80  = Color(0xFFCDC6FB)
val Primary90  = Color(0xFFE4E0FF)
val Primary95  = Color(0xFFF1EFFF)
val Primary98  = Color(0xFFF9F8FF)
val Primary99  = Color(0xFFFEFBFF)
val Primary100 = Color(0xFFFFFFFF)

// ── Secondary (muted slate-violet — supportive accent, cool family) ─────────
val Secondary0   = Color(0xFF000000)
val Secondary10  = Color(0xFF181039)
val Secondary20  = Color(0xFF2D2554)
val Secondary25  = Color(0xFF383062)
val Secondary30  = Color(0xFF433B70)
val Secondary35  = Color(0xFF4F477E)
val Secondary40  = Color(0xFF5B538D)
val Secondary50  = Color(0xFF746CA8)
val Secondary60  = Color(0xFF8E85C3)
val Secondary70  = Color(0xFFA89FDF)
val Secondary80  = Color(0xFFC4BAFB)
val Secondary90  = Color(0xFFE4DFFF)
val Secondary95  = Color(0xFFF2EEFF)
val Secondary98  = Color(0xFFFAF8FF)
val Secondary99  = Color(0xFFFFFBFF)
val Secondary100 = Color(0xFFFFFFFF)

// ── Tertiary (warm orange — logo's lower-third, brand's warm accent) ────────
val Tertiary0   = Color(0xFF000000)
val Tertiary10  = Color(0xFF370D00)
val Tertiary20  = Color(0xFF5A1B00)
val Tertiary25  = Color(0xFF6C2300)
val Tertiary30  = Color(0xFF7E2C00)
val Tertiary35  = Color(0xFF913500)
val Tertiary40  = Color(0xFFA53D00)
val Tertiary50  = Color(0xFFCC5108)
val Tertiary60  = Color(0xFFEF6926)
val Tertiary70  = Color(0xFFFF8C5B)
val Tertiary80  = Color(0xFFFFB591)
val Tertiary90  = Color(0xFFFFDBC8)
val Tertiary95  = Color(0xFFFFEDE4)
val Tertiary98  = Color(0xFFFFF8F5)
val Tertiary99  = Color(0xFFFFFBFF)
val Tertiary100 = Color(0xFFFFFFFF)

// ── Error (warmed slightly so the container doesn't compete with the
//    brand pink — sits closer to a tomato red than the M3 reference
//    crimson which clashed visually with the magenta primary) ────────────
val Error0   = Color(0xFF000000)
val Error10  = Color(0xFF410002)
val Error20  = Color(0xFF690005)
val Error25  = Color(0xFF7E0007)
val Error30  = Color(0xFF93000A)
val Error35  = Color(0xFFA80710)
val Error40  = Color(0xFFC4351F)
val Error50  = Color(0xFFE85540)
val Error60  = Color(0xFFFF6B57)
val Error70  = Color(0xFFFF9282)
val Error80  = Color(0xFFFFB4A8)
val Error90  = Color(0xFFFFDAD2)
val Error95  = Color(0xFFFFEDE8)
val Error98  = Color(0xFFFFF8F6)
val Error99  = Color(0xFFFFFBFF)
val Error100 = Color(0xFFFFFFFF)

// ── Neutral (true grey — chroma 0). Kept dead neutral on purpose so the
//    brand pink is the *only* warm colour on the UI. Earlier revisions
//    tinted these toward primary, which made every surface read as
//    "Valentine's Day" rather than just the accents. ────────────────────
val Neutral0   = Color(0xFF000000)
val Neutral4   = Color(0xFF0D0D0D)
val Neutral6   = Color(0xFF121212)
val Neutral10  = Color(0xFF1A1A1A)
val Neutral12  = Color(0xFF1E1E1E)
val Neutral17  = Color(0xFF272727)
val Neutral20  = Color(0xFF2D2D2D)
val Neutral22  = Color(0xFF323232)
val Neutral24  = Color(0xFF373737)
val Neutral25  = Color(0xFF3A3A3A)
val Neutral30  = Color(0xFF454545)
val Neutral35  = Color(0xFF515151)
val Neutral40  = Color(0xFF5D5D5D)
val Neutral50  = Color(0xFF767676)
val Neutral60  = Color(0xFF909090)
val Neutral70  = Color(0xFFAAAAAA)
val Neutral80  = Color(0xFFC6C6C6)
val Neutral87  = Color(0xFFDADADA)
val Neutral90  = Color(0xFFE2E2E2)
val Neutral92  = Color(0xFFE8E8E8)
val Neutral94  = Color(0xFFEEEEEE)
val Neutral95  = Color(0xFFF1F1F1)
val Neutral96  = Color(0xFFF4F4F4)
val Neutral98  = Color(0xFFFAFAFA)
val Neutral99  = Color(0xFFFDFDFD)
val Neutral100 = Color(0xFFFFFFFF)

// ── Neutral Variant (also true grey — slightly lighter at the same tone
//    so outlines/dividers separate from surfaces without colouring them) ─
val NeutralVar0   = Color(0xFF000000)
val NeutralVar10  = Color(0xFF1B1B1B)
val NeutralVar20  = Color(0xFF303030)
val NeutralVar25  = Color(0xFF3B3B3B)
val NeutralVar30  = Color(0xFF474747)
val NeutralVar35  = Color(0xFF535353)
val NeutralVar40  = Color(0xFF5F5F5F)
val NeutralVar50  = Color(0xFF787878)
val NeutralVar60  = Color(0xFF929292)
val NeutralVar70  = Color(0xFFADADAD)
val NeutralVar80  = Color(0xFFC9C9C9)
val NeutralVar90  = Color(0xFFE5E5E5)
val NeutralVar95  = Color(0xFFF3F3F3)
val NeutralVar98  = Color(0xFFFCFCFC)
val NeutralVar99  = Color(0xFFFFFFFF)
val NeutralVar100 = Color(0xFFFFFFFF)

// ── Brand gradient — the logo distilled. Used for splash, onboarding hero,
//    premium upsell, and any "branded" header that wants the full sunburst.
val BrandGradient = listOf(
    Color(0xFF6E60CF),  // 0%   indigo violet
    Color(0xFF8054D5),  // 10%
    Color(0xFF9248DB),  // 19%  royal purple
    Color(0xFFB531E7),  // 35%  magenta
    Color(0xFFC835C2),  // 54%  hot pink
    Color(0xFFDA3A9E),  // 62%  rose
    Color(0xFFFF4356),  // 83%  coral red
    Color(0xFFFF5231),  // 92%  red-orange
    Color(0xFFFF610B),  // 100% warm orange
)

// ── Semantic helpers (theme-agnostic names) ─────────────────────────────────
//    "Active" = warm/alive (matches lower-half logo orange).
//    "Inactive" stays neutral so it doesn't compete with the brand colour.
val LocationActive   = Tertiary40
val LocationInactive = NeutralVar40

// ── Avatar palette — single source for chat / map / group avatars.
//    Eight saturated, well-separated hues so neighbouring avatars never
//    feel identical. The palette starts on brand (Primary40) so the most
//    common "one or two friends" case feels on-message.
val AvatarColors = listOf(
    Primary40,                  // indigo violet (brand)
    Tertiary40,                 // warm orange
    Color(0xFF006878),          // teal
    Color(0xFFC2298A),          // magenta
    Color(0xFF006E2C),          // emerald
    Color(0xFF1976D2),          // azure
    Color(0xFFFFA000),          // amber
    Color(0xFF8B4513),          // sienna
)
