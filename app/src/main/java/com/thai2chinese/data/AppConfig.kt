package com.thai2chinese.data

import android.content.Context

class AppConfig(context: Context) {
    private val prefs = context.getSharedPreferences("app_config", Context.MODE_PRIVATE)

    var whisperApiKey: String
        get() = prefs.getString("whisper_api_key", "") ?: ""
        set(value) = prefs.edit().putString("whisper_api_key", value).apply()

    var whisperBaseUrl: String
        get() = prefs.getString("whisper_base_url", "") ?: ""
        set(value) = prefs.edit().putString("whisper_base_url", value).apply()

    var thaiwordUrl: String
        get() = prefs.getString("thaiword_url", "") ?: ""
        set(value) = prefs.edit().putString("thaiword_url", value).apply()

    var dictApiUrl: String
        get() = prefs.getString("dict_api_url", "") ?: ""
        set(value) = prefs.edit().putString("dict_api_url", value).apply()

    var enableExternalDict: Boolean
        get() = prefs.getBoolean("enable_external_dict", false)
        set(value) = prefs.edit().putBoolean("enable_external_dict", value).apply()

    var translateEndpoint: String
        get() = prefs.getString("translate_endpoint", "") ?: ""
        set(value) = prefs.edit().putString("translate_endpoint", value).apply()

    var translateToken: String
        get() = prefs.getString("translate_token", "") ?: ""
        set(value) = prefs.edit().putString("translate_token", value).apply()

    var translateModel: String
        get() = prefs.getString("translate_model", "") ?: ""
        set(value) = prefs.edit().putString("translate_model", value).apply()

    val isConfigured: Boolean
        get() = whisperApiKey.isNotBlank() && whisperBaseUrl.isNotBlank() && thaiwordUrl.isNotBlank()
                && translateEndpoint.isNotBlank() && translateToken.isNotBlank()

    companion object {
        @Volatile
        private var instance: AppConfig? = null
        fun getInstance(context: Context): AppConfig {
            return instance ?: synchronized(this) {
                instance ?: AppConfig(context.applicationContext).also { instance = it }
            }
        }
    }
}
