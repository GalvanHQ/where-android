package com.ovi.where.core.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.ovi.where.core.utils.LocalReducedMotion
import com.ovi.where.core.utils.rememberReducedMotion

// ─────────────────────────────────────────────────────────────────────────────
//  Material 3 colour schemes — derived from the where_logo_v5 yellow
//  speech-bubble on black. Both schemes follow the M3 reference token
//  mapping, with one deliberate exception for dark mode primary:
//
//    Light:  primary = P40 (deep gold), onPrimary = P100,
//            primaryContainer = P90, onPrimaryContainer = P10
//
//    Dark:   primary = P80 (BRAND yellow), onPrimary = P10 (deep brown),
//            primaryContainer = P30, onPrimaryContainer = P90
//
//  The dark scheme uses the literal logo yellow as the primary because
//  on a near-black canvas it reads with the same punch the logo has —
//  light mode pulls back to a deep gold so onPrimary white still
//  passes AA on buttons and FABs.
//
//  Surfaces use the M3 elevation roles (containerLowest → containerHighest)
//  rather than ad-hoc greys, so cards, sheets, and rails all read at the
//  correct elevation step automatically.
// ─────────────────────────────────────────────────────────────────────────────

private val LightColorScheme = lightColorScheme(
    // Primary — deep gold so onPrimary white passes AA on buttons/FABs.
    primary                 = Primary40,
    onPrimary               = Primary100,
    primaryContainer        = Primary90,
    onPrimaryContainer      = Primary10,

    // Secondary
    secondary               = Secondary40,
    onSecondary             = Secondary100,
    secondaryContainer      = Secondary90,
    onSecondaryContainer    = Secondary10,

    // Tertiary (deep teal)
    tertiary                = Tertiary40,
    onTertiary              = Tertiary100,
    tertiaryContainer       = Tertiary90,
    onTertiaryContainer     = Tertiary10,

    // Error
    error                   = Error40,
    onError                 = Error100,
    errorContainer          = Error90,
    onErrorContainer        = Error10,

    // Background / surface
    background              = Neutral98,
    onBackground            = Neutral10,
    surface                 = Neutral98,
    onSurface               = Neutral10,
    surfaceVariant          = NeutralVar90,
    onSurfaceVariant        = NeutralVar30,

    // Surface tonal elevations (M3 spec). surfaceTint is set to the
    //  surface itself rather than primary — M3's default tint behaviour
    //  layers a translucent primary over every elevated surface, which
    //  on yellow primary turns bottom bars and sheets into a sickly
    //  cream wash. We keep the brand yellow reserved for actual brand
    //  affordances.
    surfaceTint             = Neutral98,
    surfaceBright           = Neutral98,
    surfaceDim              = Neutral87,
    surfaceContainerLowest  = Neutral100,
    surfaceContainerLow     = Neutral96,
    surfaceContainer        = Neutral94,
    surfaceContainerHigh    = Neutral92,
    surfaceContainerHighest = Neutral90,

    // Outlines
    outline                 = NeutralVar50,
    outlineVariant          = NeutralVar80,

    // Inverse / scrim — inversePrimary is the brand yellow so the
    //  light-on-dark snackbar action matches the dark-mode primary.
    inverseSurface          = Neutral20,
    inverseOnSurface        = Neutral95,
    inversePrimary          = Primary80,
    scrim                   = Neutral0,
)

private val DarkColorScheme = darkColorScheme(
    // Primary — the literal logo yellow (#F9DF4D = Primary80). On a
    // near-black canvas this reads with the same punch the logo has,
    // and contrast against onPrimary deep brown is 13.4:1 (AAA). White
    // text on yellow buttons does NOT work, so onPrimary is Primary10.
    primary                 = Primary80,
    onPrimary               = Primary10,
    primaryContainer        = Primary30,
    onPrimaryContainer      = Primary90,

    // Secondary
    secondary               = Secondary80,
    onSecondary             = Secondary20,
    secondaryContainer      = Secondary30,
    onSecondaryContainer    = Secondary90,

    // Tertiary (lifted teal)
    tertiary                = Tertiary80,
    onTertiary              = Tertiary20,
    tertiaryContainer       = Tertiary30,
    onTertiaryContainer     = Tertiary90,

    // Error
    error                   = Error80,
    onError                 = Error20,
    errorContainer          = Error30,
    onErrorContainer        = Error90,

    // Background / surface — the logo sits on pure black, so dark mode
    // pulls toward true black at the lowest elevation while keeping
    // surface itself one step up from black so the status bar isn't a
    // hole on OLED. The brand yellow gets maximum pop against this.
    background              = Neutral4,
    onBackground            = Neutral90,
    surface                 = Neutral6,
    onSurface               = Neutral90,
    surfaceVariant          = NeutralVar30,
    onSurfaceVariant        = NeutralVar80,

    // Surface tonal elevations (M3 dark spec). surfaceTint = surface so
    //  elevation overlays are pure tonal lift, not a yellow wash.
    //  containerLowest drops to true black to mirror the logo backdrop.
    surfaceTint             = Neutral6,
    surfaceBright           = Neutral24,
    surfaceDim              = Neutral4,
    surfaceContainerLowest  = Neutral0,
    surfaceContainerLow     = Neutral10,
    surfaceContainer        = Neutral12,
    surfaceContainerHigh    = Neutral17,
    surfaceContainerHighest = Neutral22,

    // Outlines
    outline                 = NeutralVar60,
    outlineVariant          = NeutralVar30,

    // Inverse / scrim — inversePrimary uses Primary40 (deep gold) so the
    //  dark-on-light snackbar action stays AA against an inverted surface.
    inverseSurface          = Neutral90,
    inverseOnSurface        = Neutral20,
    inversePrimary          = Primary40,
    scrim                   = Neutral0,
)

// ── Shape scale ───────────────────────────────────────────────────────────────
val WhereShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small      = RoundedCornerShape(12.dp),
    medium     = RoundedCornerShape(16.dp),
    large      = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

// ── Theme entry point ─────────────────────────────────────────────────────────
@Composable
fun WhereTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val reducedMotion = rememberReducedMotion()

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Status & navigation bars match the surface so the chrome
            // blends with the canvas instead of fighting it.
            @Suppress("DEPRECATION")
            window.statusBarColor = Color.Transparent.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalReducedMotion provides reducedMotion) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography  = Typography,
            shapes      = WhereShapes,
            content     = content
        )
    }
}
