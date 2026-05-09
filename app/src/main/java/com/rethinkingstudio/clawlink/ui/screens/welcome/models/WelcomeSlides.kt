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

internal enum class WelcomeSlideKind {
    Connect,
    Session,
    Backup,
    Restart,
    Transfer,
    Office,
    Voice
}

internal data class WelcomeSlide(
    val kind: WelcomeSlideKind,
    @StringRes val eyebrow: Int,
    @StringRes val title: Int,
    @StringRes val subtitle: Int,
    val tintRole: TintRole = TintRole.Primary
)

internal enum class TintRole {
    Primary,
    Success,
    Warning
}

internal val WelcomeSlides = listOf(
    WelcomeSlide(WelcomeSlideKind.Connect, R.string.welcome_eyebrow_connect, R.string.welcome_title_connect, R.string.welcome_subtitle_connect),
    WelcomeSlide(WelcomeSlideKind.Session, R.string.welcome_eyebrow_session, R.string.welcome_title_session, R.string.welcome_subtitle_session),
    WelcomeSlide(WelcomeSlideKind.Backup, R.string.welcome_eyebrow_backup, R.string.welcome_title_backup, R.string.welcome_subtitle_backup, TintRole.Success),
    WelcomeSlide(WelcomeSlideKind.Restart, R.string.welcome_eyebrow_restart, R.string.welcome_title_restart, R.string.welcome_subtitle_restart, TintRole.Warning),
    WelcomeSlide(WelcomeSlideKind.Transfer, R.string.welcome_eyebrow_transfer, R.string.welcome_title_transfer, R.string.welcome_subtitle_transfer),
    WelcomeSlide(WelcomeSlideKind.Office, R.string.welcome_eyebrow_office, R.string.welcome_title_office, R.string.welcome_subtitle_office, TintRole.Success),
    WelcomeSlide(WelcomeSlideKind.Voice, R.string.welcome_eyebrow_voice, R.string.welcome_title_voice, R.string.welcome_subtitle_voice)
)
