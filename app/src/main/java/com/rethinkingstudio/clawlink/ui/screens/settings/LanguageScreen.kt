package com.rethinkingstudio.clawlink.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rethinkingstudio.clawlink.R
import com.rethinkingstudio.clawlink.core.state.LanguageManager
import com.rethinkingstudio.clawlink.core.state.LanguagePreference
import com.rethinkingstudio.clawlink.ui.components.ClawLinkCard
import com.rethinkingstudio.clawlink.ui.components.ClawLinkScaffold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageScreen(
    onBack: () -> Unit
) {
    var selectedPreference by remember { 
        mutableStateOf(LanguageManager.getCurrentPreference()) 
    }

    ClawLinkScaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_row_language)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_action_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ClawLinkCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    LanguageRow(
                        preference = LanguagePreference.SYSTEM,
                        isSelected = selectedPreference == LanguagePreference.SYSTEM,
                        onClick = { 
                            selectedPreference = LanguagePreference.SYSTEM
                            LanguageManager.setLanguage(LanguagePreference.SYSTEM) 
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                    LanguageRow(
                        preference = LanguagePreference.ZH_HANS,
                        isSelected = selectedPreference == LanguagePreference.ZH_HANS,
                        onClick = { 
                            selectedPreference = LanguagePreference.ZH_HANS
                            LanguageManager.setLanguage(LanguagePreference.ZH_HANS) 
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                    LanguageRow(
                        preference = LanguagePreference.EN,
                        isSelected = selectedPreference == LanguagePreference.EN,
                        onClick = { 
                            selectedPreference = LanguagePreference.EN
                            LanguageManager.setLanguage(LanguagePreference.EN) 
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguageRow(
    preference: LanguagePreference,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val title = when (preference) {
        LanguagePreference.SYSTEM -> stringResource(R.string.settings_language_system)
        LanguagePreference.ZH_HANS -> stringResource(R.string.settings_language_zh_hans)
        LanguagePreference.EN -> stringResource(R.string.settings_language_en)
    }

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = androidx.compose.ui.graphics.Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
            if (isSelected) {
                Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
