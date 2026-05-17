package com.thai2chinese.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.thai2chinese.data.TaskInfo
import com.thai2chinese.data.TaskStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val store = TaskStore(application)
    private val _tasks = MutableStateFlow<List<TaskInfo>>(emptyList())
    val tasks: StateFlow<List<TaskInfo>> = _tasks
    init { refresh() }
    fun refresh() { _tasks.value = store.getAll() }
    fun deleteTask(id: String) { store.delete(id); refresh() }
}
