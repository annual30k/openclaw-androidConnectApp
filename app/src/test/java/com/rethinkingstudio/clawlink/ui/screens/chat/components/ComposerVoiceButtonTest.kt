package com.rethinkingstudio.clawlink.ui.screens.chat.components

import androidx.compose.ui.graphics.Color
import com.rethinkingstudio.clawlink.ui.screens.chat.VoiceInputPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposerVoiceButtonTest {
    @Test
    fun confirmingPhaseIsNotTreatedAsActiveHoldToSpeak() {
        assertTrue(isVoiceHoldRecordingActive(VoiceInputPhase.Starting))
        assertTrue(isVoiceHoldRecordingActive(VoiceInputPhase.Recording))
        assertTrue(isVoiceHoldRecordingActive(VoiceInputPhase.Stopping))
        assertFalse(isVoiceHoldRecordingActive(VoiceInputPhase.Confirming))
        assertFalse(isVoiceHoldRecordingActive(VoiceInputPhase.Idle))
    }

    @Test
    fun darkVoiceHoldIdlePaletteUsesSoftSurfaceAndReadableText() {
        val palette = voiceHoldToSpeakPalette(
            darkTheme = true,
            cancelPreview = false,
            isHoldRecordingActive = false
        )

        assertEquals(Color(0xFF29292C), palette.container)
        assertEquals(Color(0xFFC7C7CC), palette.content)
        assertEquals(Color.White.copy(alpha = 0.12f), palette.border)
    }

    @Test
    fun darkSendReadyPaletteUsesMutedGreenInsteadOfWhiteSurface() {
        val palette = composerSendActionPalette(
            darkTheme = true,
            enabled = true,
            isStreaming = false,
            isStoppingRun = false,
            hasDraft = true,
            lightReadyContainer = Color.White,
            idleContent = Color.White
        )

        assertEquals(Color(0xFF298547), palette.container)
        assertEquals(Color.White, palette.content)
    }
}
