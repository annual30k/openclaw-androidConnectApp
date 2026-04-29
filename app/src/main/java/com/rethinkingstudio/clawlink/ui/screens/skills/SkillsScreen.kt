package com.rethinkingstudio.clawlink.ui.screens.skills

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rethinkingstudio.clawlink.core.models.skills.SkillItem
import com.rethinkingstudio.clawlink.core.state.gateway.GatewayStore
import com.rethinkingstudio.clawlink.core.state.skill.SkillStore
import com.rethinkingstudio.clawlink.ui.components.ClawLinkScaffold
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillsScreen(
    skillStore: SkillStore,
    gatewayStore: GatewayStore,
    onBack: () -> Unit
) {
    val skillState by skillStore.state.collectAsState()
    val gatewayState by gatewayStore.state.collectAsState()
    val scope = rememberCoroutineScope()
    val gatewayId = gatewayState.selectedGatewayId

    LaunchedEffect(gatewayId) {
        if (gatewayId != null) skillStore.loadSkills(gatewayId)
    }

    ClawLinkScaffold(
        topBar = {
            TopAppBar(
                title = { Text("Skills (${skillState.enabledCount}/${skillState.totalCount})") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (gatewayId != null) scope.launch { skillStore.loadSkills(gatewayId) }
                    }) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        if (skillState.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(skillState.filteredSkills, key = { it.key }) { skill ->
                    SkillRow(
                        skill = skill,
                        onToggle = { enabled ->
                            if (gatewayId != null) {
                                scope.launch { skillStore.updateSkill(gatewayId, skill.key, enabled) }
                            }
                        }
                    )
                }

                if (skillState.filteredSkills.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("No skills found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SkillRow(skill: SkillItem, onToggle: (Boolean) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(skill.displayName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    skill.description?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Switch(checked = skill.enabled, onCheckedChange = onToggle)
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, "Details")
                }
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                DetailLine("Key", skill.key)
                skill.version?.let { DetailLine("Version", it) }
                skill.source?.let { DetailLine("Source", it) }
                skill.commands?.let { commands ->
                    if (commands.isNotEmpty()) {
                        Text("Commands:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        commands.forEach { cmd ->
                            Text("  /${cmd.name} - ${cmd.description ?: ""}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text("$label: ", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}
