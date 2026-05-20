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
import com.thai2chinese.api.toSentences
import com.thai2chinese.api.toSyllable
import com.thai2chinese.api.toWords
import com.thai2chinese.api.ThaiWordHeaders
import com.thai2chinese.audio.AudioExtractor
import com.thai2chinese.data.AppConfig
import com.thai2chinese.data.Sentence
import com.thai2chinese.data.TaskInfo
import com.thai2chinese.data.TaskStore
import com.thai2chinese.data.WordDetail
import com.thai2chinese.util.retry
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

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val store = TaskStore.getInstance(application)
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

    // 显示模式: 0=罗马音, 1=IPA
    private val _displayMode = MutableStateFlow(0)
    val displayMode: StateFlow<Int> = _displayMode
    fun toggleDisplayMode() { _displayMode.value = if (_displayMode.value == 0) 1 else 0 }

    // 跟读功能
    private val _shadowingSentence = MutableStateFlow<Sentence?>(null)
    val shadowingSentence: StateFlow<Sentence?> = _shadowingSentence
    private val _isLooping = MutableStateFlow(true)
    val isLooping: StateFlow<Boolean> = _isLooping
    private val _recordingFile = MutableStateFlow<java.io.File?>(null)
    val recordingFile: StateFlow<java.io.File?> = _recordingFile
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording
    private val _isPlayingRecording = MutableStateFlow(false)
    val isPlayingRecording: StateFlow<Boolean> = _isPlayingRecording
    private var loopJob: Job? = null
    private val recordingHelper = com.thai2chinese.audio.RecordingHelper(application)
    private var recordingPlayer: android.media.MediaPlayer? = null

    private suspend fun doEnrichSentence(sentence: Sentence, twUrl: String, headers: ThaiWordHeaders): Sentence {
        val analyzed = retry(2) {
            val r = ThaiWordApi.analyze(sentence.text, twUrl, headers)
            if (r.words.isNotEmpty()) sentence.copy(words = r.words.toWords(sentence.start, sentence.end)) else sentence
        } ?: sentence
        val translation = if (analyzed.translation.isBlank()) {
            retry(2) { ThaiWordApi.translate(sentence.text, twUrl, headers).translated } ?: ""
        } else analyzed.translation
        return if (translation.isNotBlank()) analyzed.copy(translation = translation) else analyzed
    }

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
        // 清理孤立的录音文件（超过1小时的）
        try {
            val now = System.currentTimeMillis()
            context.cacheDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("shadowing_") && now - file.lastModified() > 3600_000) {
                    file.delete()
                }
            }
        } catch (_: Exception) {}
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
                val enriched = withContext(Dispatchers.IO) { doEnrichSentence(sentence, twUrl, headers) }
                val updatedSentences = currentTask.sentences.toMutableList()
                updatedSentences[index] = enriched
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

    // 跟读功能
    fun startShadowing(sentence: Sentence) {
        player.pause()
        _shadowingSentence.value = sentence
        _isLooping.value = true
        startLoop(sentence)
    }

    fun stopShadowing() {
        loopJob?.cancel()
        loopJob = null
        _isLooping.value = false
        _shadowingSentence.value = null
        _shadowingWordIndex.value = -1
        stopRecording()
        stopRecordingPlayback()
        cleanupRecordingFile()
    }

    fun toggleLoop() {
        _isLooping.value = !_isLooping.value
        if (_isLooping.value) {
            _shadowingSentence.value?.let { startLoop(it) }
        } else {
            loopJob?.cancel()
        }
    }

    fun playSentenceOnce() {
        val sentence = _shadowingSentence.value ?: return
        loopJob?.cancel()
        _isLooping.value = false
        val startMs = (sentence.start * 1000).toLong()
        val endMs = (sentence.end * 1000).toLong()
        player.seekTo(startMs)
        player.play()
        viewModelScope.launch {
            delay(100) // 等播放器启动
            while (player.currentPosition < endMs && _shadowingSentence.value != null) {
                updateShadowingWordIndex(sentence, player.currentPosition / 1000.0)
                delay(50)
            }
            player.pause()
            _shadowingWordIndex.value = -1
        }
    }

    private fun startLoop(sentence: Sentence) {
        loopJob?.cancel()
        loopJob = viewModelScope.launch {
            while (_isLooping.value && _shadowingSentence.value != null) {
                val startMs = (sentence.start * 1000).toLong()
                val endMs = (sentence.end * 1000).toLong()
                player.seekTo(startMs)
                player.play()
                delay(100) // 等播放器启动
                while (player.currentPosition < endMs && _isLooping.value && _shadowingSentence.value != null) {
                    updateShadowingWordIndex(sentence, player.currentPosition / 1000.0)
                    delay(50)
                }
                player.pause()
                _shadowingWordIndex.value = -1
                if (!_isLooping.value) break
                delay(300) // 句子间停顿
            }
        }
    }

    private val _shadowingWordIndex = MutableStateFlow(-1)
    val shadowingWordIndex: StateFlow<Int> = _shadowingWordIndex

    private fun updateShadowingWordIndex(sentence: Sentence, posSec: Double) {
        val words = sentence.words
        var found = -1
        for (i in words.indices) {
            if (posSec >= words[i].start && posSec < words[i].end) { found = i; break }
        }
        _shadowingWordIndex.value = found
    }

    fun startRecording() {
        val file = java.io.File(context.cacheDir, "shadowing_${System.currentTimeMillis()}.m4a")
        _recordingFile.value = file
        recordingHelper.startRecording(file)
        _isRecording.value = true
    }

    fun stopRecording() {
        recordingHelper.stopRecording()
        _isRecording.value = false
    }

    fun playRecording() {
        val file = _recordingFile.value ?: return
        if (!file.exists()) return
        stopRecordingPlayback()
        recordingPlayer = android.media.MediaPlayer().apply {
            setDataSource(file.absolutePath)
            prepare()
            start()
            setOnCompletionListener { _isPlayingRecording.value = false }
        }
        _isPlayingRecording.value = true
    }

    fun stopRecordingPlayback() {
        recordingPlayer?.release()
        recordingPlayer = null
        _isPlayingRecording.value = false
    }

    private fun cleanupRecordingFile() {
        _recordingFile.value?.delete()
        _recordingFile.value = null
    }

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

    fun reAnalyzeSentence() {
        val idx = menuSentenceIndex; val sentence = _menuSentence.value ?: return
        if (idx < 0) return
        _menuSentence.value = null
        enrichingSentences.remove(idx)

        viewModelScope.launch {
            try {
                val enriched = withContext(Dispatchers.IO) { doEnrichSentence(sentence, config.thaiwordUrl, twHeaders()) }
                val currentTask = _task.value ?: return@launch
                val updated = currentTask.sentences.toMutableList()
                updated[idx] = enriched
                val newTask = currentTask.copy(sentences = updated)
                _task.value = newTask; store.put(newTask)
            } catch (e: Exception) { Log.w("PlayerVM", "reAnalyze failed", e) }
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
                val json = JsonParser.parseString(raw).asJsonObject
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
                            val enriched = doEnrichSentence(current, twUrl, headers)
                            val task = _task.value ?: return@async
                            val updated = task.sentences.toMutableList()
                            updated[idx] = enriched
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
                val newSentences = whisperResult.toSentences(startSec)

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
                        try { doEnrichSentence(s, twUrl, headers) } finally { semaphore.release() }
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

    override fun onCleared() {
        super.onCleared()
        loopJob?.cancel()
        recordingHelper.cancel()
        recordingPlayer?.release()
        cleanupRecordingFile()
        syncJob?.cancel()
        player.release()
    }
}
