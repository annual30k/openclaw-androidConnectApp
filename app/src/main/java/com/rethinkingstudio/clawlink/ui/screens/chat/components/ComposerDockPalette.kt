package com.rethinkingstudio.clawlink.ui.screens.chat.components

import androidx.compose.ui.graphics.Color

internal data class ComposerControlPalette(
    val container: Color,
    val content: Color,
    val border: Color = Color.Transparent
)

internal fun voiceHoldToSpeakPalette(
    darkTheme: Boolean,
    cancelPreview: Boolean,
    isHoldRecordingActive: Boolean
): ComposerControlPalette {
    return when {
        cancelPreview -> ComposerControlPalette(
            container = Color(0xFFE75F58).copy(alpha = 0.14f),
            content = Color(0xFFE75F58),
            border = Color(0xFFE75F58).copy(alpha = 0.34f)
        )
        isHoldRecordingActive -> ComposerControlPalette(
            container = if (darkTheme) Color(0xFF2D7D4E) else Color(0xFF0A84FF),
            content = Color.White,
            border = if (darkTheme) Color.White.copy(alpha = 0.14f) else Color(0xFF0A84FF).copy(alpha = 0.30f)
        )
        darkTheme -> ComposerControlPalette(
            container = Color(0xFF29292C),
            content = Color(0xFFC7C7CC),
            border = Color.White.copy(alpha = 0.12f)
        )
        else -> ComposerControlPalette(
            container = Color(0xFFF2F2F7).copy(alpha = 0.72f),
            content = Color(0xFF1C1C1E),
            border = Color(0xFFD8DAE1).copy(alpha = 0.36f)
        )
    }
}

internal fun composerSendActionPalette(
    darkTheme: Boolean,
    enabled: Boolean,
    isStreaming: Boolean,
    isStoppingRun: Boolean,
    hasDraft: Boolean,
    lightReadyContainer: Color,
    idleContent: Color
): ComposerControlPalette {
    return when {
        isStoppingRun -> ComposerControlPalette(
            container = Color(0xFFE75F58).copy(alpha = 0.72f),
            content = Color.White
        )
        isStreaming -> ComposerControlPalette(
            container = Color(0xFFE75F58),
            content = Color.White
        )
        hasDraft && enabled -> ComposerControlPalette(
            container = if (darkTheme) Color(0xFF298547) else lightReadyContainer,
            content = Color.White
        )
        hasDraft -> ComposerControlPalette(
            container = idleContent.copy(alpha = 0.10f),
            content = Color.White.copy(alpha = 0.9f)
        )
        else -> ComposerControlPalette(
            container = if (enabled) idleContent else idleContent.copy(alpha = 0.10f),
            content = if (enabled) Color.White else Color.White.copy(alpha = 0.9f)
        )
    }
}
