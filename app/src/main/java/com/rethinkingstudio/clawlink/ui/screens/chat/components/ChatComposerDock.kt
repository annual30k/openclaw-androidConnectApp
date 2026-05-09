package com.rethinkingstudio.clawlink.ui.screens.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.rethinkingstudio.clawlink.R
import com.rethinkingstudio.clawlink.ui.screens.chat.ChatColors
import com.rethinkingstudio.clawlink.ui.screens.chat.ComposerAttachmentDraft
import com.rethinkingstudio.clawlink.ui.screens.chat.VoiceInputPhase
import com.rethinkingstudio.clawlink.ui.screens.chat.filePath
import com.rethinkingstudio.clawlink.ui.screens.chat.isImage

@Composable
internal fun ComposerDock(
    messageText: String,
    onMessageTextChange: (String) -> Unit,
    selectedModelText: String,
    isStreaming: Boolean,
    isStoppingRun: Boolean,
    voiceMode: Boolean,
    voiceInputPhase: VoiceInputPhase,
    voiceInputCancelPreview: Boolean,
    attachments: List<ComposerAttachmentDraft>,
    isUploadingAttachment: Boolean,
    hasActiveSession: Boolean,
    canEditComposer: Boolean,
    canSendMessage: Boolean,
    showAttachmentMenu: Boolean,
    onDismissAttachmentMenu: () -> Unit,
    attachmentButtonPosition: IntOffset,
    attachmentButtonSize: IntSize,
    onAttachmentButtonPositionChanged: (IntOffset) -> Unit,
    onAttachmentButtonSizeChanged: (IntSize) -> Unit,
    onPickFiles: () -> Unit,
    onPickAlbum: () -> Unit,
    onPickCamera: () -> Unit,
    onRemoveAttachment: (ComposerAttachmentDraft) -> Unit,
    onOpenModelPicker: () -> Unit,
    onShowSkillSheet: () -> Unit,
    onOpenAttachment: () -> Unit,
    onToggleVoiceMode: () -> Unit,
    onBeginVoiceInputHold: () -> Unit,
    onEndVoiceInputHold: () -> Unit,
    onCancelVoiceInput: () -> Unit,
    onVoiceInputCancelPreviewChange: (Boolean) -> Unit,
    onTextFieldFocusChanged: (Boolean) -> Unit = {},
    onSend: () -> Unit,
    onAbort: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = ChatColors.dockSurface,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, ChatColors.dockBorder)
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(start = 14.dp, top = 4.dp, end = 14.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DockPillButton(stringResource(R.string.chat_skills_extension), Icons.Default.AutoAwesome, enabled = hasActiveSession, onClick = onShowSkillSheet)
                Spacer(Modifier.weight(1f))
                DockPillButton(selectedModelText, Icons.Default.SmartToy, enabled = hasActiveSession, trailingIcon = Icons.Default.UnfoldMore, onClick = onOpenModelPicker)
            }

            if (attachments.isNotEmpty()) {
                AttachmentTray(
                    attachments = attachments,
                    isUploading = isUploadingAttachment,
                    onRemove = onRemoveAttachment
                )
            }

            if (voiceMode) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RoundIconButton(Icons.Default.Keyboard, stringResource(R.string.chat_placeholder), enabled = !voiceInputPhase.isBusy, onClick = onToggleVoiceMode)
                    VoiceHoldToSpeakButton(
                        modifier = Modifier.weight(1f),
                        hasDraftText = messageText.trim().isNotEmpty(),
                        canBeginHoldToSpeak = canEditComposer || voiceInputPhase.isBusy,
                        voiceInputPhase = voiceInputPhase,
                        cancelPreview = voiceInputCancelPreview,
                        onBeginHold = onBeginVoiceInputHold,
                        onEndHold = onEndVoiceInputHold,
                        onCancel = onCancelVoiceInput,
                        onCancelPreviewChange = onVoiceInputCancelPreviewChange
                    )
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier.onGloballyPositioned { coordinates ->
                            val topLeft = coordinates.localToRoot(Offset.Zero)
                            onAttachmentButtonPositionChanged(IntOffset(topLeft.x.toInt(), topLeft.y.toInt()))
                            onAttachmentButtonSizeChanged(coordinates.size)
                        }
                    ) {
                        RoundIconButton(Icons.Default.Add, stringResource(R.string.chat_attachment), enabled = canEditComposer, onClick = onOpenAttachment)
                    }
                    BasicTextField(
                        value = messageText,
                        onValueChange = onMessageTextChange,
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .onFocusChanged { focusState ->
                                onTextFieldFocusChanged(focusState.isFocused)
                            },
                        enabled = canEditComposer,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { onSend() }),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.Black),
                        decorationBox = { innerTextField ->
                            Surface(
                                shape = RoundedCornerShape(21.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, Color(0xFFE2E4E9))
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (messageText.isEmpty()) {
                                        Text(
                                            when {
                                                !hasActiveSession -> stringResource(R.string.chat_add_gateway_placeholder)
                                                !canSendMessage -> stringResource(R.string.gateway_status_disconnected)
                                                else -> stringResource(R.string.chat_placeholder)
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color(0xFFA0A4AF)
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        }
                    )
                    RoundIconButton(Icons.Default.Mic, stringResource(R.string.chat_voice_message), enabled = canEditComposer, onClick = onToggleVoiceMode)
                    SendButton(
                        enabled = (canSendMessage || isStreaming) && !isUploadingAttachment && !isStoppingRun && (messageText.isNotBlank() || attachments.isNotEmpty() || isStreaming),
                        isStreaming = isStreaming,
                        isStoppingRun = isStoppingRun,
                        onClick = { if (isStreaming && !isStoppingRun) onAbort() else onSend() }
                    )
                }
            }
        }
    }

    if (showAttachmentMenu) {
        AttachmentMenuPopup(
            anchorPosition = attachmentButtonPosition,
            anchorSize = attachmentButtonSize,
            onDismiss = onDismissAttachmentMenu,
            onPickAlbum = onPickAlbum,
            onPickCamera = onPickCamera,
            onPickFiles = onPickFiles
        )
    }
}
