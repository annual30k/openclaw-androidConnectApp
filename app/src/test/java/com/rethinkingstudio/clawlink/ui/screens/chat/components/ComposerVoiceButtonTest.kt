package com.rethinkingstudio.clawlink.ui.screens.chat.components

import com.rethinkingstudio.clawlink.ui.screens.chat.VoiceInputPhase
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
}
