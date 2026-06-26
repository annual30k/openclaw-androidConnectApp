package com.rethinkingstudio.clawlink.ui.screens.chat

import com.rethinkingstudio.clawlink.ui.screens.chat.components.SkillExpansionGuideFile
import com.rethinkingstudio.clawlink.ui.screens.chat.components.SkillExpansionScreen
import com.rethinkingstudio.clawlink.ui.screens.chat.components.visibleSkillExpansionScreensForGateway
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillExpansionFileTransferTest {
    @Test
    fun hermesShowsFileTransferSkillExpansion() {
        assertTrue(visibleSkillExpansionScreensForGateway(isHermesGateway = true).contains(SkillExpansionScreen.FILE_TRANSFER))
        assertTrue(visibleSkillExpansionScreensForGateway(isHermesGateway = true).contains(SkillExpansionScreen.VOICE_RECOGNITION))
    }

    @Test
    fun hermesFileTransferPromptCreatesHermesSkill() {
        val prompt = SkillExpansionGuideFile.hermesInstallPrompt

        assertTrue(prompt.contains("创建或更新 `file-transfer` 技能"))
        assertTrue(prompt.contains("clawconnect send-file"))
        assertTrue(prompt.contains("--profile hermes"))
        assertFalse(prompt.contains("删除"))
    }
}
