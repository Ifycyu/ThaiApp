package com.thai2chinese.api

import com.google.gson.Gson
import com.thai2chinese.util.HttpClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.util.concurrent.TimeUnit

data class ThaiWordHeaders(
    val dictApi: String = "",
    val translateEndpoint: String = "",
    val translateToken: String = "",
    val translateModel: String = ""
)

object ThaiWordApi {
    private val gson = Gson()
    private val client = HttpClient.instance

    fun analyze(sentence: String, baseUrl: String, headers: ThaiWordHeaders): AnalyzeResponse {
        val url = "${baseUrl.trimEnd('/')}/api/v1/analyze".toHttpUrl().newBuilder()
            .addQueryParameter("sentence", sentence).build()
        val builder = Request.Builder().url(url).post("".toRequestBody(null))
        if (headers.dictApi.isNotBlank()) builder.header("X-Dict-API", headers.dictApi)
        val response = client.newCall(builder.build()).execute()
        val body = response.body?.string() ?: throw Exception("Empty response")
        if (!response.isSuccessful) throw Exception("Analyze error ${response.code}: $body")
        return gson.fromJson(body, AnalyzeResponse::class.java)
    }

    fun dict(word: String, baseUrl: String, headers: ThaiWordHeaders): DictResponse {
        val builder = Request.Builder().url("${baseUrl.trimEnd('/')}/api/v1/dict/$word").get()
        if (headers.dictApi.isNotBlank()) builder.header("X-Dict-API", headers.dictApi)
        val response = client.newCall(builder.build()).execute()
        val body = response.body?.string() ?: throw Exception("Empty response")
        if (!response.isSuccessful) throw Exception("Dict error ${response.code}: $body")
        return gson.fromJson(body, DictResponse::class.java)
    }

    fun translate(sentence: String, baseUrl: String, headers: ThaiWordHeaders): TranslateResponse {
        val url = "${baseUrl.trimEnd('/')}/api/v1/translate".toHttpUrl().newBuilder()
            .addQueryParameter("sentence", sentence).build()
        val builder = Request.Builder().url(url).post("".toRequestBody(null))
        if (headers.translateEndpoint.isNotBlank()) builder.header("X-Translate-Endpoint", headers.translateEndpoint)
        if (headers.translateToken.isNotBlank()) builder.header("X-Translate-Token", headers.translateToken)
        if (headers.translateModel.isNotBlank()) builder.header("X-Translate-Model", headers.translateModel)
        val response = client.newCall(builder.build()).execute()
        val body = response.body?.string() ?: throw Exception("Empty response")
        if (!response.isSuccessful) throw Exception("Translate error ${response.code}: $body")
        return gson.fromJson(body, TranslateResponse::class.java)
    }

    fun ttsUrl(word: String, baseUrl: String): String = "${baseUrl.trimEnd('/')}/api/tts/$word"

    fun learn(sentence: String, baseUrl: String, headers: ThaiWordHeaders): String {
        val jsonBody = gson.toJson(mapOf("sentence" to sentence))
        val builder = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/api/learn")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
        if (headers.translateEndpoint.isNotBlank()) builder.header("X-Translate-Endpoint", headers.translateEndpoint)
        if (headers.translateToken.isNotBlank()) builder.header("X-Translate-Token", headers.translateToken)
        if (headers.translateModel.isNotBlank()) builder.header("X-Translate-Model", headers.translateModel)
        val response = client.newCall(builder.build()).execute()
        val body = response.body?.string() ?: throw Exception("Empty response")
        if (!response.isSuccessful) throw Exception("Learn error ${response.code}: $body")
        return body
    }

    fun learnCheckTask(taskId: String, baseUrl: String): String {
        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/api/learn/$taskId")
            .get()
            .build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: throw Exception("Empty response")
        if (!response.isSuccessful) throw Exception("Task check error ${response.code}: $body")
        return body
    }

    fun dictApiLookup(word: String, dictApiUrl: String): List<DictApiResult>? {
        if (dictApiUrl.isBlank()) return null
        try {
            val body = okhttp3.FormBody.Builder().add("str", word).build()
            val request = Request.Builder().url(dictApiUrl).post(body).build()
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: return null
            if (!response.isSuccessful) return null
            val json = gson.fromJson(responseBody, Map::class.java) as? Map<*, *> ?: return null
            val entry = json["1"] as? Map<*, *> ?: return null
            val list = entry["list"] as? List<*> ?: return null
            if (list.isEmpty()) return null
            return list.mapNotNull { raw ->
                val item = raw as? Map<*, *> ?: return@mapNotNull null
                DictApiResult(
                    word = item["word"]?.toString() ?: "",
                    explain = item["explain"]?.toString() ?: "",
                    pronu = item["pronu"]?.toString() ?: "",
                    fyfx = item["fyfx"]?.toString() ?: "",
                    thesaurus = item["thesaurus"]?.toString() ?: "",
                    examp = (item["examp"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                )
            }.ifEmpty { null }
        } catch (e: Exception) { e.printStackTrace(); return null }
    }
}

data class DictApiResult(
    val word: String = "",
    val explain: String = "",
    val pronu: String = "",
    val fyfx: String = "",
    val thesaurus: String = "",
    val examp: List<String> = emptyList()
)

data class LearnResult(
    val translation: String = "",
    val vocabulary: List<VocabItem> = emptyList(),
    val grammar: List<String> = emptyList(),
    val notes: List<String> = emptyList()
)

data class VocabItem(
    val word: String = "",
    val meaning: String = "",
    val usage: String = ""
)
