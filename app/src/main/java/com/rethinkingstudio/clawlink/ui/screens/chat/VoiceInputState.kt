package com.rethinkingstudio.clawlink.ui.screens.chat

internal enum class VoiceInputPhase {
    Idle,
    Starting,
    Recording,
    Stopping,
    Confirming;

    val isBusy: Boolean
        get() = this != Idle
}

internal fun composedVoiceInputText(baseText: String, transcript: String): String {
    val trimmedTranscript = transcript.trim()
    if (trimmedTranscript.isEmpty()) return baseText
    if (baseText.isEmpty()) return trimmedTranscript
    return if (baseText.last().isWhitespace()) {
        baseText + trimmedTranscript
    } else {
        "$baseText $trimmedTranscript"
    }
}
