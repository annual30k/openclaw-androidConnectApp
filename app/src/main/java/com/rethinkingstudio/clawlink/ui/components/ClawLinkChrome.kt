package com.rethinkingstudio.clawlink.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rethinkingstudio.clawlink.app.AppSystemBarsEffect

private val ClawLinkCardShape = RoundedCornerShape(24.dp)
private val ClawLinkPillShape = RoundedCornerShape(999.dp)

@Composable
fun ClawLinkBackdrop(modifier: Modifier = Modifier) {
    val brush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
        ),
        start = Offset.Zero,
        end = Offset.Infinite
    )
    Box(modifier = modifier.fillMaxSize().background(brush))
}

@Composable
fun ClawLinkScaffold(
    modifier: Modifier = Modifier,
    applyDefaultSystemBars: Boolean = true,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    if (applyDefaultSystemBars) {
        AppSystemBarsEffect()
    }
    Box(modifier = modifier.fillMaxSize()) {
        ClawLinkBackdrop()
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            topBar = topBar,
            bottomBar = bottomBar
        ) { padding ->
            content(padding)
        }
    }
}

@Composable
fun ClawLinkCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    showsBorder: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = CardDefaults.cardColors(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface
    )
    val cardModifierBase = modifier
        .shadow(
            elevation = 16.dp,
            shape = ClawLinkCardShape,
            ambientColor = Color.Black.copy(alpha = 0.035f),
            spotColor = Color.Black.copy(alpha = 0.08f)
        )
        .clip(ClawLinkCardShape)
        .background(
            brush = Brush.linearGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.055f),
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.90f)
                ),
                start = Offset.Zero,
                end = Offset.Infinite
            ),
            shape = ClawLinkCardShape
        )
    val cardModifier = if (showsBorder) {
        cardModifierBase.border(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.14f), ClawLinkCardShape)
    } else {
        cardModifierBase
    }

    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = cardModifier,
            shape = ClawLinkCardShape,
            colors = colors,
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(modifier = Modifier.padding(contentPadding), verticalArrangement = Arrangement.spacedBy(0.dp), content = content)
        }
    } else {
        Card(
            modifier = cardModifier,
            shape = ClawLinkCardShape,
            colors = colors,
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(modifier = Modifier.padding(contentPadding), verticalArrangement = Arrangement.spacedBy(0.dp), content = content)
        }
    }
}

@Composable
fun ClawLinkSectionHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ClawLinkHeroHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ClawLinkPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        shape = ClawLinkPillShape,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        contentPadding = PaddingValues(vertical = 16.dp, horizontal = 20.dp)
    ) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}
