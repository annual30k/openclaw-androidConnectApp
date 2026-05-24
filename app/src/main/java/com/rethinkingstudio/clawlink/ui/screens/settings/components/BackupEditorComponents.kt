package com.rethinkingstudio.clawlink.ui.screens.settings

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rethinkingstudio.clawlink.R
import com.rethinkingstudio.clawlink.core.models.backups.BackupDraft
import com.rethinkingstudio.clawlink.core.models.backups.BackupItem
import com.rethinkingstudio.clawlink.core.network.RelayAPIClient
import com.rethinkingstudio.clawlink.core.state.LocalizedText.choose
import com.rethinkingstudio.clawlink.core.state.gateway.GatewayStore
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BackupEditorSheet(
    isCreate: Boolean,
    isHermesGateway: Boolean = false,
    initialDraft: BackupDraft,
    onDismiss: () -> Unit,
    onSave: (BackupDraft) -> Unit
) {
    var draft by remember { mutableStateOf(initialDraft) }
    var isSubmitting by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = BackupSheetShape,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 42.dp, height = 5.dp)
                    .clip(BackupPillShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.26f))
            )
        }
    ) {
        val topSafePadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = topSafePadding + 8.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(if (isCreate) choose("New backup", "新建备份") else choose("Edit backup", "编辑备份"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            when {
                                isCreate && isHermesGateway -> choose("Create a Hermes zip backup on the host.", "在宿主机上创建 Hermes zip 备份。")
                                isCreate -> choose("Fill in name, details, and filename. The backup is saved locally on the host.", "填写名称、详情和文件名，备份会保存在宿主机本地。")
                                else -> choose("Adjust the backup display name, notes, and filename.", "可以调整备份的展示名、备注和文件名。")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = onDismiss) { Text(choose("Cancel", "取消")) }
                    Button(
                        onClick = { 
                            isSubmitting = true
                            onSave(draft) 
                        },
                        enabled = draft.title.isNotBlank() && !isSubmitting,
                        shape = BackupPillShape,
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                    ) {
                        if (isSubmitting) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp) else Text(choose("Save", "保存"))
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FieldLabel(choose("Backup name", "备份名称"))
                    IosTextField(
                        value = draft.title,
                        onValueChange = { draft = draft.copy(title = it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = choose("Example: 2026-05-08 backup", "例如：2026-05-08 备份"),
                        singleLine = true
                    )
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FieldLabel(choose("Notes", "备注详情"))
                    IosTextField(
                        value = draft.detail,
                        onValueChange = { draft = draft.copy(detail = it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = choose("Describe this backup...", "填写一些关于此备份的描述..."),
                        minLines = 3,
                        maxLines = 5
                    )
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FieldLabel(choose("Filename", "文件名称"))
                    IosTextField(
                        value = draft.filename,
                        onValueChange = { draft = draft.copy(filename = it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = if (isHermesGateway) "hermes-backup-example.zip" else "example.json",
                        singleLine = true
                    )
                    Text(
                        if (isHermesGateway) choose("Tip: filename must end with .zip", "提示：文件名必须以 .zip 结尾")
                        else choose("Tip: filename must end with .json", "提示：文件名必须以 .json 结尾"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FieldLabel(choose("Preview", "效果预览"))
                    BackupRowCard(
                        backup = BackupItem(
                            id = "preview",
                            title = draft.title.ifBlank { choose("Untitled backup", "未命名备份") },
                            detail = draft.detail.ifBlank { choose("No notes", "没有备注") },
                            filename = draft.filename.ifBlank { choose("Auto-generated filename", "自动生成文件名") },
                            createdAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date()),
                            sizeBytes = 1024 * 1024 // Mock size
                        ),
                        canManage = false,
                        onEdit = {},
                        onDelete = {},
                        onRestore = {}
                    )
                }
            }
        }
    }
}

@Composable
internal fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun IosTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .clip(BackupFieldShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f))
            .border(0.6.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), BackupFieldShape)
            .padding(horizontal = 18.dp, vertical = 17.dp),
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        keyboardOptions = keyboardOptions,
        textStyle = TextStyle(
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = MaterialTheme.typography.bodyMedium.fontSize,
            fontWeight = FontWeight.Medium
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { innerTextField ->
            Box {
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f)
                    )
                }
                innerTextField()
            }
        }
    )
}
