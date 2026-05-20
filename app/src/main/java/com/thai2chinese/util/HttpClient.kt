package com.thai2chinese.util

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object HttpClient {
    val instance: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
}
