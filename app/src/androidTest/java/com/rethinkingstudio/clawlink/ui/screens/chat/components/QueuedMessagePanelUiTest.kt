package com.rethinkingstudio.clawlink.ui.screens.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.rethinkingstudio.clawlink.app.PocketClawTheme
import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class QueuedMessagePanelUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun queueSurfacesAreOpaqueOverConversationContent() {
        val surfaceColor = Color(0xFFFFFFFF)
        val listColor = Color(0xFFF7F8FC)

        composeRule.setContent {
            PocketClawTheme(darkTheme = false, dynamicColor = false) {
                Box(
                    modifier = Modifier
                        .width(360.dp)
                        .background(Color.Red)
                ) {
                    QueuedMessagePanel(
                        messages = listOf(
                            ChatMessage(
                                id = "queued-1",
                                role = MessageRole.user,
                                content = "next question"
                            )
                        ),
                        onMove = { _, _ -> },
                        onRemove = {}
                    )
                }
            }
        }

        val capture = composeRule.onNodeWithTag("queued_message_panel").captureToImage()
        val pixels = capture.toPixelMap()
        val density = composeRule.density
        val headerPixel = pixels[
            with(density) { 20.dp.roundToPx() },
            with(density) { 20.dp.roundToPx() }
        ]
        val listPixel = pixels[
            with(density) { 20.dp.roundToPx() },
            with(density) { 60.dp.roundToPx() }
        ]

        assertColorEquals(surfaceColor, headerPixel)
        assertColorEquals(listColor, listPixel)
    }

    private fun assertColorEquals(expected: Color, actual: Color) {
        assertEquals(expected.red, actual.red, 0.02f)
        assertEquals(expected.green, actual.green, 0.02f)
        assertEquals(expected.blue, actual.blue, 0.02f)
        assertEquals(1f, actual.alpha, 0.001f)
    }
}
