package com.thai2chinese.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object AudioExtractor {
    fun extractToWav(context: Context, videoUri: String): File {
        // 清理旧的临时文件
        cleanupTempFiles(context)

        // 先复制到临时文件，避免 content:// URI 问题
        val tempVideo = File(context.cacheDir, "temp_video_${System.currentTimeMillis()}.mp4")
        try {
            context.contentResolver.openInputStream(Uri.parse(videoUri))?.use { input ->
                tempVideo.outputStream().use { output -> input.copyTo(output) }
            } ?: throw Exception("Cannot open video: $videoUri")
        } catch (e: Exception) {
            tempVideo.delete()
            throw Exception("Failed to read video: ${e.message}")
        }

        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(tempVideo.absolutePath)
        } catch (e: Exception) {
            tempVideo.delete()
            throw Exception("Failed to read video format: ${e.message}")
        }

        var audioTrackIndex = -1
        var audioFormat: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) { audioTrackIndex = i; audioFormat = format; break }
        }
        if (audioTrackIndex < 0 || audioFormat == null) {
            extractor.release(); tempVideo.delete()
            throw Exception("No audio track found in video")
        }

        extractor.selectTrack(audioTrackIndex)
        val sampleRate = audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channelCount = audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val mime = audioFormat.getString(MediaFormat.KEY_MIME)!!

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(audioFormat, null, null, 0)
        codec.start()

        val pcmData = mutableListOf<Byte>()
        val bufferInfo = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false

        while (!outputDone) {
            if (!inputDone) {
                val inputIndex = codec.dequeueInputBuffer(10_000L)
                if (inputIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputIndex)!!
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000L)
            if (outputIndex >= 0) {
                val outputBuffer = codec.getOutputBuffer(outputIndex)!!
                val chunk = ByteArray(bufferInfo.size)
                outputBuffer.get(chunk)
                pcmData.addAll(chunk.toList())
                codec.releaseOutputBuffer(outputIndex, false)
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
            }
        }
        codec.stop(); codec.release(); extractor.release()
        tempVideo.delete()

        val wavFile = File(context.cacheDir, "audio_${System.currentTimeMillis()}.wav")
        writeWav(wavFile, pcmData.toByteArray(), sampleRate, channelCount)
        return wavFile
    }

    private fun cleanupTempFiles(context: Context) {
        try {
            val cacheDir = context.cacheDir
            val now = System.currentTimeMillis()
            cacheDir.listFiles()?.forEach { file ->
                // 清理超过 1 小时的临时文件
                if (file.name.startsWith("temp_video_") && now - file.lastModified() > 3600_000) {
                    file.delete()
                }
                if (file.name.startsWith("audio_") && file.extension == "wav" && now - file.lastModified() > 3600_000) {
                    file.delete()
                }
            }
        } catch (_: Exception) {}
    }

    private fun writeWav(file: File, pcmData: ByteArray, sampleRate: Int, channels: Int) {
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        FileOutputStream(file).use { fos ->
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray()); header.putInt(36 + pcmData.size)
            header.put("WAVE".toByteArray()); header.put("fmt ".toByteArray())
            header.putInt(16); header.putShort(1); header.putShort(channels.toShort())
            header.putInt(sampleRate); header.putInt(byteRate)
            header.putShort(blockAlign.toShort()); header.putShort(bitsPerSample.toShort())
            header.put("data".toByteArray()); header.putInt(pcmData.size)
            fos.write(header.array()); fos.write(pcmData)
        }
    }
}
