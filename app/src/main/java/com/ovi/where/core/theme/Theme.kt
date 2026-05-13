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

// ── Light colour scheme ───────────────────────────────────────────────────────
private val LightColorScheme = lightColorScheme(
    primary                = Primary40,
    onPrimary              = Color.White,
    primaryContainer       = Primary90,
    onPrimaryContainer     = Primary10,

    secondary              = Secondary40,
    onSecondary            = Color.White,
    secondaryContainer     = Secondary90,
    onSecondaryContainer   = Secondary10,

    tertiary               = Tertiary40,
    onTertiary             = Color.White,
    tertiaryContainer      = Tertiary90,
    onTertiaryContainer    = Tertiary10,

    error                  = Error40,
    onError                = Color.White,
    errorContainer         = Error90,
    onErrorContainer       = Error10,

    background             = Neutral99,
    onBackground           = Neutral10,

    surface                = Neutral99,
    onSurface              = Neutral10,
    surfaceVariant         = NeutralVar90,
    onSurfaceVariant       = NeutralVar30,

    outline                = NeutralVar40,
    outlineVariant         = NeutralVar80,

    inverseSurface         = Neutral20,
    inverseOnSurface       = Neutral94,
    inversePrimary         = Primary80,

    scrim                  = Color.Black,
)

// ── Dark colour scheme ────────────────────────────────────────────────────────
private val DarkColorScheme = darkColorScheme(
    primary                = Primary80,
    onPrimary              = Primary20,
    primaryContainer       = Primary30,
    onPrimaryContainer     = Primary90,

    secondary              = Secondary80,
    onSecondary            = Secondary20,
    secondaryContainer     = Secondary30,
    onSecondaryContainer   = Secondary90,

    tertiary               = Tertiary80,
    onTertiary             = Tertiary20,
    tertiaryContainer      = Tertiary30,
    onTertiaryContainer    = Tertiary90,

    error                  = Error80,
    onError                = Error10,
    errorContainer         = Error40,
    onErrorContainer       = Error90,

    background             = Neutral10,
    onBackground           = Neutral90,

    surface                = Neutral10,
    onSurface              = Neutral90,
    surfaceVariant         = NeutralVar30,
    onSurfaceVariant       = NeutralVar80,

    outline                = NeutralVar80,
    outlineVariant         = NeutralVar30,

    inverseSurface         = Neutral90,
    inverseOnSurface       = Neutral20,
    inversePrimary         = Primary40,

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
    val reducedMotion = rememberReducedMotion()

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            window.statusBarColor = colorScheme.surface.toArgb()
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
