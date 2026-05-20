package com.thai2chinese.api

import com.thai2chinese.data.Sentence
import com.thai2chinese.data.Word

data class WhisperResponse(val segments: List<WhisperSegment> = emptyList())
data class WhisperSegment(val text: String = "", val start: Double = 0.0, val end: Double = 0.0, val words: List<WhisperWord> = emptyList(), val no_speech_prob: Double = 0.0)
data class WhisperWord(val word: String = "", val start: Double = 0.0, val end: Double = 0.0)

/** 将 Whisper 结果转为 Sentence 列表，可选时间偏移 */
fun WhisperResponse.toSentences(offset: Double = 0.0): List<Sentence> {
    return segments.map { seg ->
        val words = if (seg.words.isNotEmpty()) {
            seg.words.map { Word(text = it.word.trim(), start = it.start + offset, end = it.end + offset) }
        } else {
            val dur = seg.end - seg.start
            val tokens = seg.text.trim().split("\\s+".toRegex())
            val wordDur = if (tokens.isNotEmpty()) dur / tokens.size else dur
            tokens.mapIndexed { i, t -> Word(text = t, start = seg.start + offset + i * wordDur, end = seg.start + offset + (i + 1) * wordDur) }
        }
        Sentence(text = seg.text.trim(), start = seg.start + offset, end = seg.end + offset, words = words)
    }
}

data class AnalyzeResponse(val sentence: String = "", val words: List<AnalyzeWord> = emptyList())
data class AnalyzeWord(val word: String = "", val ipa: String = "", val romanize: String = "", val word_class: String = "", val chinese: String = "", val syllables: List<AnalyzeSyllable> = emptyList())
data class AnalyzeSyllable(
    val syllable: String = "",
    val text: String = "",
    val ipa: String = "",
    val consonant: String = "",
    val consonant_class: String = "",
    val vowel: String = "",
    val vowel_length: String = "",
    val tone_mark: String? = null,
    val final_consonant: String? = null,
    val final_type: String = "",
    val tone: AnalyzeTone? = null,
    val explanation: String = "",
    val pronunciation_tip: String = ""
)
data class AnalyzeTone(val tone: String = "", val tone_cn: String = "", val tone_number: Int = 0, val explanation: String = "")

data class DictResponse(val word: String = "", val ipa: String = "", val chinese: String = "", val word_class: String = "", val syllables: List<AnalyzeSyllable> = emptyList(), val examples: List<String> = emptyList(), val compounds: List<String> = emptyList())
data class TranslateResponse(val original: String = "", val translated: String = "")
