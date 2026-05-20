package com.thai2chinese.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class TaskStore private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("tasks", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val type = object : TypeToken<MutableMap<String, TaskInfo>>() {}.type
    private var tasks: MutableMap<String, TaskInfo> = load()

    companion object {
        @Volatile private var instance: TaskStore? = null
        fun getInstance(context: Context): TaskStore =
            instance ?: synchronized(this) { instance ?: TaskStore(context).also { instance = it } }
    }

    private fun load(): MutableMap<String, TaskInfo> {
        val json = prefs.getString("data", null) ?: return mutableMapOf()
        return try { gson.fromJson(json, type) } catch (_: Exception) { mutableMapOf() }
    }

    @Synchronized
    private fun save() { prefs.edit().putString("data", gson.toJson(tasks)).apply() }

    @Synchronized
    fun getAll(): List<TaskInfo> = tasks.values.sortedByDescending { it.id }

    @Synchronized
    fun get(id: String): TaskInfo? = tasks[id]

    @Synchronized
    fun put(task: TaskInfo) { tasks[task.id] = task; save() }

    @Synchronized
    fun putWithoutSave(task: TaskInfo) { tasks[task.id] = task }

    @Synchronized
    fun saveNow() { save() }

    @Synchronized
    fun delete(id: String) { tasks.remove(id); save() }
}
