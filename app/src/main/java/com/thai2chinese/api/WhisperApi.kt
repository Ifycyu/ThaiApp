package com.thai2chinese.api

import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

object WhisperApi {
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    fun transcribe(audioFile: File, baseUrl: String, apiKey: String): WhisperResponse {
        if (baseUrl.isBlank() || apiKey.isBlank()) {
            throw Exception("请先在设置中配置 Whisper API 地址和密钥")
        }
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("model", "whisper-1")
            .addFormDataPart("language", "th")
            .addFormDataPart("response_format", "verbose_json")
            .addFormDataPart("timestamp_granularities", "segment,word")
            .addFormDataPart("prompt", "输出较长的完整句子，不要拆分成短句")
            .addFormDataPart("file", audioFile.name, audioFile.asRequestBody("audio/mp4".toMediaType()))
            .build()

        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/audio/transcriptions")
            .header("Authorization", "Bearer $apiKey")
            .post(body).build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: throw Exception("Empty response")
        if (!response.isSuccessful) throw Exception("Whisper error ${response.code}: $responseBody")
        return gson.fromJson(responseBody, WhisperResponse::class.java)
    }
}
