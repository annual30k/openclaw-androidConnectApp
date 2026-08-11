package com.rethinkingstudio.clawlink.ui.screens.chat.components

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.rethinkingstudio.clawlink.app.PocketClawTheme
import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.ui.screens.chat.VoiceInputPhase
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class QueuedMessageOverlayLayoutUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun expandedQueueFloatsAboveComposerWithoutMovingIt() {
        val queueVisible = mutableStateOf(false)
        val queuedMessages = listOf(
            queuedMessage("queued-1", 1, "Codex 和 ACP 的主要区别是什么？"),
            queuedMessage("queued-2", 2, "如何在 OpenClaw 中配置 Codex 插件？"),
            queuedMessage("queued-3", 3, "有没有推荐的 Codex 使用最佳实践？")
        )

        composeRule.setContent {
            PocketClawTheme(darkTheme = false, dynamicColor = false) {
                var composerHeight by remember { mutableStateOf(0.dp) }
                val density = LocalDensity.current

                Box(
                    modifier = Modifier
                        .width(390.dp)
                        .height(700.dp)
                        .background(MaterialTheme.colorScheme.background)
                        .testTag("queue_overlay_fixture")
                ) {
                    Column(Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Spacer(Modifier.weight(1f))
                            MessageBubble(
                                message = ChatMessage(
                                    id = "assistant-1",
                                    role = MessageRole.assistant,
                                    content = "可以，我会按顺序处理这些问题。",
                                    createdAt = "2026-08-06T17:00:00Z"
                                ),
                                showInvocationProcess = false,
                                relayBaseUrl = "",
                                accessToken = ""
                            )
                            MessageBubble(
                                message = ChatMessage(
                                    id = "user-1",
                                    role = MessageRole.user,
                                    content = "先解释一下 Codex 的队列机制",
                                    createdAt = "2026-08-06T17:01:00Z"
                                ),
                                showInvocationProcess = false,
                                relayBaseUrl = "",
                                accessToken = ""
                            )
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("composer_area")
                                .onSizeChanged { size ->
                                    composerHeight = with(density) { size.height.toDp() }
                                }
                        ) {
                            ComposerDock(
                                messageText = "",
                                onMessageTextChange = {},
                                selectedModelText = "mimo-v2.5-pro",
                                isStreaming = true,
                                isStoppingRun = false,
                                voiceMode = false,
                                voiceInputPhase = VoiceInputPhase.Idle,
                                voiceInputCancelPreview = false,
                                showsOpenClawControls = true,
                                showsModelPicker = true,
                                attachments = emptyList(),
                                isUploadingAttachment = false,
                                hasActiveSession = true,
                                canEditComposer = true,
                                canSendMessage = true,
                                showAttachmentMenu = false,
                                onDismissAttachmentMenu = {},
                                attachmentButtonPosition = IntOffset.Zero,
                                attachmentButtonSize = IntSize.Zero,
                                onAttachmentButtonPositionChanged = {},
                                onAttachmentButtonSizeChanged = {},
                                onPickFiles = {},
                                onPickAlbum = {},
                                onPickCamera = {},
                                onRemoveAttachment = {},
                                onOpenModelPicker = {},
                                onShowSkillSheet = {},
                                onOpenAttachment = {},
                                onToggleVoiceMode = {},
                                onBeginVoiceInputHold = {},
                                onEndVoiceInputHold = {},
                                onCancelVoiceInput = {},
                                onVoiceInputCancelPreviewChange = {},
                                onSend = {},
                                onAbort = {}
                            )
                        }
                    }

                    if (queueVisible.value && composerHeight > 0.dp) {
                        QueuedMessageOverlay(
                            messages = queuedMessages,
                            composerHeight = composerHeight,
                            onMove = { _, _ -> },
                            onRemove = {}
                        )
                    }
                }
            }
        }

        val composerBefore = composeRule.onNodeWithTag("composer_area")
            .fetchSemanticsNode().boundsInRoot

        composeRule.runOnIdle { queueVisible.value = true }
        composeRule.waitForIdle()

        val composerAfter = composeRule.onNodeWithTag("composer_area")
            .fetchSemanticsNode().boundsInRoot
        val queueBounds = composeRule.onNodeWithTag("queued_message_panel")
            .fetchSemanticsNode().boundsInRoot
        val expectedGap = with(composeRule.density) { queuedMessageOverlayGap.toPx() }

        assertEquals(8.dp, queuedMessageOverlayGap)
        assertEquals(composerBefore.top, composerAfter.top, 1f)
        assertEquals(composerBefore.bottom, composerAfter.bottom, 1f)
        assertEquals(composerAfter.top - expectedGap, queueBounds.bottom, 1f)
        assertTrue(queueBounds.top < composerAfter.top)

        val capture = composeRule.onNodeWithTag("queue_overlay_fixture").captureToImage()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(context.getExternalFilesDir(null), "android-queue-overlay-final.png")
        FileOutputStream(output).use { stream ->
            capture.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        assertTrue(output.isFile && output.length() > 0L)
    }

    @Test
    fun newMessagesButtonUsesRaisedComposerSafetyGap() {
        val composerHeight = 112.dp

        composeRule.setContent {
            PocketClawTheme(darkTheme = false, dynamicColor = false) {
                Box(
                    modifier = Modifier
                        .width(390.dp)
                        .height(700.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(composerHeight)
                            .align(androidx.compose.ui.Alignment.BottomCenter)
                            .testTag("new_messages_composer")
                    )
                    NewMessagesFloatingButton(
                        composerHeight = composerHeight,
                        onClick = {},
                        modifier = Modifier
                            .align(androidx.compose.ui.Alignment.BottomCenter)
                            .testTag("new_messages_button")
                    )
                }
            }
        }

        val composerBounds = composeRule.onNodeWithTag("new_messages_composer")
            .fetchSemanticsNode().boundsInRoot
        val buttonBounds = composeRule.onNodeWithTag("new_messages_button")
            .fetchSemanticsNode().boundsInRoot
        val expectedGap = with(composeRule.density) { newMessagesFloatingButtonGap.toPx() }

        assertEquals(8.dp, newMessagesFloatingButtonGap)
        // Surface 的语义边界不包含外部阴影；可交互本体只保留紧凑安全间距。
        assertTrue(buttonBounds.bottom <= composerBounds.top - expectedGap)
    }

    private fun queuedMessage(id: String, position: Long, content: String) = ChatMessage(
        id = id,
        role = MessageRole.user,
        content = content,
        clientMessageText = content,
        deliveryState = "queued",
        queuePosition = position
    )
}
