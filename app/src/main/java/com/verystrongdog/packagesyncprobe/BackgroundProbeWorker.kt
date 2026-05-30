package com.verystrongdog.packagesyncprobe

import android.Manifest
import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class BackgroundProbeWorker(
    appContext: Context,
    params: WorkerParameters,
) : Worker(appContext, params) {
    override fun doWork(): Result {
        val store = PreferenceStore(applicationContext)
        val logStore = EventLogStore(applicationContext)
        val endpoint = inputData.getString(KEY_ENDPOINT).orEmpty()
        var report = store.loadReport()

        val packageSummary = ProbeEngine.collectPackages(applicationContext)
        val contactsSummary = if (ProbeEngine.hasPermission(applicationContext, Manifest.permission.READ_CONTACTS)) {
            ProbeEngine.collectContacts(applicationContext)
        } else {
            "Skipped contact read because READ_CONTACTS is missing"
        }
        val uploadSummary = if (endpoint.isBlank()) {
            applicationContext.getString(R.string.upload_skipped)
        } else {
            runCatching {
                NetworkUploader.uploadJson(
                    endpoint,
                    ProbeEngine.buildUploadPayload(applicationContext, report.copy(packageSummary = packageSummary)),
                )
            }.getOrElse { error ->
                "Background upload failed: ${error.message}"
            }
        }

        val summary = "Background probe ran package scan and optional upload. $packageSummary. $uploadSummary"
        report = report.copy(
            generatedAtMillis = System.currentTimeMillis(),
            contactsSummary = contactsSummary,
            packageSummary = packageSummary,
            lastUploadSummary = uploadSummary,
            lastBackgroundProbeSummary = summary,
        )
        store.saveReport(report)
        logStore.append(summary)
        return Result.success()
    }

    companion object {
        const val KEY_ENDPOINT = "endpoint"
    }
}
