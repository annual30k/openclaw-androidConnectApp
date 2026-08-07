package com.rethinkingstudio.clawlink.ui.screens.chat.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.rethinkingstudio.clawlink.ui.screens.chat.formatChatTimestamp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MessageFooterSpacingUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun shortBubbleFooterKeepsTenDpBetweenSenderAndTimestamp() {
        val createdAt = "2026-08-07T10:42:00Z"
        val timestamp = formatChatTimestamp(createdAt)

        composeRule.setContent {
            MaterialTheme {
                Box(Modifier.width(120.dp)) {
                    MessageFooter(
                        title = "You",
                        createdAt = createdAt,
                        isUser = true,
                        fillsAvailableWidth = true
                    )
                }
            }
        }

        val titleBounds = composeRule.onNodeWithText("You", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val timestampBounds = composeRule.onNodeWithText(timestamp, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val minimumGap = with(composeRule.density) { messageFooterMinimumItemGap.toPx() }

        assertTrue(timestampBounds.left - titleBounds.right >= minimumGap - 1f)
    }
}
