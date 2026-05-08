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
import com.rethinkingstudio.clawlink.core.models.skills.SkillCommand
import com.rethinkingstudio.clawlink.core.models.skills.SkillItem
import com.rethinkingstudio.clawlink.core.state.gateway.GatewayStore
import com.rethinkingstudio.clawlink.core.state.skill.SkillStore
import kotlinx.coroutines.launch

private val CardShape = RoundedCornerShape(32.dp)
private val RowShape = RoundedCornerShape(20.dp)
private val PillShape = RoundedCornerShape(999.dp)
private val SuccessGreen = Color(0xFF20C873)
private val WarningOrange = Color(0xFFFFB13D)
private val AccentBlue = Color(0xFF0A84FF)
private val AccentBlueSoft = Color(0xFF5AC8FA)
private val DangerRed = Color(0xFFFF453A)
private val MutedGray = Color(0xFF8E8E93)
private val ScreenWhite = Color(0xFFFAFBFF)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    val gatewayName = gatewayState.selectedGateway?.displayName ?: "未选择网关"
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
                    title = { Text("技能", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
                                Icon(Icons.Default.Refresh, contentDescription = "刷新")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
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
                                title = "网关维护中",
                                message = "重启期间技能刷新已暂停，恢复后会自动继续。",
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
                            title = "技能列表",
                            subtitle = when {
                                searchText.isNotBlank() -> "正在搜索「${searchText.trim()}」"
                                selectedFilter == SkillListFilter.All -> "按可用状态排序展示"
                                else -> "显示${selectedFilter.title}技能"
                            },
                            modifier = Modifier.animateItemPlacement()
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
                                modifier = Modifier.animateItemPlacement()
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
                                modifier = Modifier.animateItemPlacement()
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
                action = { TextButton(onClick = skillStore::clearError) { Text("关闭") } }
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

@Composable
private fun SkillScreenBackdrop() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFFF2F5FA), Color.White),
                    start = androidx.compose.ui.geometry.Offset(0f, 0f),
                    end = androidx.compose.ui.geometry.Offset(1000f, 1000f)
                )
            )
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(320.dp)
                .offset(x = 60.dp, y = (-60).dp)
                .graphicsLayer(alpha = 0.45f)
                .background(
                    Brush.radialGradient(
                        colors = listOf(AccentBlue.copy(alpha = 0.25f), Color.Transparent),
                        radius = Float.POSITIVE_INFINITY
                    ),
                    CircleShape
                )
                .blur(80.dp)
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(340.dp)
                .offset(x = (-80).dp, y = 80.dp)
                .graphicsLayer(alpha = 0.4f)
                .background(
                    Brush.radialGradient(
                        colors = listOf(AccentBlueSoft.copy(alpha = 0.22f), Color.Transparent),
                        radius = Float.POSITIVE_INFINITY
                    ),
                    CircleShape
                )
                .blur(90.dp)
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(500.dp)
                .offset(x = 100.dp, y = 150.dp)
                .graphicsLayer(alpha = 0.25f)
                .background(Brush.radialGradient(listOf(AccentBlue.copy(alpha = 0.1f), Color.Transparent)), CircleShape)
                .blur(120.dp)
        )
    }
}

@Composable
private fun SkillOverviewCard(
    gatewayName: String,
    totalCount: Int,
    readyCount: Int,
    needsSetupCount: Int,
    disabledCount: Int,
    isRefreshing: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "已安装技能",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        brush = Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.onSurface,
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            )
                        )
                    ),
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        gatewayName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .shadow(8.dp, CircleShape, spotColor = AccentBlue.copy(alpha = 0.25f))
                    .background(AccentBlue.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = AccentBlue)
                } else {
                    Icon(Icons.Default.Extension, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(22.dp))
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            SkillSmallStatChip("全部", totalCount, AccentBlue)
            SkillSmallStatChip("就绪", readyCount, SuccessGreen)
            if (needsSetupCount > 0) SkillSmallStatChip("待配置", needsSetupCount, WarningOrange)
            if (disabledCount > 0) SkillSmallStatChip("停用", disabledCount, MutedGray)
        }
    }
}

@Composable
private fun SkillSmallStatChip(title: String, value: Int, tint: Color) {
    Row(
        modifier = Modifier
            .clip(PillShape)
            .background(tint.copy(alpha = 0.08f))
            .border(0.5.dp, tint.copy(alpha = 0.12f), PillShape)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(value.toString(), fontWeight = FontWeight.Black, color = tint, style = MaterialTheme.typography.bodyLarge)
        Text(title, fontWeight = FontWeight.Bold, color = tint, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SkillFilterStrip(
    skills: List<SkillItem>,
    selectedFilter: SkillListFilter,
    onSelectFilter: (SkillListFilter) -> Unit
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SkillListFilter.entries.forEach { filter ->
            val count = skills.count { filter.matches(it) }
            IosFilterPill(
                title = filter.title,
                count = count,
                selected = selectedFilter == filter,
                tint = filter.tint,
                onClick = { onSelectFilter(filter) }
            )
        }
    }
}

@Composable
private fun IosFilterPill(
    title: String,
    count: Int,
    selected: Boolean,
    tint: Color,
    onClick: () -> Unit
) {
    val background = if (selected) tint else MaterialTheme.colorScheme.surface
    val foreground = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .then(
                if (selected) Modifier.shadow(8.dp, PillShape, spotColor = tint.copy(alpha = 0.4f))
                else Modifier.border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), PillShape)
            )
            .clip(PillShape)
            .background(background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 18.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            color = foreground
        )
        if (count > 0 || selected) {
            Text(
                count.toString(),
                modifier = Modifier
                    .clip(PillShape)
                    .background(if (selected) Color.White.copy(alpha = 0.22f) else tint.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = if (selected) Color.White else tint
            )
        }
    }
}

@Composable
private fun SkillListRow(
    skill: SkillItem,
    canEdit: Boolean,
    isBusy: Boolean,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val availability = skill.availability
    val tint = availability.tint
    val canToggle = canEdit && !isBusy && (skill.isEnabled || availability != SkillAvailability.Blocked)

    Surface(
        color = Color.Transparent,
        modifier = modifier
            .fillMaxWidth()
            .shadow(10.dp, RowShape, clip = false, ambientColor = Color.Black.copy(alpha = 0.03f), spotColor = Color.Black.copy(alpha = 0.06f))
            .border(0.6.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f), RowShape)
            .clip(RowShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onOpen
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SkillAvatar(skill = skill, diameter = 48)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    skill.effectiveName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    skill.effectiveDescription.ifBlank { skill.statusDetailText },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            SkillStatusBadge(availability)
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background((if (skill.isEnabled) DangerRed else AccentBlue).copy(alpha = 0.1f))
                    .clickable(enabled = canToggle, onClick = onToggle),
                contentAlignment = Alignment.Center
            ) {
                if (isBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = AccentBlue)
                } else {
                    Icon(
                        if (skill.isEnabled) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (skill.isEnabled) "停用" else "启用",
                        tint = if (skill.isEnabled) DangerRed else AccentBlue,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SkillAvatar(skill: SkillItem, diameter: Int = 52) {
    val availability = skill.availability
    Box(
        modifier = Modifier
            .size(diameter.dp)
            .clip(RoundedCornerShape((diameter * 0.24f).dp))
            .background(availability.tint.copy(alpha = 0.14f))
            .border(0.6.dp, availability.tint.copy(alpha = 0.22f), RoundedCornerShape((diameter * 0.24f).dp)),
        contentAlignment = Alignment.Center
    ) {
        val emoji = skill.emoji?.trim().orEmpty()
        if (emoji.isNotEmpty()) {
            Text(emoji, style = MaterialTheme.typography.titleLarge)
        } else {
            Icon(availability.icon, contentDescription = null, tint = availability.tint, modifier = Modifier.size((diameter * 0.42f).dp))
        }
    }
}

@Composable
private fun SkillStatusBadge(availability: SkillAvailability) {
    Row(
        modifier = Modifier
            .clip(PillShape)
            .background(availability.tint.copy(alpha = 0.1f))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(6.dp).background(availability.tint, CircleShape))
        Text(availability.title, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = availability.tint)
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SkillEmptyStateCard(
    hasSkills: Boolean,
    searchText: String,
    onReset: () -> Unit,
    onReload: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)),
        border = androidx.compose.foundation.BorderStroke(0.6.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Extension, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
            }
            Text(if (hasSkills) "没有匹配的技能" else "未找到技能", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                if (hasSkills) "换个关键词或筛选条件再试。" else "刷新后会从当前网关同步 OpenClaw 技能目录。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
            Button(
                onClick = if (hasSkills) onReset else onReload,
                modifier = Modifier.padding(top = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                shape = PillShape
            ) {
                Text(
                    if (hasSkills && searchText.isNotBlank()) "清空搜索" else if (hasSkills) "查看全部" else "重新加载",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun SkillSearchBar(searchText: String, onSearchTextChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(25.dp, PillShape, clip = false, ambientColor = Color.Black.copy(alpha = 0.15f), spotColor = Color.Black.copy(alpha = 0.18f))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)
                    )
                ),
                PillShape
            )
            .border(
                width = 0.8.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.05f)
                    )
                ),
                shape = PillShape
            )
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        BasicTextField(
            value = searchText,
            onValueChange = onSearchTextChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                fontWeight = FontWeight.SemiBold
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { innerTextField ->
                Box {
                    if (searchText.isEmpty()) {
                        Text(
                            "搜索技能名称、用途或来源",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    innerTextField()
                }
            }
        )
        if (searchText.isNotEmpty()) {
            IconButton(onClick = { onSearchTextChange("") }, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Default.Close, contentDescription = "清空", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
private fun SkillMaintenanceBanner(title: String, message: String, icon: ImageVector, tint: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = tint.copy(alpha = 0.08f)),
        border = androidx.compose.foundation.BorderStroke(0.6.dp, tint.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(modifier = Modifier.size(42.dp).background(tint.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = tint)
                Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SkillDetailSheet(
    skill: SkillItem,
    canEdit: Boolean,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onToggle: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp),
        containerColor = ScreenWhite,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 42.dp, height = 5.dp)
                    .clip(PillShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.26f))
            )
        }
    ) {
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SkillDetailHero(skill = skill, canEdit = canEdit, isBusy = isBusy, onToggle = onToggle)
            }
            item {
                SkillDetailSection("状态与来源") {
                    SkillInfoRow("当前状态", skill.availability.title, skill.availability.tint)
                    SkillInfoRow("来源", skill.sourceLabel)
                    SkillInfoRow("安装路径", skill.filePath ?: skill.baseDir ?: skill.effectiveKey)
                    skill.primaryEnv?.takeIf { it.isNotBlank() }?.let { SkillInfoRow("主要环境变量", it) }
                    skill.homepage?.takeIf { it.isNotBlank() }?.let { SkillInfoRow("主页", it) }
                }
            }
            item {
                SkillDetailSection("运行要求") {
                    RequirementBlock("命令", skill.requirements.bins, skill.missing.bins)
                    RequirementBlock("任一命令", skill.requirements.anyBins, skill.missing.anyBins)
                    RequirementBlock("环境变量", mergedEnvRequirements(skill), skill.missing.env)
                    RequirementBlock("配置文件", skill.requirements.config, skill.missing.config)
                    RequirementBlock("平台", skill.requirements.os, skill.missing.os)
                    if (skill.requirements.isEmpty && skill.missing.isEmpty && skill.envKeys.isNullOrEmpty()) {
                        Text("没有额外要求", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (skill.configChecks.isNotEmpty()) {
                item {
                    SkillDetailSection("配置检查") {
                        skill.configChecks.forEach { check ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                                Icon(
                                    if (check.satisfied) Icons.Default.CheckCircle else Icons.Default.Info,
                                    contentDescription = null,
                                    tint = if (check.satisfied) SuccessGreen else WarningOrange,
                                    modifier = Modifier.size(16.dp)
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(check.path, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    Text(if (check.satisfied) "已满足" else "未满足", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
            val commands = skill.commands.orEmpty()
            if (commands.isNotEmpty()) {
                item {
                    SkillDetailSection("可用命令") {
                        commands.forEach { command -> SkillCommandRow(command) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SkillDetailHero(skill: SkillItem, canEdit: Boolean, isBusy: Boolean, onToggle: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
                        skill.availability.tint.copy(alpha = 0.10f)
                    )
                )
            )
            .border(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f), CardShape)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            SkillAvatar(skill = skill, diameter = 72)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(skill.effectiveName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    SkillStatusBadge(skill.availability)
                    if (skill.always) {
                        Text(
                            "常驻加载",
                            modifier = Modifier.clip(PillShape).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f)).padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        Text(skill.effectiveDescription.ifBlank { skill.statusDetailText }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("当前状态", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (skill.isEnabled) "已启用" else "已停用", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = if (skill.isEnabled) SuccessGreen else MaterialTheme.colorScheme.onSurface)
            }
            Button(
                onClick = onToggle,
                enabled = canEdit && !isBusy && (skill.isEnabled || skill.availability != SkillAvailability.Blocked),
                shape = PillShape,
                colors = ButtonDefaults.buttonColors(containerColor = if (skill.isEnabled) DangerRed else AccentBlue),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
            ) {
                if (isBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Icon(if (skill.isEnabled) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (skill.isEnabled) "停用" else "启用", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SkillDetailSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.78f))
            .border(0.6.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f), CardShape)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

@Composable
private fun SkillInfoRow(label: String, value: String, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = tint)
    }
}

@Composable
private fun RequirementBlock(title: String, required: List<String>, missing: List<String>) {
    val visibleItems = if (required.isEmpty()) missing else required
    if (visibleItems.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant)
        visibleItems.forEach { item ->
            val isMissing = missing.contains(item)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (isMissing) Icons.Default.Info else Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = if (isMissing) WarningOrange else SuccessGreen,
                    modifier = Modifier.size(16.dp)
                )
                Text(item, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun SkillCommandRow(command: SkillCommand) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("/${command.name}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Black, color = AccentBlue)
        command.description?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private enum class SkillListFilter(val title: String, val tint: Color) {
    All("全部", AccentBlue),
    Ready("就绪", SuccessGreen),
    NeedsSetup("待配置", WarningOrange),
    Disabled("已停用", MutedGray);

    fun matches(skill: SkillItem): Boolean = when (this) {
        All -> true
        Ready -> skill.availability == SkillAvailability.Ready
        NeedsSetup -> skill.availability == SkillAvailability.NeedsSetup || skill.availability == SkillAvailability.Blocked
        Disabled -> skill.availability == SkillAvailability.Disabled
    }
}

private enum class SkillAvailability(val title: String, val tint: Color, val icon: ImageVector) {
    Ready("就绪", SuccessGreen, Icons.Default.CheckCircle),
    NeedsSetup("待配置", WarningOrange, Icons.Default.Build),
    Disabled("已停用", MutedGray, Icons.Default.PauseCircle),
    Blocked("已限制", DangerRed, Icons.Default.Shield)
}

private val SkillItem.availability: SkillAvailability
    get() = when {
        blockedByAllowlist || status.equals("blocked", ignoreCase = true) -> SkillAvailability.Blocked
        !isEnabled || status.equals("disabled", ignoreCase = true) -> SkillAvailability.Disabled
        eligible == false || !missing.isEmpty || status.equals("needs_setup", ignoreCase = true) -> SkillAvailability.NeedsSetup
        else -> SkillAvailability.Ready
    }

private val SkillItem.sourceLabel: String
    get() = when (source?.trim()) {
        "openclaw-bundled" -> "内置"
        "openclaw-managed", "openclaw-hosted" -> "托管"
        "openclaw-workspace" -> "工作区"
        "openclaw-extra" -> "扩展"
        "openclaw-plugin" -> "插件"
        null, "" -> "未标记"
        else -> source.orEmpty()
    }

private val SkillItem.statusDetailText: String
    get() = statusDetail?.takeIf { it.isNotBlank() } ?: when (availability) {
        SkillAvailability.Ready -> "依赖已满足，可直接使用。"
        SkillAvailability.NeedsSetup -> if (missing.count > 0) "缺少 ${missing.count} 项配置或依赖。" else "需要完成配置后才能使用。"
        SkillAvailability.Disabled -> "技能已停用，需要启用后才会参与会话。"
        SkillAvailability.Blocked -> "技能被 allowlist 限制，当前不可启用。"
    }

private fun SkillItem.matchesSearch(query: String): Boolean {
    val normalized = query.trim().lowercase()
    if (normalized.isEmpty()) return true
    val haystack = listOf(
        effectiveName,
        effectiveDescription,
        effectiveKey,
        source.orEmpty(),
        sourceLabel,
        primaryEnv.orEmpty(),
        homepage.orEmpty(),
        statusDetailText,
        requirements.bins.joinToString(" "),
        requirements.anyBins.joinToString(" "),
        requirements.env.joinToString(" "),
        missing.bins.joinToString(" "),
        missing.env.joinToString(" ")
    ).joinToString(" ").lowercase()
    return haystack.contains(normalized)
}

private fun mergedEnvRequirements(skill: SkillItem): List<String> {
    return (skill.requirements.env + skill.envKeys.orEmpty()).distinct()
}
