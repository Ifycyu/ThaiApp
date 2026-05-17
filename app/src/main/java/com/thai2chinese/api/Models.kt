package com.thai2chinese.api

data class WhisperResponse(val segments: List<WhisperSegment> = emptyList())
data class WhisperSegment(val text: String = "", val start: Double = 0.0, val end: Double = 0.0, val words: List<WhisperWord> = emptyList())
data class WhisperWord(val word: String = "", val start: Double = 0.0, val end: Double = 0.0)

data class AnalyzeResponse(val sentence: String = "", val words: List<AnalyzeWord> = emptyList())
data class AnalyzeWord(val word: String = "", val ipa: String = "", val word_class: String = "", val chinese: String = "", val syllables: List<AnalyzeSyllable> = emptyList())
data class AnalyzeSyllable(val syllable: String = "", val ipa: String = "", val tone: AnalyzeTone? = null, val explanation: String = "", val pronunciation_tip: String = "")
data class AnalyzeTone(val tone: String = "", val tone_cn: String = "", val tone_number: Int = 0)

data class DictResponse(val word: String = "", val ipa: String = "", val chinese: String = "", val word_class: String = "", val syllables: List<AnalyzeSyllable> = emptyList(), val examples: List<String> = emptyList(), val compounds: List<String> = emptyList())
data class TranslateResponse(val original: String = "", val translated: String = "")
