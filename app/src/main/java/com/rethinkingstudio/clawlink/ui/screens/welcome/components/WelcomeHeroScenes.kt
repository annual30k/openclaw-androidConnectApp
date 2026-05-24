package com.rethinkingstudio.clawlink.ui.screens.welcome

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rethinkingstudio.clawlink.R
import com.rethinkingstudio.clawlink.ui.components.ClawLinkScaffold
import kotlinx.coroutines.launch

@Composable
internal fun SceneStage(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
internal fun ConnectHero(modifier: Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val success = MaterialTheme.colorScheme.secondary
    val warning = MaterialTheme.colorScheme.tertiary

    SceneStage(modifier) {
        Box(Modifier.fillMaxSize().padding(14.dp), contentAlignment = Alignment.Center) {
            SoftGlow(color = primary, size = 180, offsetX = (-82).dp, offsetY = (-56).dp)
            SoftGlow(color = warning, size = 160, offsetX = 90.dp, offsetY = 48.dp)
            DashedBridge(Modifier.size(width = 264.dp, height = 170.dp), primary)

            NodeCard(
                title = stringResource(R.string.welcome_connect_phone),
                subtitle = stringResource(R.string.app_name),
                icon = Icons.Default.PhoneAndroid,
                tint = primary,
                modifier = Modifier
                    .size(width = 100.dp, height = 108.dp)
                    .offset(x = (-102).dp, y = 32.dp)
            )
            NodeCard(
                title = stringResource(R.string.welcome_connect_gateway),
                subtitle = "OpenClaw",
                icon = Icons.Default.Router,
                tint = success,
                isPrimary = true,
                modifier = Modifier
                    .size(width = 114.dp, height = 120.dp)
                    .offset(y = (-50).dp)
            )
            NodeCard(
                title = stringResource(R.string.welcome_connect_remote),
                subtitle = stringResource(R.string.welcome_connect_worker),
                icon = Icons.Default.DesktopWindows,
                tint = warning,
                modifier = Modifier
                    .size(width = 100.dp, height = 108.dp)
                    .offset(x = 102.dp, y = 32.dp)
            )
        }
    }
}

@Composable
internal fun SessionHero(modifier: Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val success = MaterialTheme.colorScheme.secondary

    SceneStage(modifier) {
        Box(Modifier.fillMaxSize().padding(14.dp), contentAlignment = Alignment.Center) {
            SoftGlow(color = primary, size = 194, offsetX = (-76).dp, offsetY = (-50).dp)
            SoftGlow(color = success, size = 164, offsetX = 88.dp, offsetY = 48.dp)

            Column(
                modifier = Modifier
                    .width(250.dp)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HeroHeaderRow(
                    icon = Icons.AutoMirrored.Filled.Chat,
                    title = stringResource(R.string.welcome_session_switch),
                    trailing = stringResource(R.string.welcome_session_recent_sort),
                    tint = primary
                )
                InsightCard(
                    icon = Icons.Default.CheckCircle,
                    title = stringResource(R.string.welcome_session_main),
                    subtitle = stringResource(R.string.welcome_session_main_context),
                    tint = primary,
                    badge = stringResource(R.string.welcome_session_current_badge),
                    isPrimary = true
                )
                InsightCard(
                    icon = Icons.Default.SmartToy,
                    title = stringResource(R.string.welcome_session_new),
                    subtitle = stringResource(R.string.welcome_session_new_context),
                    tint = success,
                    badge = stringResource(R.string.welcome_session_switch_badge)
                )
            }
        }
    }
}

@Composable
internal fun BackupHero(modifier: Modifier) {
    val success = MaterialTheme.colorScheme.secondary

    SceneStage(modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CapsuleLabel(
                    icon = Icons.Default.CloudDone,
                    text = stringResource(R.string.welcome_backup_management),
                    tint = success
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.welcome_backup_max_copies),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            VaultCard(
                icon = Icons.Default.CloudDone,
                title = stringResource(R.string.welcome_backup_host_backup),
                subtitle = "openclaw.json",
                tint = success,
                modifier = Modifier.height(158.dp)
            )
        }
    }
}

@Composable
internal fun RestartHero(modifier: Modifier) {
    val warning = MaterialTheme.colorScheme.tertiary

    SceneStage(modifier) {
        Box(Modifier.fillMaxSize().padding(14.dp), contentAlignment = Alignment.Center) {
            SoftGlow(color = warning, size = 176, offsetX = 0.dp, offsetY = (-40).dp)
            Box(
                modifier = Modifier
                    .size(202.dp)
                    .background(warning.copy(alpha = 0.06f), CircleShape)
            )
            Column(
                modifier = Modifier
                    .width(244.dp)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HeroHeaderRow(
                    icon = Icons.Default.RestartAlt,
                    title = stringResource(R.string.welcome_restart_gateway),
                    trailing = stringResource(R.string.welcome_restart_auto_check),
                    tint = warning
                )
                VaultCard(
                    icon = Icons.Default.RestartAlt,
                    title = stringResource(R.string.welcome_restart_auto_track),
                    subtitle = stringResource(R.string.welcome_restart_brief_disconnect),
                    tint = warning,
                    modifier = Modifier.height(146.dp)
                )
            }
        }
    }
}

@Composable
internal fun TransferHero(modifier: Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val success = MaterialTheme.colorScheme.secondary
    val warning = MaterialTheme.colorScheme.tertiary

    SceneStage(modifier) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(contentAlignment = Alignment.Center) {
                SoftGlow(color = primary, size = 136, offsetX = 0.dp, offsetY = 0.dp)
                DashedRibbon(Modifier.size(width = 220.dp, height = 80.dp).offset(y = (-8).dp), primary)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TransferTile(Icons.Default.Photo, stringResource(R.string.welcome_transfer_photo), primary)
                    TransferTile(Icons.Default.VideoFile, stringResource(R.string.welcome_transfer_video), success)
                    TransferTile(Icons.Default.AttachFile, stringResource(R.string.welcome_transfer_file), warning)
                }
            }
            TransferTray(Modifier.offset(y = (-6).dp))
        }
    }
}

@Composable
internal fun OfficeHero(modifier: Modifier) {
    val success = MaterialTheme.colorScheme.secondary

    SceneStage(modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(164.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.86f))
            ) {
                Image(
                    painter = painterResource(R.drawable.office_background),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = 0.42f
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.62f),
                                    Color.White.copy(alpha = 0.78f)
                                )
                            )
                        )
                )
                Image(
                    painter = painterResource(R.drawable.star_working_hero),
                    contentDescription = null,
                    modifier = Modifier
                        .size(108.dp)
                        .align(Alignment.Center)
                        .offset(x = 16.dp, y = (-28).dp),
                    contentScale = ContentScale.Fit
                )
                Image(
                    painter = painterResource(R.drawable.office_desk),
                    contentDescription = null,
                    modifier = Modifier
                        .width(196.dp)
                        .align(Alignment.Center)
                        .offset(x = 20.dp, y = 24.dp),
                    contentScale = ContentScale.Fit
                )
                CapsuleLabel(
                    icon = Icons.Default.Computer,
                    text = stringResource(R.string.welcome_office_live),
                    tint = success,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                )
            }

            CapsuleLabel(
                icon = Icons.Default.SettingsEthernet,
                text = stringResource(R.string.welcome_office_gateway),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 18.dp, bottom = 10.dp)
            )
        }
    }
}

@Composable
internal fun VoiceHero(modifier: Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val success = MaterialTheme.colorScheme.secondary

    SceneStage(modifier) {
        Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            SoftGlow(color = primary, size = 176, offsetX = (-34).dp, offsetY = (-36).dp)
            SoftGlow(color = success, size = 148, offsetX = 72.dp, offsetY = 52.dp)
            Column(
                modifier = Modifier.width(250.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HeroHeaderRow(
                    icon = Icons.Default.Mic,
                    title = stringResource(R.string.welcome_voice_header),
                    trailing = stringResource(R.string.welcome_voice_hands_free),
                    tint = primary
                )
                InsightCard(
                    icon = Icons.Default.KeyboardVoice,
                    title = stringResource(R.string.welcome_voice_input),
                    subtitle = stringResource(R.string.welcome_voice_input_context),
                    tint = primary,
                    badge = stringResource(R.string.welcome_voice_badge_live),
                    isPrimary = true
                )
                InsightCard(
                    icon = Icons.Default.GraphicEq,
                    title = stringResource(R.string.welcome_voice_output),
                    subtitle = stringResource(R.string.welcome_voice_output_context),
                    tint = success,
                    badge = stringResource(R.string.welcome_voice_badge_reply)
                )
            }
        }
    }
}
