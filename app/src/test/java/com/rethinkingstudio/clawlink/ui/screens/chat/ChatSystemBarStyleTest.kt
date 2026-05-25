package com.rethinkingstudio.clawlink.ui.screens.chat

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatSystemBarStyleTest {
    @Test
    fun defaultStyleUsesNormalChatBars() {
        val style = chatSystemBarStyle(
            normalStatusBarColor = Color(0xFFF7F7FA),
            normalNavigationBarColor = Color(0xFFFFFFFF),
            modalOverlayActive = false
        )

        assertEquals(Color(0xFFF7F7FA), style.statusBarColor)
        assertEquals(Color(0xFFFFFFFF), style.navigationBarColor)
        assertTrue(style.useDarkStatusBarIcons)
        assertTrue(style.useDarkNavigationBarIcons)
    }

    @Test
    fun modalOverlayUsesBlackBarsWithLightIcons() {
        val style = chatSystemBarStyle(
            normalStatusBarColor = Color(0xFFF7F7FA),
            normalNavigationBarColor = Color(0xFFFFFFFF),
            modalOverlayActive = true
        )

        assertEquals(Color.Black, style.statusBarColor)
        assertEquals(Color.Black, style.navigationBarColor)
        assertFalse(style.useDarkStatusBarIcons)
        assertFalse(style.useDarkNavigationBarIcons)
    }
}
