package com.rethinkingstudio.clawlink

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.rethinkingstudio.clawlink.ui.navigation.AppNavigation
import com.rethinkingstudio.clawlink.app.PocketClawTheme
import com.rethinkingstudio.clawlink.ui.components.ClawLinkBackdrop

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as ClawLinkApplication
        setContent {
            PocketClawTheme {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    ClawLinkBackdrop()
                    AppNavigation(container = app.container)
                }
            }
        }
    }
}
