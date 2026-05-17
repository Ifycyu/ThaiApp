package com.thai2chinese.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object AudioExtractor {
    fun extractToWav(context: Context, videoUri: String): File {
        val extractor = MediaExtractor()
        if (videoUri.startsWith("content://")) {
            extractor.setDataSource(context, android.net.Uri.parse(videoUri), null)
        } else {
            extractor.setDataSource(videoUri)
        }

        var audioTrackIndex = -1
        var audioFormat: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) { audioTrackIndex = i; audioFormat = format; break }
        }
        if (audioTrackIndex < 0 || audioFormat == null) { extractor.release(); throw Exception("No audio track") }

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

        val wavFile = File(context.cacheDir, "audio_${System.currentTimeMillis()}.wav")
        writeWav(wavFile, pcmData.toByteArray(), sampleRate, channelCount)
        return wavFile
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
