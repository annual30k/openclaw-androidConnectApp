package com.rethinkingstudio.clawlink.ui.screens.chat.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamingIndicatorMotionTest {
    @Test
    fun `typing dots move vertically through the animation cycle`() {
        assertEquals(1.dp, StreamingIndicatorMotion.verticalOffset(0f))
        assertEquals((-0.5).dp, StreamingIndicatorMotion.verticalOffset(0.5f))
        assertEquals((-2).dp, StreamingIndicatorMotion.verticalOffset(1f))
    }

    @Test
    fun `typing dots keep the shared staggered rhythm`() {
        assertEquals(720, StreamingIndicatorMotion.durationMillis)
        assertEquals(160, StreamingIndicatorMotion.staggerMillis)
    }
}
