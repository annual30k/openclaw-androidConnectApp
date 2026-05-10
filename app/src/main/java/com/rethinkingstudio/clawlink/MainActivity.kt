package com.rethinkingstudio.clawlink

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.rethinkingstudio.clawlink.ui.navigation.AppNavigation
import com.rethinkingstudio.clawlink.app.PocketClawTheme
import com.rethinkingstudio.clawlink.ui.components.ClawLinkBackdrop
import kotlinx.coroutines.delay

class MainActivity : AppCompatActivity() {
    private lateinit var appContainer: AppContainer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        enableEdgeToEdge()
        val app = application as ClawLinkApplication
        appContainer = app.container

        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    appContainer.chatStore.suspendWebSocket()
                }
                Lifecycle.Event.ON_START -> {
                    appContainer.chatStore.resumeWebSocket()
                }
                else -> {}
            }
        })

        setContent {
            PocketClawTheme {
                LaunchRoot(container = appContainer)
            }
        }
    }
}

private enum class LaunchPhase {
    StaticSplash,
    AnimatedSplash,
    Content
}

@Composable
private fun LaunchRoot(container: AppContainer) {
    var launchPhase by remember { mutableStateOf(LaunchPhase.StaticSplash) }

    LaunchedEffect(Unit) {
        delay(320)
        launchPhase = LaunchPhase.AnimatedSplash
        delay(1_150)
        launchPhase = LaunchPhase.Content
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .scale(if (launchPhase == LaunchPhase.Content) 1f else 0.995f)
        ) {
            ClawLinkBackdrop()
            AppNavigation(container = container)
        }

        AnimatedVisibility(
            visible = launchPhase == LaunchPhase.StaticSplash,
            enter = fadeIn(animationSpec = tween(0)),
            exit = fadeOut(animationSpec = tween(180)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            LaunchArtworkSplashView()
        }

        AnimatedVisibility(
            visible = launchPhase == LaunchPhase.AnimatedSplash,
            enter = fadeIn(animationSpec = tween(180)) + scaleIn(initialScale = 1.02f),
            exit = fadeOut(animationSpec = tween(280)) + scaleOut(targetScale = 1.02f),
            modifier = Modifier.align(Alignment.Center)
        ) {
            LaunchAnimatedSplashView()
        }
    }
}

@Composable
private fun LaunchArtworkSplashView() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.launch_splash_art),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
private fun LaunchAnimatedSplashView() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        Image(
            painter = painterResource(R.drawable.launch_crab),
            contentDescription = null,
            modifier = Modifier.size(176.dp),
            contentScale = ContentScale.Fit
        )

        Text(
            text = stringResource(R.string.app_name),
            modifier = Modifier.padding(top = 16.dp),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Text(
            text = stringResource(R.string.root_launch_waking_connection),
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        LaunchLoadingRail(
            modifier = Modifier
                .padding(top = 18.dp)
                .width(236.dp)
                .height(8.dp)
        )
    }
}

@Composable
private fun LaunchLoadingRail(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "launch-loading")
    val offsetFraction by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_050),
            repeatMode = RepeatMode.Reverse
        ),
        label = "launch-loading-offset"
    )

    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f))
    ) {
        val fillWidth = maxWidth * 0.38f
        val xOffset = (maxWidth - fillWidth) * offsetFraction
        Box(
            modifier = Modifier
                .offset(x = xOffset)
                .width(fillWidth)
                .height(maxHeight)
                .clip(RoundedCornerShape(999.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.78f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                        )
                    )
                )
        )
    }
}
