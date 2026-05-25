package com.rethinkingstudio.clawlink.ui.screens.chat

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal object ChatColors {
    val canvas: Color
        @Composable get() = MaterialTheme.colorScheme.background
    val sheet: Color
        @Composable get() = MaterialTheme.colorScheme.surface
    val dockSurface: Color
        @Composable get() = MaterialTheme.colorScheme.surface
    val dockControl: Color
        @Composable get() = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isSystemInDarkTheme()) 0.62f else 0.48f)
    val dockBorder: Color
        @Composable get() = MaterialTheme.colorScheme.outline.copy(alpha = chatDockBorderAlpha(isSystemInDarkTheme()))
    val secondaryText: Color
        @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
    val online = Color(0xFF5ECF7A)
    val offline = Color(0xFFE75F58)
    val pending = Color(0xFF7EADF4)
    val linkBlue: Color
        @Composable get() = MaterialTheme.colorScheme.primary
    val selectionBlue: Color
        @Composable get() = MaterialTheme.colorScheme.primary.copy(alpha = 0.62f)
    val userBubble: Color
        @Composable get() = if (isSystemInDarkTheme()) Color(0xFF298547) else Color(0xFF171923)
    val disabledAction: Color
        @Composable get() = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
}

internal fun chatDockBorderAlpha(darkTheme: Boolean): Float = if (darkTheme) 0.10f else 0.08f
