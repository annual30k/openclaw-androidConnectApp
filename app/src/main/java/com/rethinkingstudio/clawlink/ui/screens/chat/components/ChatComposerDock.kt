package com.rethinkingstudio.clawlink.ui.screens.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.rethinkingstudio.clawlink.core.state.LocalizedText.choose
import com.rethinkingstudio.clawlink.ui.screens.chat.ChatColors
import com.rethinkingstudio.clawlink.ui.screens.chat.ComposerAttachmentDraft
import com.rethinkingstudio.clawlink.ui.screens.chat.VoiceInputPhase
import com.rethinkingstudio.clawlink.ui.screens.chat.filePath
import com.rethinkingstudio.clawlink.ui.screens.chat.isImage

@OptIn(ExperimentalMaterial3Api::class)
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
    showsOpenClawControls: Boolean = true,
    showsModelPicker: Boolean = showsOpenClawControls,
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
    var showExpandedComposer by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = ChatColors.dockSurface,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(ChatColors.dockBorder)
            )

            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(start = 14.dp, top = 8.dp, end = 14.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (showsOpenClawControls || showsModelPicker) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (showsOpenClawControls) {
                            DockPillButton(
                                text = stringResource(R.string.chat_skills_extension),
                                icon = Icons.Default.AutoAwesome,
                                enabled = hasActiveSession,
                                iconTint = MaterialTheme.colorScheme.onSurface,
                                onClick = onShowSkillSheet
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        if (showsModelPicker) {
                            DockPillButton(
                                text = selectedModelText,
                                icon = Icons.Default.SmartToy,
                                enabled = hasActiveSession,
                                trailingIcon = Icons.Default.UnfoldMore,
                                iconTint = Color(0xFF1C7A55),
                                iconContainerColor = Color(0xFFBDEDD0),
                                accentTrailingIcon = Icons.Default.WorkspacePremium,
                                onClick = onOpenModelPicker
                            )
                        }
                    }
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
                            canBeginHoldToSpeak = canEditComposer || isVoiceHoldRecordingActive(voiceInputPhase),
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
                        val hasDraft = messageText.isNotBlank() || attachments.isNotEmpty()
                        val showRunStop = isStreaming
                        val estimatedLineCount = estimateComposerLineCount(messageText)
                        val visibleLineCount = estimatedLineCount.coerceIn(1, 3)
                        val isMultilineComposer = estimatedLineCount > 1
                        val showExpandedComposerButton = estimatedLineCount > 3
                        val inputHeight = when (visibleLineCount) {
                            1 -> 42.dp
                            2 -> 66.dp
                            else -> 90.dp
                        }
                        val inputTextAlignment = if (isMultilineComposer) Alignment.TopStart else Alignment.CenterStart
                        val inputActionAlignment = if (isMultilineComposer) Alignment.BottomEnd else Alignment.CenterEnd
                        val textTrailingPadding = if (showExpandedComposerButton) 86.dp else 44.dp
                        val inputActionEnabled = when {
                            isStoppingRun -> false
                            showRunStop -> true
                            hasDraft -> canSendMessage && !isUploadingAttachment
                            else -> canEditComposer
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(inputHeight)
                        ) {
                            BasicTextField(
                                value = messageText,
                                onValueChange = onMessageTextChange,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .onFocusChanged { focusState ->
                                        onTextFieldFocusChanged(focusState.isFocused)
                                    },
                                enabled = canEditComposer,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(onSend = { onSend() }),
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                                maxLines = 3,
                                decorationBox = { innerTextField ->
                                    Surface(
                                        shape = RoundedCornerShape(21.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
                                        border = BorderStroke(1.dp, ChatColors.dockBorder)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(start = 18.dp, end = 5.dp),
                                            contentAlignment = inputTextAlignment
                                        ) {
                                            if (messageText.isEmpty()) {
                                                Text(
                                                    when {
                                                        !hasActiveSession -> stringResource(R.string.chat_add_gateway_placeholder)
                                                        !canSendMessage -> stringResource(R.string.gateway_status_disconnected)
                                                        else -> stringResource(R.string.chat_placeholder_message)
                                                    },
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                                                )
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(
                                                        top = if (isMultilineComposer) 12.dp else 0.dp,
                                                        end = textTrailingPadding
                                                    ),
                                                contentAlignment = inputTextAlignment
                                            ) {
                                                innerTextField()
                                            }
                                            ComposerInputActionButton(
                                                enabled = inputActionEnabled,
                                                isStreaming = showRunStop,
                                                isStoppingRun = isStoppingRun,
                                                hasDraft = hasDraft,
                                                onVoice = onToggleVoiceMode,
                                                onSend = onSend,
                                                onAbort = onAbort,
                                                modifier = Modifier.align(inputActionAlignment)
                                            )
                                        }
                                    }
                                }
                            )
                            if (showExpandedComposerButton) {
                                ComposerExpandButton(
                                    enabled = canEditComposer,
                                    onClick = {
                                        showExpandedComposer = true
                                        onTextFieldFocusChanged(false)
                                    },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(top = 8.dp, end = 12.dp)
                                )
                            }
                        }
                    }
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

    if (showExpandedComposer) {
        ExpandedComposerSheet(
            messageText = messageText,
            onMessageTextChange = onMessageTextChange,
            hasActiveSession = hasActiveSession,
            canEditComposer = canEditComposer,
            canSendMessage = canSendMessage,
            isUploadingAttachment = isUploadingAttachment,
            attachments = attachments,
            isStreaming = isStreaming,
            isStoppingRun = isStoppingRun,
            onDismiss = { showExpandedComposer = false },
            onToggleVoiceMode = {
                showExpandedComposer = false
                onToggleVoiceMode()
            },
            onSend = {
                onSend()
                showExpandedComposer = false
            },
            onAbort = onAbort
        )
    }
}

@Composable
private fun ComposerExpandButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.size(26.dp).alpha(if (enabled) 1f else 0.45f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Default.OpenInFull, choose("Expand editor", "展开编辑"), modifier = Modifier.size(15.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpandedComposerSheet(
    messageText: String,
    onMessageTextChange: (String) -> Unit,
    hasActiveSession: Boolean,
    canEditComposer: Boolean,
    canSendMessage: Boolean,
    isUploadingAttachment: Boolean,
    attachments: List<ComposerAttachmentDraft>,
    isStreaming: Boolean,
    isStoppingRun: Boolean,
    onDismiss: () -> Unit,
    onToggleVoiceMode: () -> Unit,
    onSend: () -> Unit,
    onAbort: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val hasDraft = messageText.isNotBlank() || attachments.isNotEmpty()
    val inputActionEnabled = when {
        isStoppingRun -> false
        isStreaming -> true
        hasDraft -> canSendMessage && !isUploadingAttachment
        else -> canEditComposer
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ChatColors.dockSurface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(44.dp))
                Spacer(Modifier.weight(1f))
                Text(
                    text = choose("Message", "消息"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.weight(1f))
                Surface(
                    onClick = onDismiss,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Close,
                            stringResource(R.string.common_action_close),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
                border = BorderStroke(1.dp, ChatColors.dockBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .heightIn(min = 220.dp)
            ) {
                BasicTextField(
                    value = messageText,
                    onValueChange = onMessageTextChange,
                    enabled = canEditComposer,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Default),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(18.dp),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.TopStart
                        ) {
                            if (messageText.isEmpty()) {
                                Text(
                                    when {
                                        !hasActiveSession -> stringResource(R.string.chat_add_gateway_placeholder)
                                        !canSendMessage -> stringResource(R.string.gateway_status_disconnected)
                                        else -> stringResource(R.string.chat_placeholder_message)
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.weight(1f))
                ComposerInputActionButton(
                    enabled = inputActionEnabled,
                    isStreaming = isStreaming,
                    isStoppingRun = isStoppingRun,
                    hasDraft = hasDraft,
                    onVoice = onToggleVoiceMode,
                    onSend = onSend,
                    onAbort = onAbort
                )
            }
        }
    }
}

private fun estimateComposerLineCount(text: String): Int {
    if (text.isEmpty()) return 1
    val charactersPerLine = 19
    return text.split('\n').sumOf { line ->
        val length = line.length.coerceAtLeast(1)
        ((length - 1) / charactersPerLine) + 1
    }.coerceAtLeast(1)
}

@Composable
private fun ComposerInputActionButton(
    enabled: Boolean,
    isStreaming: Boolean,
    isStoppingRun: Boolean,
    hasDraft: Boolean,
    onVoice: () -> Unit,
    onSend: () -> Unit,
    onAbort: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = composerSendActionPalette(
        darkTheme = isSystemInDarkTheme(),
        enabled = enabled,
        isStreaming = isStreaming,
        isStoppingRun = isStoppingRun,
        hasDraft = hasDraft,
        lightReadyContainer = MaterialTheme.colorScheme.onSurface,
        idleContent = MaterialTheme.colorScheme.onSurface
    )
    Surface(
        onClick = {
            when {
                isStreaming && !isStoppingRun -> onAbort()
                hasDraft -> onSend()
                else -> onVoice()
            }
        },
        enabled = enabled,
        shape = CircleShape,
        color = palette.container,
        contentColor = palette.content,
        modifier = modifier.size(32.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            when {
                isStoppingRun -> CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                isStreaming -> Icon(Icons.Default.Stop, choose("Stop", "停止"), modifier = Modifier.size(19.dp))
                hasDraft -> Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.chat_send), modifier = Modifier.size(19.dp))
                else -> Icon(Icons.Default.Mic, stringResource(R.string.chat_voice_message), modifier = Modifier.size(22.dp))
            }
        }
    }
}
