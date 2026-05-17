package com.thai2chinese.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
    onWordClick: (String, String) -> Unit, onSentenceClick: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    LaunchedEffect(activeSentence) { if (activeSentence >= 0) listState.animateScrollToItem(activeSentence) }

    LazyColumn(state = listState, modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        itemsIndexed(sentences) { index, sentence ->
            SentenceItem(sentence = sentence, isActive = index == activeSentence,
                activeWord = if (index == activeSentence) activeWord else -1,
                onWordClick = onWordClick, onSentenceClick = { onSentenceClick(sentence.start) })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SentenceItem(sentence: Sentence, isActive: Boolean, activeWord: Int, onWordClick: (String, String) -> Unit, onSentenceClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
        .background(if (isActive) DarkCard else Color.Transparent)
        .clickable(onClick = onSentenceClick).padding(horizontal = 12.dp, vertical = 8.dp)) {
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
