package com.rethinkingstudio.clawlink.app

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.unit.dp

// Apple-like palette: restrained blue, soft grouped surfaces, tight contrast.
private val PocketClawBlue = Color(0xFF0A84FF)
private val PocketClawBlueSoft = Color(0xFF5AC8FA)
private val PocketClawGreen = Color(0xFF34C759)
private val PocketClawOrange = Color(0xFFFF9F0A)
private val PocketClawBackgroundLightTop = Color(0xFFF7F7FA)
private val PocketClawBackgroundLightBottom = Color(0xFFEDEFF5)
private val PocketClawBackgroundDarkTop = Color(0xFF0B0B0F)
private val PocketClawBackgroundDarkBottom = Color(0xFF141419)
private val PocketClawDark = Color(0xFF1C1C1E)
private val PocketClawSurface = Color(0xFFFFFFFF)
private val PocketClawSurfaceMuted = Color(0xFFF2F2F7)
private val PocketClawSurfaceDark = Color(0xFF2C2C2E)
private val PocketClawSurfaceMutedDark = Color(0xFF38383A)
private val PocketClawSeparatorLight = Color(0x1A000000)
private val PocketClawSeparatorDark = Color(0x26FFFFFF)

private val LightColors = lightColorScheme(
    primary = PocketClawBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8ECFF),
    secondary = PocketClawGreen,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD7F4E3),
    tertiary = PocketClawOrange,
    tertiaryContainer = Color(0xFFFFE8C2),
    background = PocketClawBackgroundLightTop,
    surface = PocketClawSurface,
    surfaceVariant = PocketClawSurfaceMuted,
    onSurface = Color(0xFF1C1C1E),
    onSurfaceVariant = Color(0xFF636366),
    outline = PocketClawSeparatorLight,
    outlineVariant = Color(0x14000000),
    error = Color(0xFFFF453A),
    errorContainer = Color(0xFFFFDAD6)
)

private val DarkColors = darkColorScheme(
    primary = PocketClawBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF173C63),
    secondary = PocketClawGreen,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF193A2B),
    tertiary = PocketClawOrange,
    tertiaryContainer = Color(0xFF4B3311),
    background = PocketClawBackgroundDarkTop,
    surface = PocketClawDark,
    surfaceVariant = PocketClawSurfaceDark,
    onSurface = Color(0xFFF2F2F7),
    onSurfaceVariant = Color(0xFFAEAEB2),
    outline = PocketClawSeparatorDark,
    outlineVariant = Color(0x24FFFFFF),
    error = Color(0xFFFF6B5E),
    errorContainer = Color(0xFF5A1E1A)
)

@Composable
fun PocketClawTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            window.statusBarColor = colorScheme.background.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = colorScheme.background.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(12.dp),
            small = RoundedCornerShape(16.dp),
            medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(26.dp),
            extraLarge = RoundedCornerShape(32.dp)
        ),
        content = content
    )
}
