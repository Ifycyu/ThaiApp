package com.thai2chinese.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thai2chinese.audio.TtsPlayer
import com.thai2chinese.data.AppConfig
import com.thai2chinese.data.Syllable
import com.thai2chinese.data.WordDetail
import com.thai2chinese.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordCardSheet(wordDetail: WordDetail?, isLoading: Boolean, onDismiss: () -> Unit) {
    if (wordDetail != null || isLoading) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = DarkCard, contentColor = TextPrimary, dragHandle = null) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentBlue)
                }
            } else if (wordDetail != null) { WordCardContent(wordDetail) }
        }
    }
}

@Composable
fun WordCardContent(detail: WordDetail) {
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val config = remember { AppConfig.getInstance(context) }

    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text(detail.word, color = AccentBlue, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        if (detail.ipa.isNotBlank()) { Text("/${detail.ipa}/", color = TextSecondary, fontSize = 16.sp, modifier = Modifier.padding(top = 4.dp)) }
        if (detail.word_class.isNotBlank()) {
            Text(detail.word_class, color = AccentPurple, fontSize = 14.sp,
                modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(4.dp)).background(AccentPurple.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 2.dp))
        }
        Spacer(modifier = Modifier.height(12.dp))
        if (detail.meaning.isNotBlank()) { Text(detail.meaning, color = TextPrimary, fontSize = 18.sp, modifier = Modifier.padding(bottom = 12.dp)) }
        if (detail.syllables.isNotEmpty()) {
            Text("音节分解", color = TextMuted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                detail.syllables.forEach { SyllableCard(it) }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
        if (detail.examples.isNotEmpty()) {
            Text("例句", color = TextMuted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp))
            detail.examples.take(3).forEach { Text("• $it", color = TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(vertical = 2.dp)) }
            Spacer(modifier = Modifier.height(12.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { scope.launch { TtsPlayer.play(context, detail.word, config.thaiwordUrl) } },
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue), shape = RoundedCornerShape(8.dp)) { Text("发音") }
            OutlinedButton(onClick = { clipboardManager.setText(AnnotatedString("${detail.word} ${detail.meaning}")) }, shape = RoundedCornerShape(8.dp)) {
                Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp)); Spacer(modifier = Modifier.width(4.dp)); Text("复制")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun SyllableCard(syllable: Syllable) {
    val toneNum = syllable.tone?.tone_number ?: 0
    val color = toneColor(toneNum)
    Card(colors = CardDefaults.cardColors(containerColor = DarkSurface), shape = RoundedCornerShape(8.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(syllable.syllable, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            if (syllable.ipa.isNotBlank()) { Text(syllable.ipa, color = TextSecondary, fontSize = 12.sp) }
            if (toneNum > 0) {
                Box(modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(4.dp)).background(color.copy(alpha = 0.2f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                    Text(syllable.tone?.tone_cn ?: "", color = color, fontSize = 11.sp)
                }
            }
            if (syllable.pronunciation_tip.isNotBlank()) { Text(syllable.pronunciation_tip, color = TextMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp)) }
        }
    }
}
