package com.rethinkingstudio.clawlink.ui.screens.chat.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class GatewaySelectionBreathingStyleTest {
    @Test
    fun `selected gateway breathing matches shared card specification`() {
        assertEquals(2.dp, GatewaySelectionBreathingStyle.borderWidth)
        assertEquals(1_200, GatewaySelectionBreathingStyle.halfCycleMillis)
        assertEquals(0.62f, GatewaySelectionBreathingStyle.minimumBorderAlpha)
        assertEquals(0.96f, GatewaySelectionBreathingStyle.maximumBorderAlpha)
    }
}
