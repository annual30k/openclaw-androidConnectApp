package com.rethinkingstudio.clawlink.ui.screens.chat.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.rethinkingstudio.clawlink.ui.screens.chat.VoiceInputPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ChatComposerDockTimelineUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun streamingActionInvokesAbort() {
        var abortCount = 0
        setComposer(isStreaming = true, onAbort = { abortCount++ })

        clickFirstContentDescription("Stop", "停止")

        assertEquals(1, abortCount)
    }

    @Test
    fun completedReplyDoesNotExposeStopAction() {
        setComposer(isStreaming = false)

        assertNoContentDescription("Stop", "停止")
    }

    @Test
    fun stoppingRunDoesNotExposeClickableStopAction() {
        var abortCount = 0
        setComposer(isStreaming = true, isStoppingRun = true, onAbort = { abortCount++ })

        assertNoContentDescription("Stop", "停止")

        assertEquals(0, abortCount)
    }

    @Test
    fun completedReplyWithDraftExposesSendNotStop() {
        setComposer(messageText = "next question", isStreaming = false, canSendMessage = true)

        assertNoContentDescription("Stop", "停止")
        assertHasContentDescription("Send", "发送")
    }

    @Test
    fun draftActionInvokesSend() {
        var sendCount = 0
        setComposer(messageText = "hello", canSendMessage = true, onSend = { sendCount++ })

        clickFirstContentDescription("Send", "发送")

        assertEquals(1, sendCount)
    }

    @Test
    fun idleActionInvokesVoice() {
        var voiceCount = 0
        setComposer(onToggleVoiceMode = { voiceCount++ })

        clickFirstContentDescription("Voice message", "语音消息")

        assertEquals(1, voiceCount)
    }

    @Test
    fun longModelNameDoesNotExpandModelPickerBeyondMaximumWidth() {
        setComposer(
            selectedModelText = "Xiaomi MiMo V2.5 Pro With A Very Long Variant Name",
            showsOpenClawControls = true,
            showsModelPicker = true
        )

        val width = composeRule.onNodeWithTag("composer_model_picker_button")
            .fetchSemanticsNode()
            .boundsInRoot
            .width
        val maximumWidth = with(composeRule.density) { 224.dp.toPx() }

        assertTrue("Model picker width was $width px", width <= maximumWidth + 1f)
    }

    private fun setComposer(
        messageText: String = "",
        selectedModelText: String = "Hermes",
        isStreaming: Boolean = false,
        isStoppingRun: Boolean = false,
        canSendMessage: Boolean = false,
        showsOpenClawControls: Boolean = false,
        showsModelPicker: Boolean = false,
        onToggleVoiceMode: () -> Unit = {},
        onSend: () -> Unit = {},
        onAbort: () -> Unit = {}
    ) {
        composeRule.setContent {
            MaterialTheme {
                Box(Modifier.width(390.dp)) {
                    ComposerDock(
                        messageText = messageText,
                        onMessageTextChange = {},
                        selectedModelText = selectedModelText,
                        isStreaming = isStreaming,
                        isStoppingRun = isStoppingRun,
                        voiceMode = false,
                        voiceInputPhase = VoiceInputPhase.Idle,
                        voiceInputCancelPreview = false,
                        showsOpenClawControls = showsOpenClawControls,
                        showsModelPicker = showsModelPicker,
                        attachments = emptyList(),
                        isUploadingAttachment = false,
                        hasActiveSession = true,
                        canEditComposer = !isStreaming && !isStoppingRun,
                        canSendMessage = canSendMessage,
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
                        onToggleVoiceMode = onToggleVoiceMode,
                        onBeginVoiceInputHold = {},
                        onEndVoiceInputHold = {},
                        onCancelVoiceInput = {},
                        onVoiceInputCancelPreviewChange = {},
                        onSend = onSend,
                        onAbort = onAbort
                    )
                }
            }
        }
    }

    private fun clickFirstContentDescription(vararg descriptions: String) {
        for (description in descriptions) {
            val nodes = composeRule.onAllNodesWithContentDescription(description, useUnmergedTree = true)
                .fetchSemanticsNodes()
            if (nodes.isNotEmpty()) {
                composeRule.onAllNodesWithContentDescription(description, useUnmergedTree = true)[0]
                    .performClick()
                return
            }
        }
        throw AssertionError("No matching content description: ${descriptions.joinToString()}")
    }

    private fun assertHasContentDescription(vararg descriptions: String) {
        for (description in descriptions) {
            val nodes = composeRule.onAllNodesWithContentDescription(description, useUnmergedTree = true)
                .fetchSemanticsNodes()
            if (nodes.isNotEmpty()) return
        }
        throw AssertionError("No matching content description: ${descriptions.joinToString()}")
    }

    private fun assertNoContentDescription(vararg descriptions: String) {
        descriptions.forEach { description ->
            composeRule.onAllNodesWithContentDescription(description, useUnmergedTree = true)
                .assertCountEquals(0)
        }
    }
}
