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

@Composable
internal fun BackupHeroCard(gatewayName: String, backupCount: Int, maxBackups: Int, storagePath: String?, latestUpdate: String) {
    BackupGlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(15.dp)) {
                Box(Modifier.size(48.dp).background(Color(0xFF22C55E).copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Archive, null, tint = Color(0xFF22C55E), modifier = Modifier.size(22.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(gatewayName, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color(0xFF1F2937))
                    Text(stringResource(R.string.backup_hero_subtitle), fontSize = 12.sp, color = Color(0xFF6B7280))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("$backupCount/$maxBackups", fontWeight = FontWeight.Black, fontSize = 17.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF1F2937))
                    Text(stringResource(R.string.backup_hero_count_label), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6B7280))
                }
            }
            HorizontalDivider(modifier = Modifier.alpha(0.4f), color = Color(0xFFE5E7EB))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BackupStatBlock(stringResource(R.string.backup_hero_latest), latestUpdate, Modifier.weight(1f))
                BackupStatBlock(stringResource(R.string.backup_hero_storage_node), "LOCAL HOST", Modifier.weight(1f))
            }

            // Storage Path Row (iOS Style)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF22C55E).copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Folder, null, tint = Color(0xFF22C55E).copy(alpha = 0.8f), modifier = Modifier.size(12.dp))
                Text(stringResource(R.string.backup_hero_storage_path).uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF22C55E).copy(alpha = 0.8f))
                Spacer(Modifier.weight(1f))
                Text(storagePath ?: "~/.clawconnect/backups/openclaw", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF1F2937).copy(alpha = 0.8f), maxLines = 1)
            }
        }
    }
}

@Composable
internal fun BackupStatBlock(title: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.background(Color.Black.copy(alpha = 0.04f), RoundedCornerShape(12.dp)).padding(12.dp)) {
        Text(title.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6B7280))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1F2937))
    }
}

@Composable
internal fun BackupRowCard(backup: BackupItem, canManage: Boolean, onEdit: () -> Unit, onDelete: () -> Unit, onRestore: () -> Unit) {
    BackupGlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(backup.displayLabel, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Color(0xFF1F2937))
                    Text(if (backup.detail.isBlank()) stringResource(R.string.backup_editor_no_detail) else backup.detail, fontSize = 13.sp, color = Color(0xFF6B7280), maxLines = 2)
                }
                val sizeBytes = backup.sizeBytes
                if (sizeBytes != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        val sizeStr = if (sizeBytes < 1024) "${sizeBytes}B"
                            else if (sizeBytes < 1024 * 1024) "${sizeBytes / 1024}KB"
                            else "${sizeBytes / (1024 * 1024)}MB"
                        Text(sizeStr, fontWeight = FontWeight.Bold, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.06f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 4.dp))
                        Text(stringResource(R.string.backup_size_label), fontSize = 10.sp, color = Color(0xFF6B7280), fontWeight = FontWeight.Bold)
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BackupInfoTag("FILE", backup.filename.ifBlank { backup.id.takeLast(12) }, Icons.Default.Description)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BackupInfoTag("CREATED", backup.createdAt.take(16).replace("T", " "), Icons.Default.CalendarToday, Modifier.weight(1f))
                    BackupInfoTag("VERSION", "2.0", Icons.Default.Tag, Modifier.weight(1f))
                }
            }

            HorizontalDivider(modifier = Modifier.alpha(0.4f), color = Color(0xFFE5E7EB))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onRestore, enabled = canManage,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(44.dp)
                ) {
                    Icon(Icons.Default.Restore, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.backup_restore_button), fontWeight = FontWeight.Bold)
                }
                
                IconButton(
                    onClick = onEdit, 
                    enabled = canManage, 
                    modifier = Modifier.size(44.dp).background(Color.Black.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                ) {
                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(20.dp), tint = Color(0xFF1F2937))
                }
                
                IconButton(
                    onClick = onDelete, 
                    enabled = canManage, 
                    modifier = Modifier.size(44.dp).background(Color(0xFFEF4444).copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                ) {
                    Icon(Icons.Default.Delete, null, tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
internal fun BackupInfoTag(title: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.background(Color.Black.copy(alpha = 0.04f), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, null, tint = Color(0xFF6B7280), modifier = Modifier.size(10.dp))
        Text(title, fontSize = 9.sp, fontWeight = FontWeight.Black, color = Color(0xFF6B7280))
        Text(value, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF1F2937), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

