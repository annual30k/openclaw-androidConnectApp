package com.rethinkingstudio.clawlink

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

internal data class LaunchSystemBarStyle(
    val statusBarColor: Color,
    val navigationBarColor: Color,
    val useDarkStatusBarIcons: Boolean,
    val useDarkNavigationBarIcons: Boolean,
    val enforceSystemBarContrast: Boolean
)

internal fun launchSystemBarStyle(
    launchPhase: LaunchPhase,
    darkTheme: Boolean,
    normalBackground: Color
): LaunchSystemBarStyle {
    if (launchPhase == LaunchPhase.StaticSplash) {
        return LaunchSystemBarStyle(
            statusBarColor = Color.Transparent,
            navigationBarColor = Color.Transparent,
            useDarkStatusBarIcons = true,
            useDarkNavigationBarIcons = true,
            enforceSystemBarContrast = false
        )
    }

    val useDarkIcons = normalBackground.luminance() > 0.5f
    return LaunchSystemBarStyle(
        statusBarColor = normalBackground,
        navigationBarColor = normalBackground,
        useDarkStatusBarIcons = useDarkIcons && !darkTheme,
        useDarkNavigationBarIcons = useDarkIcons && !darkTheme,
        enforceSystemBarContrast = true
    )
}
