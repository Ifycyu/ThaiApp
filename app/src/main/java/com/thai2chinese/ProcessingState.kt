package com.thai2chinese

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object ProcessingState {
    private val _progressText = MutableStateFlow("")
    val progressText: StateFlow<String> = _progressText

    private val _progressPercent = MutableStateFlow(0f)
    val progressPercent: StateFlow<Float> = _progressPercent

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private val _resultTaskId = MutableStateFlow<String?>(null)
    val resultTaskId: StateFlow<String?> = _resultTaskId

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun reset() {
        _progressText.value = ""
        _progressPercent.value = 0f
        _isRunning.value = true
        _resultTaskId.value = null
        _error.value = null
    }

    fun updateProgress(text: String, percent: Float) {
        _progressText.value = text
        _progressPercent.value = percent
    }

    fun complete(taskId: String) {
        _resultTaskId.value = taskId
        _isRunning.value = false
    }

    fun fail(errorMsg: String) {
        _error.value = errorMsg
        _progressText.value = "失败: $errorMsg"
        _isRunning.value = false
    }

    fun stop() {
        _isRunning.value = false
    }
}
