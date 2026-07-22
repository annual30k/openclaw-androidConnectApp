package com.rethinkingstudio.clawlink.ui.screens.chat

import com.rethinkingstudio.clawlink.ui.screens.chat.components.SkillExpansionGuideFile
import com.rethinkingstudio.clawlink.ui.screens.chat.components.SkillExpansionGuideAsr
import com.rethinkingstudio.clawlink.ui.screens.chat.components.SkillExpansionScreen
import com.rethinkingstudio.clawlink.ui.screens.chat.components.visibleSkillExpansionScreensForGateway
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillExpansionFileTransferTest {
    @Test
    fun hermesAsrPromptIsPlatformAwareAndDoesNotUseOpenClawNaming() {
        val prompt = SkillExpansionGuideAsr.installPromptForGateway(isHermesGateway = true)

        assertTrue(prompt.length < 2_200)
        assertTrue(prompt.contains("CLAWCONNECT_ASR_COMMAND"))
        assertTrue(prompt.contains("原生 Windows（PowerShell）"))
        assertTrue(prompt.contains("sensevoice-venv\\Scripts\\python.exe"))
        assertTrue(prompt.contains("`py -0p`、`Get-Command`"))
        assertTrue(prompt.contains("Python `tarfile` 解压"))
        assertTrue(prompt.contains("平台选择硬门禁"))
        assertTrue(prompt.contains("只根据原始 stdout 锁定平台"))
        assertTrue(prompt.contains("macOS：用 `sw_vers`"))
        assertTrue(prompt.contains("Linux/WSL：读取 `/etc/os-release`"))
        assertTrue(prompt.contains("~/.clawconnect/sensevoice-venv/bin/python"))
        assertTrue(prompt.contains("禁止在原生 Windows 后台使用 WSL 路径"))
        assertTrue(prompt.contains("Windows Task Scheduler"))
        assertTrue(prompt.contains("禁止 `uname`、`command -v`、`which`、`/proc`、`${'$'}SHELL`"))
        assertTrue(prompt.contains("imageio-ffmpeg"))
        assertTrue(prompt.contains("[IO.File]::Copy(${'$'}tempEnv, ${'$'}envFile, ${'$'}true)"))
        assertTrue(prompt.contains("禁止 `Move-Item -Force`"))
        assertTrue(prompt.contains("阶段回执协议（禁止静默执行）"))
        assertTrue(prompt.contains("语音识别安装失败"))
        assertTrue(prompt.contains("语音识别技能已安装，Hermes 保持在线。请发一条语音试试吧。"))
        assertTrue(prompt.contains("宿主机链路已就绪，等待手机语音验证"))
        assertTrue(SkillExpansionGuideAsr.steps[1].detail.contains("Windows：%USERPROFILE%\\.clawconnect\\.env"))
        assertTrue(SkillExpansionGuideAsr.steps[1].detail.contains("macOS/Linux：~/.clawconnect/.env"))
        assertTrue(prompt.contains("下一条 `chat.voice.send` 动态加载"))
        assertFalse(prompt.contains("clawconnect restart-hermes"))
        assertTrue(prompt.contains("clawconnect status --profile hermes"))
        assertTrue(prompt.contains("配对安全边界（优先级最高）"))
        assertTrue(prompt.contains("名称含 `reset`、`pair`、`uninstall`"))
        assertTrue(prompt.contains("不得修改配对、Relay、Gateway ID 或凭据"))
        assertTrue(prompt.contains("精确更新 `.clawconnect/.env` 中的 `CLAWCONNECT_ASR_COMMAND`"))
        assertTrue(prompt.contains("不需要重启"))
        assertTrue(prompt.contains("首个工具调用必须是只读的 `clawconnect status --profile hermes`"))
        assertFalse(prompt.contains("openclaw", ignoreCase = true))
        assertFalse(prompt.contains("clawconnect reset", ignoreCase = true))
        assertFalse(prompt.contains("reset-hermes", ignoreCase = true))
        assertFalse(prompt.contains("pair-hermes", ignoreCase = true))
        assertFalse(prompt.contains("clawconnect stop", ignoreCase = true))
    }

    @Test
    fun hermesShowsFileTransferSkillExpansion() {
        assertTrue(visibleSkillExpansionScreensForGateway(isHermesGateway = true).contains(SkillExpansionScreen.FILE_TRANSFER))
        assertTrue(visibleSkillExpansionScreensForGateway(isHermesGateway = true).contains(SkillExpansionScreen.VOICE_RECOGNITION))
    }

    @Test
    fun hermesFileTransferPromptCreatesHermesSkill() {
        val prompt = SkillExpansionGuideFile.hermesInstallPrompt

        assertTrue(SkillExpansionGuideFile.installPromptForGateway(isHermesGateway = true) == SkillExpansionGuideFile.hermesInstallPrompt)
        assertTrue(SkillExpansionGuideFile.installPromptForGateway(isHermesGateway = false) == SkillExpansionGuideFile.installPrompt)
        assertTrue(prompt.contains("创建或更新 `file-transfer` 技能"))
        assertTrue(prompt.contains("version: 1.3.0"))
        assertTrue(prompt.contains("clawconnect send-file --profile hermes --json \"<本地文件绝对路径>\""))
        assertTrue(prompt.contains("--profile hermes"))
        assertTrue(prompt.contains("不要使用 `delegate_task`、子代理、`execute_code`、Python、Node.js、shell 脚本或包装命令"))
        assertTrue(prompt.contains("这个请求就是发送意图确认"))
        assertTrue(prompt.contains("pending_approval"))
        assertTrue(prompt.contains("approval_pending"))
        assertTrue(prompt.contains("不要换用 `execute_code`、脚本包装、聊天附件或最终回答本地路径绕过审批"))
        assertTrue(prompt.contains("不要在 ClawLink/移动端触发的回合里添加 `--session` 或 `--source-run-id`"))
        assertTrue(prompt.contains("CLAWCONNECT_CHAT_SESSION_KEY"))
        assertTrue(prompt.contains("CLAWCONNECT_SOURCE_RUN_ID"))
        assertTrue(prompt.contains("只在最终回答里输出本地路径并不会发送文件"))
        assertFalse(prompt.contains("skill-creator"))
        assertFalse(prompt.contains("~/.openclaw"))
        assertFalse(prompt.contains("OpenClaw"))
        assertFalse(prompt.contains("删除"))
        assertFalse(prompt.contains("默认会话选择规则"))
        assertFalse(prompt.contains("不传时默认使用最近活跃会话"))
        assertFalse(prompt.contains("桌面"))
        assertFalse(prompt.contains("Desktop"))
    }

    @Test
    fun openClawFileTransferPromptCreatesFixedOpenClawSkill() {
        val prompt = SkillExpansionGuideFile.installPrompt

        assertTrue(prompt.contains("创建或更新 OpenClaw 的 `file-transfer` 技能"))
        assertTrue(prompt.contains("~/.openclaw/workspace/.agents/skills/file-transfer/SKILL.md"))
        assertTrue(prompt.contains("version: 1.3.0"))
        assertTrue(prompt.contains("clawconnect --help"))
        assertTrue(prompt.contains("clawconnect send-file --help"))
        assertTrue(prompt.contains("clawconnect send-file --profile openclaw --json \"<本地文件绝对路径>\""))
        assertTrue(prompt.contains("必须在最终回复前执行这个命令"))
        assertTrue(prompt.contains("只在最终回答里输出本地路径并不会发送文件"))
        assertTrue(prompt.contains("不要使用 `delegate_task`、子代理、`execute_code`、Python、Node.js、shell 脚本或包装命令"))
        assertTrue(prompt.contains("不要用聊天附件、`chat.send` attachments、Markdown 图片链接或最终回答里的本地路径替代文件发送"))
        assertTrue(prompt.contains("不要在 ClawLink/移动端触发的回合里添加 `--session` 或 `--source-run-id`"))
        assertTrue(prompt.contains("CLAWCONNECT_CHAT_SESSION_KEY"))
        assertTrue(prompt.contains("CLAWCONNECT_SOURCE_RUN_ID"))
        assertTrue(prompt.contains("不要写到 `~/.codex/skills`"))
        assertTrue(prompt.contains("如果文件在 iPhone/手机上"))
        assertFalse(prompt.contains("clawconnect send-file <本地路径>"))
        assertFalse(prompt.contains("默认会话选择规则"))
        assertFalse(prompt.contains("不传时默认使用最近活跃会话"))
        assertFalse(prompt.contains("长期记忆"))
    }
}
