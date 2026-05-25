package com.rethinkingstudio.clawlink.app

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

internal data class AppSystemBarStyle(
    val statusBarColor: Color,
    val navigationBarColor: Color,
    val useDarkStatusBarIcons: Boolean,
    val useDarkNavigationBarIcons: Boolean,
    val enforceSystemBarContrast: Boolean
)

internal fun appSystemBarStyle(darkTheme: Boolean): AppSystemBarStyle {
    return AppSystemBarStyle(
        statusBarColor = Color.Transparent,
        navigationBarColor = Color.Transparent,
        useDarkStatusBarIcons = !darkTheme,
        useDarkNavigationBarIcons = !darkTheme,
        enforceSystemBarContrast = false
    )
}

@Composable
internal fun AppSystemBarsEffect(darkTheme: Boolean = isSystemInDarkTheme()) {
    val view = LocalView.current
    val style = appSystemBarStyle(darkTheme)
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            @Suppress("DEPRECATION")
            window.statusBarColor = style.statusBarColor.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = style.navigationBarColor.toArgb()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                @Suppress("DEPRECATION")
                window.isStatusBarContrastEnforced = style.enforceSystemBarContrast
                @Suppress("DEPRECATION")
                window.isNavigationBarContrastEnforced = style.enforceSystemBarContrast
            }
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = style.useDarkStatusBarIcons
            controller.isAppearanceLightNavigationBars = style.useDarkNavigationBarIcons
        }
    }
}
