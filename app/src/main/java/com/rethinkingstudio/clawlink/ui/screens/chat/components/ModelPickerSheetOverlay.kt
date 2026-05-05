package com.rethinkingstudio.clawlink.ui.screens.chat.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rethinkingstudio.clawlink.R
import com.rethinkingstudio.clawlink.core.models.catalog.ModelItem
import com.rethinkingstudio.clawlink.ui.screens.chat.ChatColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerSheetOverlay(
    models: List<ModelItem>,
    isLoading: Boolean,
    errorMessage: String?,
    selectedModel: ModelItem?,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onSelect: (ModelItem) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val groupedModels = models.groupBy { it.providerId }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ChatColors.sheet,
        shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 12.dp)
                    .size(width = 48.dp, height = 5.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xFFB8BCC4))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 22.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SheetHeaderButton(Icons.Default.Close, "Close", onDismiss)
                Text(
                    text = stringResource(R.string.nav_models),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = Color.Black
                )
                if (isLoading) {
                    Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                } else {
                    SheetHeaderButton(Icons.Default.Refresh, "Refresh", onRefresh)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (models.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    ModelPickerEmptyState(isLoading = isLoading, errorMessage = errorMessage, onRefresh = onRefresh)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    groupedModels.entries.sortedBy { it.key }.forEach { (provider, providerModels) ->
                        item {
                            Text(
                                text = provider.lowercase(),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.Gray,
                                modifier = Modifier.padding(start = 12.dp, bottom = 4.dp)
                            )
                            Surface(
                                shape = RoundedCornerShape(22.dp),
                                color = Color.White.copy(alpha = 0.86f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.55f))
                            ) {
                                Column(modifier = Modifier.padding(20.dp)) {
                                    providerModels.forEachIndexed { index, model ->
                                        ModelPickerRow(
                                            model = model,
                                            isSelected = model.modelId == selectedModel?.modelId,
                                            onClick = { onSelect(model) }
                                        )
                                        if (index < providerModels.size - 1) {
                                            Divider(
                                                color = Color.Black.copy(alpha = 0.06f),
                                                modifier = Modifier.padding(vertical = 8.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelPickerEmptyState(isLoading: Boolean, errorMessage: String?, onRefresh: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.padding(horizontal = 32.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(48.dp), color = ChatColors.linkBlue)
            Text(
                text = stringResource(R.string.gateway_sync_models),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = Color.Gray
            )
        } else {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(if (errorMessage != null) ChatColors.offline.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.04f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (errorMessage != null) Icons.Default.ErrorOutline else Icons.Default.SmartToy,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = if (errorMessage != null) ChatColors.offline else Color.Gray.copy(alpha = 0.6f)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (errorMessage != null) "Load Failed" else stringResource(R.string.gateway_no_models),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (errorMessage != null) ChatColors.offline else Color.Black
                )
                Text(
                    text = errorMessage ?: "Ensure your gateway is online and the model provider is correctly configured.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
            }

            Surface(
                onClick = onRefresh,
                shape = CircleShape,
                color = Color.White,
                border = BorderStroke(1.dp, Color.Black.copy(alpha = 0.1f)),
                shadowElevation = 0.dp,
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp), tint = Color.Black)
                    Text(
                        "Refresh Models",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Black
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelPickerRow(model: ModelItem, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(modifier = Modifier.width(18.dp), contentAlignment = Alignment.Center) {
                if (isSelected) {
                    Icon(Icons.Default.Check, null, tint = ChatColors.linkBlue, modifier = Modifier.size(18.dp))
                }
            }

            Icon(Icons.Default.SmartToy, null, tint = if (isSelected) ChatColors.linkBlue else Color.Gray, modifier = Modifier.size(24.dp))

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = model.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Black
                    )
                    if (model.isDefault) {
                        Icon(Icons.Default.Star, "Default", tint = Color(0xFFFF9500), modifier = Modifier.size(10.dp))
                    }
                }
                
                val contextText = model.contextWindow ?: "Unknown"
                val tags = model.capabilities?.joinToString(" · ") ?: "Standard"
                Text(
                    text = "$contextText · $tags",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
private fun SheetHeaderButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.White,
        shadowElevation = 0.dp,
        modifier = Modifier.size(44.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = Color.Black, modifier = Modifier.size(24.dp))
        }
    }
}
