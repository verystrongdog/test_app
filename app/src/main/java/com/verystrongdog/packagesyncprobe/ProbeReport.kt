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
    val phoneStateSummary: String? = null,
    val audioSummary: String? = null,
    val cameraSummary: String? = null,
    val notificationSummary: String? = null,
    val foregroundServiceSummary: String? = null,
    val lastUploadSummary: String? = null,
    val lastBackgroundProbeSummary: String? = null,
    val auditExportSummary: String? = null,
) {
    fun toJson(): String {
        return JSONObject()
            .put("generatedAtMillis", generatedAtMillis)
            .put("contactsSummary", contactsSummary)
            .put("imageSummary", imageSummary)
            .put("packageSummary", packageSummary)
            .put("locationSummary", locationSummary)
            .put("phoneStateSummary", phoneStateSummary)
            .put("audioSummary", audioSummary)
            .put("cameraSummary", cameraSummary)
            .put("notificationSummary", notificationSummary)
            .put("foregroundServiceSummary", foregroundServiceSummary)
            .put("lastUploadSummary", lastUploadSummary)
            .put("lastBackgroundProbeSummary", lastBackgroundProbeSummary)
            .put("auditExportSummary", auditExportSummary)
            .toString()
    }

    fun format(context: Context): String {
        val lines = listOf(
            context.getString(
                R.string.report_generated_at,
                DateFormat.format("yyyy-MM-dd HH:mm:ss", Date(generatedAtMillis)),
            ),
            "",
            context.getString(R.string.report_section_baseline),
            context.getString(R.string.report_packages, packageSummary ?: context.getString(R.string.report_not_collected)),
            "",
            context.getString(R.string.report_section_permission_surface),
            context.getString(R.string.report_contacts, contactsSummary ?: context.getString(R.string.report_not_collected)),
            context.getString(R.string.report_images, imageSummary ?: context.getString(R.string.report_not_collected)),
            context.getString(R.string.report_location, locationSummary ?: context.getString(R.string.report_not_collected)),
            context.getString(R.string.report_phone_state, phoneStateSummary ?: context.getString(R.string.report_not_collected)),
            context.getString(R.string.report_audio, audioSummary ?: context.getString(R.string.report_not_collected)),
            context.getString(R.string.report_camera, cameraSummary ?: context.getString(R.string.report_not_collected)),
            "",
            context.getString(R.string.report_section_runtime),
            context.getString(
                R.string.report_notification,
                notificationSummary ?: context.getString(R.string.report_not_collected),
            ),
            context.getString(
                R.string.report_foreground_service,
                foregroundServiceSummary ?: context.getString(R.string.report_not_collected),
            ),
            context.getString(
                R.string.report_background_probe,
                lastBackgroundProbeSummary ?: context.getString(R.string.report_not_collected),
            ),
            "",
            context.getString(R.string.report_section_outbound),
            context.getString(R.string.report_upload, lastUploadSummary ?: context.getString(R.string.report_not_collected)),
            "",
            context.getString(R.string.report_section_audit),
            context.getString(
                R.string.report_audit_export,
                auditExportSummary ?: context.getString(R.string.report_not_collected),
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
                phoneStateSummary = json.optString("phoneStateSummary").takeIf { it.isNotBlank() },
                audioSummary = json.optString("audioSummary").takeIf { it.isNotBlank() },
                cameraSummary = json.optString("cameraSummary").takeIf { it.isNotBlank() },
                notificationSummary = json.optString("notificationSummary").takeIf { it.isNotBlank() },
                foregroundServiceSummary = json.optString("foregroundServiceSummary").takeIf { it.isNotBlank() },
                lastUploadSummary = json.optString("lastUploadSummary").takeIf { it.isNotBlank() },
                lastBackgroundProbeSummary = json.optString("lastBackgroundProbeSummary").takeIf { it.isNotBlank() },
                auditExportSummary = json.optString("auditExportSummary").takeIf { it.isNotBlank() },
            )
        }
    }
}
