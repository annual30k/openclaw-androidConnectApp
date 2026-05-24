package com.rethinkingstudio.clawlink.ui.screens.chat.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rethinkingstudio.clawlink.R
import com.rethinkingstudio.clawlink.core.state.LocalizedText.choose
import com.rethinkingstudio.clawlink.ui.screens.chat.ChatColors
import kotlinx.coroutines.launch

data class SkillExpansionGuideStep(
    val id: String,
    val number: Int,
    val title: String,
    val detail: String
)

object SkillExpansionGuideFile {
    val title get() = choose("Start file transfer", "启动文件互传")
    val heroTitle get() = choose("Let the host send and receive files", "让宿主机帮你收发文件")
    val heroSubtitle get() = choose("No need to write skill content manually. One tap lets OpenClaw create and verify it.", "不用手写技能内容，点一下就能让它自己创建并验证。")
    val installButtonTitle get() = choose("Install file transfer on host", "让宿主机安装文件互传")
    val installPrompt = """
    先确认 OpenClaw 里是否已经可用 `skill-creator`；如果可用，直接使用 OpenClaw 的 `skill-creator`，把 `file-transfer` 技能写到 OpenClaw 的技能存放目录 `~/.openclaw/workspace/.agents/skills/file-transfer/SKILL.md`。如果本地已经存在 `file-transfer` skill，先判断是否需要更新；如果不需要更新，就直接告诉用户本地已存在，不要重复创建。不要写到 `~/.codex/skills`，也不要放到 Codex 的技能目录里。

    然后执行 `clawconnect --help`，必要时再执行 `clawconnect send-file --help`，确认命令结构、参数和输出。

    接着阅读并理解当前仓库里用户可见的文档：
    - `clawconnect-agent/README.zh-CN.md`
    - `clawconnect-agent/README.md`

    如果当前环境里还能直接运行 `clawconnect`，再结合实际命令输出验证 `send-file` 的真实行为。不要依赖打包后看不到的源码文件，也不要引用内部实现路径或接口细节；以可见文档和实际运行结果为准。

    请准确总结 `clawconnect send-file` 的真实行为，不要写成猜测：
    - 它是 host 侧命令，只在 PC / Mac 上执行，不是在 Android 上执行
    - 它处理本机可访问的本地文件
    - 它会先检查文件是否存在、是否为普通文件
    - 它会把文件上传到 relay，再把它作为聊天里的文件消息发到对应会话
    - `--session <key>` 可指定会话；不传时默认使用最近活跃会话
    - `--gateway <id>` 可指定网关；不传时使用本地配置里的网关
    - 不要把 `chat.send` 的 attachments 当成跨设备文件互传入口
    - 如果文件其实在手机上，先确认文件所在设备，再走 Android App 里的附件上传入口，不要把 `clawconnect send-file` 说成能在手机本机运行

    使用 OpenClaw 的 `skill-creator` 创建或更新一个 OpenClaw skill，名称必须是 `file-transfer`，位置存放在 OpenClaw 技能存放的默认位置。不要让我手写技能内容，直接生成完整的技能文件。
    """.trimIndent()
    val hermesInstallPrompt = """
    请使用 Hermes Agent 的技能扩展机制创建或更新 `file-transfer` 技能。先执行 `hermes skills --help`、`hermes skills list` 或 Hermes 当前可用的等价命令，确认技能安装目录和安装方式；不要写到 `~/.codex/skills`，也不要假设 OpenClaw 的技能目录适用于 Hermes。

    然后执行 `clawconnect --help`，必要时再执行 `clawconnect send-file --help`，确认命令结构、参数和输出。接着阅读用户可见文档 `clawconnect-agent/README.zh-CN.md` 和 `clawconnect-agent/README.md`。如果当前环境能运行 `clawconnect`，必须结合实际命令输出验证 `send-file` 的真实行为。

    请创建或更新 Hermes 的 `file-transfer` 技能，触发范围覆盖：从 Mac / PC 向移动端发送图片或文件、ClawLink 文件互传、`clawconnect send-file`、电脑到手机发文件。技能内容必须明确：PC / Mac 侧发送文件优先使用 `clawconnect send-file <本地路径>`；这是宿主机命令，不是在手机本机执行；图片也走 host -> relay -> mobile 文件转发流程；如果文件在手机上，则使用 App 内附件上传入口。

    完成后说明 Hermes 技能是否安装成功，并总结 `clawconnect send-file` 的真实用法、默认会话选择规则和限制。
    """.trimIndent()

    val steps get() = listOf(
        SkillExpansionGuideStep("understand-capability", 1, choose("Understand this capability", "了解这个能力"), choose("After setup, ClawLink can send computer files to mobile via `clawconnect send-file`, and mobile can send files back through chat attachments.", "配置完成后，ClawLink 可以把电脑上的文件通过 `clawconnect send-file` 发到手机，手机端也能把文件通过附件入口回传到聊天中。")),
        SkillExpansionGuideStep("confirm-source", 2, choose("Confirm skill source", "确认技能来源"), choose("This depends on `skill-creator` and the `clawconnect-agent` send-file flow. Confirm tools first, then generate the correct `file-transfer` skill from the fixed template.", "这个能力依赖 `skill-creator` 和 `clawconnect-agent` 的 send-file 流程，先确认工具已安装，再按固定模板生成正确的 `file-transfer` 技能。")),
        SkillExpansionGuideStep("install-verify", 3, choose("Install and verify file transfer", "安装并验证文件互传"), choose("After tapping the bottom button, OpenClaw will understand `clawconnect send-file`, generate the `file-transfer` skill, verify relay upload, and save the preference.", "点底部按钮后，OpenClaw 会先读懂 `clawconnect send-file` 的用法，再自己生成 `file-transfer` 技能、验证 relay 上传流程，并把偏好写进记忆。"))
    )
}

object SkillExpansionGuideAsr {
    val title get() = choose("Install voice recognition", "安装语音识别")
    val heroTitle get() = choose("Let the host recognize mobile voice", "让宿主机识别手机语音")
    val heroSubtitle get() = choose("Install SenseVoice int8 on the host, write OPENCLAW_ASR_COMMAND, and verify real transcription.", "在宿主机安装 SenseVoice int8，写入 OPENCLAW_ASR_COMMAND，并验证真实转写。")
    val installButtonTitle get() = choose("Install voice recognition on host", "让宿主机安装语音识别")
    val installPrompt = """
    请在宿主机上安装并验证 ClawLink 的真实语音识别能力，不要使用测试假数据或硬编码返回值。目标是让手机发送 `chat.voice.send` 语音后，宿主机通过 `OPENCLAW_ASR_COMMAND` 转写成文字，再转发给当前网关聊天。

    先判断当前助手类型：
    - 如果是 OpenClaw，优先使用 OpenClaw 可用的 `skill-creator` 创建或更新 `clawlink-host-asr` 技能，位置放在 OpenClaw 的默认技能目录，不要写到 `~/.codex/skills`
    - 如果是 Hermes Agent，优先使用 Hermes 自己的技能扩展/技能安装机制创建或更新同名技能；如果 Hermes 没有创建器，就直接完成宿主机安装和配置，并说明技能创建受限

    真实安装步骤如下：
    1. 检查 `python3`、`ffmpeg`、`curl` 是否可用。macOS 优先使用 Homebrew 安装缺失项：`brew install ffmpeg`。
    2. 创建目录 `~/.clawconnect/bin` 和 `~/.clawconnect/models`，并创建隔离环境 `python3 -m venv ~/.clawconnect/sensevoice-venv`。
    3. 在隔离环境中安装真实依赖：`~/.clawconnect/sensevoice-venv/bin/python -m pip install --upgrade pip setuptools wheel`，然后安装 `sherpa-onnx soundfile numpy`。
    4. 下载官方 SenseVoice int8 模型 `sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17.tar.bz2` 到 `~/.clawconnect/models` 并解压，确认目录里存在 `model.int8.onnx` 和 `tokens.txt`。
    5. 创建可执行脚本 `~/.clawconnect/bin/transcribe-sensevoice-int8`。脚本入参为 `{file} {language} {mimeType}`，需要用 `ffmpeg` 把输入音频转成 16k 单声道 wav，再调用 `sherpa_onnx.OfflineRecognizer.from_sense_voice(..., use_itn=True)` 输出纯文本到 stdout。
    6. 写入或更新 `~/.clawconnect/.env`，路径必须换成当前宿主机的真实绝对路径，例如：
       `OPENCLAW_ASR_COMMAND=/Users/alex/.clawconnect/bin/transcribe-sensevoice-int8 {file} {language} {mimeType}`
       `OPENCLAW_SENSEVOICE_MODEL_DIR=/Users/alex/.clawconnect/models/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17`
       `OPENCLAW_SENSEVOICE_THREADS=2`
    7. 分别根据当前网关重启服务：OpenClaw 用 `clawconnect restart-openclaw`，Hermes Agent 用 `clawconnect restart-hermes`。如果两者都在使用，就两个都重启。
    8. 必须做真实验证：用一段真实音频文件或系统合成语音生成的音频跑 `~/.clawconnect/bin/transcribe-sensevoice-int8 <audio> zh-CN audio/mp4`，确认 stdout 是可读文本。不要只检查文件存在。
    9. 完成后说明模型路径、脚本路径、当前生效的 `OPENCLAW_ASR_COMMAND`，并提示用户可以从手机直接发一条语音做端到端验证。

    技能内容要明确：以后遇到 ClawLink 手机语音输入、`voice_asr_not_configured`、Hermes/OpenClaw 收不到语音转写时，优先检查并修复 `OPENCLAW_ASR_COMMAND` 这条宿主机 ASR 链路。
    """.trimIndent()

    val steps get() = listOf(
        SkillExpansionGuideStep("install-engine", 1, choose("Install recognition engine", "安装识别引擎"), choose("Install ffmpeg and sherpa-onnx, then download the real SenseVoice int8 model.", "先安装 ffmpeg、sherpa-onnx 等真实依赖，并下载真实 SenseVoice int8 模型。")),
        SkillExpansionGuideStep("write-env", 2, choose("Write host config", "写入宿主机配置"), choose("Create the host transcription script and write OPENCLAW_ASR_COMMAND into ~/.clawconnect/.env.", "创建宿主机转写脚本，并把 OPENCLAW_ASR_COMMAND 写入 ~/.clawconnect/.env。")),
        SkillExpansionGuideStep("verify-restart", 3, choose("Verify and restart", "验证并重启"), choose("Run the script against real audio, restart the selected gateway, then send voice from mobile to verify end to end.", "用真实音频跑脚本，重启当前网关，再从手机发送语音做端到端验证。"))
    )
}

enum class SkillExpansionScreen {
    MENU,
    FILE_TRANSFER,
    VOICE_RECOGNITION
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillExpansionSheetOverlay(
    isHermesGateway: Boolean = false,
    onDismiss: () -> Unit,
    onSendPrompt: (String) -> Unit
) {
    var currentScreen by remember { mutableStateOf(SkillExpansionScreen.MENU) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ChatColors.sheet,
        shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 12.dp)
                    .size(width = 48.dp, height = 5.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xFFB8BCC4))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
        ) {

                AnimatedContent(targetState = currentScreen, label = "SkillExpansionNav") { screen ->
                    when (screen) {
                        SkillExpansionScreen.MENU -> {
                            SkillExpansionMenu(
                                isHermesGateway = isHermesGateway,
                                onDismiss = onDismiss,
                                onNavigateToFileTransfer = { currentScreen = SkillExpansionScreen.FILE_TRANSFER },
                                onNavigateToVoiceRecognition = { currentScreen = SkillExpansionScreen.VOICE_RECOGNITION }
                            )
                        }
                        SkillExpansionScreen.FILE_TRANSFER -> {
                            SkillSetupDetailView(
                                title = SkillExpansionGuideFile.title,
                                heroTitle = SkillExpansionGuideFile.heroTitle,
                                heroSubtitle = SkillExpansionGuideFile.heroSubtitle,
                                installButtonTitle = SkillExpansionGuideFile.installButtonTitle,
                                installPrompt = SkillExpansionGuideFile.installPrompt,
                                steps = SkillExpansionGuideFile.steps,
                                symbol = Icons.Default.Attachment,
                                tint = Color(0xFF5ECF7A),
                                onBack = { currentScreen = SkillExpansionScreen.MENU },
                                onInstall = {
                                    onSendPrompt(if (isHermesGateway) SkillExpansionGuideFile.hermesInstallPrompt else SkillExpansionGuideFile.installPrompt)
                                    onDismiss()
                                }
                            )
                        }
                        SkillExpansionScreen.VOICE_RECOGNITION -> {
                            SkillSetupDetailView(
                                title = SkillExpansionGuideAsr.title,
                                heroTitle = SkillExpansionGuideAsr.heroTitle,
                                heroSubtitle = SkillExpansionGuideAsr.heroSubtitle,
                                installButtonTitle = SkillExpansionGuideAsr.installButtonTitle,
                                installPrompt = SkillExpansionGuideAsr.installPrompt,
                                steps = SkillExpansionGuideAsr.steps,
                                symbol = Icons.Default.Mic,
                                tint = Color(0xFFE0A52B),
                                onBack = { currentScreen = SkillExpansionScreen.MENU },
                                onInstall = {
                                    onSendPrompt(SkillExpansionGuideAsr.installPrompt)
                                    onDismiss()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

@Composable
private fun SheetHeaderButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.White,
        shadowElevation = 0.dp,
        modifier = Modifier.size(44.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = Color.Black, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun SkillExpansionMenu(
    isHermesGateway: Boolean,
    onDismiss: () -> Unit,
    onNavigateToFileTransfer: () -> Unit,
    onNavigateToVoiceRecognition: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 22.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SheetHeaderButton(Icons.Default.Close, choose("Close", "关闭"), onDismiss)
            Text(stringResource(R.string.chat_skill_extension_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
            Box(modifier = Modifier.size(32.dp))
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Hero Card
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.46f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.10f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(ChatColors.linkBlue.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.AutoAwesome, null, tint = ChatColors.linkBlue, modifier = Modifier.size(22.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.chat_skill_installable), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text(stringResource(R.string.chat_skill_installable_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Actions Card
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.46f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.10f))
        ) {
            Column {
                if (!isHermesGateway) {
                    AdvancedFeatureRow(
                        title = SkillExpansionGuideFile.title,
                        detail = stringResource(R.string.chat_skill_extension_file_transfer),
                        icon = Icons.Default.ArrowUpward,
                        tint = Color(0xFF5ECF7A),
                        onClick = onNavigateToFileTransfer
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 18.dp))
                }
                AdvancedFeatureRow(
                    title = SkillExpansionGuideAsr.title,
                    detail = stringResource(R.string.chat_skill_extension_asr),
                    icon = Icons.Default.Mic,
                    tint = Color(0xFFE0A52B),
                    onClick = onNavigateToVoiceRecognition
                )
            }
        }
    }
}

@Composable
private fun AdvancedFeatureRow(
    title: String,
    detail: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(tint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun SkillSetupDetailView(
    title: String,
    heroTitle: String,
    heroSubtitle: String,
    installButtonTitle: String,
    installPrompt: String,
    steps: List<SkillExpansionGuideStep>,
    symbol: ImageVector,
    tint: Color,
    onBack: () -> Unit,
    onInstall: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 22.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SheetHeaderButton(Icons.AutoMirrored.Filled.ArrowBack, choose("Back", "返回"), onBack)
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
            Box(modifier = Modifier.size(32.dp))
        }

        Spacer(modifier = Modifier.height(24.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.46f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.10f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(tint.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(symbol, null, tint = tint, modifier = Modifier.size(22.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(heroTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Text(heroSubtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            item {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.46f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.10f))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        steps.forEachIndexed { index, step ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = if (index < steps.size - 1) 16.dp else 0.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(tint.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(step.number.toString(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = tint)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(step.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                    Text(step.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        Text(stringResource(R.string.chat_skill_auto_install_hint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        Button(
                            onClick = onInstall,
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ChatColors.dockControl, contentColor = Color.Black)
                        ) {
                            Text(installButtonTitle, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
