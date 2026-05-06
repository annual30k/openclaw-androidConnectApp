package com.rethinkingstudio.clawlink.ui.screens.chat

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

internal sealed class VoiceInputError(message: String) : Exception(message) {
    data object AlreadyRecording : VoiceInputError("语音输入正在进行中。")
    data object Unsupported : VoiceInputError("当前设备暂不支持语音识别。")
    data object Unavailable : VoiceInputError("语音识别服务当前不可用，请稍后再试。")
    data object NoSpeech : VoiceInputError("没有识别到有效语音，请再试一次。")
    data object PermissionDenied : VoiceInputError("未获得麦克风权限，请到系统设置中允许后再试。")
    data class RecognitionFailed(val detail: String) : VoiceInputError(detail)
}

internal class ComposerSpeechCoordinator(
    private val scope: CoroutineScope,
    private val onPartialTranscript: (String) -> Unit,
    private val onFinalTranscript: (String) -> Unit,
    private val onAudioLevel: (Double) -> Unit,
    private val onError: (VoiceInputError) -> Unit
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var currentSessionId: UUID? = null
    private var latestTranscript = ""
    private var isStopping = false
    private var isCancelled = false
    private var stopFallbackJob: Job? = null

    fun start(context: Context) {
        if (speechRecognizer != null) {
            throw VoiceInputError.AlreadyRecording
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            throw VoiceInputError.Unsupported
        }

        val sessionId = UUID.randomUUID()
        currentSessionId = sessionId
        latestTranscript = ""
        isStopping = false
        isCancelled = false
        stopFallbackJob?.cancel()
        stopFallbackJob = null

        val recognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
        recognizer.setRecognitionListener(makeListener(sessionId))
        speechRecognizer = recognizer
        runCatching {
            recognizer.startListening(makeRecognitionIntent(context))
        }.onFailure { error ->
            cleanup()
            currentSessionId = null
            throw VoiceInputError.RecognitionFailed(error.localizedMessage ?: "语音识别启动失败，请重试。")
        }
    }

    fun stop() {
        if (speechRecognizer == null || currentSessionId == null) return
        isStopping = true
        onAudioLevel(0.0)
        speechRecognizer?.stopListening()
        scheduleStopFallback(currentSessionId)
    }

    fun cancel() {
        isCancelled = true
        stopFallbackJob?.cancel()
        stopFallbackJob = null
        speechRecognizer?.cancel()
        cleanup()
        currentSessionId = null
        latestTranscript = ""
        isStopping = false
        isCancelled = false
        onAudioLevel(0.0)
    }

    fun destroy() {
        cancel()
    }

    private fun makeRecognitionIntent(context: Context): Intent {
        val preferredLanguage = listOf(
            "zh-CN",
            "zh-Hans-CN",
            "zh",
            Locale.getDefault().toLanguageTag(),
            "en-US"
        ).firstOrNull { it.isNotBlank() } ?: "zh-CN"

        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, preferredLanguage)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, preferredLanguage)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(
                RecognizerIntent.EXTRA_PROMPT,
                "ClawLink"
            )
        }
    }

    private fun makeListener(sessionId: UUID): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit

            override fun onRmsChanged(rmsdB: Float) {
                if (currentSessionId == sessionId && !isCancelled) {
                    onAudioLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f).toDouble())
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                handleTranscript(sessionId, partialResults, final = false)
            }

            override fun onResults(results: Bundle?) {
                handleTranscript(sessionId, results, final = true)
            }

            override fun onError(error: Int) {
                if (currentSessionId != sessionId || isCancelled) return
                if (isStopping && latestTranscript.isNotBlank()) {
                    complete(sessionId, latestTranscript)
                    return
                }
                fail(sessionId, mapRecognitionError(error))
            }
        }
    }

    private fun handleTranscript(sessionId: UUID, bundle: Bundle?, final: Boolean) {
        if (currentSessionId != sessionId || isCancelled) return
        val transcript = bundle
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.trim()
            .orEmpty()
        if (transcript.isBlank()) return
        latestTranscript = transcript
        onPartialTranscript(transcript)
        if (final) {
            complete(sessionId, transcript)
        }
    }

    private fun scheduleStopFallback(sessionId: UUID?) {
        stopFallbackJob?.cancel()
        stopFallbackJob = scope.launch {
            delay(2_500)
            if (currentSessionId == sessionId && isStopping) {
                complete(sessionId, latestTranscript)
            }
        }
    }

    private fun complete(sessionId: UUID?, transcript: String) {
        if (currentSessionId != sessionId) return
        stopFallbackJob?.cancel()
        stopFallbackJob = null
        cleanup()
        currentSessionId = null
        latestTranscript = ""
        isStopping = false
        isCancelled = false
        onAudioLevel(0.0)
        onFinalTranscript(transcript)
    }

    private fun fail(sessionId: UUID?, error: VoiceInputError) {
        if (currentSessionId != sessionId) return
        stopFallbackJob?.cancel()
        stopFallbackJob = null
        cleanup()
        currentSessionId = null
        latestTranscript = ""
        isStopping = false
        isCancelled = false
        onAudioLevel(0.0)
        onError(error)
    }

    private fun cleanup() {
        speechRecognizer?.setRecognitionListener(null)
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    private fun mapRecognitionError(error: Int): VoiceInputError {
        return when (error) {
            SpeechRecognizer.ERROR_AUDIO -> VoiceInputError.RecognitionFailed("麦克风会话配置失败，请稍后重试。")
            SpeechRecognizer.ERROR_CLIENT -> VoiceInputError.RecognitionFailed("语音识别初始化失败，请稍后再试。")
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> VoiceInputError.PermissionDenied
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> VoiceInputError.Unavailable
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> VoiceInputError.NoSpeech
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> VoiceInputError.RecognitionFailed("当前语音识别仍在进行中，请稍后再试。")
            SpeechRecognizer.ERROR_SERVER -> VoiceInputError.RecognitionFailed("语音识别失败，请重试。")
            else -> VoiceInputError.RecognitionFailed("语音识别失败，请重试。")
        }
    }
}
