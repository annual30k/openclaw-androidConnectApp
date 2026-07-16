package com.rethinkingstudio.clawlink.ui.screens.settings

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rethinkingstudio.clawlink.R
import com.rethinkingstudio.clawlink.app.AppSystemBarsEffect
import com.rethinkingstudio.clawlink.core.network.RelayAPIClient
import com.rethinkingstudio.clawlink.core.network.dto.LogTailResponse
import com.rethinkingstudio.clawlink.core.models.gateway.GatewayType
import com.rethinkingstudio.clawlink.core.state.LocalizedText.choose
import com.rethinkingstudio.clawlink.core.state.gateway.GatewayStore
import com.rethinkingstudio.clawlink.core.state.log.LogSeverity
import com.rethinkingstudio.clawlink.core.state.log.ParsedLogLine
import com.rethinkingstudio.clawlink.core.state.log.parseLogLine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private enum class LogViewerSource(val wireValue: String) {
    Gateway("gateway"), GatewayError("gateway-error"), Connection("connection");

    val title: String get() = when (this) {
        Gateway -> choose("Run", "运行")
        GatewayError -> choose("Errors", "错误")
        Connection -> choose("Connection", "连接")
    }

    val detail: String get() = when (this) {
        Gateway -> "OpenClaw gateway.log"
        GatewayError -> "OpenClaw gateway.err.log"
        Connection -> choose("ClawConnect connection log", "ClawConnect 连接日志")
    }
}

@Composable
private fun LogControlPanel(
    report: LogTailResponse?,
    selectedSource: LogViewerSource,
    sources: List<LogViewerSource>,
    sourceNotice: String?,
    selectedSeverity: LogSeverityFilter,
    entries: List<ParsedLogLine>,
    onSourceSelected: (LogViewerSource) -> Unit,
    onSeveritySelected: (LogSeverityFilter) -> Unit
) {
    LogGlassCard {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(choose("Log source", "日志源"), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(logPathLabel(report?.logPath) ?: selectedSource.detail, fontFamily = FontFamily.Monospace, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(selectedSource.detail, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("${report?.returnedLines ?: 0}/${report?.totalLines ?: 0}", color = Color(0xFF3B82F6), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        }
        sourceNotice?.let {
            Text(
                it,
                color = Color(0xFF9A6700),
                fontSize = 10.sp,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp).background(Color(0xFFF59E0B).copy(alpha = 0.1f), RoundedCornerShape(8.dp)).padding(9.dp)
            )
        }
        Row(Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            sources.forEach { source -> LogChip(source.title, selectedSource == source) { onSourceSelected(source) } }
        }
        Row(Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LogSeverityFilter.entries.forEach { filter ->
                val count = entries.count { filter.matches(it.severity) }
                LogChip("${filter.title} $count", selectedSeverity == filter) { onSeveritySelected(filter) }
            }
        }
    }
}

@Composable
private fun LogChip(title: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        title,
        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 11.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier.clip(CircleShape).background(if (selected) Color(0xFF3B82F6) else MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp)
    )
}

@Composable
private fun LogTerminalPanel(
    report: LogTailResponse?,
    selectedSource: LogViewerSource,
    entries: List<ParsedLogLine>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF0B1220)).border(0.8.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
    ) {
        Row(
            Modifier.fillMaxWidth().height(34.dp).background(Color(0xFF111B2E)).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(Modifier.size(7.dp).background(Color(0xFFFB7185), CircleShape))
            Box(Modifier.size(7.dp).background(Color(0xFFFBBF24), CircleShape))
            Box(Modifier.size(7.dp).background(Color(0xFF34D399), CircleShape))
            Text(
                "${logPathLabel(report?.logPath) ?: selectedSource.detail} · ${report?.returnedLines ?: 0}/${report?.totalLines ?: 0}",
                color = Color.White.copy(alpha = 0.5f), fontFamily = FontFamily.Monospace, fontSize = 10.sp
            )
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 6.dp)) {
            itemsIndexed(entries) { index, entry -> TerminalLogLine(index + 1, entry) }
        }
    }
}

@Composable
private fun TerminalLogLine(lineNumber: Int, entry: ParsedLogLine) {
    val color = when (entry.severity) {
        LogSeverity.Error -> Color(0xFFFDA4AF)
        LogSeverity.Warning -> Color(0xFFFCD34D)
        LogSeverity.Info -> Color(0xFF7DD3FC)
        LogSeverity.Debug -> Color(0xFF94A3B8)
        LogSeverity.Unknown -> Color(0xFFDBE7F7)
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Text("$lineNumber", color = Color(0xFF52617A), fontFamily = FontFamily.Monospace, fontSize = 10.sp, modifier = Modifier.width(28.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
        Spacer(Modifier.width(9.dp))
        Column {
            val metadata = listOfNotNull(entry.timestampText, entry.sourceText).joinToString(" · ")
            if (metadata.isNotEmpty()) Text(metadata, color = Color(0xFF71819D), fontFamily = FontFamily.Monospace, fontSize = 9.sp)
            Text(entry.displayText, color = color, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp)
        }
    }
}

private fun resolveLogSource(response: LogTailResponse, requested: LogViewerSource): LogViewerSource {
    LogViewerSource.entries.firstOrNull { it.wireValue == response.source }?.let { return it }
    val path = response.logPath?.lowercase().orEmpty()
    return when {
        path.endsWith("gateway.err.log") -> LogViewerSource.GatewayError
        path.endsWith("gateway.log") -> LogViewerSource.Gateway
        path.contains("clawconnect") || path.endsWith("agent.log") -> LogViewerSource.Connection
        else -> requested
    }
}

private fun logPathLabel(path: String?): String? = path?.trim()?.takeIf { it.isNotEmpty() }?.substringAfterLast('/')?.substringAfterLast('\\')

private enum class LogSeverityFilter {
    All, Error, Warning, Info, Debug, Unknown;

    val title: String get() = when (this) {
        All -> choose("All", "全部")
        Error -> choose("Errors", "错误")
        Warning -> choose("Warnings", "警告")
        Info -> choose("Info", "信息")
        Debug -> choose("Debug", "调试")
        Unknown -> choose("Unknown", "未知")
    }

    fun matches(severity: LogSeverity): Boolean = this == All || name == severity.name
}

@Composable
private fun LogAppBackground() {
    val accentBlue = Color(0xFF0A84FF)
    val accentBlueSoft = Color(0xFF5AC8FA)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.surface),
                    start = Offset(0f, 0f),
                    end = Offset(1000f, 1000f)
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
                        colors = listOf(accentBlue.copy(alpha = 0.25f), Color.Transparent)
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
                        colors = listOf(accentBlueSoft.copy(alpha = 0.22f), Color.Transparent)
                    ),
                    CircleShape
                )
                .blur(90.dp)
        )
    }
}

@Composable
private fun LogGlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(24.dp),
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.04f),
                spotColor = Color.Black.copy(alpha = 0.12f)
            )
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
            .border(BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)), RoundedCornerShape(24.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(
    gatewayStore: GatewayStore,
    apiClient: RelayAPIClient,
    onBack: () -> Unit
) {
    AppSystemBarsEffect()

    val gatewayState by gatewayStore.state.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var report by remember { mutableStateOf<LogTailResponse?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var searchText by remember { mutableStateOf("") }
    var lastLoadedAt by remember { mutableStateOf<String?>(null) }
    var activeLoadRequestId by remember { mutableStateOf(0L) }
    var selectedSource by remember { mutableStateOf(LogViewerSource.Gateway) }
    var selectedSeverity by remember { mutableStateOf(LogSeverityFilter.All) }
    var sourceNotice by remember { mutableStateOf<String?>(null) }

    val selectedGateway = gatewayState.selectedGateway
    val hasSession = gatewayState.gateways.isNotEmpty()
    val hasGateway = selectedGateway != null
    val accessHint = when {
        !hasSession -> stringResource(R.string.log_hint_no_session)
        !hasGateway -> stringResource(R.string.log_hint_no_gateway)
        else -> null
    }

    val allLines = report?.lines ?: emptyList()
    val allEntries = allLines.map(::parseLogLine).filter { it.rawText.isNotBlank() }
    val searchFilter = searchText.trim().lowercase()
    val visibleEntries = allEntries.filter {
        selectedSeverity.matches(it.severity) && (searchFilter.isEmpty() || it.searchText.contains(searchFilter))
    }
    val availableSources = if (selectedGateway?.gatewayType == GatewayType.hermes) {
        listOf(LogViewerSource.Connection)
    } else LogViewerSource.entries

    suspend fun loadLogs() {
        if (accessHint != null) return
        val requestedGatewayId = selectedGateway?.id ?: return
        val requestId = activeLoadRequestId + 1
        activeLoadRequestId = requestId
        isLoading = true
        try {
            val requestedSource = selectedSource
            val fetchedReport = apiClient.fetchLogs(requestedGatewayId, source = requestedSource.wireValue)
            // 固定本次请求的网关，避免切换网关时旧响应覆盖当前日志页。
            if (activeLoadRequestId != requestId || gatewayStore.state.value.selectedGateway?.id != requestedGatewayId) return
            val actualSource = resolveLogSource(fetchedReport, requestedSource)
            selectedSource = actualSource
            sourceNotice = if (actualSource == requestedSource) null else choose(
                "The current Relay does not support this log source. Showing the returned connection log.",
                "当前 Relay 暂不支持该日志源，已显示实际返回的连接日志。"
            )
            report = fetchedReport
            errorMessage = null
            val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            lastLoadedAt = sdf.format(java.util.Date())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (activeLoadRequestId != requestId || gatewayStore.state.value.selectedGateway?.id != requestedGatewayId) return
            errorMessage = context.getString(R.string.log_error_prefix, e.message ?: "Unknown")
        } finally {
            if (activeLoadRequestId == requestId) {
                isLoading = false
            }
        }
    }

    LaunchedEffect(selectedGateway?.id) {
        activeLoadRequestId += 1
        isLoading = false
        report = null
        errorMessage = null
        searchText = ""
        selectedSeverity = LogSeverityFilter.All
        selectedSource = if (selectedGateway?.gatewayType == GatewayType.hermes) LogViewerSource.Connection else LogViewerSource.Gateway
        sourceNotice = null
        lastLoadedAt = null
        loadLogs()
    }

    val listState = rememberLazyListState()

    Box(modifier = Modifier.fillMaxSize()) {
        LogAppBackground()
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing.only(
                WindowInsetsSides.Horizontal + WindowInsetsSides.Top
            ),
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(stringResource(R.string.log_title), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface) },
                    navigationIcon = {
                        Surface(
                            modifier = Modifier.padding(start = 16.dp).size(40.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface,
                            shadowElevation = 2.dp,
                            onClick = onBack
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_action_back), modifier = Modifier.size(20.dp))
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { scope.launch { loadLogs() } },
                            enabled = !isLoading && accessHint == null
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color(0xFF3B82F6))
                            } else {
                                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(20.dp), tint = Color(0xFF3B82F6))
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                        actionIconContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        LogMetricCard(stringResource(R.string.log_metric_gateway), selectedGateway?.displayName ?: "--", Icons.Default.Memory, Modifier.weight(1f))
                        LogMetricCard(stringResource(R.string.log_metric_fetch_time), lastLoadedAt ?: stringResource(R.string.log_last_loaded), Icons.Default.Schedule, Modifier.weight(1f))
                    }

                    LogControlPanel(
                        report = report,
                        selectedSource = selectedSource,
                        sources = availableSources,
                        sourceNotice = sourceNotice,
                        selectedSeverity = selectedSeverity,
                        entries = allEntries,
                        onSourceSelected = { source ->
                            if (selectedSource != source) {
                                selectedSource = source
                                scope.launch { loadLogs() }
                            }
                        },
                        onSeveritySelected = { selectedSeverity = it }
                    )

                    when {
                        errorMessage != null -> LogGlassCard(Modifier.weight(1f)) { Text(errorMessage!!, color = Color(0xFFEF4444), fontSize = 13.sp) }
                        accessHint != null && !isLoading -> LogGlassCard(Modifier.weight(1f)) { Text(accessHint, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp) }
                        isLoading && report == null -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        visibleEntries.isNotEmpty() -> LogTerminalPanel(report, selectedSource, visibleEntries, listState, Modifier.weight(1f))
                        report != null -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.log_no_match_title), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        else -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.log_empty_title), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                if (report != null) {
                    val navigationBarBottom = WindowInsets.navigationBars
                        .asPaddingValues()
                        .calculateBottomPadding()
                    LogSearchBar(
                        searchText = searchText,
                        onSearchTextChange = { searchText = it },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(
                                start = 20.dp,
                                end = 20.dp,
                                bottom = navigationBarBottom + 24.dp
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun LogSearchBar(searchText: String, onSearchTextChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val pillShape = RoundedCornerShape(999.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(25.dp, pillShape, clip = false, ambientColor = Color.Black.copy(alpha = 0.15f), spotColor = Color.Black.copy(alpha = 0.18f))
            .background(MaterialTheme.colorScheme.surface, pillShape)
            .border(width = 0.8.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f), shape = pillShape)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF3B82F6), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        BasicTextField(
            value = searchText,
            onValueChange = onSearchTextChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { innerTextField ->
                Box {
                    if (searchText.isEmpty()) {
                        Text(
                            stringResource(R.string.log_search_placeholder),
                            style = TextStyle(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                    innerTextField()
                }
            }
        )
        if (searchText.isNotEmpty()) {
            IconButton(onClick = { onSearchTextChange("") }, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Default.Close, contentDescription = choose("Clear", "清空"), modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LogMetricCard(title: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    LogGlassCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(32.dp).background(Color(0xFF3B82F6).copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Color(0xFF3B82F6), modifier = Modifier.size(14.dp))
            }
            Column {
                Text(title, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}

@Composable
private fun LogLineRow(entry: ParsedLogLine) {
    val logType = when (entry.sourceText?.lowercase()) {
        "ws" -> "ws" to Color(0xFFA855F7)
        "http" -> "http" to Color(0xFF3B82F6)
        "sys" -> "sys" to Color(0xFFF97316)
        else -> null to MaterialTheme.colorScheme.onSurfaceVariant
    }
    val isError = entry.severity == LogSeverity.Error
    val contentColor = when {
        isError -> Color(0xFFEF4444)
        entry.rawText.contains("✓") -> Color(0xFF22C55E)
        entry.rawText.contains("✗") -> Color(0xFFEF4444)
        entry.severity == LogSeverity.Warning -> Color(0xFFF59E0B)
        entry.severity == LogSeverity.Info -> Color(0xFF3B82F6)
        entry.severity == LogSeverity.Debug -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }

    Column(modifier = Modifier.fillMaxWidth().background(if (isError) Color(0xFFEF4444).copy(0.05f) else Color.Transparent, RoundedCornerShape(10.dp)).padding(vertical = 6.dp, horizontal = 10.dp)) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (logType.first != null) {
                Text(logType.first!!.uppercase(), fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = logType.second,
                    modifier = Modifier.background(logType.second.copy(alpha = 0.15f), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 2.dp))
            }
            Text(entry.displayText, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = contentColor, lineHeight = 16.sp)
        }
    }
}
