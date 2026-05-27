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
//  Material 3 colour schemes — derived from the where_logo_v2 gradient.
//
//  Both schemes follow the M3 reference token mapping exactly:
//
//    Light:  primary = T40, onPrimary = T100, primaryContainer = T90,
//            onPrimaryContainer = T10
//    Dark:   primary = T80, onPrimary = T20,  primaryContainer = T30,
//            onPrimaryContainer = T90
//
//  Surfaces use the M3 elevation roles (containerLowest → containerHighest)
//  rather than ad-hoc greys, so cards, sheets, and rails all read at the
//  correct elevation step automatically.
// ─────────────────────────────────────────────────────────────────────────────

private val LightColorScheme = lightColorScheme(
    // Primary
    primary                 = Primary40,
    onPrimary               = Primary100,
    primaryContainer        = Primary90,
    onPrimaryContainer      = Primary10,

    // Secondary
    secondary               = Secondary40,
    onSecondary             = Secondary100,
    secondaryContainer      = Secondary90,
    onSecondaryContainer    = Secondary10,

    // Tertiary
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
    //  on our hot pink primary turns bottom bars and sheets into a pink
    //  wash. We keep the brand pink reserved for actual brand affordances.
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

    // Inverse / scrim
    inverseSurface          = Neutral20,
    inverseOnSurface        = Neutral95,
    inversePrimary          = Primary80,
    scrim                   = Neutral0,
)

private val DarkColorScheme = darkColorScheme(
    // Primary — kept identical to light mode so the brand magenta is the
    // single, recognisable "Where pink" wherever it appears (buttons,
    // FABs, links, focus rings). Primary40 stays AA on both white and
    // dark surfaces (5.6:1 vs ~5.4:1) so onPrimary = Primary100 (white)
    // works in either scheme.
    primary                 = Primary40,
    onPrimary               = Primary100,
    primaryContainer        = Primary30,
    onPrimaryContainer      = Primary90,

    // Secondary
    secondary               = Secondary80,
    onSecondary             = Secondary20,
    secondaryContainer      = Secondary30,
    onSecondaryContainer    = Secondary90,

    // Tertiary
    tertiary                = Tertiary80,
    onTertiary              = Tertiary20,
    tertiaryContainer       = Tertiary30,
    onTertiaryContainer     = Tertiary90,

    // Error
    error                   = Error80,
    onError                 = Error20,
    errorContainer          = Error30,
    onErrorContainer        = Error90,

    // Background / surface — sits one tone higher than pure black so the
    // status bar isn't a hole on OLED, while still feeling deep + premium.
    background              = Neutral6,
    onBackground            = Neutral90,
    surface                 = Neutral6,
    onSurface               = Neutral90,
    surfaceVariant          = NeutralVar30,
    onSurfaceVariant        = NeutralVar80,

    // Surface tonal elevations (M3 dark spec). surfaceTint = surface so
    //  elevation overlays are pure tonal lift, not a pink wash.
    surfaceTint             = Neutral6,
    surfaceBright           = Neutral24,
    surfaceDim              = Neutral6,
    surfaceContainerLowest  = Neutral4,
    surfaceContainerLow     = Neutral10,
    surfaceContainer        = Neutral12,
    surfaceContainerHigh    = Neutral17,
    surfaceContainerHighest = Neutral22,

    // Outlines
    outline                 = NeutralVar60,
    outlineVariant          = NeutralVar30,

    // Inverse / scrim — inversePrimary stays on the brand magenta so the
    // light-on-dark "snackbar action" colour matches the rest of the app.
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
