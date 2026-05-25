package com.rethinkingstudio.clawlink.ui.screens.chat

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

internal data class ChatSystemBarStyle(
    val statusBarColor: Color,
    val navigationBarColor: Color,
    val useDarkStatusBarIcons: Boolean,
    val useDarkNavigationBarIcons: Boolean
)

internal fun chatSystemBarStyle(
    normalStatusBarColor: Color,
    normalNavigationBarColor: Color,
    modalOverlayActive: Boolean
): ChatSystemBarStyle {
    val statusBarColor = if (modalOverlayActive) Color.Black else normalStatusBarColor
    val navigationBarColor = if (modalOverlayActive) Color.Black else normalNavigationBarColor
    return ChatSystemBarStyle(
        statusBarColor = statusBarColor,
        navigationBarColor = navigationBarColor,
        useDarkStatusBarIcons = statusBarColor.luminance() > 0.5f,
        useDarkNavigationBarIcons = navigationBarColor.luminance() > 0.5f
    )
}
