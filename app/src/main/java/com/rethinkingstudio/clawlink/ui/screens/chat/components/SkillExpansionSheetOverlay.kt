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
import com.rethinkingstudio.clawlink.ui.screens.chat.ChatColors
import kotlinx.coroutines.launch

data class SkillExpansionGuideStep(
    val id: String,
    val number: Int,
    val title: String,
    val detail: String
)

object SkillExpansionGuideFile {
    val title = "启动文件互传"
    val heroTitle = "让 OpenClaw 帮你收发文件"
    val heroSubtitle = "不用手写技能内容，点一下就能让它自己创建并验证。"
    val installButtonTitle = "让 OpenClaw 安装文件互传"
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

    val steps = listOf(
        SkillExpansionGuideStep("understand-capability", 1, "了解这个能力", "配置完成后，ClawLink 可以把电脑上的文件通过 `clawconnect send-file` 发到手机，手机端也能把文件通过附件入口回传到聊天中。"),
        SkillExpansionGuideStep("confirm-source", 2, "确认技能来源", "这个能力依赖 `skill-creator` 和 `clawconnect-agent` 的 send-file 流程，先确认工具已安装，再按固定模板生成正确的 `file-transfer` 技能。"),
        SkillExpansionGuideStep("install-verify", 3, "安装并验证文件互传", "点底部按钮后，OpenClaw 会先读懂 `clawconnect send-file` 的用法，再自己生成 `file-transfer` 技能、验证 relay 上传流程，并把偏好写进记忆。")
    )
}

object SkillExpansionGuideVoice {
    val title = "配置语音发送"
    val heroTitle = "让 OpenClaw 把回复变成语音"
    val heroSubtitle = "先装 edge-tts-universal，再把默认音色和语速写进配置，受限时自动回退到系统语音。"
    val installButtonTitle = "让 OpenClaw 配置语音发送"
    val installPrompt = """
    先确认 OpenClaw 里是否已经可用 `skill-creator`；如果可用，直接使用 OpenClaw 的 `skill-creator`，把 `edge-tts-universal` 技能写到 OpenClaw 的技能存放目录 `~/.openclaw/workspace/.agents/skills/edge-tts-universal/SKILL.md`。如果本地已经存在 `edge-tts-universal` skill，先判断是否需要更新；如果不需要更新，就直接告诉用户本地已存在，不要重复创建。不要写到 `~/.codex/skills`，也不要放到 Codex 的技能目录里。

    请在用户电脑上完成语音转文字/转语音能力的准备工作，目标是安装并验证 `edge-tts-universal`，并在安装后向移动端发送一条测试数据确认链路可用。

    当前默认配置如下：
    - 音色：留空（自动选择）
    - 语速：0%

    执行顺序如下：

    1. 先检查是否已经存在对应技能
    - 如果当前环境里已经有用于语音转语音/转语音插件安装的技能，或者已经存在同名的相关技能，请不要重复创建
    - 如果技能已存在，只需要更新或直接复用，不要再调用 `skill-creator` 重新创建同一个技能
    - 只有在技能不存在时，才使用 OpenClaw 的 `skill-creator` 来创建新技能

    2. 如果需要创建技能，必须使用 `skill-creator`
    - 创建新技能时，明确使用 OpenClaw 的 `skill-creator`
    - 不要手写成普通文本说明，也不要用其他创建方式替代
    - 技能内容要围绕安装、验证和使用 `edge-tts-universal` 展开
    - 把默认语速写入 `OPENCLAW_TTS_RATE`
    - 如果 `edge-tts-universal` 的语音生成能力受到限制，就继续使用系统自带的语音生成做兜底

    3. 检查 Node.js 是否可用
    - 依次执行 `node -v` 和 `npm -v`
    - 如果 `node` 或 `npm` 不可用，或者版本过旧，请先安装最新稳定版 Node.js
    - 安装完成后再次确认 `node` 和 `npm` 可正常使用

    4. 安装 `edge-tts-universal`
    - 使用下面的命令安装：
      `npm install -g edge-tts-universal`
    - 如果已经安装过，先判断是否需要重新安装或升级
    - 尽量不要破坏用户现有环境；如果需要额外权限，请先说明原因

    5. 验证安装是否成功
    - 确认 `edge-tts-universal` 安装完成
    - 给出一个最小可执行的验证方式，证明后续可以用它生成语音
    - 如果需要，先在本机生成一条最小测试数据
    - 如果生成触发限制，就回退到系统自带语音生成，仍然把测试数据走完

    6. 发送测试数据到移动端
    - 在确认安装和功能都可用后，把一条测试数据发到移动端，通过clawconnect-agent发送
    - 测试数据要能用于确认手机端已经收到，且跨设备发送链路正常
    - 如果测试数据是音频、文件或其他可展示内容，请走 OpenClaw 现有的跨设备传输流程
    - 不要只在本机完成测试，必须实际发到移动端验证

    7. 输出结果
    - 简要说明 Node.js 是否已安装、`edge-tts-universal` 是否安装成功
    - 简要说明测试数据是否已成功发送到移动端
    - 如果失败，请说明失败点和下一步建议

    如果系统支持长期记忆，请把下面这条偏好写入 OpenClaw 的长期记忆：
    - 以后遇到“把电脑上的语音回复发到手机”或“在 ClawLink / OpenClaw 里配置语音发送”的需求，优先使用 `edge-tts-universal` skill。

    如果环境里已经存在对应技能，就直接复用或更新，不要重复创建；只有不存在时，才用 `skill-creator` 创建。
    """.trimIndent()

    val steps = listOf(
        SkillExpansionGuideStep("understand-capability", 1, "了解这个能力", "先把 `edge-tts-universal`、默认音色和系统语音的兜底关系理清楚，这样语音回复在生成受限时也不会断。"),
        SkillExpansionGuideStep("confirm-source", 2, "确认技能来源", "这个能力依赖 `skill-creator`、`edge-tts-universal` 和宿主机语音引擎，先确认可用再继续生成技能，并把音色和语速默认值写进去。"),
        SkillExpansionGuideStep("install-verify", 3, "安装并验证语音发送", "点底部按钮后，OpenClaw 会先安装并验证 `edge-tts-universal`，再把测试语音发到手机。")
    )
}

enum class SkillExpansionScreen {
    MENU,
    FILE_TRANSFER,
    VOICE_REPLY
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillExpansionSheetOverlay(
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
                                onDismiss = onDismiss,
                                onNavigateToFileTransfer = { currentScreen = SkillExpansionScreen.FILE_TRANSFER },
                                onNavigateToVoiceReply = { currentScreen = SkillExpansionScreen.VOICE_REPLY }
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
                                    onSendPrompt(SkillExpansionGuideFile.installPrompt)
                                    onDismiss()
                                }
                            )
                        }
                        SkillExpansionScreen.VOICE_REPLY -> {
                            SkillSetupDetailView(
                                title = SkillExpansionGuideVoice.title,
                                heroTitle = SkillExpansionGuideVoice.heroTitle,
                                heroSubtitle = SkillExpansionGuideVoice.heroSubtitle,
                                installButtonTitle = SkillExpansionGuideVoice.installButtonTitle,
                                installPrompt = SkillExpansionGuideVoice.installPrompt,
                                steps = SkillExpansionGuideVoice.steps,
                                symbol = Icons.Default.GraphicEq,
                                tint = ChatColors.linkBlue,
                                onBack = { currentScreen = SkillExpansionScreen.MENU },
                                onInstall = {
                                    onSendPrompt(SkillExpansionGuideVoice.installPrompt)
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
    onDismiss: () -> Unit,
    onNavigateToFileTransfer: () -> Unit,
    onNavigateToVoiceReply: () -> Unit
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
            SheetHeaderButton(Icons.Default.Close, "Close", onDismiss)
            Text(stringResource(R.string.chat_skill_extension_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = Color.Black)
            Box(modifier = Modifier.size(32.dp))
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Hero Card
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = Color.White.copy(alpha = 0.86f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.55f))
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
                    Text(stringResource(R.string.chat_skill_installable), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.Black)
                    Text(stringResource(R.string.chat_skill_installable_desc), style = MaterialTheme.typography.bodySmall, color = Color.Gray, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Actions Card
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = Color.White.copy(alpha = 0.86f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.55f))
        ) {
            Column {
                AdvancedFeatureRow(
                    title = SkillExpansionGuideFile.title,
                    detail = stringResource(R.string.chat_skill_extension_file_transfer),
                    icon = Icons.Default.ArrowUpward,
                    tint = Color(0xFF5ECF7A),
                    onClick = onNavigateToFileTransfer
                )
                Divider(color = Color.Black.copy(alpha = 0.06f), modifier = Modifier.padding(horizontal = 18.dp))
                AdvancedFeatureRow(
                    title = SkillExpansionGuideVoice.title,
                    detail = stringResource(R.string.chat_skill_extension_voice_reply),
                    icon = Icons.Default.GraphicEq,
                    tint = ChatColors.linkBlue,
                    onClick = onNavigateToVoiceReply
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
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = Color.Black)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            Icon(Icons.Default.ChevronRight, null, tint = Color.Gray.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
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
            SheetHeaderButton(Icons.Default.ArrowBack, "Back", onBack)
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = Color.Black)
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
                    color = Color.White.copy(alpha = 0.86f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.55f))
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
                            Text(heroTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.Black)
                            Text(heroSubtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                    }
                }
            }

            item {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = Color.White.copy(alpha = 0.86f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.55f))
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
                                    Text(step.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Color.Black)
                                    Text(step.detail, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        Text(stringResource(R.string.chat_skill_auto_install_hint), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
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
