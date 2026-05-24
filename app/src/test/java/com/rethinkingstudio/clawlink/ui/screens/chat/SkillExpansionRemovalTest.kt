package com.rethinkingstudio.clawlink.ui.screens.chat

import com.rethinkingstudio.clawlink.ui.navigation.Routes
import com.rethinkingstudio.clawlink.ui.screens.chat.components.SkillExpansionScreen
import org.junit.Assert.assertFalse
import org.junit.Test

class SkillExpansionRemovalTest {
    @Test
    fun voiceReplyExtensionAndSettingsRouteAreRemoved() {
        assertFalse(SkillExpansionScreen.values().map { it.name }.contains("VOICE_REPLY"))
        assertFalse(Routes::class.java.fields.map { it.name }.contains("VOICE_SETUP"))
    }
}
