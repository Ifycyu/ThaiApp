package com.thai2chinese.ui.player

import android.content.res.Configuration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.activity.compose.BackHandler
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.PlayerView
import com.thai2chinese.ui.theme.*
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

@Composable
fun PlayerScreen(taskId: String, onBack: () -> Unit, viewModel: PlayerViewModel = viewModel()) {
    val task by viewModel.task.collectAsState()
    val activeSentence by viewModel.activeSentence.collectAsState()
    val activeWord by viewModel.activeWord.collectAsState()
    val selectedWord by viewModel.selectedWord.collectAsState()
    val selectedDictResult by viewModel.selectedDictResult.collectAsState()
    val isLoadingWord by viewModel.isLoadingWord.collectAsState()
    val menuSentence by viewModel.menuSentence.collectAsState()
    val learnResult by viewModel.learnResult.collectAsState()
    val isLearning by viewModel.isLearning.collectAsState()
    val batchProgress by viewModel.batchProgress.collectAsState()
    val retranscribeProgress by viewModel.retranscribeProgress.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    var isPlaying by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
        }
        viewModel.player.addListener(listener)
        onDispose { viewModel.player.removeListener(listener) }
    }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val clipboardManager = LocalClipboardManager.current
    var showEditDialog by remember { mutableStateOf(false) }
    var editSentence by remember { mutableStateOf<com.thai2chinese.data.Sentence?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showRetranscribeDialog by remember { mutableStateOf(false) }
    var retranscribeSentence by remember { mutableStateOf<com.thai2chinese.data.Sentence?>(null) }
    var showPlayer by remember { mutableStateOf(true) }
    var showShadowingSheet by remember { mutableStateOf(false) }
    var pendingShadowingSentence by remember { mutableStateOf<com.thai2chinese.data.Sentence?>(null) }
    val context = LocalContext.current

    val recordPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            pendingShadowingSentence?.let {
                viewModel.startShadowing(it)
                showShadowingSheet = true
            }
        }
        pendingShadowingSentence = null
    }

    fun launchShadowing(sentence: com.thai2chinese.data.Sentence) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            viewModel.startShadowing(sentence)
            showShadowingSheet = true
        } else {
            pendingShadowingSentence = sentence
            recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(taskId) { viewModel.loadTask(taskId) }

    // 返回前先隐藏播放器，避免残留
    BackHandler {
        showPlayer = false
        viewModel.player.clearMediaItems()
        viewModel.player.stop()
        onBack()
    }

    // 词卡
    WordCardSheet(wordDetail = selectedWord, dictResult = selectedDictResult, isLoading = isLoadingWord, onDismiss = { viewModel.dismissWordCard() })

    // 句子分析
    LearnSheet(result = learnResult, isLoading = isLearning, onDismiss = { viewModel.dismissLearn() })

    // 长按菜单
    SentenceMenuSheet(sentence = menuSentence, onDismiss = { viewModel.dismissSentenceMenu() },
        onCopy = {
            menuSentence?.let { clipboardManager.setText(AnnotatedString(it.text)); viewModel.dismissSentenceMenu() }
        },
        onCopyBilingual = {
            menuSentence?.let {
                val bilingual = "${it.text}\n${it.translation}"
                clipboardManager.setText(AnnotatedString(bilingual))
            }
            viewModel.dismissSentenceMenu()
        },
        onRetranslate = { viewModel.retranslateSentence() },
        onDelete = { showDeleteConfirm = true; viewModel.dismissSentenceMenu() },
        onEdit = {
            val captured = menuSentence
            editSentence = captured
            captured?.let { viewModel.prepareEdit(it) }
            showEditDialog = true
            viewModel.dismissSentenceMenu()
        },
        onLearn = { viewModel.learnSentence() },
        onReAnalyze = { viewModel.reAnalyzeSentence() },
        onRetranscribe = {
            val captured = menuSentence
            retranscribeSentence = captured
            showRetranscribeDialog = true
            viewModel.dismissSentenceMenu()
        }
    )

    // 编辑对话框
    // 删除确认
    if (showDeleteConfirm) {
        AlertDialog(onDismissRequest = { showDeleteConfirm = false }, containerColor = DarkCard,
            title = { Text("删除句子", color = TextPrimary) },
            text = { Text("确定删除这句字幕吗？", color = TextSecondary) },
            confirmButton = { TextButton(onClick = { viewModel.deleteSentence(); showDeleteConfirm = false }) { Text("删除", color = ToneLow) } },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("取消", color = TextMuted) } })
    }

    if (showEditDialog && editSentence != null) {
        EditSentenceDialog(sentence = editSentence, onDismiss = { showEditDialog = false },
            onSave = { text, translation -> viewModel.editSentence(text, translation); showEditDialog = false })
    }

    if (showRetranscribeDialog && retranscribeSentence != null) {
        RetranscribeDialog(
            sentence = retranscribeSentence!!,
            duration = duration / 1000f,
            onDismiss = { showRetranscribeDialog = false },
            onConfirm = { startSec, endSec ->
                showRetranscribeDialog = false
                viewModel.retranscribeRange(startSec.toDouble(), endSec.toDouble())
            }
        )
    }

    // 跟读弹窗
    val shadowingSentence by viewModel.shadowingSentence.collectAsState()
    val isLooping by viewModel.isLooping.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val recordingFile by viewModel.recordingFile.collectAsState()
    val isPlayingRecording by viewModel.isPlayingRecording.collectAsState()

    if (showShadowingSheet) {
        ShadowingSheet(
            sentence = shadowingSentence,
            isLooping = isLooping,
            isRecording = isRecording,
            recordingFile = recordingFile,
            isPlayingRecording = isPlayingRecording,
            onDismiss = { showShadowingSheet = false; viewModel.stopShadowing() },
            onToggleLoop = { viewModel.toggleLoop() },
            onStartRecording = { viewModel.startRecording() },
            onStopRecording = { viewModel.stopRecording() },
            onPlayRecording = { viewModel.playRecording() },
            onStopRecordingPlayback = { viewModel.stopRecordingPlayback() },
            onWordClick = { w, c -> viewModel.onWordClick(w, c) }
        )
    }

    if (task == null) {
        Box(modifier = Modifier.fillMaxSize().background(DarkBg), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AccentBlue) }
        return
    }

    val currentTask = task!!

    if (isLandscape) {
        Column(modifier = Modifier.fillMaxSize().background(DarkBg)) {
            Row(modifier = Modifier.weight(1f)) {
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    androidx.compose.animation.AnimatedVisibility(visible = showPlayer, exit = fadeOut()) {
                        VideoPlayerView(viewModel.player, Modifier.fillMaxSize())
                    }
                }
                Column(modifier = Modifier.weight(1f).fillMaxHeight().background(DarkSurface)) {
                    SentenceList(currentTask.sentences, activeSentence, activeWord,
                        onWordClick = { w, c -> viewModel.onWordClick(w, c) },
                        onSentenceClick = { viewModel.seekTo(it) },
                        onSentenceLongClick = { idx, sent -> viewModel.showSentenceMenu(idx, sent) },
                        modifier = Modifier.weight(1f))
                }
            }
            PlayerProgressBar(currentPosition, duration, isPlaying,
                onSeek = { viewModel.seekToMs(it) },
                onPlayPause = { viewModel.togglePlayPause() },
                onShadowing = {
                    val sentences = currentTask.sentences
                    val idx = activeSentence
                    if (idx >= 0 && idx < sentences.size) {
                        launchShadowing(sentences[idx])
                    }
                })
        }
    } else {
        Column(modifier = Modifier.fillMaxSize().background(DarkBg)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = TextPrimary) }
                Text(currentTask.filename, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, modifier = Modifier.weight(1f))
                if (retranscribeProgress != null) {
                    Text(retranscribeProgress!!, color = AccentBlue, fontSize = 13.sp, modifier = Modifier.padding(end = 8.dp))
                } else if (batchProgress != null) {
                    Text(batchProgress!!, color = AccentBlue, fontSize = 13.sp, modifier = Modifier.padding(end = 8.dp))
                } else if (currentTask.sentences.any { it.translation.isBlank() || (it.words.isNotEmpty() && it.words.first().ipa.isBlank()) }) {
                    TextButton(onClick = { viewModel.enrichAllPending() }) {
                        Text("分析全部", color = AccentBlue, fontSize = 13.sp)
                    }
                }
            }
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                androidx.compose.animation.AnimatedVisibility(visible = showPlayer, exit = fadeOut()) {
                    VideoPlayerView(viewModel.player, Modifier.fillMaxSize())
                }
            }
            SentenceList(currentTask.sentences, activeSentence, activeWord,
                onWordClick = { w, c -> viewModel.onWordClick(w, c) },
                onSentenceClick = { viewModel.seekTo(it) },
                onSentenceLongClick = { idx, sent -> viewModel.showSentenceMenu(idx, sent) },
                modifier = Modifier.weight(1f))
            PlayerProgressBar(currentPosition, duration, isPlaying,
                onSeek = { viewModel.seekToMs(it) },
                onPlayPause = { viewModel.togglePlayPause() },
                onShadowing = {
                    val sentences = currentTask.sentences
                    val idx = activeSentence
                    if (idx >= 0 && idx < sentences.size) {
                        launchShadowing(sentences[idx])
                    }
                })
        }
    }
}

@Composable
fun VideoPlayerView(player: androidx.media3.common.Player, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    AndroidView(factory = {
        PlayerView(context).apply {
            this.player = player
            useController = false
            setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            useArtwork = false
        }
    }, modifier = modifier)
}

@Composable
fun PlayerProgressBar(
    currentPosition: Float, duration: Float, isPlaying: Boolean,
    onSeek: (Float) -> Unit, onPlayPause: () -> Unit, onShadowing: () -> Unit = {}
) {
    val totalSec = duration / 1000f
    val curSec = currentPosition / 1000f
    var isDragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf(0f) }
    val displayValue = if (isDragging) dragValue else curSec

    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(start = 4.dp, end = 4.dp, bottom = 4.dp)
        .shadow(4.dp, RoundedCornerShape(12.dp))
        .clip(RoundedCornerShape(12.dp))
        .background(DarkCard)
        .padding(horizontal = 12.dp, vertical = 8.dp)) {
        Slider(
            value = displayValue,
            onValueChange = { isDragging = true; dragValue = it },
            onValueChangeFinished = { isDragging = false; onSeek(dragValue * 1000f) },
            valueRange = 0f..(if (totalSec > 0) totalSec else 1f),
            modifier = Modifier.fillMaxWidth().height(32.dp),
            colors = SliderDefaults.colors(
                thumbColor = AccentBlue,
                activeTrackColor = AccentBlue,
                inactiveTrackColor = DarkSurface
            )
        )
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(formatTime(displayValue.toDouble()), color = if (isDragging) AccentBlue else TextMuted, fontSize = 13.sp)
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onPlayPause, modifier = Modifier.size(36.dp)) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                    tint = TextPrimary, modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onShadowing, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Mic, contentDescription = "跟读", tint = TextPrimary, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(formatTime(duration / 1000.0), color = TextMuted, fontSize = 13.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearnSheet(result: String?, isLoading: Boolean, onDismiss: () -> Unit) {
    if (result == null && !isLoading) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = DarkCard, contentColor = TextPrimary) {
        if (isLoading) {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = AccentBlue)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("AI 分析中...", color = TextSecondary)
                }
            }
        } else if (result != null) {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp)) {
                Text("句子分析", color = AccentBlue, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))
                MarkdownText(result)
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun MarkdownText(markdown: String) {
    val lines = markdown.split("\n")
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trimEnd()
            when {
                line.startsWith("### ") -> {
                    Text(line.removePrefix("### "), color = AccentPurple, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
                }
                line.startsWith("## ") -> {
                    Text(line.removePrefix("## "), color = AccentBlue, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                }
                line.startsWith("# ") -> {
                    Text(line.removePrefix("# "), color = AccentBlue, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                }
                line.matches(Regex("^\\d+\\.\\s.*")) -> {
                    val num = line.replaceFirst(Regex("^(\\d+\\.\\s).*"), "$1")
                    val text = line.removePrefix(num)
                    Row(modifier = Modifier.fillMaxWidth().padding(start = 4.dp)) {
                        Text(num, color = AccentBlue, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        RichText(text, fontSize = 15.sp, color = TextPrimary)
                    }
                }
                line.startsWith("- ") || line.startsWith("* ") -> {
                    Row(modifier = Modifier.fillMaxWidth().padding(start = 4.dp)) {
                        Text("  •  ", color = AccentBlue, fontSize = 15.sp)
                        RichText(line.removePrefix("- ").removePrefix("* "), fontSize = 15.sp, color = TextPrimary)
                    }
                }
                line.isBlank() -> { Spacer(modifier = Modifier.height(6.dp)) }
                else -> { RichText(line, fontSize = 15.sp, color = TextPrimary) }
            }
            i++
        }
    }
}

@Composable
fun RichText(text: String, fontSize: TextUnit, color: Color) {
    // Parse **bold** segments
    val parts = mutableListOf<Pair<String, Boolean>>()
    val regex = Regex("\\*\\*(.*?)\\*\\*")
    var lastEnd = 0
    regex.findAll(text).forEach { match ->
        if (match.range.first > lastEnd) parts.add(text.substring(lastEnd, match.range.first) to false)
        parts.add(match.groupValues[1] to true)
        lastEnd = match.range.last + 1
    }
    if (lastEnd < text.length) parts.add(text.substring(lastEnd) to false)

    if (parts.isEmpty()) {
        Text(text, color = color, fontSize = fontSize, lineHeight = (fontSize.value * 1.5).sp)
        return
    }

    Text(buildAnnotatedString {
        parts.forEach { (segment, isBold) ->
            if (isBold) {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = AccentBlue))
                append(segment)
                pop()
            } else {
                append(segment)
            }
        }
    }, color = color, fontSize = fontSize, lineHeight = (fontSize.value * 1.5).sp)
}

@Composable
fun RetranscribeDialog(sentence: com.thai2chinese.data.Sentence, duration: Float, onDismiss: () -> Unit, onConfirm: (Float, Float) -> Unit) {
    val totalSec = if (duration > 0) duration else sentence.end.toFloat()
    var startSec by remember { mutableFloatStateOf(sentence.start.toFloat()) }
    var endSec by remember { mutableFloatStateOf(sentence.end.toFloat()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = { Text("重新识别", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(sentence.text, color = AccentBlue, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                Spacer(modifier = Modifier.height(16.dp))

                Text("开始时间: ${formatTime(startSec.toDouble())}", color = TextSecondary, fontSize = 14.sp)
                Slider(
                    value = startSec,
                    onValueChange = { if (it < endSec - 0.5f) startSec = it },
                    valueRange = 0f..totalSec,
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(thumbColor = AccentBlue, activeTrackColor = AccentBlue, inactiveTrackColor = DarkSurface)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text("结束时间: ${formatTime(endSec.toDouble())}", color = TextSecondary, fontSize = 14.sp)
                Slider(
                    value = endSec,
                    onValueChange = { if (it > startSec + 0.5f) endSec = it },
                    valueRange = 0f..totalSec,
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(thumbColor = AccentBlue, activeTrackColor = AccentBlue, inactiveTrackColor = DarkSurface)
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text("范围: ${formatTime(startSec.toDouble())} - ${formatTime(endSec.toDouble())}（${String.format("%.1f", endSec - startSec)}秒）",
                    color = TextMuted, fontSize = 13.sp)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(startSec, endSec) }) { Text("开始识别", color = AccentBlue) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = TextMuted) } }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun ShadowingSheet(
    sentence: com.thai2chinese.data.Sentence?,
    isLooping: Boolean,
    isRecording: Boolean,
    recordingFile: java.io.File?,
    isPlayingRecording: Boolean,
    onDismiss: () -> Unit,
    onToggleLoop: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onPlayRecording: () -> Unit,
    onStopRecordingPlayback: () -> Unit,
    onWordClick: (String, String) -> Unit
) {
    if (sentence == null) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = DarkCard, contentColor = TextPrimary) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            // 句子显示
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                if (sentence.words.isNotEmpty()) {
                    FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        sentence.words.forEach { word ->
                            val ipa = when { word.ipa.isNotBlank() -> word.ipa; word.roman.isNotBlank() -> word.roman; else -> word.text }
                            Column(horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable { onWordClick(word.text, sentence.text) }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)) {
                                Text(ipa, color = TextMuted, fontSize = 13.sp, lineHeight = 16.sp)
                                Text(word.text, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }
                    }
                } else {
                    Text(sentence.text, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                if (sentence.translation.isNotBlank()) {
                    Text(sentence.translation, color = TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }

            HorizontalDivider(color = DarkSurface, modifier = Modifier.padding(vertical = 8.dp))

            // 操作按钮
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                // 循环开关
                IconButton(onClick = onToggleLoop) {
                    Icon(if (isLooping) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        contentDescription = "循环", tint = if (isLooping) AccentBlue else TextMuted, modifier = Modifier.size(28.dp))
                }
                // 录音按钮
                IconButton(onClick = { if (isRecording) onStopRecording() else onStartRecording() }) {
                    Icon(if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = if (isRecording) "停止录音" else "录音",
                        tint = if (isRecording) ToneLow else TextPrimary, modifier = Modifier.size(28.dp))
                }
                // 播放录音
                if (recordingFile != null && recordingFile.exists() && !isRecording) {
                    IconButton(onClick = { if (isPlayingRecording) onStopRecordingPlayback() else onPlayRecording() }) {
                        Icon(if (isPlayingRecording) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = if (isPlayingRecording) "停止" else "播放录音",
                            tint = if (isPlayingRecording) ToneLow else AccentBlue, modifier = Modifier.size(28.dp))
                    }
                }
            }

            // 录音状态提示
            if (isRecording) {
                Text("录音中...", color = ToneLow, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp).fillMaxWidth(), textAlign = TextAlign.Center)
            }
        }
    }
}
