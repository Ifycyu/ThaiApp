package com.thai2chinese.ui.processing

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thai2chinese.ProcessingService
import com.thai2chinese.ui.theme.*

@Composable
fun ProcessingScreen(
    videoUri: String, filename: String,
    onNavigateToPlayer: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: ProcessingViewModel = viewModel()
) {
    val step by viewModel.step.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val statusText by viewModel.statusText.collectAsState()
    val error by viewModel.error.collectAsState()
    val taskId by viewModel.taskId.collectAsState()
    val context = LocalContext.current

    // Request notification permission for Android 13+
    val notifPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val intent = Intent(context, ProcessingService::class.java).apply {
                putExtra(ProcessingService.EXTRA_VIDEO_URI, videoUri)
                putExtra(ProcessingService.EXTRA_FILENAME, filename)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!ProcessingService.isRunning && ProcessingService.resultTaskId == null) {
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                val intent = Intent(context, ProcessingService::class.java).apply {
                    putExtra(ProcessingService.EXTRA_VIDEO_URI, videoUri)
                    putExtra(ProcessingService.EXTRA_FILENAME, filename)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }
    }

    LaunchedEffect(taskId) {
        taskId?.let { onNavigateToPlayer(it) }
    }

    Column(modifier = Modifier.fillMaxSize().background(DarkBg).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = TextPrimary) }
            Text("正在处理...", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(48.dp))

        if (error != null) {
            Card(colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("处理失败", color = ToneLow, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(error!!, color = TextSecondary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onBack) { Text("返回") }
                }
            }
        } else {
            Card(colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    StepItem("提取音频", when { step > 0 -> StepState.DONE; step == 0 -> StepState.ACTIVE; else -> StepState.PENDING })
                    StepItem("Whisper 转写", when { step > 1 -> StepState.DONE; step == 1 -> StepState.ACTIVE; else -> StepState.PENDING })
                    StepItem("分词分析", when { step > 2 -> StepState.DONE; step == 2 -> StepState.ACTIVE; else -> StepState.PENDING })
                    Spacer(modifier = Modifier.height(20.dp))
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)), color = AccentBlue, trackColor = DarkSurface)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(statusText, color = TextSecondary, fontSize = 14.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 可以先去看视频，不用等处理完
            if (step >= 1 && ProcessingService.resultTaskId == null) {
                Text("处理在后台继续，你可以返回", color = TextMuted, fontSize = 13.sp)
            }
        }
    }
}

enum class StepState { DONE, ACTIVE, PENDING }

@Composable
fun StepItem(text: String, state: StepState) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(when (state) { StepState.DONE -> "✅"; StepState.ACTIVE -> "⏳"; StepState.PENDING -> "⬜" }, fontSize = 18.sp)
        Spacer(modifier = Modifier.width(12.dp))
        Text(text, color = when (state) { StepState.DONE -> AccentBlue; StepState.ACTIVE -> TextPrimary; StepState.PENDING -> TextMuted },
            fontSize = 16.sp, fontWeight = if (state == StepState.ACTIVE) FontWeight.Bold else FontWeight.Normal)
    }
}
