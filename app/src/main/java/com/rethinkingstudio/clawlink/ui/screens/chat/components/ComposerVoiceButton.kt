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
internal fun VoiceHoldToSpeakButton(
    modifier: Modifier = Modifier,
    hasDraftText: Boolean,
    canBeginHoldToSpeak: Boolean,
    voiceInputPhase: VoiceInputPhase,
    cancelPreview: Boolean,
    onBeginHold: () -> Unit,
    onEndHold: () -> Unit,
    onCancel: () -> Unit,
    onCancelPreviewChange: (Boolean) -> Unit
) {
    val isHoldRecordingActive = isVoiceHoldRecordingActive(voiceInputPhase)
    val releaseSend = stringResource(R.string.chat_voice_release_send)
    val releaseCancel = stringResource(R.string.chat_voice_release_cancel)
    val holdContinue = stringResource(R.string.chat_voice_hold_continue)
    val holdTalk = stringResource(R.string.chat_voice_hold_talk)
    val buttonText = when {
        cancelPreview -> releaseCancel
        isHoldRecordingActive -> releaseSend
        hasDraftText -> holdContinue
        else -> holdTalk
    }
    val palette = voiceHoldToSpeakPalette(
        darkTheme = isSystemInDarkTheme(),
        cancelPreview = cancelPreview,
        isHoldRecordingActive = isHoldRecordingActive
    )

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = palette.container,
        border = BorderStroke(1.dp, palette.border),
        modifier = modifier
            .height(42.dp)
            .alpha(if (canBeginHoldToSpeak || voiceInputPhase.isBusy) 1f else 0.6f)
            .pointerInput(canBeginHoldToSpeak) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (!canBeginHoldToSpeak && !voiceInputPhase.isBusy) return@awaitEachGesture
                    var didBegin = false
                    var didCancel = false
                    val heightPx = size.height.toFloat()
                    val widthPx = size.width.toFloat()
                    onBeginHold()
                    didBegin = true
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.pressed) {
                            val inside = isPointInsideVoiceRegion(
                                point = change.position,
                                width = widthPx,
                                height = heightPx
                            )
                            didCancel = !inside
                            onCancelPreviewChange(didCancel)
                            change.consume()
                        } else {
                            if (didBegin) {
                                if (didCancel) onCancel() else onEndHold()
                            }
                            onCancelPreviewChange(false)
                            change.consume()
                            break
                        }
                    }
                }
            }
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Text(
                text = buttonText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
                color = palette.content,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

internal fun isVoiceHoldRecordingActive(phase: VoiceInputPhase): Boolean {
    return phase == VoiceInputPhase.Starting ||
        phase == VoiceInputPhase.Recording ||
        phase == VoiceInputPhase.Stopping
}

internal fun isPointInsideVoiceRegion(point: Offset, width: Float, height: Float): Boolean {
    if (width <= 0f || height <= 0f) return true
    val centerX = width * 0.5f
    val centerY = height * 0.92f
    val radiusX = maxOf(width * 0.78f, 28f)
    val radiusY = maxOf(height * 2.65f, 104f)
    val dx = (point.x - centerX) / radiusX
    val dy = (point.y - centerY) / radiusY
    return dx * dx + dy * dy <= 1f
}
