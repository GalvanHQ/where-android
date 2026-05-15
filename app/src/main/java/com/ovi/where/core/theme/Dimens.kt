package com.ovi.where.core.theme

import androidx.compose.ui.unit.dp

object Dimens {
    // ── Spacing ───────────────────────────────────────────────────────────────
    val spaceNone     = 0.dp
    val spaceXSmall   = 2.dp
    val spaceSmall    = 4.dp
    val spaceMedium   = 8.dp
    val spaceLarge    = 16.dp
    val spaceXLarge   = 24.dp
    val space2XLarge  = 32.dp
    val space3XLarge  = 48.dp

    // ── Corner radii (mirrors WhereShapes in Theme.kt) ───────────────────────
    val cornerExtraSmall = 6.dp
    val cornerSmall      = 12.dp
    val cornerMedium     = 16.dp
    val cornerLarge      = 24.dp
    val cornerExtraLarge = 32.dp
    val cornerRound      = 50.dp

    // ── Buttons ───────────────────────────────────────────────────────────────
    val buttonHeight      = 56.dp
    val buttonHeightSmall = 48.dp
    val buttonHeightLarge = 64.dp

    // ── Icons ─────────────────────────────────────────────────────────────────
    val iconSizeSmall    = 16.dp
    val iconSizeMedium   = 24.dp
    val iconSizeLarge    = 32.dp
    val iconSizeXLarge   = 48.dp
    val iconSizeXXLarge  = 72.dp   // onboarding feature icons

    // ── Avatars / circles ────────────────────────────────────────────────────
    val avatarSizeSmall   = 32.dp
    val avatarSizeMedium  = 48.dp
    val avatarSizeLarge   = 64.dp
    val avatarSizeXLarge  = 96.dp
    val avatarCircle      = 120.dp  // empty-state / onboarding hero circle

    // ── Cards ─────────────────────────────────────────────────────────────────
    val cardElevation       = 2.dp
    val cardElevationSubtle = 1.dp
    val cardElevationHigh   = 6.dp

    // ── Strokes / dividers ────────────────────────────────────────────────────
    val strokeWidthThin = 2.dp
    val dividerThickness = 1.dp

    // ── Map markers ───────────────────────────────────────────────────────────
    val markerRadius          = 20.dp   // radius of member circle on map
    val destinationMarkerSize = 48.dp

    // ── Miscellaneous ──────────────────────────────────────────────────────────
    val settingIconContainer = 40.dp  // icon container in settings rows
    val badgeIconSize        = 18.dp  // camera / edit icon on profile avatar
    val shimmerBarHeightL    = 16.dp  // tall shimmer placeholder bar
    val shimmerBarHeightS    = 12.dp  // short shimmer placeholder bar
    val memberListHeight     = 160.dp // constrained height of map member list

    // ── Splash / onboarding ───────────────────────────────────────────────────
    val splashIconSize         = 100.dp  // animated icon on splash screen
    val indicatorWidthSelected = 24.dp   // pager indicator active width
    val indicatorWidthIdle     = 8.dp    // pager indicator idle width
    val indicatorHeight        = 8.dp    // pager indicator height
    val logoSize               = 64.dp   // app logo on onboarding screens
    val indicatorIdle          = 8.dp    // onboarding page indicator idle size
    val indicatorActive        = 24.dp   // onboarding page indicator active pill width
    val placeholderMinSize     = 120.dp  // image placeholder minimum dimension
    val placeholderIconSize    = 48.dp   // icon inside image placeholder

    // ── Dense icon sizes ─────────────────────────────────────────────────────
    val iconSizeXSmall = 12.dp  // admin badge, dense chip icons
}
