package com.rethinkingstudio.clawlink.ui.screens.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rethinkingstudio.clawlink.ui.components.ClawLinkCard
import com.rethinkingstudio.clawlink.ui.components.ClawLinkScaffold

private data class WelcomeSlide(
    val eyebrow: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val tint: Color
)

@Composable
fun WelcomeCarouselScreen(
    onFinish: () -> Unit
) {
    val slides = listOf(
        WelcomeSlide(
            eyebrow = "Connect",
            title = "Link Android to your OpenClaw gateway",
            subtitle = "Sign in to the relay, pair a running host, and keep the same calm ClawLink workspace on mobile.",
            icon = Icons.Default.PhoneAndroid,
            tint = MaterialTheme.colorScheme.primary
        ),
        WelcomeSlide(
            eyebrow = "Sessions",
            title = "Switch conversations without losing context",
            subtitle = "Create a new session or jump back into recent work before sending the next prompt.",
            icon = Icons.Default.Chat,
            tint = MaterialTheme.colorScheme.primary
        ),
        WelcomeSlide(
            eyebrow = "Backups",
            title = "Preserve gateway state before risky changes",
            subtitle = "Create and restore backups from Advanced Settings when you need a clean recovery point.",
            icon = Icons.Default.CloudDone,
            tint = MaterialTheme.colorScheme.secondary
        ),
        WelcomeSlide(
            eyebrow = "Restart",
            title = "Maintain hosts from the phone",
            subtitle = "Use management tools to inspect logs, recover sessions, and keep the remote gateway healthy.",
            icon = Icons.Default.RestartAlt,
            tint = MaterialTheme.colorScheme.tertiary
        ),
        WelcomeSlide(
            eyebrow = "Transfers",
            title = "Send work and files into the assistant flow",
            subtitle = "Use chat, tasks, models, skills, and gateway tools from a single Android hub.",
            icon = Icons.Default.AttachFile,
            tint = MaterialTheme.colorScheme.primary
        )
    )
    var pageIndex by remember { mutableIntStateOf(0) }
    val slide = slides[pageIndex]

    ClawLinkScaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
                    tonalElevation = 0.dp,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.75f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(999.dp))
                        )
                        Text("ClawLink", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(Modifier.weight(1f))

                TextButton(onClick = onFinish) {
                    Text("Skip", fontWeight = FontWeight.SemiBold)
                }
            }

            ClawLinkCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(132.dp)
                            .background(slide.tint.copy(alpha = 0.12f), RoundedCornerShape(42.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            slide.icon,
                            contentDescription = null,
                            modifier = Modifier.size(58.dp),
                            tint = slide.tint
                        )
                    }

                    Spacer(Modifier.height(24.dp))

                    Text(
                        slide.eyebrow.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = slide.tint
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        slide.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(Modifier.height(10.dp))

                    Text(
                        slide.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                slides.indices.forEach { index ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .width(if (index == pageIndex) 26.dp else 8.dp)
                            .height(8.dp)
                            .background(
                                if (index == pageIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                                RoundedCornerShape(999.dp)
                            )
                    )
                }
            }

            Button(
                onClick = {
                    if (pageIndex == slides.lastIndex) onFinish() else pageIndex += 1
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(
                    if (pageIndex == slides.lastIndex) "Get started" else "Continue",
                    modifier = Modifier.padding(vertical = 9.dp),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
