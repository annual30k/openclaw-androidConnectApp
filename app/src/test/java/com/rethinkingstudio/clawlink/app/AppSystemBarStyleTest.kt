package com.rethinkingstudio.clawlink.app

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSystemBarStyleTest {
    @Test
    fun lightThemeUsesTransparentBarsWithDarkIcons() {
        val style = appSystemBarStyle(darkTheme = false)

        assertEquals(Color.Transparent, style.statusBarColor)
        assertEquals(Color.Transparent, style.navigationBarColor)
        assertTrue(style.useDarkStatusBarIcons)
        assertTrue(style.useDarkNavigationBarIcons)
        assertFalse(style.enforceSystemBarContrast)
    }

    @Test
    fun darkThemeUsesTransparentBarsWithLightIcons() {
        val style = appSystemBarStyle(darkTheme = true)

        assertEquals(Color.Transparent, style.statusBarColor)
        assertEquals(Color.Transparent, style.navigationBarColor)
        assertFalse(style.useDarkStatusBarIcons)
        assertFalse(style.useDarkNavigationBarIcons)
        assertFalse(style.enforceSystemBarContrast)
    }
}
