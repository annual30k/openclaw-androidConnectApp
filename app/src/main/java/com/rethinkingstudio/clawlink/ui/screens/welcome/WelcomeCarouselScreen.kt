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
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Chat
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

private enum class WelcomeSlideKind {
    Connect,
    Session,
    Backup,
    Restart,
    Transfer,
    Office,
    Voice
}

private data class WelcomeSlide(
    val kind: WelcomeSlideKind,
    @StringRes val eyebrow: Int,
    @StringRes val title: Int,
    @StringRes val subtitle: Int,
    val tintRole: TintRole = TintRole.Primary
)

private enum class TintRole {
    Primary,
    Success,
    Warning
}

private val WelcomeSlides = listOf(
    WelcomeSlide(WelcomeSlideKind.Connect, R.string.welcome_eyebrow_connect, R.string.welcome_title_connect, R.string.welcome_subtitle_connect),
    WelcomeSlide(WelcomeSlideKind.Session, R.string.welcome_eyebrow_session, R.string.welcome_title_session, R.string.welcome_subtitle_session),
    WelcomeSlide(WelcomeSlideKind.Backup, R.string.welcome_eyebrow_backup, R.string.welcome_title_backup, R.string.welcome_subtitle_backup, TintRole.Success),
    WelcomeSlide(WelcomeSlideKind.Restart, R.string.welcome_eyebrow_restart, R.string.welcome_title_restart, R.string.welcome_subtitle_restart, TintRole.Warning),
    WelcomeSlide(WelcomeSlideKind.Transfer, R.string.welcome_eyebrow_transfer, R.string.welcome_title_transfer, R.string.welcome_subtitle_transfer),
    WelcomeSlide(WelcomeSlideKind.Office, R.string.welcome_eyebrow_office, R.string.welcome_title_office, R.string.welcome_subtitle_office, TintRole.Success),
    WelcomeSlide(WelcomeSlideKind.Voice, R.string.welcome_eyebrow_voice, R.string.welcome_title_voice, R.string.welcome_subtitle_voice)
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WelcomeCarouselScreen(
    onFinish: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { WelcomeSlides.size })
    val scope = rememberCoroutineScope()

    ClawLinkScaffold { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val compactHeight = maxHeight < 700.dp
            val horizontalPadding = if (maxWidth >= 390.dp) 20.dp else 16.dp
            val topPadding = 16.dp
            val bottomPadding = if (compactHeight) 12.dp else 16.dp
            val spacing = if (compactHeight) 12.dp else 16.dp

            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(maxWidth.coerceAtMost(560.dp))
                    .align(Alignment.TopCenter)
                    .padding(horizontal = horizontalPadding)
                    .padding(top = topPadding, bottom = bottomPadding),
                verticalArrangement = Arrangement.spacedBy(spacing)
            ) {
                WelcomeHeader(onSkip = onFinish)

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    pageSpacing = 12.dp,
                    key = { index -> WelcomeSlides[index].kind }
                ) { page ->
                    WelcomeSlideView(
                        slide = WelcomeSlides[page],
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                }

                WelcomeFooter(
                    pageIndex = pagerState.currentPage,
                    count = WelcomeSlides.size,
                    onContinue = {
                        if (pagerState.currentPage == WelcomeSlides.lastIndex) {
                            onFinish()
                        } else {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun WelcomeHeader(onSkip: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
            tonalElevation = 0.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .shadow(8.dp, CircleShape)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                )
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(Modifier.weight(1f))

        TextButton(onClick = onSkip) {
            Text(
                text = stringResource(R.string.welcome_carousel_skip),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WelcomeFooter(
    pageIndex: Int,
    count: Int,
    onContinue: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(count) { index ->
                val width by animateDpAsState(
                    targetValue = if (index == pageIndex) 22.dp else 6.dp,
                    label = "welcome-indicator-width"
                )
                Box(
                    modifier = Modifier
                        .width(width)
                        .height(6.dp)
                        .background(
                            if (index == pageIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                            RoundedCornerShape(999.dp)
                        )
                )
            }
        }

        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = Color.White
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                Color(0xFF5AC8FA)
                            )
                        ),
                        RoundedCornerShape(26.dp)
                    )
                    .padding(vertical = 17.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(if (pageIndex == count - 1) R.string.welcome_carousel_get_started else R.string.welcome_carousel_continue),
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Icon(
                    imageVector = Icons.Default.PlayCircle,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(17.dp),
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun WelcomeSlideView(
    slide: WelcomeSlide,
    modifier: Modifier = Modifier
) {
    val tint = welcomeTint(slide.tintRole)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val heroHeight = (maxHeight * 0.50f).coerceIn(
            minimumValue = if (slide.kind == WelcomeSlideKind.Backup) 178.dp else 172.dp,
            maximumValue = when (slide.kind) {
                WelcomeSlideKind.Connect -> 238.dp
                WelcomeSlideKind.Session -> 224.dp
                WelcomeSlideKind.Backup -> 238.dp
                else -> 216.dp
            }
        )
        val titleSize = if (slide.kind == WelcomeSlideKind.Connect || slide.kind == WelcomeSlideKind.Session) 26.sp else 24.sp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 0.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (slide.kind) {
                WelcomeSlideKind.Connect -> ConnectHero(Modifier.height(heroHeight))
                WelcomeSlideKind.Session -> SessionHero(Modifier.height(heroHeight))
                WelcomeSlideKind.Backup -> BackupHero(Modifier.height(heroHeight))
                WelcomeSlideKind.Restart -> RestartHero(Modifier.height(heroHeight))
                WelcomeSlideKind.Transfer -> TransferHero(Modifier.height(heroHeight))
                WelcomeSlideKind.Office -> OfficeHero(Modifier.height(heroHeight))
                WelcomeSlideKind.Voice -> VoiceHero(Modifier.height(heroHeight))
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Text(
                    text = stringResource(slide.eyebrow),
                    modifier = Modifier
                        .background(tint.copy(alpha = 0.10f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = tint
                )

                Text(
                    text = stringResource(slide.title),
                    fontSize = titleSize,
                    lineHeight = 29.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = stringResource(slide.subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 21.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SceneStage(
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
private fun ConnectHero(modifier: Modifier) {
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
private fun SessionHero(modifier: Modifier) {
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
                    icon = Icons.Default.Chat,
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
private fun BackupHero(modifier: Modifier) {
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
private fun RestartHero(modifier: Modifier) {
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
private fun TransferHero(modifier: Modifier) {
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
private fun OfficeHero(modifier: Modifier) {
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
private fun VoiceHero(modifier: Modifier) {
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
                    title = stringResource(R.string.welcome_voice_reply),
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

@Composable
private fun SoftGlow(
    color: Color,
    size: Int,
    offsetX: androidx.compose.ui.unit.Dp,
    offsetY: androidx.compose.ui.unit.Dp
) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .offset(x = offsetX, y = offsetY)
            .blur(34.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
            .background(color.copy(alpha = 0.11f), CircleShape)
    )
}

@Composable
private fun NodeCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false
) {
    Column(
        modifier = modifier
            .shadow(7.dp, RoundedCornerShape(22.dp), ambientColor = Color.Black.copy(alpha = 0.04f), spotColor = Color.Black.copy(alpha = 0.07f))
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = if (isPrimary) 0.96f else 0.90f))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier
                .size(42.dp)
                .background(if (isPrimary) tint else tint.copy(alpha = 0.12f), CircleShape)
                .padding(10.dp),
            tint = if (isPrimary) Color.White else tint
        )
        Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
        Text(subtitle, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun InsightCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color,
    badge: String,
    isPrimary: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(7.dp, RoundedCornerShape(22.dp), ambientColor = Color.Black.copy(alpha = 0.04f), spotColor = Color.Black.copy(alpha = 0.07f))
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = if (isPrimary) 0.98f else 0.90f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier
                .size(40.dp)
                .background(if (isPrimary) tint else tint.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                .padding(10.dp),
            tint = if (isPrimary) Color.White else tint
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Text(
            text = badge,
            modifier = Modifier
                .background(if (isPrimary) MaterialTheme.colorScheme.surface.copy(alpha = 0.98f) else tint.copy(alpha = 0.12f), RoundedCornerShape(999.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = tint,
            maxLines = 1
        )
    }
}

@Composable
private fun VaultCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(28.dp), ambientColor = Color.Black.copy(alpha = 0.04f), spotColor = Color.Black.copy(alpha = 0.07f))
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        tint.copy(alpha = 0.08f)
                    )
                )
            )
            .padding(horizontal = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier
                .size(46.dp)
                .background(tint.copy(alpha = 0.14f), CircleShape)
                .padding(13.dp),
            tint = tint
        )
        Text(
            text = title,
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subtitle,
            modifier = Modifier.padding(top = 3.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CapsuleLabel(
    icon: ImageVector,
    text: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(tint.copy(alpha = 0.12f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = tint)
        Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = tint, maxLines = 1)
    }
}

@Composable
private fun HeroHeaderRow(
    icon: ImageVector,
    title: String,
    trailing: String,
    tint: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier
                .size(28.dp)
                .background(tint.copy(alpha = 0.12f), CircleShape)
                .padding(7.dp),
            tint = tint
        )
        Text(title, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = tint)
        Spacer(Modifier.weight(1f))
        Text(trailing, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun TransferTile(icon: ImageVector, title: String, tint: Color) {
    Column(
        modifier = Modifier
            .size(width = 74.dp, height = 92.dp)
            .shadow(7.dp, RoundedCornerShape(22.dp), ambientColor = Color.Black.copy(alpha = 0.04f), spotColor = Color.Black.copy(alpha = 0.07f))
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
            .padding(vertical = 8.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier
                .size(38.dp)
                .background(tint.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                .padding(9.dp),
            tint = tint
        )
        Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}

@Composable
private fun TransferTray(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(24.dp), ambientColor = Color.Black.copy(alpha = 0.04f), spotColor = Color.Black.copy(alpha = 0.07f))
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                    )
                )
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            Icons.Default.Download,
            contentDescription = null,
            modifier = Modifier
                .size(38.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape)
                .padding(9.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.welcome_transfer_entry), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(stringResource(R.string.welcome_transfer_types), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DashedBridge(modifier: Modifier, color: Color) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val path = Path().apply {
            val left = Offset(size.width * 0.14f, size.height * 0.70f)
            val mid = Offset(size.width * 0.50f, size.height * 0.22f)
            val right = Offset(size.width * 0.86f, size.height * 0.70f)
            moveTo(left.x, left.y)
            cubicTo(size.width * 0.30f, size.height * 0.16f, size.width * 0.30f, size.height * 0.10f, mid.x, mid.y)
            cubicTo(size.width * 0.70f, size.height * 0.10f, size.width * 0.70f, size.height * 0.16f, right.x, right.y)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = 2.4.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 8.dp.toPx()))
            )
        )
    }
}

@Composable
private fun DashedRibbon(modifier: Modifier, color: Color) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val path = Path().apply {
            val start = Offset(size.width * 0.16f, size.height * 0.22f)
            val center = Offset(size.width * 0.50f, size.height * 0.54f)
            val end = Offset(size.width * 0.84f, size.height * 0.24f)
            moveTo(start.x, start.y)
            cubicTo(size.width * 0.34f, size.height * 0.08f, size.width * 0.32f, size.height * 0.12f, center.x, center.y)
            cubicTo(size.width * 0.68f, size.height * 0.94f, size.width * 0.66f, size.height * 0.08f, end.x, end.y)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = 2.4.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 8.dp.toPx()))
            )
        )
    }
}

@Composable
private fun welcomeTint(role: TintRole): Color {
    return when (role) {
        TintRole.Primary -> MaterialTheme.colorScheme.primary
        TintRole.Success -> MaterialTheme.colorScheme.secondary
        TintRole.Warning -> MaterialTheme.colorScheme.tertiary
    }
}

private fun androidx.compose.ui.unit.Dp.coerceIn(
    minimumValue: androidx.compose.ui.unit.Dp,
    maximumValue: androidx.compose.ui.unit.Dp
): androidx.compose.ui.unit.Dp = maxOf(minimumValue, minOf(this, maximumValue))
