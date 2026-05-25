package com.rethinkingstudio.clawlink

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchSystemBarStyleTest {
    @Test
    fun staticArtworkSplashUsesTransparentBarsEvenInDarkMode() {
        val style = launchSystemBarStyle(
            launchPhase = LaunchPhase.StaticSplash,
            darkTheme = true,
            normalBackground = Color.Black
        )

        assertEquals(Color.Transparent, style.statusBarColor)
        assertEquals(Color.Transparent, style.navigationBarColor)
        assertTrue(style.useDarkStatusBarIcons)
        assertTrue(style.useDarkNavigationBarIcons)
    }

    @Test
    fun nonArtworkLaunchUsesThemeBackground() {
        val style = launchSystemBarStyle(
            launchPhase = LaunchPhase.AnimatedSplash,
            darkTheme = true,
            normalBackground = Color.Black
        )

        assertEquals(Color.Black, style.statusBarColor)
        assertEquals(Color.Black, style.navigationBarColor)
        assertFalse(style.useDarkStatusBarIcons)
        assertFalse(style.useDarkNavigationBarIcons)
    }
}
