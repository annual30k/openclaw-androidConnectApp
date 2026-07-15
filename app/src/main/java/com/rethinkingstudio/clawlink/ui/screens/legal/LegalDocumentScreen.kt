package com.rethinkingstudio.clawlink.ui.screens.legal

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rethinkingstudio.clawlink.core.state.LocalizedText.choose
import com.rethinkingstudio.clawlink.core.state.LocalizedText.isChinese
import com.rethinkingstudio.clawlink.ui.components.ClawLinkScaffold
import com.rethinkingstudio.clawlink.ui.screens.legal.models.LegalDocumentSection
import com.rethinkingstudio.clawlink.ui.screens.legal.models.LegalDocumentType
import com.rethinkingstudio.clawlink.ui.screens.legal.models.legalDocument

private val LegalBlue = Color(0xFF2F73EE)
private val LegalHeroBackground = Color(0xFFEFF6FF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalDocumentScreen(type: LegalDocumentType, onBack: () -> Unit) {
    val document = remember(type) { legalDocument(type) }
    val scrollState = rememberScrollState()
    val progress by remember {
        derivedStateOf {
            if (scrollState.maxValue == 0) 0f else scrollState.value.toFloat() / scrollState.maxValue
        }
    }

    ClawLinkScaffold(
        topBar = {
            TopAppBar(
                title = { Text(choose(document.englishTitle, document.chineseTitle), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, choose("Back", "返回"))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = LegalHeroBackground)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surface)
                .verticalScroll(scrollState)
        ) {
            LegalHero(
                eyebrow = choose(document.englishEyebrow, document.chineseEyebrow),
                title = choose(document.englishTitle, document.chineseTitle),
                summary = choose(document.englishSummary, document.chineseSummary),
                version = document.version,
                progress = progress
            )

            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                document.sections.forEachIndexed { index, section ->
                    LegalSection(index = index + 1, section = section)
                }
                HorizontalDivider(modifier = Modifier.padding(top = 24.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(choose("Thank you for reading", "感谢您认真阅读"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        choose("Current version: ${document.version}", "当前版本：${document.version}"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun LegalHero(eyebrow: String, title: String, summary: String, version: String, progress: Float) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(LegalHeroBackground, RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.weight(1f).height(5.dp),
                color = LegalBlue,
                trackColor = Color(0xFFD8E1EE),
                gapSize = 0.dp,
                drawStopIndicator = {}
            )
            Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = Color(0xFF718096))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.Description, null, tint = LegalBlue, modifier = Modifier.size(24.dp))
            Text(eyebrow, color = LegalBlue, fontWeight = FontWeight.Bold)
        }
        Text(title, style = MaterialTheme.typography.headlineLarge, color = Color(0xFF0D1C3D), fontWeight = FontWeight.ExtraBold)
        Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFFE2EDFF)) {
            Text(
                choose("Version and effective date: $version", "版本与生效日期：$version"),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                color = Color(0xFF53647D)
            )
        }
        Text(summary, style = MaterialTheme.typography.bodyLarge, color = Color(0xFF44546D), lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.3f)
    }
}

@Composable
private fun LegalSection(index: Int, section: LegalDocumentSection) {
    Column(modifier = Modifier.padding(top = 26.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                modifier = Modifier.size(38.dp).background(Color(0xFFEAF2FF), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(index.toString(), color = LegalBlue, fontWeight = FontWeight.Bold)
            }
            Text(
                choose(section.englishTitle, section.chineseTitle),
                style = MaterialTheme.typography.titleLarge,
                color = Color(0xFF0D1C3D),
                fontWeight = FontWeight.Bold
            )
        }
        HorizontalDivider(modifier = Modifier.padding(top = 14.dp), color = Color(0xFFDCE4EF))
        Spacer(Modifier.height(18.dp))
        val paragraphs = if (isChinese()) section.chineseParagraphs else section.englishParagraphs
        paragraphs.forEach { paragraph ->
            Text(
                paragraph,
                modifier = Modifier.padding(bottom = 16.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFF43516A),
                lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.35f
            )
        }
    }
}
