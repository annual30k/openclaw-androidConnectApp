package com.rethinkingstudio.clawlink.ui.screens.chat

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import com.rethinkingstudio.clawlink.core.state.LocalizedText.choose
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import kotlin.math.log10

internal sealed class VoiceInputError(message: String) : Exception(message) {
    data object AlreadyRecording : VoiceInputError(choose("Voice input is already in progress.", "语音输入正在进行中。"))
    data object NoSpeech : VoiceInputError(choose("No valid audio was recorded. Please try again.", "没有录到有效语音，请再试一次。"))
    data object PermissionDenied : VoiceInputError(choose("Microphone permission denied. Please enable it in Settings and try again.", "未获得麦克风权限，请到系统设置中允许后再试。"))
    data class RecordingFailed(val detail: String) : VoiceInputError(detail)
}

internal data class RecordedVoiceInput(
    val file: File,
    val fileName: String,
    val mimeType: String = "audio/mp4"
)

internal class ComposerSpeechCoordinator(
    private val scope: CoroutineScope,
    private val onRecordingFinished: (RecordedVoiceInput) -> Unit,
    private val onAudioLevel: (Double) -> Unit,
    private val onError: (VoiceInputError) -> Unit
) {
    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var currentSessionId: UUID? = null
    private var isStopping = false
    private var isCancelled = false
    private var audioLevelJob: Job? = null

    fun start(context: Context) {
        if (mediaRecorder != null) {
            throw VoiceInputError.AlreadyRecording
        }

        val sessionId = UUID.randomUUID()
        currentSessionId = sessionId
        isStopping = false
        isCancelled = false
        audioLevelJob?.cancel()
        audioLevelJob = null
        val file = File(context.cacheDir, "voice-input-${System.currentTimeMillis()}-${sessionId.toString().take(8)}.m4a")
        outputFile = file

        runCatching {
            val recorder = createRecorder(context).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(96_000)
                setAudioSamplingRate(44_100)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            mediaRecorder = recorder
            startAudioLevelPolling(sessionId, recorder)
        }.onFailure { error ->
            cleanup()
            deleteOutputFile()
            currentSessionId = null
            throw VoiceInputError.RecordingFailed(error.localizedMessage ?: choose("Recording failed to start. Please try again.", "录音启动失败，请重试。"))
        }
    }

    fun stop() {
        val sessionId = currentSessionId ?: return
        val recorder = mediaRecorder ?: return
        isStopping = true
        onAudioLevel(0.0)
        val recordedFile = outputFile
        runCatching {
            recorder.stop()
        }.onFailure {
            fail(sessionId, VoiceInputError.NoSpeech)
            return
        }
        complete(sessionId, recordedFile)
    }

    fun cancel() {
        isCancelled = true
        cleanup()
        deleteOutputFile()
        currentSessionId = null
        isStopping = false
        isCancelled = false
        onAudioLevel(0.0)
    }

    fun destroy() {
        cancel()
    }

    private fun startAudioLevelPolling(sessionId: UUID, recorder: MediaRecorder) {
        audioLevelJob = scope.launch {
            while (currentSessionId == sessionId && !isCancelled && !isStopping) {
                val amplitude = runCatching { recorder.maxAmplitude }.getOrDefault(0)
                val level = if (amplitude > 0) {
                    ((20.0 * log10(amplitude.toDouble()) - 36.0) / 54.0).coerceIn(0.0, 1.0)
                } else {
                    0.0
                }
                onAudioLevel(level)
                delay(80)
            }
        }
    }

    private fun complete(sessionId: UUID?, file: File?) {
        if (currentSessionId != sessionId) return
        val recordedFile = file?.takeIf { it.exists() && it.length() > 0L }
        cleanup()
        currentSessionId = null
        isStopping = false
        isCancelled = false
        onAudioLevel(0.0)
        outputFile = null
        if (recordedFile == null) {
            file?.delete()
            onError(VoiceInputError.NoSpeech)
        } else {
            onRecordingFinished(
                RecordedVoiceInput(
                    file = recordedFile,
                    fileName = recordedFile.name
                )
            )
        }
    }

    private fun fail(sessionId: UUID?, error: VoiceInputError) {
        if (currentSessionId != sessionId) return
        cleanup()
        deleteOutputFile()
        currentSessionId = null
        isStopping = false
        isCancelled = false
        onAudioLevel(0.0)
        onError(error)
    }

    private fun cleanup() {
        audioLevelJob?.cancel()
        audioLevelJob = null
        mediaRecorder?.let { recorder ->
            runCatching { recorder.reset() }
            runCatching { recorder.release() }
        }
        mediaRecorder = null
    }

    private fun deleteOutputFile() {
        outputFile?.let { file -> runCatching { file.delete() } }
        outputFile = null
    }

    private fun createRecorder(context: Context): MediaRecorder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context.applicationContext)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
    }
}
