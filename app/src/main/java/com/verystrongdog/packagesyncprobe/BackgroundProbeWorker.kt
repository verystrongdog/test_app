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
        AppLocaleManager.applySavedLocale(applicationContext)
        ProbeNotificationHelper.ensureChannels(applicationContext)
        val store = PreferenceStore(applicationContext)
        val logStore = EventLogStore(applicationContext)
        val endpoint = inputData.getString(KEY_ENDPOINT).orEmpty()
        var report = store.loadReport()

        val packageSummary = ProbeEngine.collectPackages(applicationContext)
        val contactsSummary = if (ProbeEngine.hasPermission(applicationContext, Manifest.permission.READ_CONTACTS)) {
            ProbeEngine.collectContacts(applicationContext)
        } else {
            applicationContext.getString(R.string.background_contacts_skipped)
        }
        val uploadSummary = if (endpoint.isBlank()) {
            applicationContext.getString(R.string.upload_skipped)
        } else {
            runCatching {
                NetworkUploader.uploadJson(
                    applicationContext,
                    endpoint,
                    ProbeEngine.buildUploadPayload(applicationContext, report.copy(packageSummary = packageSummary)),
                )
            }.getOrElse { error ->
                applicationContext.getString(
                    R.string.background_upload_failed,
                    error.message ?: applicationContext.getString(R.string.unknown_error),
                )
            }
        }
        val notificationSummary = runCatching {
            ProbeNotificationHelper.postReviewNotification(
                applicationContext,
                applicationContext.getString(R.string.review_notification_reason_background),
            )
        }.getOrElse { error ->
            applicationContext.getString(
                R.string.notification_post_failed,
                error.message ?: applicationContext.getString(R.string.unknown_error),
            )
        }

        val summary = applicationContext.getString(
            R.string.background_probe_summary,
            packageSummary,
            uploadSummary,
            notificationSummary,
        )
        report = report.copy(
            generatedAtMillis = System.currentTimeMillis(),
            contactsSummary = contactsSummary,
            packageSummary = packageSummary,
            notificationSummary = notificationSummary,
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
