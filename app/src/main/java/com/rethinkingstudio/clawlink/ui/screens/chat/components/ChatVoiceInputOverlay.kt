package com.rethinkingstudio.clawlink.ui.screens.chat.components

import android.media.MediaPlayer
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rethinkingstudio.clawlink.R
import com.rethinkingstudio.clawlink.ui.screens.chat.ChatColors
import com.rethinkingstudio.clawlink.ui.screens.chat.RecordedVoiceInput
import com.rethinkingstudio.clawlink.ui.screens.chat.VoiceInputPhase
import kotlin.math.abs
import kotlin.math.sin

@Composable
internal fun VoiceInputOverlay(
    phase: VoiceInputPhase,
    transcript: String,
    messageText: String,
    recording: RecordedVoiceInput?,
    audioLevel: Double,
    cancelPreview: Boolean,
    canConfirm: Boolean,
    isSending: Boolean,
    onMessageTextChange: (String) -> Unit,
    onCancel: () -> Unit,
    onContinue: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (phase == VoiceInputPhase.Idle) return

    Box(
        modifier = modifier
            .fillMaxSize()
            .modalTouchBarrier()
            .background(Color.Black.copy(alpha = 0.80f))
            .navigationBarsPadding()
    ) {
        when (phase) {
            VoiceInputPhase.Starting,
            VoiceInputPhase.Recording,
            VoiceInputPhase.Stopping -> VoiceInputRecordingOverlay(
                phase = phase,
                transcript = transcript,
                audioLevel = audioLevel,
                cancelPreview = cancelPreview,
                modifier = Modifier.fillMaxSize()
            )
            VoiceInputPhase.Confirming -> VoiceInputConfirmationOverlay(
                messageText = messageText,
                recording = recording,
                canConfirm = canConfirm,
                isSending = isSending,
                onMessageTextChange = onMessageTextChange,
                onCancel = onCancel,
                onContinue = onContinue,
                onConfirm = onConfirm,
                modifier = Modifier.fillMaxSize()
            )
            VoiceInputPhase.Idle -> Unit
        }
    }
}

@Composable
private fun VoiceInputRecordingOverlay(
    phase: VoiceInputPhase,
    transcript: String,
    audioLevel: Double,
    cancelPreview: Boolean,
    modifier: Modifier
) {
    val liveTranscript = transcript.trim().ifEmpty {
        when (phase) {
            VoiceInputPhase.Starting -> stringResource(R.string.chat_voice_preparing)
            VoiceInputPhase.Recording -> stringResource(R.string.chat_voice_recording)
            VoiceInputPhase.Stopping -> stringResource(R.string.chat_voice_sending)
            else -> ""
        }
    }
    val hintText = if (cancelPreview) {
        stringResource(R.string.chat_voice_release_cancel)
    } else {
        stringResource(R.string.chat_voice_release_send)
    }

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.fillMaxHeight(0.45f))
            SpeechStatusBubble(
                text = if (cancelPreview && transcript.isBlank()) "" else liveTranscript,
                audioLevel = audioLevel,
                cancelPreview = cancelPreview,
                isAnimating = true
            )
            Spacer(Modifier.weight(1f))
        }
        Text(
            text = hintText,
            color = Color.White.copy(alpha = 0.78f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 176.dp)
        )
        VoiceMicDock(
            cancelPreview = cancelPreview,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
        Icon(
            Icons.Default.Mic,
            contentDescription = null,
            tint = Color.Black.copy(alpha = 0.58f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 18.dp)
                .size(20.dp)
        )
    }
}

@Composable
private fun SpeechStatusBubble(
    text: String,
    audioLevel: Double,
    cancelPreview: Boolean,
    isAnimating: Boolean
) {
    val bubbleColor = if (cancelPreview) ChatColors.offline else Color(0xFFD9E8FF)
    val textColor = if (cancelPreview) Color.White else Color(0xFF243040)
    Box(
        modifier = Modifier
            .width(if (cancelPreview) 286.dp else 340.dp)
            .height(64.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawSpeechBubble(bubbleColor)
        }
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 18.dp, end = 18.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.width(16.dp).padding(start = 2.dp)) {
                val dotColor = if (cancelPreview) Color.White.copy(alpha = 0.82f) else Color(0xFFC7CEDA)
                Canvas(Modifier.size(3.dp)) { drawCircle(dotColor) }
                Canvas(Modifier.size(3.dp)) { drawCircle(dotColor) }
            }
            Text(
                text = text,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = textColor.copy(alpha = 0.96f),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            WaveformBars(
                tint = if (cancelPreview) Color.White else ChatColors.linkBlue,
                audioLevel = audioLevel,
                isAnimating = isAnimating
            )
        }
    }
}

@Composable
private fun WaveformBars(tint: Color, audioLevel: Double, isAnimating: Boolean) {
    val transition = rememberInfiniteTransition(label = "voice_waveform")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Restart),
        label = "voice_waveform_phase"
    )
    val barHeights = listOf(4f, 6f, 8f, 11f, 13f, 11f, 8f, 6f, 4f)
    Row(
        modifier = Modifier.width(51.dp).height(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        barHeights.forEachIndexed { index, base ->
            val level = audioLevel.coerceIn(0.0, 1.0).toFloat()
            val wave = if (isAnimating) {
                val quiet = 0.88f + 0.12f * abs(sin(phase * 6.28f + index * 0.63f))
                val boost = maxOf(0f, level - 0.08f)
                if (level > 0.12f) 1f + boost * 2.5f else quiet
            } else {
                1f
            }
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(maxOf(2f, base * wave).dp)
                    .background(tint, CircleShape)
            )
        }
    }
}

@Composable
private fun VoiceMicDock(cancelPreview: Boolean, modifier: Modifier) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(330.dp)
    ) {
        val width = minOf(size.width * 1.72f, 680.dp.toPx())
        val height = size.height * 1.08f
        drawOval(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = if (cancelPreview) 0.98f else 1f),
                    Color(0xFFF2F2F2).copy(alpha = if (cancelPreview) 0.96f else 0.985f),
                    Color(0xFFE8E8E8).copy(alpha = if (cancelPreview) 0.92f else 0.955f)
                ),
                start = Offset(0f, 0f),
                end = Offset(size.width, size.height)
            ),
            topLeft = Offset((size.width - width) / 2f, size.height * 0.60f),
            size = Size(width, height)
        )
    }
}

@Composable
private fun VoiceInputConfirmationOverlay(
    messageText: String,
    recording: RecordedVoiceInput?,
    canConfirm: Boolean,
    isSending: Boolean,
    onMessageTextChange: (String) -> Unit,
    onCancel: () -> Unit,
    onContinue: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier
) {
    Column(
        modifier = modifier
            .padding(top = 14.dp)
            .padding(bottom = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.fillMaxHeight(0.45f))
        Column(
            modifier = Modifier.padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            RecordedVoicePreviewBubble(recording = recording)
            if (messageText.trim().isNotEmpty()) {
                EditableSpeechBubble(
                    text = messageText,
                    onTextChange = onMessageTextChange,
                    modifier = Modifier.width(340.dp)
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 26.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            VoiceCornerAction(Icons.AutoMirrored.Filled.Undo, stringResource(R.string.chat_voice_cancel), enabled = !isSending, onClick = onCancel)
            VoiceConfirmButton(enabled = canConfirm, isSending = isSending, onClick = onConfirm)
            VoiceCornerAction(Icons.Default.GraphicEq, stringResource(R.string.chat_voice_continue), enabled = !isSending, onClick = onContinue)
        }
    }
}

@Composable
private fun RecordedVoicePreviewBubble(recording: RecordedVoiceInput?) {
    val context = LocalContext.current
    var isPlaying by remember(recording?.file?.absolutePath) { mutableStateOf(false) }
    var player by remember(recording?.file?.absolutePath) { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(recording?.file?.absolutePath) {
        onDispose {
            player?.release()
            player = null
            isPlaying = false
        }
    }

    Surface(
        onClick = {
            val file = recording?.file ?: return@Surface
            if (!file.exists() || file.length() <= 0L) return@Surface
            if (isPlaying) {
                player?.stop()
                player?.release()
                player = null
                isPlaying = false
                return@Surface
            }
            runCatching {
                val mediaPlayer = MediaPlayer().apply {
                    setDataSource(context, Uri.fromFile(file))
                    setOnCompletionListener { completed ->
                        if (player === completed) {
                            player = null
                        }
                        isPlaying = false
                        completed.release()
                    }
                    prepare()
                    start()
                }
                player = mediaPlayer
                isPlaying = true
            }.onFailure { error ->
                Toast.makeText(
                    context,
                    context.getString(R.string.chat_voice_play_failed, error.message ?: ""),
                    Toast.LENGTH_SHORT
                ).show()
                player?.release()
                player = null
                isPlaying = false
            }
        },
        enabled = recording != null,
        shape = RoundedCornerShape(22.dp),
        color = ChatColors.userBubble,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
        modifier = Modifier
            .width(340.dp)
            .height(72.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(Modifier.weight(1f))

            WaveformBars(
                tint = Color.White.copy(alpha = if (isPlaying) 0.96f else 0.70f),
                audioLevel = if (isPlaying) 0.55 else 0.0,
                isAnimating = isPlaying
            )
        }
    }
}

@Composable
private fun EditableSpeechBubble(
    text: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.height(64.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawSpeechBubble(Color(0xFFD9E8FF))
        }
        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            maxLines = 3,
            textStyle = TextStyle(
                color = Color(0xFF243040),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            ),
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 18.dp, end = 18.dp, bottom = 8.dp),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    innerTextField()
                }
            }
        )
    }
}

@Composable
private fun VoiceCornerAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(onClick = onClick, enabled = enabled, color = Color.Transparent) {
        Column(
            modifier = Modifier.width(64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = if (enabled) 0.92f else 0.42f), modifier = Modifier.size(16.dp))
            Text(label, color = Color.White.copy(alpha = if (enabled) 0.92f else 0.42f), fontSize = 12.sp, maxLines = 1)
        }
    }
}

@Composable
private fun VoiceConfirmButton(enabled: Boolean, isSending: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = Color.White,
        modifier = Modifier.size(72.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isSending) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = ChatColors.linkBlue, strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Check, contentDescription = null, tint = ChatColors.linkBlue, modifier = Modifier.size(24.dp))
            }
        }
    }
}

private fun DrawScope.drawSpeechBubble(color: Color) {
    val tailHeight = 8.dp.toPx()
    val radius = 12.dp.toPx()
    val bodyHeight = size.height - tailHeight
    val path = Path().apply {
        addRoundRect(
            androidx.compose.ui.geometry.RoundRect(
                rect = Rect(0f, 0f, size.width, bodyHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius)
            )
        )
        moveTo(size.width / 2f - 7.dp.toPx(), bodyHeight)
        lineTo(size.width / 2f, size.height)
        lineTo(size.width / 2f + 7.dp.toPx(), bodyHeight)
        close()
    }
    drawPath(path, color)
}
