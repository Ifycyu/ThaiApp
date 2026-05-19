package com.thai2chinese.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thai2chinese.data.AppConfig
import com.thai2chinese.ui.theme.*

@Composable
fun SettingsScreen(onBack: () -> Unit, config: AppConfig) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var whisperKey by remember { mutableStateOf(config.whisperApiKey) }
    var whisperUrl by remember { mutableStateOf(config.whisperBaseUrl) }
    var thaiwordUrl by remember { mutableStateOf(config.thaiwordUrl) }
    var dictApiUrl by remember { mutableStateOf(config.dictApiUrl) }
    var enableExternalDict by remember { mutableStateOf(config.enableExternalDict) }
    var translateEndpoint by remember { mutableStateOf(config.translateEndpoint) }
    var translateToken by remember { mutableStateOf(config.translateToken) }
    var translateModel by remember { mutableStateOf(config.translateModel) }
    var showKey by remember { mutableStateOf(false) }
    var showToken by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var cleanedSize by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize().background(DarkBg).verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = TextPrimary) }
            Text("设置", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(24.dp))

        SectionTitle("Whisper 语音识别")
        OutlinedTextField(value = whisperKey, onValueChange = { whisperKey = it; saved = false }, modifier = Modifier.fillMaxWidth(),
            label = { Text("API 密钥", color = TextSecondary) },
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = { IconButton(onClick = { showKey = !showKey }) { Icon(if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = TextMuted) } },
            colors = fieldColors(), singleLine = true)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = whisperUrl, onValueChange = { whisperUrl = it; saved = false }, modifier = Modifier.fillMaxWidth(),
            label = { Text("API 地址", color = TextSecondary) }, colors = fieldColors(), singleLine = true)

        Spacer(modifier = Modifier.height(20.dp))
        SectionTitle("ThaiWord 服务")
        OutlinedTextField(value = thaiwordUrl, onValueChange = { thaiwordUrl = it; saved = false }, modifier = Modifier.fillMaxWidth(),
            label = { Text("服务地址", color = TextSecondary) }, colors = fieldColors(), singleLine = true)

        Spacer(modifier = Modifier.height(20.dp))
        SectionTitle("词典 API（X-Dict-API）")
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("点击查词时同时查询外部词典", color = TextSecondary, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Switch(checked = enableExternalDict, onCheckedChange = { enableExternalDict = it; saved = false })
        }
        if (enableExternalDict) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = dictApiUrl, onValueChange = { dictApiUrl = it; saved = false }, modifier = Modifier.fillMaxWidth(),
                label = { Text("词典 API 地址", color = TextSecondary) }, colors = fieldColors(), singleLine = true)
        }

        Spacer(modifier = Modifier.height(20.dp))
        SectionTitle("翻译 API")
        OutlinedTextField(value = translateEndpoint, onValueChange = { translateEndpoint = it; saved = false }, modifier = Modifier.fillMaxWidth(),
            label = { Text("X-Translate-Endpoint", color = TextSecondary) }, colors = fieldColors(), singleLine = true)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = translateToken, onValueChange = { translateToken = it; saved = false }, modifier = Modifier.fillMaxWidth(),
            label = { Text("X-Translate-Token", color = TextSecondary) },
            visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = { IconButton(onClick = { showToken = !showToken }) { Icon(if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = TextMuted) } },
            colors = fieldColors(), singleLine = true)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = translateModel, onValueChange = { translateModel = it; saved = false }, modifier = Modifier.fillMaxWidth(),
            label = { Text("X-Translate-Model（可选）", color = TextSecondary) }, colors = fieldColors(), singleLine = true)

        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = {
            config.whisperApiKey = whisperKey.trim(); config.whisperBaseUrl = whisperUrl.trim(); config.thaiwordUrl = thaiwordUrl.trim()
            config.dictApiUrl = dictApiUrl.trim()
            config.enableExternalDict = enableExternalDict; config.translateEndpoint = translateEndpoint.trim()
            config.translateToken = translateToken.trim(); config.translateModel = translateModel.trim(); saved = true
        }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = AccentBlue), shape = RoundedCornerShape(8.dp)) {
            Text("保存", fontSize = 16.sp, modifier = Modifier.padding(vertical = 4.dp))
        }
        if (saved) { Spacer(modifier = Modifier.height(8.dp)); Text("已保存", color = AccentBlue, fontSize = 14.sp) }

        Spacer(modifier = Modifier.height(16.dp))

        // 清理临时文件
        OutlinedButton(
            onClick = {
                val ctx = context
                val cacheDir = ctx.cacheDir
                var totalSize = 0L
                var count = 0
                cacheDir.listFiles()?.forEach { file ->
                    if (file.name.startsWith("temp_video_") || (file.name.startsWith("audio_") && file.extension == "m4a")) {
                        totalSize += file.length()
                        file.delete()
                        count++
                    }
                }
                cleanedSize = if (count > 0) "已清理 $count 个文件，释放 ${totalSize / 1024 / 1024}MB" else "没有临时文件"
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("清理临时文件", color = TextPrimary)
        }
        if (cleanedSize != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(cleanedSize!!, color = AccentBlue, fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))
        Card(colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(8.dp)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("说明", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text("• Whisper：语音识别，密钥和地址必填", color = TextSecondary, fontSize = 13.sp)
                Text("• ThaiWord：分词、声调、TTS", color = TextSecondary, fontSize = 13.sp)
                Text("• 词典 API：开启后点击单词会同时查外部词典", color = TextSecondary, fontSize = 13.sp)
                Text("• 翻译 API：泰语转中文 LLM", color = TextSecondary, fontSize = 13.sp)
            }
        }
    }
}

@Composable private fun SectionTitle(text: String) { Text(text, color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp)) }
@Composable private fun fieldColors() = AppTextFieldColors
