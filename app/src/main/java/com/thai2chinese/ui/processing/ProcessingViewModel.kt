package com.thai2chinese.ui.processing

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.thai2chinese.ProcessingState
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

    init {
        viewModelScope.launch {
            ProcessingState.progressText.collect { _statusText.value = it }
        }
        viewModelScope.launch {
            ProcessingState.progressPercent.collect { pct ->
                _progress.value = pct
                _step.value = when {
                    pct < 0.25f -> 0
                    pct < 0.5f -> 1
                    pct < 1f -> 2
                    else -> 3
                }
            }
        }
        viewModelScope.launch {
            ProcessingState.error.collect { _error.value = it }
        }
        viewModelScope.launch {
            ProcessingState.resultTaskId.collect { _taskId.value = it }
        }
    }

    fun checkStatus() {
        if (!ProcessingState.isRunning.value && ProcessingState.resultTaskId.value == null && ProcessingState.error.value == null) {
            _statusText.value = "等待启动..."
        }
    }
}
