package com.thai2chinese.ui.processing

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.thai2chinese.ProcessingService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ProcessingViewModel(application: Application) : AndroidViewModel(application) {
    private val _step = MutableStateFlow(0)
    val step: StateFlow<Int> = _step
    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress
    private val _statusText = MutableStateFlow("")
    val statusText: StateFlow<String> = _statusText
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    private val _taskId = MutableStateFlow<String?>(null)
    val taskId: StateFlow<String?> = _taskId

    private val listener = {
        _statusText.value = ProcessingService.progressText
        _progress.value = ProcessingService.progressPercent
        _error.value = ProcessingService.error
        _taskId.value = ProcessingService.resultTaskId
        _step.value = when {
            ProcessingService.progressPercent < 0.25f -> 0
            ProcessingService.progressPercent < 0.5f -> 1
            ProcessingService.progressPercent < 1f -> 2
            else -> 3
        }
        Unit
    }

    init {
        ProcessingService.addListener(listener)
    }

    fun checkStatus() {
        if (!ProcessingService.isRunning && ProcessingService.resultTaskId == null && ProcessingService.error == null) {
            _statusText.value = "等待启动..."
        }
    }

    override fun onCleared() {
        super.onCleared()
        ProcessingService.removeListener(listener)
    }
}
