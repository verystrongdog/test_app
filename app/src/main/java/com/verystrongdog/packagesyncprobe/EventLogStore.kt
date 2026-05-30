package com.verystrongdog.packagesyncprobe

import android.content.Context
import android.text.format.DateFormat
import java.util.Date

class EventLogStore(context: Context) {
    private val prefs = context.getSharedPreferences("probe_logs", Context.MODE_PRIVATE)

    fun append(message: String) {
        val timestamp = DateFormat.format("yyyy-MM-dd HH:mm:ss", Date()).toString()
        val entry = "[$timestamp] $message"
        val current = prefs.getString(KEY_LOGS, "").orEmpty()
        val next = listOf(entry, current)
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
            .take(MAX_CHARS)
        prefs.edit().putString(KEY_LOGS, next).apply()
    }

    fun getAll(): String {
        return prefs.getString(KEY_LOGS, "").orEmpty()
    }

    fun clear() {
        prefs.edit().remove(KEY_LOGS).apply()
    }

    companion object {
        private const val KEY_LOGS = "logs"
        private const val MAX_CHARS = 12000
    }
}
