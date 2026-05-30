package com.verystrongdog.packagesyncprobe

import android.text.format.DateFormat
import org.json.JSONObject
import java.util.Date

data class ProbeReport(
    val generatedAtMillis: Long = System.currentTimeMillis(),
    val contactsSummary: String? = null,
    val imageSummary: String? = null,
    val packageSummary: String? = null,
    val locationSummary: String? = null,
    val audioSummary: String? = null,
    val cameraSummary: String? = null,
    val lastUploadSummary: String? = null,
    val lastBackgroundProbeSummary: String? = null,
) {
    fun toJson(): String {
        return JSONObject()
            .put("generatedAtMillis", generatedAtMillis)
            .put("contactsSummary", contactsSummary)
            .put("imageSummary", imageSummary)
            .put("packageSummary", packageSummary)
            .put("locationSummary", locationSummary)
            .put("audioSummary", audioSummary)
            .put("cameraSummary", cameraSummary)
            .put("lastUploadSummary", lastUploadSummary)
            .put("lastBackgroundProbeSummary", lastBackgroundProbeSummary)
            .toString()
    }

    fun format(): String {
        val lines = listOf(
            "Generated at: ${DateFormat.format("yyyy-MM-dd HH:mm:ss", Date(generatedAtMillis))}",
            "Contacts: ${contactsSummary ?: "not collected"}",
            "Images: ${imageSummary ?: "not collected"}",
            "Packages: ${packageSummary ?: "not collected"}",
            "Location: ${locationSummary ?: "not collected"}",
            "Audio: ${audioSummary ?: "not collected"}",
            "Camera: ${cameraSummary ?: "not collected"}",
            "Upload: ${lastUploadSummary ?: "not collected"}",
            "Background probe: ${lastBackgroundProbeSummary ?: "not collected"}",
        )
        return lines.joinToString("\n")
    }

    companion object {
        fun empty(): ProbeReport = ProbeReport()

        fun fromJson(raw: String?): ProbeReport {
            if (raw.isNullOrBlank()) {
                return empty()
            }
            val json = JSONObject(raw)
            return ProbeReport(
                generatedAtMillis = json.optLong("generatedAtMillis", System.currentTimeMillis()),
                contactsSummary = json.optString("contactsSummary").takeIf { it.isNotBlank() },
                imageSummary = json.optString("imageSummary").takeIf { it.isNotBlank() },
                packageSummary = json.optString("packageSummary").takeIf { it.isNotBlank() },
                locationSummary = json.optString("locationSummary").takeIf { it.isNotBlank() },
                audioSummary = json.optString("audioSummary").takeIf { it.isNotBlank() },
                cameraSummary = json.optString("cameraSummary").takeIf { it.isNotBlank() },
                lastUploadSummary = json.optString("lastUploadSummary").takeIf { it.isNotBlank() },
                lastBackgroundProbeSummary = json.optString("lastBackgroundProbeSummary").takeIf { it.isNotBlank() },
            )
        }
    }
}
