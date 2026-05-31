package com.verystrongdog.packagesyncprobe

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class ProbeForegroundService : Service() {
    private val executor = Executors.newSingleThreadExecutor()

    @Volatile
    private var stopRequested = false

    override fun onCreate() {
        super.onCreate()
        ProbeNotificationHelper.ensureChannels(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopRequested = true
                EventLogStore(this).append(getString(R.string.foreground_service_stopped_by_user))
                RUNNING.set(false)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_START -> startForegroundChain(intent.getStringExtra(EXTRA_ENDPOINT).orEmpty())
            else -> startForegroundChain("")
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        RUNNING.set(false)
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun startForegroundChain(endpoint: String) {
        if (!RUNNING.compareAndSet(false, true)) {
            EventLogStore(this).append(getString(R.string.foreground_service_already_running))
            return
        }
        stopRequested = false
        startForeground(
            ProbeNotificationHelper.FOREGROUND_NOTIFICATION_ID,
            ProbeNotificationHelper.buildForegroundNotification(
                this,
                getString(R.string.foreground_service_notification_text),
            ),
        )
        executor.execute {
            val preferenceStore = PreferenceStore(this)
            val logStore = EventLogStore(this)
            var report = preferenceStore.loadReport()
            val attempts = mutableListOf<String>()
            val summary = runCatching {
                if (endpoint.isBlank()) {
                    getString(R.string.foreground_service_no_endpoint)
                } else {
                    repeat(3) { index ->
                        if (stopRequested) {
                            return@repeat
                        }
                        if (index > 0) {
                            Thread.sleep(1500)
                        }
                        val payload = ProbeEngine.buildUploadPayload(this, preferenceStore.loadReport())
                        attempts += NetworkUploader.uploadJson(this, endpoint, payload)
                    }
                    when {
                        stopRequested && attempts.isNotEmpty() ->
                            getString(
                                R.string.foreground_service_partial_summary,
                                attempts.size,
                                attempts.joinToString(" | "),
                            )

                        stopRequested ->
                            getString(R.string.foreground_service_stopped_by_user)

                        else ->
                            getString(
                                R.string.foreground_service_summary,
                                attempts.joinToString(" | "),
                            )
                    }
                }
            }.getOrElse { error ->
                getString(
                    R.string.foreground_service_failed,
                    error.message ?: getString(R.string.unknown_error),
                )
            }
            report = report.copy(
                generatedAtMillis = System.currentTimeMillis(),
                foregroundServiceSummary = summary,
                lastUploadSummary = attempts.lastOrNull() ?: report.lastUploadSummary,
            )
            preferenceStore.saveReport(report)
            logStore.append(summary)

            val notificationSummary = runCatching {
                ProbeNotificationHelper.postReviewNotification(
                    this,
                    getString(R.string.review_notification_reason_foreground),
                )
            }.getOrElse { error ->
                getString(
                    R.string.notification_post_failed,
                    error.message ?: getString(R.string.unknown_error),
                )
            }
            report = report.copy(
                generatedAtMillis = System.currentTimeMillis(),
                notificationSummary = notificationSummary,
            )
            preferenceStore.saveReport(report)
            logStore.append(notificationSummary)
            RUNNING.set(false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    companion object {
        private const val ACTION_START = "com.verystrongdog.packagesyncprobe.action.START_FOREGROUND_CHAIN"
        private const val ACTION_STOP = "com.verystrongdog.packagesyncprobe.action.STOP_FOREGROUND_CHAIN"
        private const val EXTRA_ENDPOINT = "endpoint"
        private val RUNNING = AtomicBoolean(false)

        fun createStartIntent(context: Context, endpoint: String): Intent {
            return Intent(context, ProbeForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_ENDPOINT, endpoint)
            }
        }

        fun createStopIntent(context: Context): Intent {
            return Intent(context, ProbeForegroundService::class.java).apply {
                action = ACTION_STOP
            }
        }

        fun isRunning(): Boolean = RUNNING.get()
    }
}
