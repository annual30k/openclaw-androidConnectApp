package com.rethinkingstudio.clawlink.ui.screens.skills

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rethinkingstudio.clawlink.app.AppSystemBarsEffect
import com.rethinkingstudio.clawlink.core.models.skills.SkillCommand
import com.rethinkingstudio.clawlink.core.models.skills.SkillItem
import com.rethinkingstudio.clawlink.core.state.LocalizedText.choose
import com.rethinkingstudio.clawlink.core.state.gateway.GatewayStore
import com.rethinkingstudio.clawlink.core.state.skill.SkillStore
import kotlinx.coroutines.launch


@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SkillsScreen(
    skillStore: SkillStore,
    gatewayStore: GatewayStore,
    onBack: () -> Unit
) {
    AppSystemBarsEffect()

    val skillState by skillStore.state.collectAsState()
    val gatewayState by gatewayStore.state.collectAsState()
    val scope = rememberCoroutineScope()
    val gatewayId = gatewayState.selectedGatewayId
    val gatewayName = gatewayState.selectedGateway?.displayName ?: choose("No gateway selected", "未选择网关")
    val canManageSkills = gatewayId != null && gatewayState.isSelectedGatewayChatChainReady

    var selectedFilter by remember { mutableStateOf(SkillListFilter.All) }
    var searchText by remember { mutableStateOf("") }
    var detailSkill by remember { mutableStateOf<SkillItem?>(null) }

    LaunchedEffect(gatewayId, gatewayState.isSelectedGatewayChatChainReady) {
        if (canManageSkills) skillStore.loadSkills(gatewayId)
    }

    val sortedSkills = remember(skillState.skills) { SkillStore.sortSkills(skillState.skills) }
    val visibleSkills = remember(sortedSkills, selectedFilter, searchText) {
        sortedSkills.filter { skill ->
            selectedFilter.matches(skill) && skill.matchesSearch(searchText)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        SkillScreenBackdrop()
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(choose("Skills", "技能"), fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = choose("Back", "返回"))
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { if (canManageSkills) scope.launch { skillStore.loadSkills(gatewayId) } },
                            enabled = canManageSkills && !skillState.isLoading
                        ) {
                            if (skillState.isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = choose("Refresh", "刷新"))
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                        actionIconContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            }
        ) { padding ->
            if (skillState.isLoading && skillState.skills.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 112.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    if (gatewayState.restartingGatewayId != null && gatewayState.isSelectedGatewayChatChainReady) {
                        item {
                            SkillMaintenanceBanner(
                                title = choose("Gateway maintenance", "网关维护中"),
                                message = choose("Skill refresh is paused during restart and will continue after recovery.", "重启期间技能刷新已暂停，恢复后会自动继续。"),
                                icon = Icons.Default.Lock,
                                tint = WarningOrange
                            )
                        }
                    }

                    item {
                        SkillOverviewCard(
                            gatewayName = gatewayName,
                            totalCount = sortedSkills.size,
                            readyCount = sortedSkills.count { it.availability == SkillAvailability.Ready },
                            needsSetupCount = sortedSkills.count { it.availability == SkillAvailability.NeedsSetup || it.availability == SkillAvailability.Blocked },
                            disabledCount = sortedSkills.count { it.availability == SkillAvailability.Disabled },
                            isRefreshing = skillState.isLoading
                        )
                    }

                    item {
                        SkillFilterStrip(
                            skills = sortedSkills,
                            selectedFilter = selectedFilter,
                            onSelectFilter = { selectedFilter = it }
                        )
                    }

                    item(key = "section_title") {
                        SectionTitle(
                            title = choose("Skill list", "技能列表"),
                            subtitle = when {
                                searchText.isNotBlank() -> choose("Searching \"${searchText.trim()}\"", "正在搜索「${searchText.trim()}」")
                                selectedFilter == SkillListFilter.All -> choose("Sorted by availability", "按可用状态排序展示")
                                else -> choose("Showing ${selectedFilter.title.lowercase()} skills", "显示${selectedFilter.title}技能")
                            },
                            modifier = Modifier.animateItem()
                        )
                    }

                    if (visibleSkills.isEmpty()) {
                        item(key = "empty_state") {
                            SkillEmptyStateCard(
                                hasSkills = sortedSkills.isNotEmpty(),
                                searchText = searchText,
                                onReset = {
                                    selectedFilter = SkillListFilter.All
                                    searchText = ""
                                },
                                onReload = { if (canManageSkills) scope.launch { skillStore.loadSkills(gatewayId) } },
                                modifier = Modifier.animateItem()
                            )
                        }
                    } else {
                        items(visibleSkills, key = { it.effectiveKey }) { skill ->
                            SkillListRow(
                                skill = skill,
                                canEdit = canManageSkills,
                                isBusy = skillState.isLoading,
                                onOpen = { detailSkill = skill },
                                onToggle = {
                                    if (canManageSkills) {
                                        scope.launch {
                                            skillStore.updateSkill(gatewayId, skill.effectiveKey, !skill.isEnabled)
                                        }
                                    }
                                },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                }
            }
        }

        SkillSearchBar(
            searchText = searchText,
            onSearchTextChange = { searchText = it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp)
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 12.dp)
        )

        if (gatewayState.isSelectedGatewayChatChainReady) skillState.errorMessage?.let { message ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .padding(bottom = 72.dp),
                action = { TextButton(onClick = skillStore::clearError) { Text(choose("Close", "关闭")) } }
            ) {
                Text(message)
            }
        }
    }

    detailSkill?.let { skill ->
        SkillDetailSheet(
            skill = skill,
            canEdit = canManageSkills,
            isBusy = skillState.isLoading,
            onDismiss = { detailSkill = null },
            onToggle = {
                if (canManageSkills) {
                    scope.launch {
                        skillStore.updateSkill(gatewayId, skill.effectiveKey, !skill.isEnabled)
                        detailSkill = skill.copy(enabled = !skill.isEnabled, disabled = skill.isEnabled)
                    }
                }
            }
        )
    }
}
