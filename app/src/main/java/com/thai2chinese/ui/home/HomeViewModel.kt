package com.thai2chinese.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.thai2chinese.ProcessingState
import com.thai2chinese.data.TaskInfo
import com.thai2chinese.data.TaskStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val store = TaskStore.getInstance(application)
    private val _tasks = MutableStateFlow<List<TaskInfo>>(emptyList())
    val tasks: StateFlow<List<TaskInfo>> = _tasks
    init { refresh() }
    fun refresh() {
        // 修复孤儿状态：Service 被杀后 status 卡在 processing 的任务
        if (!ProcessingState.isRunning.value) {
            store.getAll().filter { it.status == "processing" }.forEach { task ->
                store.put(task.copy(status = "completed"))
            }
        }
        _tasks.value = store.getAll()
    }
    fun deleteTask(id: String) { store.delete(id); refresh() }
    fun renameTask(id: String, newName: String) {
        store.get(id)?.let { store.put(it.copy(filename = newName)); refresh() }
    }
}
