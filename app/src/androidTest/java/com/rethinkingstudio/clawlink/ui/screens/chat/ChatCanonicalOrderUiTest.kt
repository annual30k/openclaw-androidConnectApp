package com.rethinkingstudio.clawlink.ui.screens.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import com.rethinkingstudio.clawlink.app.PocketClawTheme
import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock
import com.rethinkingstudio.clawlink.ui.components.ClawLinkAlertDialog
import com.rethinkingstudio.clawlink.ui.screens.chat.components.MessageBubble
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ChatCanonicalOrderUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun relayConversationSequenceControlsRenderedBubbleOrder() {
        val messages = conversationDisplayMessages(
            messages = outOfOrderHermesMessages(),
            showInvocationProcess = true
        )

        composeRule.setContent {
            PocketClawTheme(darkTheme = false, dynamicColor = false) {
                Column(Modifier.fillMaxSize()) {
                    messages.forEach { message ->
                        Box(Modifier.testTag("timeline-${message.id}")) {
                            MessageBubble(
                                message = message,
                                showInvocationProcess = true,
                                relayBaseUrl = "",
                                accessToken = ""
                            )
                        }
                    }
                }
            }
        }

        val renderedTops = listOf(
            "local-new",
            "new-answer",
            "hermes-user",
            "hermes-answer",
            "local-hello",
            "hello-answer"
        ).map { id ->
            composeRule.onNodeWithTag("timeline-$id").fetchSemanticsNode().boundsInRoot.top
        }

        assertTrue(renderedTops.zipWithNext().all { (left, right) -> left < right })
    }

    @Test
    fun stableLocalTurnsStayRenderedInSubmissionOrderAcrossMixedHermesKeys() {
        fun mixed(
            id: String,
            role: MessageRole,
            runId: String,
            text: String,
            orderKey: String,
            localTurnOrder: Long? = null
        ) = ChatMessage(
            id = id,
            role = role,
            content = text,
            contentBlocks = listOf(RelayChatContentBlock(type = "text", text = text)),
            runId = runId,
            turnId = runId,
            timelineOrderKey = orderKey,
            timelineIdentityKey = "v1|mobile-hermes|message|${role.name}|$id",
            timelineItemKind = "message:${role.name}",
            localTurnOrder = localTurnOrder
        )
        val messages = conversationDisplayMessages(
            listOf(
                mixed("new-user", MessageRole.user, "new-run", "/new", "v5|0|00000001786421720969|00000000000000000000|10|new-user", 0),
                mixed("new-answer", MessageRole.assistant, "new-run", "New session started!", "v5|0|00001786421722525000|00000000000000000000|50|new-answer"),
                mixed("hello-user", MessageRole.user, "hello-run", "hello", "v5|0|00000000000000004349|00000000000000000000|10|hello-user", 1),
                mixed("hello-answer", MessageRole.assistant, "hello-run", "hello-answer", "v5|0|00000000000000004350|00000000000000000000|50|hello-answer"),
                mixed("ping-user", MessageRole.user, "ping-run", "ping", "v5|0|00000000000000004351|00000000000000000000|10|ping-user", 2),
                mixed("ping-answer", MessageRole.assistant, "ping-run", "pong", "v5|1|00000000000000000003|00000000000000000000|50|ping-answer")
            ),
            showInvocationProcess = true
        )

        composeRule.setContent {
            PocketClawTheme(darkTheme = false, dynamicColor = false) {
                Column(Modifier.fillMaxSize()) {
                    messages.forEach { message ->
                        Box(Modifier.testTag("mixed-${message.id}")) {
                            MessageBubble(
                                message = message,
                                showInvocationProcess = true,
                                relayBaseUrl = "",
                                accessToken = ""
                            )
                        }
                    }
                }
            }
        }

        val renderedTops = listOf(
            "new-user",
            "new-answer",
            "hello-user",
            "hello-answer",
            "ping-user",
            "ping-answer"
        ).map { id ->
            composeRule.onNodeWithTag("mixed-$id").fetchSemanticsNode().boundsInRoot.top
        }

        assertTrue(renderedTops.zipWithNext().all { (left, right) -> left < right })
    }

    @Test
    fun duplicateToolMessageIdsRenderWithDistinctCanonicalListKeys() {
        val first = ChatMessage(
            id = "tool-shared-id",
            role = MessageRole.tool,
            content = "first tool row",
            timelineOrderKey = "v5|0|00000000000000000001|00000000000000000000|30|tool-a",
            timelineIdentityKey = "v1|main|tool|call-a",
            timelineItemKind = "tool"
        )
        val second = first.copy(
            content = "second tool row",
            timelineOrderKey = "v5|0|00000000000000000002|00000000000000000000|30|tool-b",
            timelineIdentityKey = "v1|main|tool|call-b"
        )
        val listItems = conversationMessageListItems(listOf(first, second), "gateway::main")

        composeRule.setContent {
            PocketClawTheme(darkTheme = false, dynamicColor = false) {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(listItems, key = ConversationMessageListItem::stableKey) { item ->
                        Text(item.message.content)
                    }
                }
            }
        }

        composeRule.onNodeWithText("first tool row").assertIsDisplayed()
        composeRule.onNodeWithText("second tool row").assertIsDisplayed()
    }

    @Test
    fun compositionCancellationDoesNotRenderAnInternalEnglishDialog() {
        val alert = resolveChatStatusAlert(
            chatErrorMessage = null,
            gatewayErrorMessage = "The coroutine scope left the composition",
            composerNotice = null
        )

        composeRule.setContent {
            PocketClawTheme(darkTheme = false, dynamicColor = false) {
                if (!alert.message.isNullOrBlank()) {
                    ClawLinkAlertDialog(
                        onDismissRequest = {},
                        title = "错误",
                        message = alert.message,
                        confirmText = "关闭",
                        onConfirm = {}
                    )
                }
            }
        }

        assertTrue(
            composeRule.onAllNodesWithText("The coroutine scope left the composition")
                .fetchSemanticsNodes().isEmpty()
        )
    }

    private fun outOfOrderHermesMessages(): List<ChatMessage> {
        return listOf(
            canonical("hermes-user", MessageRole.user, "hermes-run", "Reply exactly HERMESNEW0811", 2, 10),
            canonical("hermes-answer", MessageRole.assistant, "hermes-run", "HERMESNEW0811", 2, 50),
            local("local-hello", "hello-run", "你好"),
            local("local-new", "new-run", "/new"),
            canonical("relay-new", MessageRole.user, "new-run", "/new", 1, 10),
            canonical("new-answer", MessageRole.assistant, "new-run", "New session started!", 1, 50),
            canonical("relay-hello", MessageRole.user, "hello-run", "你好", 3, 10),
            canonical("hello-answer", MessageRole.assistant, "hello-run", "你好！有什么可以帮你的？", 3, 50)
        )
    }

    private fun canonical(
        id: String,
        role: MessageRole,
        runId: String,
        content: String,
        conversationSequence: Int,
        slot: Int
    ) = ChatMessage(
        id = id,
        role = role,
        content = content,
        contentBlocks = listOf(RelayChatContentBlock(type = "text", text = content)),
        runId = runId,
        turnId = runId,
        timelineOrderKey = "v5|1|${conversationSequence.toString().padStart(20, '0')}|00000000000000000000|${slot.toString().padStart(2, '0')}|$id",
        timelineIdentityKey = "v1|mobile-hermes|message|${role.name}|$id",
        timelineItemKind = "message:${role.name}"
    )

    private fun local(id: String, runId: String, content: String) = ChatMessage(
        id = id,
        role = MessageRole.user,
        content = content,
        runId = "local-user-$runId",
        turnId = runId,
        timelineOrderKey = "local:$runId|10|$id",
        timelineIdentityKey = "local:message:user:$runId",
        timelineItemKind = "message:user",
        source = "local"
    )
}
