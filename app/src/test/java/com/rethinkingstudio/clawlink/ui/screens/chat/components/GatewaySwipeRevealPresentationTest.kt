package com.rethinkingstudio.clawlink.ui.screens.chat.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewaySwipeRevealPresentationTest {
    @Test
    fun offsetClampsBetweenClosedAndRevealWidth() {
        assertEquals(0f, gatewaySwipeRevealOffset(isRevealed = false, dragX = 48f, revealWidth = 108f))
        assertEquals(-108f, gatewaySwipeRevealOffset(isRevealed = true, dragX = -80f, revealWidth = 108f))
        assertEquals(-60f, gatewaySwipeRevealOffset(isRevealed = false, dragX = -60f, revealWidth = 108f))
    }

    @Test
    fun projectedDragChoosesRevealState() {
        assertTrue(gatewaySwipeShouldReveal(isRevealed = false, predictedEndTranslationX = -56f, revealWidth = 108f))
        assertFalse(gatewaySwipeShouldReveal(isRevealed = false, predictedEndTranslationX = -30f, revealWidth = 108f))
        assertFalse(gatewaySwipeShouldReveal(isRevealed = true, predictedEndTranslationX = 80f, revealWidth = 108f))
    }
}
