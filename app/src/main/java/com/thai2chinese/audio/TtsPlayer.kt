package com.thai2chinese.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import com.thai2chinese.api.ThaiWordApi
import com.thai2chinese.util.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

object TtsPlayer {
    private var mediaPlayer: MediaPlayer? = null
    @Volatile private var playGeneration = 0
    private val client = HttpClient.instance

    private fun getCacheFile(context: Context, word: String): File {
        val dir = File(context.cacheDir, "tts_cache"); dir.mkdirs()
        val md5 = MessageDigest.getInstance("MD5").digest(word.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(dir, "$md5.mp3")
    }

    suspend fun play(context: Context, word: String, thaiwordUrl: String) {
        try {
            withContext(Dispatchers.IO) {
                val cacheFile = getCacheFile(context, word)
                if (!cacheFile.exists()) {
                    val url = ThaiWordApi.ttsUrl(word, thaiwordUrl)
                    val request = okhttp3.Request.Builder().url(url).build()
                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        response.body?.bytes()?.let { cacheFile.writeBytes(it) }
                    } else {
                        return@withContext
                    }
                }
                val gen = ++playGeneration
                withContext(Dispatchers.Main) {
                    stopInternal()
                    if (cacheFile.exists()) {
                        try {
                            mediaPlayer = MediaPlayer().apply {
                                setAudioAttributes(
                                    AudioAttributes.Builder()
                                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                        .setUsage(AudioAttributes.USAGE_MEDIA).build()
                                )
                                setDataSource(cacheFile.absolutePath)
                                setOnPreparedListener {
                                    if (gen == playGeneration) it.start() else it.release()
                                }
                                setOnCompletionListener {
                                    if (gen == playGeneration) { it.release(); mediaPlayer = null }
                                    else it.release()
                                }
                                prepareAsync()
                            }
                        } catch (e: Exception) { Log.w("TtsPlayer", "play failed", e) }
                    }
                }
            }
        } catch (e: Exception) { Log.w("TtsPlayer", "play error", e) }
    }

    private fun stopInternal() {
        try {
            mediaPlayer?.let { mp ->
                try { if (mp.isPlaying) mp.stop() } catch (_: Exception) {}
                mp.release()
            }
        } catch (e: Exception) { Log.w("TtsPlayer", "stop error", e) }
        mediaPlayer = null
    }

    fun stop() {
        playGeneration++
        stopInternal()
    }

    fun release() {
        stop()
    }
}
