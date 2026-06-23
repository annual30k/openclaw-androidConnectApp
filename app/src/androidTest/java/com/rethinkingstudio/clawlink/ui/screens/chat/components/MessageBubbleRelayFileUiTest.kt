package com.rethinkingstudio.clawlink.ui.screens.chat.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock
import org.junit.Rule
import org.junit.Test

class MessageBubbleRelayFileUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun relayFileMessageRendersFileCardOnDevice() {
        setBubble(
            ChatMessage(
                id = "file-report-1",
                role = MessageRole.assistant,
                state = MessageState.completed,
                content = "report.pdf",
                contentBlocks = listOf(
                    RelayChatContentBlock(
                        type = "file",
                        fileId = "file-report-1",
                        fileName = "report.pdf",
                        mimeType = "application/pdf",
                        sizeBytes = 4096,
                        downloadUrl = "/api/mobile/files/file-report-1"
                    )
                ),
                createdAt = "2026-06-22T08:30:00.000Z",
                runId = "file-report-1"
            )
        )

        composeRule.onNodeWithText("report.pdf").assertIsDisplayed()
    }

    private fun setBubble(message: ChatMessage) {
        composeRule.setContent {
            MaterialTheme {
                Box(Modifier.width(390.dp)) {
                    MessageBubble(
                        message = message,
                        showInvocationProcess = true,
                        relayBaseUrl = "https://relay.example.com",
                        accessToken = "token"
                    )
                }
            }
        }
    }
}
