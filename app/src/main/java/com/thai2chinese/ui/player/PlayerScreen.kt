package com.thai2chinese.ui.player

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.PlayerView
import com.thai2chinese.ui.theme.*

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
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val clipboardManager = LocalClipboardManager.current
    var showEditDialog by remember { mutableStateOf(false) }
    var editSentence by remember { mutableStateOf<com.thai2chinese.data.Sentence?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(taskId) { viewModel.loadTask(taskId) }

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
        onLearn = { viewModel.learnSentence() }
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

    if (task == null) {
        Box(modifier = Modifier.fillMaxSize().background(DarkBg), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AccentBlue) }
        return
    }

    val currentTask = task!!

    if (isLandscape) {
        Row(modifier = Modifier.fillMaxSize().background(DarkBg)) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) { VideoPlayerView(viewModel.player, Modifier.fillMaxSize()) }
            Column(modifier = Modifier.weight(1f).fillMaxHeight().background(DarkSurface)) {
                SentenceList(currentTask.sentences, activeSentence, activeWord,
                    onWordClick = { w, c -> viewModel.onWordClick(w, c) },
                    onSentenceClick = { viewModel.seekTo(it) },
                    onSentenceLongClick = { idx, sent -> viewModel.showSentenceMenu(idx, sent) },
                    modifier = Modifier.weight(1f))
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize().background(DarkBg)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = TextPrimary) }
                Text(currentTask.filename, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, modifier = Modifier.weight(1f))
            }
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) { VideoPlayerView(viewModel.player, Modifier.fillMaxSize()) }
            SentenceList(currentTask.sentences, activeSentence, activeWord,
                onWordClick = { w, c -> viewModel.onWordClick(w, c) },
                onSentenceClick = { viewModel.seekTo(it) },
                onSentenceLongClick = { idx, sent -> viewModel.showSentenceMenu(idx, sent) },
                modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun VideoPlayerView(player: androidx.media3.common.Player, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    AndroidView(factory = { PlayerView(context).apply { this.player = player; useController = true } }, modifier = modifier)
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
                // 直接显示 AI 返回的 Markdown 文本
                Text(result, color = TextPrimary, fontSize = 15.sp, lineHeight = 24.sp)
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
