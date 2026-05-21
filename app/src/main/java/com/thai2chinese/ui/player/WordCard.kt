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
import com.thai2chinese.api.DictApiResult
import com.thai2chinese.audio.TtsPlayer
import com.thai2chinese.data.AppConfig
import com.thai2chinese.data.Syllable
import com.thai2chinese.data.WordDetail
import com.thai2chinese.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordCardSheet(wordDetail: WordDetail?, dictResult: List<DictApiResult>?, isLoading: Boolean, onDismiss: () -> Unit) {
    if (wordDetail != null || isLoading) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = DarkCard, contentColor = TextPrimary, dragHandle = null) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentBlue)
                }
            } else if (wordDetail != null) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    WordCardContent(wordDetail)
                    if (dictResult != null) {
                        dictResult.forEach { DictApiCardContent(it) }
                    }
                }
            }
        }
    }
}

@Composable
fun WordCardContent(detail: WordDetail) {
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val config = remember { AppConfig.getInstance(context) }

    Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
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
            OutlinedButton(onClick = { clipboardManager.setText(AnnotatedString(detail.word)) }, shape = RoundedCornerShape(8.dp)) {
                Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp)); Spacer(modifier = Modifier.width(4.dp)); Text("复制")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
fun DictApiCardContent(result: DictApiResult) {
    val clipboardManager = LocalClipboardManager.current

    HorizontalDivider(color = DarkSurface, modifier = Modifier.padding(horizontal = 20.dp))

    Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("外部词典", color = AccentPurple, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = {
                val text = buildString {
                    if (result.explain.isNotBlank()) append(result.explain)
                    if (result.pronu.isNotBlank()) append("\n发音: ${result.pronu}")
                }
                clipboardManager.setText(AnnotatedString(text))
            }) {
                Icon(Icons.Default.ContentCopy, "复制", tint = TextMuted, modifier = Modifier.size(16.dp))
            }
        }

        if (result.word.isNotBlank()) {
            Text(result.word, color = AccentBlue, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
        }
        if (result.explain.isNotBlank()) {
            Text(result.explain, color = TextPrimary, fontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp))
        }
        if (result.pronu.isNotBlank()) {
            Text("发音: ${result.pronu}", color = TextSecondary, fontSize = 14.sp)
        }
        if (result.fyfx.isNotBlank()) {
            Text("翻译: ${result.fyfx}", color = TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
        }
        if (result.thesaurus.isNotBlank()) {
            Text("同义词: ${result.thesaurus}", color = TextMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
        }
        if (result.examp.isNotEmpty()) {
            Text("例句", color = TextMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
            result.examp.take(3).forEach { Text("• $it", color = TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(vertical = 2.dp)) }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
fun SyllableCard(syllable: Syllable) {
    val toneNum = syllable.tone?.tone_number ?: 0
    val color = toneColor(toneNum)
    val consonantClassLabel = when (syllable.consonant_class) {
        "high" -> "高辅音"
        "mid" -> "中辅音"
        "low" -> "低辅音"
        else -> ""
    }
    val finalTypeLabel = when (syllable.final_type) {
        "live" -> "活尾"
        "dead" -> "死尾"
        else -> ""
    }

    Card(colors = CardDefaults.cardColors(containerColor = DarkSurface), shape = RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            // 音节 + IPA + 声调
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(syllable.syllable, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(8.dp))
                if (syllable.ipa.isNotBlank()) { Text(syllable.ipa, color = TextSecondary, fontSize = 14.sp) }
                Spacer(modifier = Modifier.weight(1f))
                if (toneNum > 0) {
                    Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(color.copy(alpha = 0.2f)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                        Text("${toneNum} ${syllable.tone?.tone_cn ?: ""}", color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 辅音信息
            if (syllable.consonant.isNotBlank()) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    DetailChip("辅音", syllable.consonant)
                    if (consonantClassLabel.isNotBlank()) { DetailChip("类别", consonantClassLabel) }
                }
            }

            // 元音信息
            if (syllable.vowel.isNotBlank()) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    DetailChip("元音", syllable.vowel)
                    if (syllable.vowel_length.isNotBlank()) { DetailChip("长短", if (syllable.vowel_length == "long") "长元音" else "短元音") }
                }
            }

            // 声调标记
            if (syllable.tone_mark != null) {
                DetailChip("声调符", syllable.tone_mark)
            }

            // 尾辅音
            if (syllable.final_consonant != null) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    DetailChip("尾辅音", syllable.final_consonant)
                    if (finalTypeLabel.isNotBlank()) { DetailChip("尾音", finalTypeLabel) }
                }
            }

            // 声调规则解释
            if (syllable.tone?.explanation?.isNotBlank() == true) {
                Text(syllable.tone.explanation, color = AccentBlue, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }

            // 发音提示
            if (syllable.pronunciation_tip.isNotBlank()) {
                Text("💡 ${syllable.pronunciation_tip}", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

@Composable
fun DetailChip(label: String, value: String) {
    Row(modifier = Modifier.padding(end = 12.dp, bottom = 2.dp)) {
        Text("$label: ", color = TextMuted, fontSize = 12.sp)
        Text(value, color = TextPrimary, fontSize = 12.sp)
    }
}
