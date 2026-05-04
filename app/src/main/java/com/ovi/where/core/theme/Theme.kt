package com.ovi.where.core.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

// ── Light colour scheme ───────────────────────────────────────────────────────
private val LightColorScheme = lightColorScheme(
    primary                = Pink40,
    onPrimary              = Color.White,
    primaryContainer       = Pink90,
    onPrimaryContainer     = Pink10,

    secondary              = Green40,
    onSecondary            = Color.White,
    secondaryContainer     = Green90,
    onSecondaryContainer   = Green10,

    tertiary               = Purple40,
    onTertiary             = Color.White,
    tertiaryContainer      = Purple90,
    onTertiaryContainer    = Purple10,

    error                  = Red40,
    onError                = Color.White,
    errorContainer         = Red90,
    onErrorContainer       = Red10,

    background             = Neutral99,
    onBackground           = Neutral6,

    surface                = Neutral99,
    onSurface              = Neutral6,
    surfaceVariant         = NeutralVar90,
    onSurfaceVariant       = NeutralVar30,

    outline                = NeutralVar50,
    outlineVariant         = NeutralVar80,

    inverseSurface         = Neutral22,
    inverseOnSurface       = Neutral94,
    inversePrimary         = Pink80,

    scrim                  = Color.Black,
)

// ── Dark colour scheme ────────────────────────────────────────────────────────
private val DarkColorScheme = darkColorScheme(
    primary                = Pink80,
    onPrimary              = Pink20,
    primaryContainer       = Pink30,
    onPrimaryContainer     = Pink90,

    secondary              = Green80,
    onSecondary            = Green20,
    secondaryContainer     = Green30,
    onSecondaryContainer   = Green90,

    tertiary               = Purple80,
    onTertiary             = Purple20,
    tertiaryContainer      = Purple30,
    onTertiaryContainer    = Purple90,

    error                  = Red80,
    onError                = Red10,
    errorContainer         = Red40,
    onErrorContainer       = Red90,

    background             = Neutral6,
    onBackground           = Neutral94,

    surface                = Neutral6,
    onSurface              = Neutral94,
    surfaceVariant         = NeutralVar30,
    onSurfaceVariant       = NeutralVar80,

    outline                = NeutralVar60,
    outlineVariant         = NeutralVar30,

    inverseSurface         = Neutral94,
    inverseOnSurface       = Neutral17,
    inversePrimary         = Pink40,

    scrim                  = Color.Black,
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

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            window.statusBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography,
        shapes      = WhereShapes,
        content     = content
    )
}
