package com.thai2chinese.ui.player

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.google.gson.JsonParser
import com.thai2chinese.api.DictApiResult
import com.thai2chinese.api.ThaiWordApi
import com.thai2chinese.api.WhisperApi
import com.thai2chinese.api.toSyllable
import com.thai2chinese.api.toWords
import com.thai2chinese.api.ThaiWordHeaders
import com.thai2chinese.audio.AudioExtractor
import com.thai2chinese.data.Word
import com.thai2chinese.data.AppConfig
import com.thai2chinese.data.Sentence
import com.thai2chinese.data.TaskInfo
import com.thai2chinese.data.TaskStore
import com.thai2chinese.data.WordDetail
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext

private suspend fun <T> retry(times: Int, block: suspend () -> T): T? {
    repeat(times) { try { return block() } catch (_: Exception) { delay(1000L * (it + 1)) } }
    return try { block() } catch (_: Exception) { null }
}

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

    private val _currentPosition = MutableStateFlow(0f)
    val currentPosition: StateFlow<Float> = _currentPosition
    private val _duration = MutableStateFlow(0f)
    val duration: StateFlow<Float> = _duration

    private var syncJob: Job? = null
    private val enrichingSentences = ConcurrentHashMap.newKeySet<Int>()

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
                _currentPosition.value = player.currentPosition.toFloat()
                _duration.value = if (player.duration > 0) player.duration.toFloat() else 0f
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
                delay(if (player.isPlaying) 80 else 500)
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
                    result.words.toWords(sentence.start, sentence.end)
                } else sentence.words

                val translation = if (sentence.translation.isBlank()) {
                    try { withContext(Dispatchers.IO) { ThaiWordApi.translate(sentence.text, twUrl, headers) }.translated } catch (_: Exception) { "" }
                } else sentence.translation

                val updatedSentences = currentTask.sentences.toMutableList()
                updatedSentences[index] = sentence.copy(words = enrichedWords, translation = translation)
                val updatedTask = currentTask.copy(sentences = updatedSentences)
                _task.value = updatedTask; store.put(updatedTask)
            } catch (e: Exception) { Log.w("PlayerVM", "enrichSentence failed", e) }
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
                    syllables = twResult.syllables.map { it.toSyllable() },
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
    fun seekToMs(ms: Float) { player.seekTo(ms.toLong()) }
    fun togglePlayPause() { if (player.isPlaying) player.pause() else player.play() }

    // 长按句子菜单
    private val _menuSentence = MutableStateFlow<Sentence?>(null)
    val menuSentence: StateFlow<Sentence?> = _menuSentence
    private var menuSentenceIndex = -1
    private var editTargetIndex = -1
    private var editTargetSentence: Sentence? = null

    fun showSentenceMenu(index: Int, sentence: Sentence) {
        menuSentenceIndex = index; _menuSentence.value = sentence
    }
    fun dismissSentenceMenu() { _menuSentence.value = null }

    fun prepareEdit(sentence: Sentence) {
        editTargetIndex = menuSentenceIndex
        editTargetSentence = sentence
    }

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
            } catch (e: Exception) { Log.w("PlayerVM", "retranslate failed", e) }
        }
    }

    // 句子分析
    private val _learnResult = MutableStateFlow<String?>(null)
    val learnResult: StateFlow<String?> = _learnResult
    private val _isLearning = MutableStateFlow(false)
    val isLearning: StateFlow<Boolean> = _isLearning

    fun learnSentence() {
        val sentence = _menuSentence.value ?: return
        _menuSentence.value = null
        _isLearning.value = true; _learnResult.value = null
        val twUrl = config.thaiwordUrl; val headers = twHeaders()

        viewModelScope.launch {
            try {
                val raw = withContext(Dispatchers.IO) { ThaiWordApi.learn(sentence.text, twUrl, headers) }
                // 解析 JSON 提取 explanation 字段
                val json = com.google.gson.JsonParser.parseString(raw).asJsonObject
                _learnResult.value = json.get("explanation")?.asString ?: raw
            } catch (e: Exception) {
                _learnResult.value = "分析失败: ${e.message}"
            } finally {
                _isLearning.value = false
            }
        }
    }

    fun dismissLearn() { _learnResult.value = null }

    // 一键分析所有未分析的句子
    private val _batchProgress = MutableStateFlow<String?>(null)
    val batchProgress: StateFlow<String?> = _batchProgress
    private var batchJob: Job? = null

    fun enrichAllPending() {
        if (batchJob != null) return
        val currentTask = _task.value ?: return
        val pending = currentTask.sentences.mapIndexedNotNull { idx, s ->
            if (s.translation.isBlank() || (s.words.isNotEmpty() && s.words.first().ipa.isBlank())) idx else null
        }
        if (pending.isEmpty()) return

        batchJob = viewModelScope.launch {
            _batchProgress.value = "0/${pending.size}"
            val semaphore = Semaphore(3)
            var done = 0
            pending.chunked(5).forEach { chunk ->
                chunk.map { idx ->
                    async(Dispatchers.IO) {
                        semaphore.acquire()
                        try {
                            val twUrl = config.thaiwordUrl; val headers = twHeaders()
                            val current = _task.value?.sentences?.get(idx) ?: return@async
                            val result = ThaiWordApi.analyze(current.text, twUrl, headers)
                            val enrichedWords = if (result.words.isNotEmpty()) {
                                result.words.toWords(current.start, current.end)
                            } else current.words
                            val translation = if (current.translation.isBlank()) {
                                try { ThaiWordApi.translate(current.text, twUrl, headers).translated } catch (_: Exception) { "" }
                            } else current.translation
                            val task = _task.value ?: return@async
                            val updated = task.sentences.toMutableList()
                            updated[idx] = current.copy(words = enrichedWords, translation = translation)
                            _task.value = task.copy(sentences = updated); store.putWithoutSave(task.copy(sentences = updated))
                        } catch (e: Exception) { Log.w("PlayerVM", "batchEnrich failed", e) } finally { semaphore.release(); done++; _batchProgress.value = "$done/${pending.size}" }
                    }
                }.forEach { it.await() }
                store.saveNow()
            }
            _batchProgress.value = null; batchJob = null
        }
    }

    fun deleteSentence() {
        val idx = menuSentenceIndex
        if (idx < 0) return
        _menuSentence.value = null
        val currentTask = _task.value ?: return
        val updated = currentTask.sentences.toMutableList()
        updated.removeAt(idx)
        val newTask = currentTask.copy(sentences = updated)
        _task.value = newTask; store.put(newTask)
    }

    fun editSentence(newText: String, newTranslation: String) {
        val idx = editTargetIndex; val sentence = editTargetSentence ?: return
        if (idx < 0 || newText.isBlank()) return
        editTargetIndex = -1; editTargetSentence = null
        val currentTask = _task.value ?: return
        val updated = currentTask.sentences.toMutableList()
        // 如果泰语改了，清空旧的 words 并重新分词
        val textChanged = newText != sentence.text
        updated[idx] = sentence.copy(text = newText, translation = newTranslation, words = if (textChanged) emptyList() else sentence.words)
        val newTask = currentTask.copy(sentences = updated)
        _task.value = newTask; store.put(newTask)

        // 泰语改了，重新分词+翻译
        if (textChanged) {
            enrichingSentences.remove(idx)
            tryEnrichSentence(idx)
        }
    }

    // 重新识别时间范围
    private val _retranscribeProgress = MutableStateFlow<String?>(null)
    val retranscribeProgress: StateFlow<String?> = _retranscribeProgress

    fun retranscribeRange(startSec: Double, endSec: Double) {
        if (_retranscribeProgress.value != null) return
        val currentTask = _task.value ?: return
        val videoUri = currentTask.videoUri
        val twUrl = config.thaiwordUrl; val headers = twHeaders()

        viewModelScope.launch {
            _retranscribeProgress.value = "提取音频..."
            try {
                val audioFile = withContext(Dispatchers.IO) {
                    AudioExtractor.extractAudioRange(context, videoUri, startSec, endSec)
                }

                _retranscribeProgress.value = "Whisper 识别中..."
                val whisperResult = withContext(Dispatchers.IO) {
                    WhisperApi.transcribe(audioFile, config.whisperBaseUrl, config.whisperApiKey)
                }
                audioFile.delete()

                // 将 Whisper 结果转为 Sentence，时间戳加上偏移
                val newSentences = whisperResult.segments.filter { it.no_speech_prob < 0.3 }.map { seg ->
                    val words = if (seg.words.isNotEmpty()) {
                        seg.words.map { Word(text = it.word.trim(), start = it.start + startSec, end = it.end + startSec) }
                    } else {
                        val dur = seg.end - seg.start
                        val tokens = seg.text.trim().split("\\s+".toRegex())
                        val wordDur = if (tokens.isNotEmpty()) dur / tokens.size else dur
                        tokens.mapIndexed { i, t -> Word(text = t, start = startSec + seg.start + i * wordDur, end = startSec + seg.start + (i + 1) * wordDur) }
                    }
                    Sentence(text = seg.text.trim(), start = seg.start + startSec, end = seg.end + startSec, words = words)
                }

                // 先展示未分析的结果
                val taskNow = _task.value ?: return@launch
                if (newSentences.isEmpty()) {
                    _retranscribeProgress.value = "该时间段未识别到内容"; delay(2000); _retranscribeProgress.value = null
                    return@launch
                }
                val baseFiltered = taskNow.sentences.filter { it.end <= startSec || it.start >= endSec }.toMutableList()
                baseFiltered.addAll(newSentences)
                baseFiltered.sortBy { it.start }
                _task.value = taskNow.copy(sentences = baseFiltered)

                // 分析新句子（分词+翻译）
                _retranscribeProgress.value = "分词分析中..."
                val semaphore = Semaphore(3)
                val enriched = newSentences.map { s ->
                    async(Dispatchers.IO) {
                        semaphore.acquire()
                        try {
                            val analyzed = retry(2) {
                                val r = ThaiWordApi.analyze(s.text, twUrl, headers)
                                if (r.words.isNotEmpty()) s.copy(words = r.words.toWords(s.start, s.end)) else s
                            } ?: s
                            val translation = if (analyzed.translation.isBlank()) {
                                retry(2) { ThaiWordApi.translate(s.text, twUrl, headers).translated } ?: ""
                            } else analyzed.translation
                            if (translation.isNotBlank()) analyzed.copy(translation = translation) else analyzed
                        } finally { semaphore.release() }
                    }
                }.map { it.await() }

                // 合并最终结果：保留范围外的句子 + 替换范围内的为 enriched
                if (enriched.isEmpty()) {
                    _retranscribeProgress.value = null; return@launch
                }
                val finalBase = _task.value?.sentences?.filter { it.end <= startSec || it.start >= endSec }?.toMutableList() ?: return@launch
                finalBase.addAll(enriched)
                finalBase.sortBy { it.start }
                val finalTask = taskNow.copy(sentences = finalBase)
                _task.value = finalTask; store.put(finalTask)

                _retranscribeProgress.value = null
            } catch (e: Exception) {
                Log.w("PlayerVM", "retranscribeRange failed", e)
                _retranscribeProgress.value = "失败: ${e.message}"
                delay(3000); _retranscribeProgress.value = null
            }
        }
    }

    override fun onCleared() { super.onCleared(); syncJob?.cancel(); player.release() }
}
