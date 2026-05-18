package com.thai2chinese.api

import com.thai2chinese.data.Syllable
import com.thai2chinese.data.ToneInfo
import com.thai2chinese.data.Word

fun AnalyzeSyllable.toSyllable(): Syllable = Syllable(
    syllable = syllable,
    text = text,
    ipa = ipa,
    consonant = consonant,
    consonant_class = consonant_class,
    vowel = vowel,
    vowel_length = vowel_length,
    tone_mark = tone_mark,
    final_consonant = final_consonant,
    final_type = final_type,
    tone = tone?.let { ToneInfo(it.tone, it.tone_cn, it.tone_number, it.explanation) },
    explanation = explanation,
    pronunciation_tip = pronunciation_tip
)

fun AnalyzeWord.toWord(start: Double, end: Double): Word = Word(
    text = word,
    roman = ipa,
    start = start,
    end = end,
    ipa = ipa,
    meaning = chinese,
    word_class = word_class,
    syllables = syllables.map { it.toSyllable() }
)

fun List<AnalyzeWord>.toWords(sentenceStart: Double, sentenceEnd: Double): List<Word> {
    if (isEmpty()) return emptyList()
    val duration = sentenceEnd - sentenceStart
    val wordDuration = duration / size
    return mapIndexed { i, aw -> aw.toWord(sentenceStart + i * wordDuration, sentenceStart + (i + 1) * wordDuration) }
}
