package com.rethinkingstudio.clawlink.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rethinkingstudio.clawlink.R
import com.rethinkingstudio.clawlink.core.models.skills.SkillItem
import com.rethinkingstudio.clawlink.core.state.UserPreferencesStore
import com.rethinkingstudio.clawlink.core.state.chat.ChatStore
import com.rethinkingstudio.clawlink.core.state.gateway.GatewayStore
import com.rethinkingstudio.clawlink.core.state.skill.SkillStore

private val CardShape = RoundedCornerShape(24.dp)
private val OuterCardShape = RoundedCornerShape(28.dp)
private val AccentBlue = Color(0xFF0A84FF)
private val AccentBlueSoft = Color(0xFF5AC8FA)
private val TextPrimary = Color(0xFF111827)
private val TextSecondary = Color(0xFF6B7280)

enum class VoicePreset(val voiceIdentifier: String?, val primaryLabel: String, val secondaryLabelRes: Int) {
    AUTO(null, "Auto Select", R.string.chat_voice_settings_auto_select),
    XIAOXIAO("zh-CN-XiaoxiaoNeural", "Xiaoxiao", R.string.chat_voice_settings_lively_female),
    YUNXI("zh-CN-YunxiNeural", "Yunxi", R.string.chat_voice_settings_chinese_male),
    EMMA("en-US-EmmaMultilingualNeural", "Emma", R.string.chat_voice_settings_english_female),
    ANDREW("en-US-AndrewMultilingualNeural", "Andrew", R.string.chat_voice_settings_english_male);

    companion object {
        fun fromIdentifier(identifier: String?): VoicePreset {
            val normalized = UserPreferencesStore.normalizeVoiceIdentifier(identifier)
            return entries.firstOrNull { it.voiceIdentifier == normalized.ifBlank { null } } ?: AUTO
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceSetupScreen(
    prefsStore: UserPreferencesStore,
    gatewayStore: GatewayStore,
    skillStore: SkillStore,
    chatStore: ChatStore,
    onBack: () -> Unit
) {
    val voiceReplyEnabled by prefsStore.voiceReplyEnabled.collectAsState()
    val voiceIdentifier by prefsStore.voiceReplyVoiceIdentifier.collectAsState()
    val ratePercent by prefsStore.voiceReplyRatePercent.collectAsState()
    val gatewayState by gatewayStore.state.collectAsState()
    val skillState by skillStore.state.collectAsState()

    val gatewayId = gatewayState.selectedGatewayId
    val hasVoiceReplyGenerationSetup = skillState.skills.any { it.isVoiceReplyGenerationSkillReady() }
    val selectedPreset = VoicePreset.fromIdentifier(voiceIdentifier)

    LaunchedEffect(gatewayId) {
        if (!gatewayId.isNullOrBlank()) {
            skillStore.loadSkills(gatewayId)
        }
    }

    LaunchedEffect(voiceReplyEnabled, voiceIdentifier, ratePercent, hasVoiceReplyGenerationSetup) {
        chatStore.updateVoiceReplyConfig(
            enabled = voiceReplyEnabled,
            hasGenerationSetup = hasVoiceReplyGenerationSetup,
            voiceIdentifier = voiceIdentifier,
            ratePercent = ratePercent
        )
    }

    LaunchedEffect(voiceIdentifier, ratePercent) {
        chatStore.syncVoiceReplyConfigToRelay()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        VoiceSetupBackdrop()
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            stringResource(R.string.settings_row_voice_setup),
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_action_back))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = OuterCardShape,
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.94f)),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        VoiceReplyToggleBlock(
                            enabled = voiceReplyEnabled,
                            hasGenerationSetup = hasVoiceReplyGenerationSetup,
                            icon = Icons.Default.GraphicEq,
                            onToggle = { enabled ->
                                if (!enabled || hasVoiceReplyGenerationSetup) {
                                    prefsStore.setVoiceReplyEnabled(enabled)
                                }
                            }
                        )

                        HorizontalDivider(color = Color(0xFFE5E7EB))

                        VoiceSettingsContent(
                            selectedPreset = selectedPreset,
                            ratePercent = ratePercent,
                            onSelectPreset = { preset ->
                                prefsStore.setVoiceReplyVoiceIdentifier(preset.voiceIdentifier)
                            },
                            onRateChange = { rate ->
                                prefsStore.setVoiceReplyRatePercent(rate)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceReplyToggleBlock(
    enabled: Boolean,
    hasGenerationSetup: Boolean,
    icon: ImageVector,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(Color(0xFFF2F4F8), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = TextPrimary, modifier = Modifier.size(18.dp))
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.chat_voice_reply_title),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Text(
                if (hasGenerationSetup) {
                    stringResource(R.string.chat_voice_reply_enabled_hint)
                } else {
                    stringResource(R.string.chat_voice_reply_disabled_hint)
                },
                fontSize = 13.sp,
                color = TextSecondary,
                lineHeight = 18.sp
            )
        }

        Switch(
            checked = enabled,
            onCheckedChange = onToggle,
            enabled = hasGenerationSetup,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = AccentBlue,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFFD1D5DB),
                disabledUncheckedThumbColor = Color.White,
                disabledUncheckedTrackColor = Color(0xFFE5E7EB)
            )
        )
    }
}

@Composable
private fun VoiceSettingsContent(
    selectedPreset: VoicePreset,
    ratePercent: Int,
    onSelectPreset: (VoicePreset) -> Unit,
    onRateChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.chat_voice_settings_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Text(
                stringResource(R.string.chat_voice_settings_subtitle),
                fontSize = 13.sp,
                color = TextSecondary,
                lineHeight = 18.sp
            )
        }

        SettingsGroup {
            SectionLabel(stringResource(R.string.chat_voice_settings_voice))
            VoicePreset.entries.forEach { preset ->
                VoicePresetRow(
                    preset = preset,
                    isSelected = selectedPreset == preset,
                    onClick = { onSelectPreset(preset) }
                )
                if (preset != VoicePreset.entries.last()) {
                    HorizontalDivider(modifier = Modifier.padding(start = 16.dp), color = Color(0xFFECEFF3))
                }
            }
        }

        SettingsGroup {
            SectionLabel(stringResource(R.string.chat_voice_settings_speed))
            Slider(
                value = ratePercent.toFloat(),
                onValueChange = { onRateChange(it.toInt()) },
                valueRange = UserPreferencesStore.VOICE_REPLY_MIN_RATE_PERCENT.toFloat()..UserPreferencesStore.VOICE_REPLY_MAX_RATE_PERCENT.toFloat(),
                steps = 19,
                colors = SliderDefaults.colors(
                    thumbColor = AccentBlue,
                    activeTrackColor = AccentBlue,
                    inactiveTrackColor = Color(0xFFD7DEE8)
                )
            )
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.chat_voice_settings_slow), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
                Spacer(Modifier.weight(1f))
                Text(formatRatePercent(ratePercent), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
                Spacer(Modifier.weight(1f))
                Text(stringResource(R.string.chat_voice_settings_fast), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
            }
        }

        Text(
            stringResource(R.string.chat_voice_settings_edge_tts_hint),
            fontSize = 12.sp,
            color = TextSecondary,
            lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
    }
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
            content = content
        )
    }
}

@Composable
private fun SectionLabel(title: String) {
    Text(
        title,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = TextSecondary,
        modifier = Modifier.padding(bottom = 10.dp)
    )
}

@Composable
private fun VoicePresetRow(
    preset: VoicePreset,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), color = Color.Transparent) {
        Row(
            modifier = Modifier.padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (preset == VoicePreset.AUTO) stringResource(R.string.chat_voice_settings_auto_select) else preset.primaryLabel,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    stringResource(preset.secondaryLabelRes),
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            }
            if (isSelected) {
                Icon(Icons.Default.Check, null, tint = AccentBlue, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun VoiceSetupBackdrop() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFFF2F5FA), Color.White),
                    start = androidx.compose.ui.geometry.Offset(0f, 0f),
                    end = androidx.compose.ui.geometry.Offset(1000f, 1000f)
                )
            )
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(320.dp)
                .offset(x = 60.dp, y = (-60).dp)
                .graphicsLayer(alpha = 0.45f)
                .background(
                    Brush.radialGradient(
                        colors = listOf(AccentBlue.copy(alpha = 0.25f), Color.Transparent),
                        radius = Float.POSITIVE_INFINITY
                    ),
                    CircleShape
                )
                .blur(80.dp)
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(340.dp)
                .offset(x = (-80).dp, y = 80.dp)
                .graphicsLayer(alpha = 0.4f)
                .background(
                    Brush.radialGradient(
                        colors = listOf(AccentBlueSoft.copy(alpha = 0.22f), Color.Transparent),
                        radius = Float.POSITIVE_INFINITY
                    ),
                    CircleShape
                )
                .blur(90.dp)
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(500.dp)
                .offset(x = 100.dp, y = 150.dp)
                .graphicsLayer(alpha = 0.25f)
                .background(
                    Brush.radialGradient(
                        colors = listOf(AccentBlue.copy(alpha = 0.1f), Color.Transparent)
                    ),
                    CircleShape
                )
                .blur(120.dp)
        )
    }
}

private fun SkillItem.isVoiceReplyGenerationSkillReady(): Boolean {
    if (blockedByAllowlist || !isEnabled || eligible == false || !missing.isEmpty) return false
    val key = effectiveKey.trim().lowercase()
    val name = effectiveName.trim().lowercase()
    return key == "edge-tts-universal" ||
        name == "edge-tts-universal" ||
        key.contains("edge-tts-universal") ||
        name.contains("edge-tts-universal")
}

@Composable
private fun formatRatePercent(ratePercent: Int): String {
    return if (ratePercent == 0) {
        stringResource(R.string.common_default)
    } else {
        "${if (ratePercent > 0) "+" else ""}$ratePercent%"
    }
}
