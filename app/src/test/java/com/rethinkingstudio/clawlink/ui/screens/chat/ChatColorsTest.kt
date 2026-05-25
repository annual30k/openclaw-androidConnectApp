package com.rethinkingstudio.clawlink.ui.screens.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatColorsTest {
    @Test
    fun darkDockBorderIsSubtle() {
        val darkAlpha = chatDockBorderAlpha(darkTheme = true)
        val lightAlpha = chatDockBorderAlpha(darkTheme = false)

        assertEquals(0.10f, darkAlpha)
        assertEquals(0.08f, lightAlpha)
    }
}
