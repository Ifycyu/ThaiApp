package com.thai2chinese.ui.player

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thai2chinese.data.Sentence
import com.thai2chinese.data.Word
import com.thai2chinese.ui.theme.*

@Composable
fun SentenceList(
    sentences: List<Sentence>, activeSentence: Int, activeWord: Int,
    onWordClick: (String, String) -> Unit,
    onSentenceClick: (Double) -> Unit,
    onSentenceLongClick: (Int, Sentence) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    LaunchedEffect(activeSentence) { if (activeSentence >= 0) listState.animateScrollToItem(activeSentence) }

    LazyColumn(state = listState, modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        itemsIndexed(sentences) { index, sentence ->
            SentenceItem(sentence = sentence, isActive = index == activeSentence,
                activeWord = if (index == activeSentence) activeWord else -1,
                onWordClick = onWordClick,
                onSentenceClick = { onSentenceClick(sentence.start) },
                onLongClick = { onSentenceLongClick(index, sentence) })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun SentenceItem(sentence: Sentence, isActive: Boolean, activeWord: Int,
    onWordClick: (String, String) -> Unit, onSentenceClick: () -> Unit, onLongClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
        .background(if (isActive) DarkCard else Color.Transparent)
        .combinedClickable(onClick = onSentenceClick, onLongClick = onLongClick)
        .padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(formatTime(sentence.start), color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
        FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            sentence.words.forEachIndexed { wordIdx, word ->
                WordGroup(word = word, isActive = wordIdx == activeWord, onClick = { onWordClick(word.text, sentence.text) })
            }
        }
        if (sentence.translation.isNotBlank()) {
            Text(sentence.translation, color = TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
fun SentenceMenuSheet(
    sentence: Sentence?,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onCopyBilingual: () -> Unit,
    onRetranslate: () -> Unit,
    onEdit: () -> Unit
) {
    if (sentence == null) return
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = DarkCard, contentColor = TextPrimary) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            // 预览
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(sentence.text, color = AccentBlue, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                if (sentence.translation.isNotBlank()) {
                    Text(sentence.translation, color = TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
                }
            }
            @Suppress("DEPRECATION")
            Divider(color = DarkSurface, modifier = Modifier.padding(vertical = 4.dp))
            // 操作
            MenuAction("复制泰语") { onCopy() }
            MenuAction("复制双语字幕") { onCopyBilingual() }
            MenuAction("重新翻译") { onRetranslate() }
            MenuAction("编辑字幕") { onEdit() }
        }
    }
}

@Composable
fun MenuAction(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Text(text, color = TextPrimary, fontSize = 16.sp, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun EditSentenceDialog(sentence: Sentence?, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    if (sentence == null) return
    var text by remember(sentence) { mutableStateOf(sentence.text) }
    var translation by remember(sentence) { mutableStateOf(sentence.translation) }

    AlertDialog(onDismissRequest = onDismiss, containerColor = DarkCard, title = { Text("编辑字幕", color = TextPrimary) },
        text = {
            Column {
                OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("泰语", color = TextSecondary) },
                    modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, focusedBorderColor = AccentBlue, unfocusedBorderColor = DarkCard, cursorColor = AccentBlue))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = translation, onValueChange = { translation = it }, label = { Text("中文翻译", color = TextSecondary) },
                    modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, focusedBorderColor = AccentBlue, unfocusedBorderColor = DarkCard, cursorColor = AccentBlue))
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text, translation); onDismiss() }) { Text("保存", color = AccentBlue) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = TextMuted) } })
}

@Composable
fun WordGroup(word: Word, isActive: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clip(RoundedCornerShape(4.dp))
            .background(if (isActive) AccentBlue.copy(alpha = 0.25f) else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 3.dp, vertical = 2.dp)) {
        val romanText = when { word.ipa.isNotBlank() -> word.ipa; word.roman.isNotBlank() -> word.roman; else -> "" }
        if (romanText.isNotBlank()) { Text(romanText, color = if (isActive) AccentBlue else TextMuted, fontSize = 10.sp, maxLines = 1) }
        Text(word.text, color = if (isActive) Color.White else TextPrimary, fontSize = if (isActive) 18.sp else 16.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
    }
}

fun formatTime(seconds: Double): String {
    val mins = (seconds / 60).toInt(); val secs = (seconds % 60).toInt()
    return "%d:%02d".format(mins, secs)
}
