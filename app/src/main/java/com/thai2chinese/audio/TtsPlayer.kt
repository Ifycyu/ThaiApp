package com.thai2chinese.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.thai2chinese.api.ThaiWordApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

object TtsPlayer {
    private var mediaPlayer: MediaPlayer? = null

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
                    val client = okhttp3.OkHttpClient()
                    val request = okhttp3.Request.Builder().url(url).build()
                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        response.body?.bytes()?.let { cacheFile.writeBytes(it) }
                    } else {
                        return@withContext
                    }
                }
                withContext(Dispatchers.Main) {
                    stop()
                    if (cacheFile.exists()) {
                        try {
                            mediaPlayer = MediaPlayer().apply {
                                setAudioAttributes(
                                    AudioAttributes.Builder()
                                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                        .setUsage(AudioAttributes.USAGE_MEDIA).build()
                                )
                                setDataSource(cacheFile.absolutePath)
                                prepare()
                                start()
                                setOnCompletionListener { release() }
                            }
                        } catch (_: Exception) {}
                    }
                }
            }
        } catch (_: Exception) {}
    }

    fun stop() {
        try {
            mediaPlayer?.let { if (it.isPlaying) it.stop(); it.release() }
        } catch (_: Exception) {}
        mediaPlayer = null
    }
}
