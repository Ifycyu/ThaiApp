package com.thai2chinese.audio

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import java.io.File

object AudioExtractor {
    fun extractAudio(context: Context, videoUri: String): File {
        cleanupTempFiles(context)

        // 复制视频到临时文件
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

        // 找到音频轨道
        var audioTrackIndex = -1
        var audioFormat: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) {
                audioTrackIndex = i
                audioFormat = format
                break
            }
        }
        if (audioTrackIndex < 0 || audioFormat == null) {
            extractor.release()
            tempVideo.delete()
            throw Exception("No audio track found in video")
        }

        // 用 MediaMuxer 提取音频为 M4A
        val audioFile = File(context.cacheDir, "audio_${System.currentTimeMillis()}.m4a")
        val muxer = MediaMuxer(audioFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val muxerTrack = muxer.addTrack(audioFormat)
        muxer.start()

        extractor.selectTrack(audioTrackIndex)
        val buffer = java.nio.ByteBuffer.allocate(1024 * 1024) // 1MB buffer
        val bufferInfo = android.media.MediaCodec.BufferInfo()

        while (true) {
            val sampleSize = extractor.readSampleData(buffer, 0)
            if (sampleSize < 0) break

            bufferInfo.offset = 0
            bufferInfo.size = sampleSize
            bufferInfo.presentationTimeUs = extractor.sampleTime
            bufferInfo.flags = extractor.sampleFlags

            muxer.writeSampleData(muxerTrack, buffer, bufferInfo)
            extractor.advance()
        }

        muxer.stop()
        muxer.release()
        extractor.release()
        tempVideo.delete()

        // 检查文件大小
        val fileSize = audioFile.length()
        val maxSize = 20L * 1024 * 1024 // 20MB
        if (fileSize > maxSize) {
            audioFile.delete()
            throw Exception("音频太大（${fileSize / 1024 / 1024}MB），请使用更短的视频")
        }

        return audioFile
    }

    fun extractAudioRange(context: Context, videoUri: String, startSec: Double, endSec: Double): File {
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
        try { extractor.setDataSource(tempVideo.absolutePath) } catch (e: Exception) {
            tempVideo.delete(); throw Exception("Failed to read video format: ${e.message}")
        }

        var audioTrackIndex = -1; var audioFormat: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) { audioTrackIndex = i; audioFormat = format; break }
        }
        if (audioTrackIndex < 0 || audioFormat == null) {
            extractor.release(); tempVideo.delete(); throw Exception("No audio track found")
        }

        val audioFile = File(context.cacheDir, "range_${System.currentTimeMillis()}.m4a")
        val muxer = MediaMuxer(audioFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val muxerTrack = muxer.addTrack(audioFormat)
        muxer.start()

        extractor.selectTrack(audioTrackIndex)
        val startUs = (startSec * 1_000_000).toLong()
        val endUs = (endSec * 1_000_000).toLong()
        extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

        val buffer = java.nio.ByteBuffer.allocate(1024 * 1024)
        val bufferInfo = android.media.MediaCodec.BufferInfo()

        while (true) {
            val sampleSize = extractor.readSampleData(buffer, 0)
            if (sampleSize < 0) break
            val timeUs = extractor.sampleTime
            if (timeUs > endUs) break

            bufferInfo.offset = 0
            bufferInfo.size = sampleSize
            bufferInfo.presentationTimeUs = timeUs - startUs // 归零时间戳
            bufferInfo.flags = extractor.sampleFlags
            muxer.writeSampleData(muxerTrack, buffer, bufferInfo)
            extractor.advance()
        }

        muxer.stop(); muxer.release(); extractor.release(); tempVideo.delete()
        return audioFile
    }

    private fun cleanupTempFiles(context: Context) {
        try {
            val cacheDir = context.cacheDir
            val now = System.currentTimeMillis()
            cacheDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("temp_video_") && now - file.lastModified() > 3600_000) {
                    file.delete()
                }
                if (file.name.startsWith("audio_") && file.extension == "m4a" && now - file.lastModified() > 3600_000) {
                    file.delete()
                }
                if (file.name.startsWith("pick_") && now - file.lastModified() > 3600_000) {
                    file.delete()
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
    }
}
