package com.rethinkingstudio.clawlink.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
                icon = Icons.Default.FileDownload,
                title = stringResource(R.string.usage_guide_step_1_title),
                description = stringResource(R.string.usage_guide_step_1_desc),
                accentColor = Color(0xFF2E83EE)
            ) {
                CodeBlock(
                    label = stringResource(R.string.usage_guide_step_1_label),
                    code = "npm install -g clawconnect-agent",
                    onCopy = { clipboardManager.setText(AnnotatedString(it)) }
                )
            }

            // Step 2
            StepCard(
                stepNumber = "02",
                icon = Icons.Default.QrCodeScanner,
                title = stringResource(R.string.usage_guide_step_2_title),
                description = stringResource(R.string.usage_guide_step_2_desc),
                accentColor = Color(0xFF5DCF7A)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    CodeBlock(
                        label = stringResource(R.string.usage_guide_step_2_label_scan),
                        code = "clawconnect pair",
                        onCopy = { clipboardManager.setText(AnnotatedString(it)) }
                    )
                    CodeBlock(
                        label = stringResource(R.string.usage_guide_step_2_label_manual),
                        code = "clawconnect pair --code-only",
                        onCopy = { clipboardManager.setText(AnnotatedString(it)) }
                    )
                }
            }

            // Step 3
            StepCard(
                stepNumber = "03",
                icon = Icons.Default.Bolt,
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
                        label = stringResource(R.string.usage_guide_step_3_label_fg),
                        code = "clawconnect run",
                        onCopy = { clipboardManager.setText(AnnotatedString(it)) }
                    )
                }
            }

            // What's Next section
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.32f))
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
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun StepCard(
    stepNumber: String,
    icon: ImageVector,
    title: String,
    description: String,
    accentColor: Color,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.32f))
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Accent side bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .align(Alignment.CenterVertically)
                    .padding(vertical = 20.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(accentColor)
            )

            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = accentColor.copy(alpha = 0.1f),
                        modifier = Modifier.size(36.dp, 24.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                stepNumber,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = accentColor
                            )
                        }
                    }
                    Icon(icon, null, tint = accentColor, modifier = Modifier.size(18.dp))
                }

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

                content()
            }
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
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.26f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                code,
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
