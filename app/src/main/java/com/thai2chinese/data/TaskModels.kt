package com.thai2chinese.data

data class Sentence(
    val text: String,
    val translation: String = "",
    val start: Double,
    val end: Double,
    val words: List<Word> = emptyList()
)

data class Word(
    val text: String,
    val roman: String = "",
    val start: Double,
    val end: Double,
    val ipa: String = "",
    val meaning: String = "",
    val word_class: String = "",
    val syllables: List<Syllable> = emptyList()
)

data class Syllable(
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
    val tone: ToneInfo? = null,
    val explanation: String = "",
    val pronunciation_tip: String = ""
)

data class ToneInfo(
    val tone: String = "",
    val tone_cn: String = "",
    val tone_number: Int = 0,
    val explanation: String = ""
)

data class WordDetail(
    val word: String = "",
    val ipa: String = "",
    val meaning: String = "",
    val word_class: String = "",
    val syllables: List<Syllable> = emptyList(),
    val examples: List<String> = emptyList()
)

data class TaskInfo(
    val id: String,
    val filename: String,
    val status: String = "processing",
    val sentences: List<Sentence> = emptyList(),
    val error: String? = null,
    val videoUri: String = ""
)
