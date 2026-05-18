package com.thai2chinese.ui.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thai2chinese.data.TaskInfo
import com.thai2chinese.ui.theme.*

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToPlayer: (String) -> Unit,
    onNavigateToProcessing: (String, String) -> Unit,
    onNavigateToSettings: () -> Unit = {},
    viewModel: HomeViewModel = viewModel()
) {
    val tasks by viewModel.tasks.collectAsState()
    val context = LocalContext.current
    var renameTaskId by remember { mutableStateOf<String?>(null) }
    var renameTaskName by remember { mutableStateOf("") }
    var menuTaskId by remember { mutableStateOf<String?>(null) }
    val pickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            // 复制到缓存，避免权限问题
            try {
                val cacheFile = java.io.File(context.cacheDir, "pick_${System.currentTimeMillis()}.mp4")
                context.contentResolver.openInputStream(it)?.use { input ->
                    cacheFile.outputStream().use { output -> input.copyTo(output) }
                }
                val filename = context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx) else null
                    } else null
                } ?: "video.mp4"
                onNavigateToProcessing("file://${cacheFile.absolutePath}", filename)
            } catch (e: Exception) {
                e.printStackTrace()
                onNavigateToProcessing(it.toString(), "video.mp4")
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(DarkBg).padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("泰语学习", color = AccentBlue, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = onNavigateToSettings) {
                Icon(Icons.Default.Settings, contentDescription = "设置", tint = TextMuted)
            }
        }

        Card(modifier = Modifier.fillMaxWidth().clickable { pickerLauncher.launch(arrayOf("video/*")) },
            colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FolderOpen, null, tint = AccentBlue, modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text("选择视频", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (tasks.isNotEmpty()) {
            Text("历史记录", color = TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(tasks, key = { it.id }) { task ->
                    Card(modifier = Modifier.fillMaxWidth().combinedClickable(
                            onClick = { if (task.status == "completed" || task.status == "processing") onNavigateToPlayer(task.id) },
                            onLongClick = { menuTaskId = task.id }
                        ),
                        colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PlayArrow, null, tint = if (task.status != "failed") AccentBlue else TextMuted, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(task.filename, color = TextPrimary, fontSize = 15.sp, maxLines = 1)
                                Text(when (task.status) {
                                    "completed" -> "${task.sentences.size} 句"
                                    "processing" -> "⏳ 处理中...（可先看视频）"
                                    "failed" -> "失败: ${task.error ?: ""}"
                                    else -> task.status
                                }, color = if (task.status != "failed") AccentBlue else TextMuted, fontSize = 13.sp)
                            }
                            IconButton(onClick = { viewModel.deleteTask(task.id) }) {
                                Icon(Icons.Default.Delete, "删除", tint = TextMuted)
                            }
                        }
                    }
                }
            }
        }
    }

    // 长按菜单
    if (menuTaskId != null) {
        val menuTask = tasks.find { it.id == menuTaskId }
        ModalBottomSheet(onDismissRequest = { menuTaskId = null }, containerColor = DarkCard, contentColor = TextPrimary) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                Text(menuTask?.filename ?: "", color = AccentBlue, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                HorizontalDivider(color = DarkSurface, modifier = Modifier.padding(vertical = 4.dp))
                TextButton(onClick = {
                    renameTaskId = menuTaskId; renameTaskName = menuTask?.filename ?: ""; menuTaskId = null
                }, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    Text("重命名", color = TextPrimary, fontSize = 16.sp, modifier = Modifier.fillMaxWidth())
                }
                TextButton(onClick = {
                    menuTaskId?.let { viewModel.deleteTask(it) }; menuTaskId = null
                }, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    Text("删除", color = ToneLow, fontSize = 16.sp, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }

    if (renameTaskId != null) {
        AlertDialog(
            onDismissRequest = { renameTaskId = null },
            containerColor = DarkCard,
            title = { Text("重命名", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = renameTaskName,
                    onValueChange = { renameTaskName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = Text("文件名", color = TextSecondary),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                        focusedBorderColor = AccentBlue, unfocusedBorderColor = DarkCard, cursorColor = AccentBlue
                    ),
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (renameTaskName.isNotBlank()) {
                        viewModel.renameTask(renameTaskId!!, renameTaskName.trim())
                    }
                    renameTaskId = null
                }) { Text("确定", color = AccentBlue) }
            },
            dismissButton = {
                TextButton(onClick = { renameTaskId = null }) { Text("取消", color = TextMuted) }
            }
        )
    }
}
