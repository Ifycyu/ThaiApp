package com.thai2chinese

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.thai2chinese.api.ThaiWordApi
import com.thai2chinese.api.ThaiWordHeaders
import com.thai2chinese.api.WhisperApi
import com.thai2chinese.api.toWords
import com.thai2chinese.audio.AudioExtractor
import com.thai2chinese.data.AppConfig
import com.thai2chinese.data.Sentence
import com.thai2chinese.data.TaskInfo
import com.thai2chinese.data.TaskStore
import com.thai2chinese.data.Word
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import java.util.UUID

private suspend fun <T> retry(times: Int, block: suspend () -> T): T? {
    repeat(times) {
        try { return block() } catch (_: Exception) { kotlinx.coroutines.delay(1000L * (it + 1)) }
    }
    return try { block() } catch (_: Exception) { null }
}

class ProcessingService : Service() {
    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private lateinit var config: AppConfig
    private lateinit var store: TaskStore

    companion object {
        const val CHANNEL_ID = "processing_channel"
        const val NOTIFICATION_ID = 1
        const val EXTRA_VIDEO_URI = "video_uri"
        const val EXTRA_FILENAME = "filename"

        var progressText: String = ""; private set
        var progressPercent: Float = 0f; private set
        var isRunning: Boolean = false; private set
        var resultTaskId: String? = null; private set
        var error: String? = null; private set

        private val listeners = CopyOnWriteArrayList<() -> Unit>()
        fun addListener(l: () -> Unit) { listeners.add(l) }
        fun removeListener(l: () -> Unit) { listeners.remove(l) }
        private fun notifyListeners() { listeners.forEach { it() } }
    }

    override fun onCreate() { super.onCreate(); config = AppConfig.getInstance(this); store = TaskStore(this); createNotificationChannel() }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val videoUri = intent?.getStringExtra(EXTRA_VIDEO_URI) ?: return START_NOT_STICKY
        val filename = intent.getStringExtra(EXTRA_FILENAME) ?: "video.mp4"
        startForeground(NOTIFICATION_ID, buildNotification("准备中..."))
        isRunning = true; error = null; resultTaskId = null
        scope.launch { processVideo(videoUri, filename) }
        return START_NOT_STICKY
    }

    private suspend fun processVideo(videoUri: String, filename: String) {
        try {
            val twUrl = config.thaiwordUrl
            val headers = ThaiWordHeaders(config.dictApiUrl, config.translateEndpoint, config.translateToken, config.translateModel)

            updateProgress("提取音频...", 0.1f)
            val wavFile = withContext(Dispatchers.IO) { AudioExtractor.extractToWav(this@ProcessingService, videoUri) }

            updateProgress("Whisper 转写中...", 0.25f)
            val whisperResult = withContext(Dispatchers.IO) { WhisperApi.transcribe(wavFile, config.whisperBaseUrl, config.whisperApiKey) }

            val sentences = whisperResult.segments.map { seg ->
                val words = if (seg.words.isNotEmpty()) seg.words.map { Word(text = it.word.trim(), start = it.start, end = it.end) }
                else {
                    val dur = seg.end - seg.start; val tokens = seg.text.trim().split("\\s+".toRegex())
                    val wordDur = if (tokens.isNotEmpty()) dur / tokens.size else dur
                    tokens.mapIndexed { i, t -> Word(text = t, start = seg.start + i * wordDur, end = seg.start + (i + 1) * wordDur) }
                }
                Sentence(text = seg.text.trim(), start = seg.start, end = seg.end, words = words)
            }

            // 先保存未 enrichment 的版本，让用户可以先看视频
            val taskId = UUID.randomUUID().toString()
            val preliminaryTask = TaskInfo(id = taskId, filename = filename, status = "processing", sentences = sentences, videoUri = videoUri)
            store.put(preliminaryTask)
            resultTaskId = taskId
            notifyListeners()

            // 并发处理所有句子（限制并发数为 3，失败重试 2 次）
            val semaphore = kotlinx.coroutines.sync.Semaphore(3)
            val enrichedSentences = coroutineScope {
                sentences.mapIndexed { idx, sentence ->
                    async(Dispatchers.IO) {
                        semaphore.acquire()
                        try {
                            updateProgress("分词分析 (${idx + 1}/${sentences.size})...", 0.5f + 0.4f * (idx.toFloat() / sentences.size))

                            val enriched = retry(2) {
                                val result = ThaiWordApi.analyze(sentence.text, twUrl, headers)
                                if (result.words.isNotEmpty()) {
                                    sentence.copy(words = result.words.toWords(sentence.start, sentence.end))
                                } else sentence
                            } ?: sentence

                            val translation = if (enriched.translation.isBlank()) {
                                retry(2) { ThaiWordApi.translate(sentence.text, twUrl, headers).translated } ?: ""
                            } else enriched.translation

                            if (translation.isNotBlank()) enriched.copy(translation = translation) else enriched
                        } finally {
                            semaphore.release()
                        }
                    }
                }.awaitAll()
            }

            val task = TaskInfo(id = taskId, filename = filename, status = "completed", sentences = enrichedSentences, videoUri = videoUri)
            store.put(task)
            wavFile.delete()
            updateProgress("完成", 1f)

        } catch (e: Exception) {
            error = e.message ?: "未知错误"; progressText = "失败: ${error}"; notifyListeners()
        } finally {
            isRunning = false; notifyListeners()
            stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
        }
    }

    private fun updateProgress(text: String, percent: Float) {
        progressText = text; progressPercent = percent; notifyListeners()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID).setContentTitle("泰语学习").setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play).setOngoing(true).setProgress(100, (progressPercent * 100).toInt(), false).build()

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "视频处理", NotificationManager.IMPORTANCE_LOW)
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}
