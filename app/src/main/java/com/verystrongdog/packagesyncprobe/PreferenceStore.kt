package com.verystrongdog.packagesyncprobe

import android.content.Context

class PreferenceStore(context: Context) {
    private val prefs = context.getSharedPreferences("probe_store", Context.MODE_PRIVATE)

    fun loadEndpoint(defaultValue: String): String {
        return prefs.getString(KEY_ENDPOINT, defaultValue) ?: defaultValue
    }

    fun saveEndpoint(value: String) {
        prefs.edit().putString(KEY_ENDPOINT, value).apply()
    }

    fun loadReport(): ProbeReport {
        return ProbeReport.fromJson(prefs.getString(KEY_REPORT, null))
    }

    fun saveReport(report: ProbeReport) {
        prefs.edit().putString(KEY_REPORT, report.toJson()).apply()
    }

    fun clearReport() {
        prefs.edit().remove(KEY_REPORT).apply()
    }

    companion object {
        private const val KEY_ENDPOINT = "endpoint"
        private const val KEY_REPORT = "report"
    }
}
