package com.verystrongdog.packagesyncprobe

import android.content.Context
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

    fun format(context: Context): String {
        val lines = listOf(
            context.getString(
                R.string.report_generated_at,
                DateFormat.format("yyyy-MM-dd HH:mm:ss", Date(generatedAtMillis)),
            ),
            context.getString(R.string.report_contacts, contactsSummary ?: context.getString(R.string.report_not_collected)),
            context.getString(R.string.report_images, imageSummary ?: context.getString(R.string.report_not_collected)),
            context.getString(R.string.report_packages, packageSummary ?: context.getString(R.string.report_not_collected)),
            context.getString(R.string.report_location, locationSummary ?: context.getString(R.string.report_not_collected)),
            context.getString(R.string.report_audio, audioSummary ?: context.getString(R.string.report_not_collected)),
            context.getString(R.string.report_camera, cameraSummary ?: context.getString(R.string.report_not_collected)),
            context.getString(R.string.report_upload, lastUploadSummary ?: context.getString(R.string.report_not_collected)),
            context.getString(
                R.string.report_background_probe,
                lastBackgroundProbeSummary ?: context.getString(R.string.report_not_collected),
            ),
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
