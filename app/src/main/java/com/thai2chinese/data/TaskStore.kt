package com.thai2chinese.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class TaskStore(context: Context) {
    private val prefs = context.getSharedPreferences("tasks", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val type = object : TypeToken<MutableMap<String, TaskInfo>>() {}.type
    private var tasks: MutableMap<String, TaskInfo> = load()

    private fun load(): MutableMap<String, TaskInfo> {
        val json = prefs.getString("data", null) ?: return mutableMapOf()
        return try { gson.fromJson(json, type) } catch (_: Exception) { mutableMapOf() }
    }

    private fun save() { prefs.edit().putString("data", gson.toJson(tasks)).apply() }

    fun getAll(): List<TaskInfo> = tasks.values.sortedByDescending { it.id }
    fun get(id: String): TaskInfo? = tasks[id]
    fun put(task: TaskInfo) { tasks[task.id] = task; save() }
    fun delete(id: String) { tasks.remove(id); save() }
}
