package com.thai2chinese.ui.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.thai2chinese.api.DictApiResult
import com.thai2chinese.api.ThaiWordApi
import com.thai2chinese.api.ThaiWordHeaders
import com.thai2chinese.data.AppConfig
import com.thai2chinese.data.Sentence
import com.thai2chinese.data.Syllable
import com.thai2chinese.data.TaskInfo
import com.thai2chinese.data.TaskStore
import com.thai2chinese.data.ToneInfo
import com.thai2chinese.data.Word
import com.thai2chinese.data.WordDetail
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val store = TaskStore(application)
    private val config = AppConfig.getInstance(application)
    private val context = application
    val player: ExoPlayer = ExoPlayer.Builder(application).build()

    private val _task = MutableStateFlow<TaskInfo?>(null)
    val task: StateFlow<TaskInfo?> = _task
    private val _activeSentence = MutableStateFlow(-1)
    val activeSentence: StateFlow<Int> = _activeSentence
    private val _activeWord = MutableStateFlow(-1)
    val activeWord: StateFlow<Int> = _activeWord
    private val _selectedWord = MutableStateFlow<WordDetail?>(null)
    val selectedWord: StateFlow<WordDetail?> = _selectedWord
    private val _isLoadingWord = MutableStateFlow(false)
    val isLoadingWord: StateFlow<Boolean> = _isLoadingWord
    private val _selectedDictResult = MutableStateFlow<DictApiResult?>(null)
    val selectedDictResult: StateFlow<DictApiResult?> = _selectedDictResult

    private var syncJob: Job? = null
    private val enrichingSentences = mutableSetOf<Int>()

    private fun twHeaders() = ThaiWordHeaders(
        dictApi = config.dictApiUrl,
        translateEndpoint = config.translateEndpoint,
        translateToken = config.translateToken,
        translateModel = config.translateModel
    )

    fun loadTask(taskId: String) {
        val t = store.get(taskId) ?: return
        _task.value = t
        if (t.videoUri.isNotEmpty()) {
            player.setMediaItem(MediaItem.fromUri(t.videoUri))
            player.prepare()
        }
        startSync()
    }

    private fun startSync() {
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            while (true) {
                val pos = player.currentPosition / 1000.0
                val sentences = _task.value?.sentences ?: emptyList()
                var foundSent = -1; var foundWord = -1
                for (i in sentences.indices) {
                    val s = sentences[i]
                    if (pos >= s.start && pos < s.end) {
                        foundSent = i
                        for (j in s.words.indices) { if (pos >= s.words[j].start && pos < s.words[j].end) { foundWord = j; break } }
                        break
                    }
                }
                _activeSentence.value = foundSent
                _activeWord.value = foundWord

                if (foundSent >= 0) {
                    val sent = sentences[foundSent]
                    if (sent.translation.isBlank() || (sent.words.isNotEmpty() && sent.words.first().ipa.isBlank())) {
                        tryEnrichSentence(foundSent)
                    }
                }
                delay(80)
            }
        }
    }

    private fun tryEnrichSentence(index: Int) {
        if (index in enrichingSentences) return
        enrichingSentences.add(index)
        val twUrl = config.thaiwordUrl; val headers = twHeaders()

        viewModelScope.launch {
            try {
                val currentTask = _task.value ?: return@launch
                val sentence = currentTask.sentences[index]
                val result = withContext(Dispatchers.IO) { ThaiWordApi.analyze(sentence.text, twUrl, headers) }
                val enrichedWords = if (result.words.isNotEmpty()) {
                    val duration = sentence.end - sentence.start
                    val wordDuration = if (result.words.size > 0) duration / result.words.size else duration
                    result.words.mapIndexed { i, aw ->
                        Word(text = aw.word, roman = aw.ipa, start = sentence.start + i * wordDuration, end = sentence.start + (i + 1) * wordDuration,
                            ipa = aw.ipa, meaning = aw.chinese, word_class = aw.word_class,
                            syllables = aw.syllables.map { s -> Syllable(syllable = s.syllable, text = s.text, ipa = s.ipa, consonant = s.consonant, consonant_class = s.consonant_class, vowel = s.vowel, vowel_length = s.vowel_length, tone_mark = s.tone_mark, final_consonant = s.final_consonant, final_type = s.final_type, tone = s.tone?.let { ToneInfo(it.tone, it.tone_cn, it.tone_number, it.explanation) }, explanation = s.explanation, pronunciation_tip = s.pronunciation_tip) })
                    }
                } else sentence.words

                val translation = if (sentence.translation.isBlank()) {
                    try { withContext(Dispatchers.IO) { ThaiWordApi.translate(sentence.text, twUrl, headers) }.translated } catch (_: Exception) { "" }
                } else sentence.translation

                val updatedSentences = currentTask.sentences.toMutableList()
                updatedSentences[index] = sentence.copy(words = enrichedWords, translation = translation)
                val updatedTask = currentTask.copy(sentences = updatedSentences)
                _task.value = updatedTask; store.put(updatedTask)
            } catch (_: Exception) {}
        }
    }

    fun onWordClick(word: String, context: String) {
        player.pause()
        _isLoadingWord.value = true; _selectedWord.value = null; _selectedDictResult.value = null
        val twUrl = config.thaiwordUrl; val headers = twHeaders()
        val useExternalDict = config.enableExternalDict && config.dictApiUrl.isNotBlank()

        viewModelScope.launch {
            // 两个查询并发
            val twDeferred = async(Dispatchers.IO) {
                try { ThaiWordApi.dict(word, twUrl, headers) } catch (_: Exception) { null }
            }
            val extDeferred = if (useExternalDict) {
                async(Dispatchers.IO) { ThaiWordApi.dictApiLookup(word, config.dictApiUrl) }
            } else null

            // ThaiWord 结果
            val twResult = twDeferred.await()
            if (twResult != null) {
                _selectedWord.value = WordDetail(word = twResult.word, ipa = twResult.ipa, meaning = twResult.chinese, word_class = twResult.word_class,
                    syllables = twResult.syllables.map { s -> Syllable(syllable = s.syllable, text = s.text, ipa = s.ipa, consonant = s.consonant, consonant_class = s.consonant_class, vowel = s.vowel, vowel_length = s.vowel_length, tone_mark = s.tone_mark, final_consonant = s.final_consonant, final_type = s.final_type, tone = s.tone?.let { ToneInfo(it.tone, it.tone_cn, it.tone_number, it.explanation) }, explanation = s.explanation, pronunciation_tip = s.pronunciation_tip) },
                    examples = twResult.examples)
            } else {
                _selectedWord.value = WordDetail(word = word, meaning = "查询失败")
            }
            _isLoadingWord.value = false

            // 外部词典结果
            if (extDeferred != null) {
                _selectedDictResult.value = extDeferred.await()
            }
        }
    }

    fun dismissWordCard() { _selectedWord.value = null; _selectedDictResult.value = null }
    fun seekTo(time: Double) { player.seekTo((time * 1000).toLong()) }

    // 长按句子菜单
    private val _menuSentence = MutableStateFlow<Sentence?>(null)
    val menuSentence: StateFlow<Sentence?> = _menuSentence
    private var menuSentenceIndex = -1

    fun showSentenceMenu(index: Int, sentence: Sentence) {
        menuSentenceIndex = index; _menuSentence.value = sentence
    }
    fun dismissSentenceMenu() { _menuSentence.value = null }

    fun retranslateSentence() {
        val idx = menuSentenceIndex; val sentence = _menuSentence.value ?: return
        if (idx < 0) return
        _menuSentence.value = null
        val twUrl = config.thaiwordUrl; val headers = twHeaders()

        viewModelScope.launch {
            try {
                val translated = withContext(Dispatchers.IO) { ThaiWordApi.translate(sentence.text, twUrl, headers).translated }
                val currentTask = _task.value ?: return@launch
                val updated = currentTask.sentences.toMutableList()
                updated[idx] = sentence.copy(translation = translated)
                val newTask = currentTask.copy(sentences = updated)
                _task.value = newTask; store.put(newTask)
            } catch (_: Exception) {}
        }
    }

    fun editSentence(newText: String, newTranslation: String) {
        val idx = menuSentenceIndex; val sentence = _menuSentence.value ?: return
        if (idx < 0) return
        _menuSentence.value = null
        val currentTask = _task.value ?: return
        val updated = currentTask.sentences.toMutableList()
        updated[idx] = sentence.copy(text = newText, translation = newTranslation)
        val newTask = currentTask.copy(sentences = updated)
        _task.value = newTask; store.put(newTask)
    }

    override fun onCleared() { super.onCleared(); syncJob?.cancel(); player.release() }
}
