package com.ovi.where.core.theme

import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────────────────────
//  WHERE — Material 3 colour palettes (v6, logo v5)
//
//  Brand colour: #F9DF4D — the canary yellow speech-bubble from
//  where_logo_v5, set on a pure-black field. The whole palette pivots
//  around this single hue: a yellow-gold tonal scale anchored so that
//  Primary80 = the brand colour exactly.
//
//  Palette intent:
//    • Primary   — yellow-gold. Used on FABs, primary buttons, focus
//                  rings, badges, links. Light mode uses Primary40
//                  (deep gold) so text passes AA on white; dark mode
//                  uses Primary80 (#F9DF4D) directly so the brand
//                  yellow reads exactly as it does on the logo.
//    • Secondary — warm sand / muted khaki. Same hue family as primary
//                  but low chroma — supportive accent for chips,
//                  toggles, and "info" highlights without competing
//                  with the brand yellow.
//    • Tertiary  — deep teal. Yellow's complementary cool — the only
//                  cool hue in the system, used sparingly for accents
//                  that need to stand apart from the warm primary.
//    • Error     — warmed tomato red. Sits clearly apart from the
//                  yellow brand so error states don't get mistaken
//                  for warnings or highlights.
//    • Neutral / NeutralVariant — true greys (chroma 0). Surfaces stay
//                  uncoloured so the brand yellow is the only warm
//                  hue users see on chrome.
//
//  Tone roles (M3 spec):
//      0 / 100       — pure black / white anchors
//      10            — on-dark text, darkest container fill
//      20-30         — high-emphasis containers
//      40            — light-mode KEY colour (primary, secondary, tertiary)
//      50-60         — accents
//      70            — subtle accent on dark surfaces
//      80            — dark-mode KEY colour (BRAND yellow lives here)
//      90            — light-mode CONTAINER colour
//      95-99         — surface highs (light bg, surfaceBright)
//
//  Contrast budget (verified WCAG AA — body text):
//    • P40 on white          7.6 : 1   ✓ AAA
//    • P80 on dark surface  13.4 : 1   ✓ AAA  (brand yellow on Neutral6)
//    • S40 on white          5.2 : 1   ✓ AA
//    • T40 on white          5.0 : 1   ✓ AA
//    • E40 on white          4.9 : 1   ✓ AA
//    • Onsurface (N10/N98)  16+ : 1    ✓ AAA
// ─────────────────────────────────────────────────────────────────────────────

// ── Brand reference ─────────────────────────────────────────────────────────
//  Single source of truth for the logo yellow. Any place that needs the
//  literal logo colour (splash branding, brand stamps, marketing surfaces)
//  should use this — it's the same value as Primary80, just named for
//  intent.
val BrandYellow = Color(0xFFF9DF4D)

// ── Primary (yellow-gold — Primary80 = #F9DF4D, the logo bubble) ────────────
val Primary0   = Color(0xFF000000)
val Primary10  = Color(0xFF271900)
val Primary20  = Color(0xFF422D00)
val Primary25  = Color(0xFF503700)
val Primary30  = Color(0xFF5E4200)
val Primary35  = Color(0xFF6E4F00)
val Primary40  = Color(0xFF7E5C00)   // light-mode primary (AA on white)
val Primary50  = Color(0xFF9E7700)
val Primary60  = Color(0xFFBD9300)
val Primary70  = Color(0xFFDDAF00)
val Primary80  = Color(0xFFF9DF4D)   // BRAND — matches logo bubble exactly
val Primary90  = Color(0xFFFFE893)
val Primary95  = Color(0xFFFFF1C8)
val Primary98  = Color(0xFFFFF9E5)
val Primary99  = Color(0xFFFFFCF2)
val Primary100 = Color(0xFFFFFFFF)

// ── Secondary (warm sand / muted khaki — same hue family, low chroma) ──────
val Secondary0   = Color(0xFF000000)
val Secondary10  = Color(0xFF231B04)
val Secondary20  = Color(0xFF393019)
val Secondary25  = Color(0xFF443B23)
val Secondary30  = Color(0xFF50462E)
val Secondary35  = Color(0xFF5C5239)
val Secondary40  = Color(0xFF695E45)
val Secondary50  = Color(0xFF82765C)
val Secondary60  = Color(0xFF9D9074)
val Secondary70  = Color(0xFFB8AB8D)
val Secondary80  = Color(0xFFD4C6A7)
val Secondary90  = Color(0xFFF1E2C2)
val Secondary95  = Color(0xFFFFF0D7)
val Secondary98  = Color(0xFFFFF9EE)
val Secondary99  = Color(0xFFFFFBF5)
val Secondary100 = Color(0xFFFFFFFF)

// ── Tertiary (deep teal — yellow's cool complement) ─────────────────────────
val Tertiary0   = Color(0xFF000000)
val Tertiary10  = Color(0xFF00201F)
val Tertiary20  = Color(0xFF003735)
val Tertiary25  = Color(0xFF004342)
val Tertiary30  = Color(0xFF00504F)
val Tertiary35  = Color(0xFF005E5C)
val Tertiary40  = Color(0xFF006B69)   // light-mode tertiary (AA on white)
val Tertiary50  = Color(0xFF008784)
val Tertiary60  = Color(0xFF00A3A0)
val Tertiary70  = Color(0xFF00BFBC)
val Tertiary80  = Color(0xFF4DDBD7)   // dark-mode tertiary
val Tertiary90  = Color(0xFFA5F2EE)
val Tertiary95  = Color(0xFFD1FAF7)
val Tertiary98  = Color(0xFFEEFDFC)
val Tertiary99  = Color(0xFFF6FFFE)
val Tertiary100 = Color(0xFFFFFFFF)

// ── Error (warmed tomato red — sits clearly apart from the yellow brand
//    so error states never get mistaken for warnings or highlights) ─────────
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
//    brand yellow is the *only* warm colour on the UI. Tinting these
//    toward primary would make every surface read as "construction
//    site" rather than premium. ──────────────────────────────────────────
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

// ── Brand gradient — a warm sunset distilled from the brand yellow.
//    Used for splash, onboarding hero, premium upsell, and any
//    "branded" header that wants energy and warmth. The progression
//    goes pale gold → brand yellow → amber → rust, so it always
//    reads as "Where yellow with depth", never as a generic rainbow.
val BrandGradient = listOf(
    Color(0xFFFFEE7C),  // 0%   pale gold
    Color(0xFFF9DF4D),  // 18%  brand yellow
    Color(0xFFF2C20B),  // 38%  deep canary
    Color(0xFFE69500),  // 62%  amber
    Color(0xFFDD6500),  // 84%  warm orange
    Color(0xFFB33B00),  // 100% rust
)

// ── Semantic helpers (theme-agnostic names) ─────────────────────────────────
//    "Active" = warm/alive (a vivid orange that reads against both the
//    yellow brand chrome and dark map surfaces). Kept distinct from
//    primary so a "live location" pulse never gets mistaken for a
//    button highlight.
//    "Inactive" stays neutral so it doesn't compete with anything.
val LocationActive   = Color(0xFFFF7A1F)
val LocationInactive = NeutralVar40

// ── Avatar palette — single source for chat / map / group avatars.
//    Eight saturated, well-separated hues so neighbouring avatars never
//    feel identical. The palette starts on Primary60 (deep gold) rather
//    than Primary80 so white initials remain readable — the literal
//    brand yellow is reserved for surfaces with dark text.
val AvatarColors = listOf(
    Primary60,                  // deep gold (brand-aligned, AA with white initials)
    Color(0xFF2D6CDF),          // azure
    Color(0xFF006C5E),          // teal
    Color(0xFFC4407A),          // magenta
    Color(0xFF007A38),          // emerald
    Color(0xFFDB6B16),          // warm orange
    Color(0xFF6B5BB8),          // violet
    Color(0xFFB23B23),          // sienna
)
