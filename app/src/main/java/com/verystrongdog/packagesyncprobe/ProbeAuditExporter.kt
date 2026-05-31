package com.verystrongdog.packagesyncprobe

import android.content.Context
import android.os.Environment
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object ProbeAuditExporter {
    fun export(
        context: Context,
        endpoint: String,
        profile: String,
        report: ProbeReport,
        eventLog: String,
    ): String {
        val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        if (!directory.exists()) {
            directory.mkdirs()
        }
        val file = File(directory, "probe_audit_${System.currentTimeMillis()}.json")
        val payload = JSONObject()
            .put("exportedAtMillis", System.currentTimeMillis())
            .put("packageName", context.packageName)
            .put("endpoint", endpoint)
            .put("appProfile", profile)
            .put("report", JSONObject(report.toJson()))
            .put(
                "eventLog",
                JSONArray(
                    eventLog.lineSequence()
                        .map(String::trim)
                        .filter(String::isNotBlank)
                        .toList(),
                ),
            )
        file.writeText(payload.toString(2))
        return context.getString(R.string.audit_export_summary, file.absolutePath)
    }
}
