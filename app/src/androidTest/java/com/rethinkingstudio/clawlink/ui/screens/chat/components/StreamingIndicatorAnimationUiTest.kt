package com.rethinkingstudio.clawlink.ui.screens.chat.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class StreamingIndicatorAnimationUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun standaloneTypingDotMovesUpDuringItsForwardCycle() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            MaterialTheme {
                StreamingIndicatorBubble()
            }
        }

        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        val initialTop = firstDotTop()
        composeRule.mainClock.advanceTimeBy(StreamingIndicatorMotion.durationMillis / 2L)
        composeRule.waitForIdle()
        val animatedTop = firstDotTop()

        assertTrue(
            "Typing dot should move upward instead of only changing alpha and scale: " +
                "initialTop=$initialTop, animatedTop=$animatedTop",
            animatedTop < initialTop
        )
    }

    private fun firstDotTop(): Float = composeRule
        .onNodeWithTag("streaming_indicator_dot_0", useUnmergedTree = true)
        .fetchSemanticsNode()
        .boundsInRoot
        .top
}
