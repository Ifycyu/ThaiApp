package com.thai2chinese.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class RecordingHelper(private val context: Context) {
    private var recorder: MediaRecorder? = null
    var isRecording: Boolean = false; private set

    fun startRecording(outputFile: File) {
        outputFile.parentFile?.mkdirs()
        recorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(44100)
            setAudioEncodingBitRate(128000)
            setOutputFile(outputFile.absolutePath)
            prepare()
            start()
        }
        isRecording = true
    }

    fun stopRecording() {
        try {
            recorder?.stop()
        } catch (_: Exception) {}
        try {
            recorder?.release()
        } catch (_: Exception) {}
        recorder = null
        isRecording = false
    }

    fun cancel() {
        try { recorder?.release() } catch (_: Exception) {}
        recorder = null
        isRecording = false
    }
}
