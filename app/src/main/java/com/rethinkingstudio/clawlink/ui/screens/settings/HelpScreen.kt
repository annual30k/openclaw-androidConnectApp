package com.rethinkingstudio.clawlink.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rethinkingstudio.clawlink.R
import com.rethinkingstudio.clawlink.ui.components.ClawLinkScaffold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(
    onBack: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    var showManualConfiguration by rememberSaveable { mutableStateOf(false) }
    var expandedFaqId by rememberSaveable { mutableStateOf<String?>(null) }
    var showFullAgentPrompt by rememberSaveable { mutableStateOf(false) }
    val openClawParameters = listOf(
        ConfigurationParameter("OPENCLAW_HOME", stringResource(R.string.usage_guide_param_openclaw_home_desc)),
        ConfigurationParameter("OPENCLAW_STATE_DIR", stringResource(R.string.usage_guide_param_openclaw_state_dir_desc)),
        ConfigurationParameter("OPENCLAW_CONFIG_PATH", stringResource(R.string.usage_guide_param_openclaw_config_path_desc)),
        ConfigurationParameter("OPENCLAW_INSTALL_DIR", stringResource(R.string.usage_guide_param_openclaw_install_dir_desc)),
        ConfigurationParameter("OPENCLAW_BIN", stringResource(R.string.usage_guide_param_openclaw_bin_desc)),
        ConfigurationParameter("OPENCLAW_PACKAGE_BIN", stringResource(R.string.usage_guide_param_openclaw_package_bin_desc)),
        ConfigurationParameter("OPENCLAW_GATEWAY_PORT", stringResource(R.string.usage_guide_param_openclaw_gateway_port_desc)),
        ConfigurationParameter("CLAWCONNECT_GATEWAY_URL", stringResource(R.string.usage_guide_param_gateway_url_desc))
    )
    val hermesParameters = listOf(
        ConfigurationParameter("HERMES_HOME", stringResource(R.string.usage_guide_param_hermes_home_desc)),
        ConfigurationParameter("HERMES_BIN", stringResource(R.string.usage_guide_param_hermes_bin_desc)),
        ConfigurationParameter("HERMES_PYTHON", stringResource(R.string.usage_guide_param_hermes_python_desc)),
        ConfigurationParameter("HERMES_SKILLS_DIR", stringResource(R.string.usage_guide_param_hermes_skills_dir_desc)),
        ConfigurationParameter("CLAWCONNECT_HERMES_STATE_DB", stringResource(R.string.usage_guide_param_hermes_state_db_desc)),
        ConfigurationParameter("API_SERVER_HOST", stringResource(R.string.usage_guide_param_api_server_host_desc)),
        ConfigurationParameter("API_SERVER_PORT", stringResource(R.string.usage_guide_param_api_server_port_desc)),
        ConfigurationParameter("CLAWCONNECT_HERMES_API_URL", stringResource(R.string.usage_guide_param_hermes_api_url_desc)),
        ConfigurationParameter("CLAWCONNECT_HERMES_API_KEY", stringResource(R.string.usage_guide_param_hermes_api_key_desc))
    )
    val agentPrompt = stringResource(R.string.usage_guide_agent_prompt)

    ClawLinkScaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text(
                        stringResource(R.string.usage_guide_title),
                        fontWeight = FontWeight.Black
                    ) 
                },
                navigationIcon = {
                    Surface(
                        onClick = onBack,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.padding(start = 12.dp).size(40.dp),
                        shadowElevation = 2.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.size(20.dp))
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.usage_guide_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black
                )
                Text(
                    stringResource(R.string.usage_guide_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Step 1
            StepCard(
                stepNumber = "01",
                title = stringResource(R.string.usage_guide_step_1_title),
                description = stringResource(R.string.usage_guide_step_1_desc),
                accentColor = Color(0xFF2E83EE)
            ) {
                CodeBlock(
                    label = stringResource(R.string.usage_guide_step_1_label),
                    code = "npm install -g clawconnect-agent@latest",
                    onCopy = { clipboardManager.setText(AnnotatedString(it)) }
                )
            }

            // Step 2
            StepCard(
                stepNumber = "02",
                title = stringResource(R.string.usage_guide_step_2_title),
                description = stringResource(R.string.usage_guide_step_2_desc),
                accentColor = Color(0xFF5DCF7A)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    CodeBlock(
                        label = stringResource(R.string.usage_guide_step_2_label_openclaw),
                        code = "clawconnect pair-openclaw",
                        onCopy = { clipboardManager.setText(AnnotatedString(it)) }
                    )
                    CodeBlock(
                        label = stringResource(R.string.usage_guide_step_2_label_hermes),
                        code = "clawconnect pair-hermes",
                        onCopy = { clipboardManager.setText(AnnotatedString(it)) }
                    )
                }
            }

            // Step 3
            StepCard(
                stepNumber = "03",
                title = stringResource(R.string.usage_guide_step_3_title),
                description = stringResource(R.string.usage_guide_step_3_desc),
                accentColor = Color(0xFFF5A623)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    CodeBlock(
                        label = stringResource(R.string.usage_guide_step_3_label),
                        code = "clawconnect install",
                        onCopy = { clipboardManager.setText(AnnotatedString(it)) }
                    )
                    CodeBlock(
                        label = stringResource(R.string.usage_guide_step_3_label_status_all),
                        code = "clawconnect status-all",
                        onCopy = { clipboardManager.setText(AnnotatedString(it)) }
                    )
                }
            }

            Column(
                modifier = Modifier.padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    stringResource(R.string.usage_guide_troubleshooting_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    stringResource(R.string.usage_guide_troubleshooting_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 17.sp
                )
            }

            // Optional troubleshooting fallback. Standard installations stay zero-config.
            StepCard(
                stepNumber = null,
                title = stringResource(R.string.usage_guide_step_4_title),
                description = stringResource(R.string.usage_guide_step_4_desc),
                accentColor = Color(0xFF2E83EE)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF5DCF7A).copy(alpha = 0.1f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Default.VerifiedUser,
                                    null,
                                    tint = Color(0xFF42B969),
                                    modifier = Modifier.size(17.dp)
                                )
                                Text(
                                    stringResource(R.string.usage_guide_auto_title),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                stringResource(R.string.usage_guide_auto_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    TextButton(
                        onClick = { showManualConfiguration = !showManualConfiguration },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF2E83EE))
                    ) {
                        Icon(
                            if (showManualConfiguration) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            stringResource(
                                if (showManualConfiguration) R.string.usage_guide_manual_hide
                                else R.string.usage_guide_manual_show
                            ),
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (showManualConfiguration) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text(
                                stringResource(R.string.usage_guide_manual_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )
                            CodeBlock(
                                label = stringResource(R.string.usage_guide_env_unix),
                                code = "~/.clawconnect/.env",
                                onCopy = { clipboardManager.setText(AnnotatedString(it)) }
                            )
                            CodeBlock(
                                label = stringResource(R.string.usage_guide_env_windows),
                                code = "%USERPROFILE%\\.clawconnect\\.env",
                                onCopy = { clipboardManager.setText(AnnotatedString(it)) }
                            )
                            ParameterGuide(
                                label = stringResource(R.string.usage_guide_openclaw_env),
                                parameters = openClawParameters,
                                template = """
                                    # OPENCLAW_HOME=/path/to/openclaw-user-home
                                    # OPENCLAW_STATE_DIR=/path/to/openclaw-state
                                    # OPENCLAW_CONFIG_PATH=/path/to/openclaw.json
                                    # OPENCLAW_INSTALL_DIR=/path/to/openclaw
                                    # OPENCLAW_BIN=/path/to/openclaw
                                    # OPENCLAW_PACKAGE_BIN=/path/to/openclaw/package/dist/index.js
                                    # OPENCLAW_GATEWAY_PORT=18789
                                    # CLAWCONNECT_GATEWAY_URL=ws://127.0.0.1:18789
                                """.trimIndent(),
                                onCopyTemplate = { clipboardManager.setText(AnnotatedString(it)) }
                            )
                            ParameterGuide(
                                label = stringResource(R.string.usage_guide_hermes_env),
                                parameters = hermesParameters,
                                template = """
                                    # HERMES_HOME=/path/to/hermes-data
                                    # HERMES_BIN=/path/to/hermes
                                    # HERMES_PYTHON=/path/to/python
                                    # HERMES_SKILLS_DIR=/path/to/hermes-skills
                                    # CLAWCONNECT_HERMES_STATE_DB=/path/to/state.db
                                    # API_SERVER_HOST=127.0.0.1
                                    # API_SERVER_PORT=8642
                                    # CLAWCONNECT_HERMES_API_URL=http://127.0.0.1:8642
                                    # CLAWCONNECT_HERMES_API_KEY=
                                """.trimIndent(),
                                onCopyTemplate = { clipboardManager.setText(AnnotatedString(it)) }
                            )
                            Text(
                                stringResource(R.string.usage_guide_manual_restart),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            // What's Next section
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            null,
                            tint = Color(0xFF5DCF7A),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            stringResource(R.string.usage_guide_after_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        stringResource(R.string.usage_guide_after_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)
                    )
                    HelpFaqItem(
                        title = stringResource(R.string.usage_guide_faq_repair_title),
                        description = stringResource(R.string.usage_guide_faq_repair_desc),
                        expanded = expandedFaqId == "repair",
                        onToggle = { expandedFaqId = toggledHelpFaq(expandedFaqId, "repair") }
                    )
                    HelpFaqItem(
                        title = stringResource(R.string.usage_guide_faq_offline_title),
                        description = stringResource(R.string.usage_guide_faq_offline_desc),
                        expanded = expandedFaqId == "offline",
                        onToggle = { expandedFaqId = toggledHelpFaq(expandedFaqId, "offline") }
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                stringResource(R.string.usage_guide_agent_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                stringResource(R.string.usage_guide_agent_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            onClick = { clipboardManager.setText(AnnotatedString(agentPrompt)) },
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF42B969).copy(alpha = 0.1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(14.dp), tint = Color(0xFF278C49))
                                Text(
                                    stringResource(R.string.usage_guide_agent_copy),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF278C49)
                                )
                            }
                        }
                    }
                    Text(
                        agentPrompt,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF42B969).copy(alpha = 0.06f))
                            .padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 17.sp,
                        maxLines = helpAgentMaxLines(showFullAgentPrompt),
                        overflow = TextOverflow.Ellipsis
                    )
                    TextButton(
                        onClick = { showFullAgentPrompt = !showFullAgentPrompt },
                        modifier = Modifier.align(Alignment.End),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text(
                            stringResource(
                                if (showFullAgentPrompt) R.string.usage_guide_agent_collapse
                                else R.string.usage_guide_agent_expand
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

private data class ConfigurationParameter(
    val name: String,
    val description: String
)

internal fun toggledHelpFaq(current: String?, selected: String): String? =
    if (current == selected) null else selected

internal fun helpAgentMaxLines(isExpanded: Boolean): Int =
    if (isExpanded) Int.MAX_VALUE else 5

@Composable
private fun HelpFaqItem(
    title: String,
    description: String,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(
            onClick = onToggle,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(0.dp)
        ) {
            Text(
                title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                stringResource(if (expanded) R.string.usage_guide_collapse else R.string.usage_guide_expand),
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF2E83EE)
            )
        }
        if (expanded) {
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun ParameterGuide(
    label: String,
    parameters: List<ConfigurationParameter>,
    template: String,
    onCopyTemplate: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    onClick = { onCopyTemplate(template) },
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF2E83EE).copy(alpha = 0.1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(14.dp), tint = Color(0xFF2E83EE))
                        Text(
                            stringResource(R.string.usage_guide_copy_template),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E83EE)
                        )
                    }
                }
            }

            parameters.forEachIndexed { index, parameter ->
                if (index > 0) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f))
                }
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        parameter.name,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1F75D6)
                    )
                    Text(
                        parameter.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun StepCard(
    stepNumber: String?,
    title: String,
    description: String,
    accentColor: Color,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (stepNumber != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = accentColor.copy(alpha = 0.1f),
                        modifier = Modifier.height(40.dp).widthIn(min = 40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                stepNumber,
                                modifier = Modifier.padding(horizontal = 8.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Black,
                                color = accentColor
                            )
                        }
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp
                    )
                }
            }
            content()
        }
    }
}

@Composable
private fun CodeBlock(
    label: String,
    code: String,
    onCopy: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                modifier = Modifier.padding(vertical = 2.dp)
            ) {
                Text(
                    label,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                onClick = { onCopy(code) },
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                modifier = Modifier.height(28.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(14.dp), tint = Color(0xFF2E83EE))
                    Text(
                        stringResource(R.string.usage_guide_copy),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E83EE)
                    )
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.46f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Text(
                    code,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}
