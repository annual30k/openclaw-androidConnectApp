package com.rethinkingstudio.clawlink

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.rethinkingstudio.clawlink.ui.navigation.AppNavigation
import com.rethinkingstudio.clawlink.app.PocketClawTheme
import com.rethinkingstudio.clawlink.ui.components.ClawLinkBackdrop

class MainActivity : AppCompatActivity() {
    private lateinit var appContainer: AppContainer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    ClawLinkBackdrop()
                    AppNavigation(container = appContainer)
                }
            }
        }
    }
}
