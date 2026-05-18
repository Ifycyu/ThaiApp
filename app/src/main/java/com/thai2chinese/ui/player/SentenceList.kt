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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thai2chinese.data.Sentence
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

    LazyColumn(state = listState, modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        itemsIndexed(sentences) { index, sentence ->
            SentenceItem(sentence = sentence, isActive = index == activeSentence,
                activeWord = if (index == activeSentence) activeWord else -1,
                onWordClick = onWordClick,
                onSentenceClick = { onSentenceClick(sentence.start) },
                onLongClick = { onSentenceLongClick(index, sentence) })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SentenceItem(sentence: Sentence, isActive: Boolean, activeWord: Int,
    onWordClick: (String, String) -> Unit, onSentenceClick: () -> Unit, onLongClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .background(if (isActive) DarkCard else Color.Transparent)
        .combinedClickable(onClick = onSentenceClick, onLongClick = onLongClick)
        .padding(horizontal = 16.dp, vertical = 12.dp)) {

        // Romanization + Thai words (per-word stacked)
        if (sentence.words.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                sentence.words.forEachIndexed { wordIdx, word ->
                    val ipa = when { word.ipa.isNotBlank() -> word.ipa; word.roman.isNotBlank() -> word.roman; else -> word.text }
                    val isActive = wordIdx == activeWord
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp))
                            .background(if (isActive) ActiveGreen else Color.Transparent)
                            .clickable { onWordClick(word.text, sentence.text) }
                            .padding(horizontal = 6.dp, vertical = 4.dp)) {
                        Text(ipa, color = TextMuted, fontSize = 13.sp, lineHeight = 16.sp, textAlign = TextAlign.Center)
                        Text(word.text, color = if (isActive) Color.White else TextPrimary,
                            fontSize = if (isActive) 20.sp else 18.sp,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                            textAlign = TextAlign.Center, maxLines = 1)
                    }
                }
            }
        } else {
            Text(sentence.text, color = TextPrimary, fontSize = 20.sp, lineHeight = 28.sp,
                modifier = Modifier.padding(bottom = 4.dp))
        }

        // Row 3: Translation
        if (sentence.translation.isNotBlank()) {
            Text(sentence.translation, color = TextSecondary, fontSize = 14.sp, lineHeight = 20.sp,
                modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SentenceMenuSheet(
    sentence: Sentence?,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onCopyBilingual: () -> Unit,
    onRetranslate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onLearn: () -> Unit
) {
    if (sentence == null) return
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = DarkCard, contentColor = TextPrimary) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(sentence.text, color = AccentBlue, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                if (sentence.translation.isNotBlank()) {
                    Text(sentence.translation, color = TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
                }
            }
            HorizontalDivider(color = DarkSurface, modifier = Modifier.padding(vertical = 4.dp))
            MenuAction("复制泰语") { onCopy() }
            MenuAction("复制双语字幕") { onCopyBilingual() }
            MenuAction("重新翻译") { onRetranslate() }
            MenuAction("句子分析") { onLearn() }
            MenuAction("编辑字幕") { onEdit() }
            MenuAction("删除句子", color = ToneLow) { onDelete() }
        }
    }
}

@Composable
fun MenuAction(text: String, color: Color = TextPrimary, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Text(text, color = color, fontSize = 16.sp, modifier = Modifier.fillMaxWidth())
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

fun formatTime(seconds: Double): String {
    val mins = (seconds / 60).toInt(); val secs = (seconds % 60).toInt()
    return "%d:%02d".format(mins, secs)
}
