package com.thai2chinese

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.thai2chinese.api.ThaiWordApi
import com.thai2chinese.api.ThaiWordHeaders
import com.thai2chinese.api.WhisperApi
import com.thai2chinese.api.toSentences
import com.thai2chinese.api.toWords
import com.thai2chinese.audio.AudioExtractor
import com.thai2chinese.data.AppConfig
import com.thai2chinese.data.TaskInfo
import com.thai2chinese.data.TaskStore
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import java.util.UUID
import com.thai2chinese.util.retry

class ProcessingService : Service() {
    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private lateinit var config: AppConfig
    private lateinit var store: TaskStore

    companion object {
        const val CHANNEL_ID = "processing_channel"
        const val NOTIFICATION_ID = 1
        const val EXTRA_VIDEO_URI = "video_uri"
        const val EXTRA_FILENAME = "filename"
    }

    override fun onCreate() { super.onCreate(); config = AppConfig.getInstance(this); store = TaskStore.getInstance(this); createNotificationChannel() }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val videoUri = intent?.getStringExtra(EXTRA_VIDEO_URI) ?: return START_NOT_STICKY
        val filename = intent.getStringExtra(EXTRA_FILENAME) ?: "video.mp4"
        startForeground(NOTIFICATION_ID, buildNotification("准备中..."))
        ProcessingState.reset()
        scope.launch { processVideo(videoUri, filename) }
        return START_NOT_STICKY
    }

    private suspend fun processVideo(videoUri: String, filename: String) {
        try {
            val twUrl = config.thaiwordUrl
            val headers = ThaiWordHeaders(config.dictApiUrl, config.translateEndpoint, config.translateToken, config.translateModel)

            updateProgress("提取音频...", 0.1f)
            val audioFile = withContext(Dispatchers.IO) { AudioExtractor.extractAudio(this@ProcessingService, videoUri) }

            updateProgress("Whisper 转写中...", 0.25f)
            val whisperResult = withContext(Dispatchers.IO) { WhisperApi.transcribe(audioFile, config.whisperBaseUrl, config.whisperApiKey) }

            val sentences = whisperResult.toSentences()

            val taskId = UUID.randomUUID().toString()
            val preliminaryTask = TaskInfo(id = taskId, filename = filename, status = "processing", sentences = sentences, videoUri = videoUri)
            store.put(preliminaryTask)
            ProcessingState.complete(taskId)

            val semaphore = Semaphore(3)
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
            audioFile.delete()
            updateProgress("完成", 1f)

        } catch (e: Exception) {
            ProcessingState.fail(e.message ?: "未知错误")
        } finally {
            ProcessingState.stop()
            stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
        }
    }

    private fun updateProgress(text: String, percent: Float) {
        ProcessingState.updateProgress(text, percent)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID).setContentTitle("泰语学习").setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play).setOngoing(true).setProgress(100, (ProcessingState.progressPercent.value * 100).toInt(), false).build()

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "视频处理", NotificationManager.IMPORTANCE_LOW)
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}
