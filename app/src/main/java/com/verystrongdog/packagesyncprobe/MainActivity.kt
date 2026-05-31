package com.verystrongdog.packagesyncprobe

import android.graphics.Bitmap
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.content.Intent
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private lateinit var languageToggleButton: Button
    private lateinit var endpointInput: EditText
    private lateinit var coverageText: TextView
    private lateinit var profileText: TextView
    private lateinit var reportText: TextView
    private lateinit var logText: TextView
    private lateinit var preferenceStore: PreferenceStore
    private lateinit var eventLogStore: EventLogStore

    private val workerExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var activeRecorder: MediaRecorder? = null
    private var activeAudioFile: File? = null
    private var currentReport: ProbeReport = ProbeReport.empty()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val grantedCount = results.values.count { it }
        appendLog(getString(R.string.log_runtime_permission_request_completed, grantedCount, results.size))
        refreshUi()
    }

    private val cameraPreviewLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview(),
    ) { bitmap: Bitmap? ->
        if (bitmap == null) {
            appendLog(getString(R.string.camera_failed))
            refreshUi()
            return@registerForActivityResult
        }
        runOnWorker(getString(R.string.action_saving_camera_preview)) {
            val summary = ProbeEngine.saveCameraPreview(this, bitmap)
            updateReport(
                currentReport.copy(
                    generatedAtMillis = System.currentTimeMillis(),
                    cameraSummary = summary,
                ),
            )
            appendLog(summary)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppLocaleManager.applySavedLocale(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ProbeNotificationHelper.ensureChannels(this)
        preferenceStore = PreferenceStore(this)
        eventLogStore = EventLogStore(this)
        currentReport = preferenceStore.loadReport()

        languageToggleButton = findViewById(R.id.languageToggleButton)
        endpointInput = findViewById(R.id.endpointInput)
        coverageText = findViewById(R.id.coverageText)
        profileText = findViewById(R.id.profileText)
        reportText = findViewById(R.id.reportText)
        logText = findViewById(R.id.logText)

        endpointInput.setText(preferenceStore.loadEndpoint(getString(R.string.default_endpoint)))

        languageToggleButton.setOnClickListener {
            preferenceStore.saveEndpoint(currentEndpoint())
            AppLocaleManager.toggleLocale(this)
        }
        findViewById<Button>(R.id.requestPermissionsButton).setOnClickListener {
            permissionLauncher.launch(ProbeEngine.requestedRuntimePermissions().toTypedArray())
        }
        findViewById<Button>(R.id.openAppSettingsButton).setOnClickListener {
            openAppSettings()
        }
        findViewById<Button>(R.id.refreshProfileButton).setOnClickListener {
            refreshUi()
            appendLog(getString(R.string.log_refreshed_app_profile))
        }
        findViewById<Button>(R.id.scanPackagesButton).setOnClickListener {
            runProbe(getString(R.string.log_enumerated_installed_packages)) {
                val summary = ProbeEngine.collectPackages(this)
                ProbeActionResult(
                    currentReport.copy(
                        generatedAtMillis = System.currentTimeMillis(),
                        packageSummary = summary,
                    ),
                    summary,
                )
            }
        }
        findViewById<Button>(R.id.readContactsButton).setOnClickListener {
            runProbe(getString(R.string.log_read_contacts_snapshot)) {
                val summary = ProbeEngine.collectContacts(this)
                ProbeActionResult(
                    currentReport.copy(
                        generatedAtMillis = System.currentTimeMillis(),
                        contactsSummary = summary,
                    ),
                    summary,
                )
            }
        }
        findViewById<Button>(R.id.scanImagesButton).setOnClickListener {
            runProbe(getString(R.string.log_scanned_photo_library)) {
                val summary = ProbeEngine.collectImages(this)
                ProbeActionResult(
                    currentReport.copy(
                        generatedAtMillis = System.currentTimeMillis(),
                        imageSummary = summary,
                    ),
                    summary,
                )
            }
        }
        findViewById<Button>(R.id.captureLocationButton).setOnClickListener {
            runProbe(getString(R.string.log_captured_device_location)) {
                val summary = ProbeEngine.collectLocation(this)
                ProbeActionResult(
                    currentReport.copy(
                        generatedAtMillis = System.currentTimeMillis(),
                        locationSummary = summary,
                    ),
                    summary,
                )
            }
        }
        findViewById<Button>(R.id.readPhoneStateButton).setOnClickListener {
            runProbe(getString(R.string.log_collected_phone_state)) {
                val summary = ProbeEngine.collectPhoneState(this)
                ProbeActionResult(
                    currentReport.copy(
                        generatedAtMillis = System.currentTimeMillis(),
                        phoneStateSummary = summary,
                    ),
                    summary,
                )
            }
        }
        findViewById<Button>(R.id.recordAudioButton).setOnClickListener {
            startAudioCapture()
        }
        findViewById<Button>(R.id.cameraPreviewButton).setOnClickListener {
            cameraPreviewLauncher.launch(null)
        }
        findViewById<Button>(R.id.postReviewNotificationButton).setOnClickListener {
            runProbe(getString(R.string.log_posted_review_notification)) {
                val summary = ProbeNotificationHelper.postReviewNotification(
                    this,
                    getString(R.string.review_notification_reason_manual),
                )
                ProbeActionResult(
                    currentReport.copy(
                        generatedAtMillis = System.currentTimeMillis(),
                        notificationSummary = summary,
                    ),
                    summary,
                )
            }
        }
        findViewById<Button>(R.id.uploadReportButton).setOnClickListener {
            val endpoint = currentEndpoint()
            preferenceStore.saveEndpoint(endpoint)
            if (endpoint.isBlank()) {
                appendLog(getString(R.string.upload_skipped))
            } else {
                runOnWorker(getString(R.string.action_uploading_report)) {
                    val summary = NetworkUploader.uploadJson(
                        this,
                        endpoint,
                        ProbeEngine.buildUploadPayload(this, currentReport),
                    )
                    updateReport(
                        currentReport.copy(
                            generatedAtMillis = System.currentTimeMillis(),
                            lastUploadSummary = summary,
                        ),
                    )
                    appendLog(summary)
                }
            }
        }
        findViewById<Button>(R.id.startForegroundUploadButton).setOnClickListener {
            val endpoint = currentEndpoint()
            preferenceStore.saveEndpoint(endpoint)
            val intent = ProbeForegroundService.createStartIntent(this, endpoint)
            ContextCompat.startForegroundService(this, intent)
            appendLog(getString(R.string.log_started_foreground_upload_service))
            refreshUi()
        }
        findViewById<Button>(R.id.stopForegroundUploadButton).setOnClickListener {
            startService(ProbeForegroundService.createStopIntent(this))
            appendLog(getString(R.string.log_stopped_foreground_upload_service))
            refreshUi()
        }
        findViewById<Button>(R.id.scheduleBackgroundProbeButton).setOnClickListener {
            val endpoint = currentEndpoint()
            preferenceStore.saveEndpoint(endpoint)
            val request = OneTimeWorkRequestBuilder<BackgroundProbeWorker>()
                .setInitialDelay(15, TimeUnit.SECONDS)
                .setInputData(workDataOf(BackgroundProbeWorker.KEY_ENDPOINT to endpoint))
                .build()
            WorkManager.getInstance(this).enqueue(request)
            appendLog(getString(R.string.background_probe_note))
        }
        findViewById<Button>(R.id.exportAuditSnapshotButton).setOnClickListener {
            runOnWorker(getString(R.string.action_exporting_audit_snapshot)) {
                val summary = ProbeAuditExporter.export(
                    context = this,
                    endpoint = currentEndpoint(),
                    profile = ProbeEngine.buildAppProfile(this),
                    report = currentReport,
                    eventLog = eventLogStore.getAll(),
                )
                updateReport(
                    currentReport.copy(
                        generatedAtMillis = System.currentTimeMillis(),
                        auditExportSummary = summary,
                    ),
                )
                appendLog(summary)
            }
        }
        findViewById<Button>(R.id.clearLogsButton).setOnClickListener {
            eventLogStore.clear()
            preferenceStore.clearReport()
            currentReport = ProbeReport.empty()
            appendLog(getString(R.string.log_cleared_local_state))
        }

        refreshUi()
        consumeIntentAction(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeIntentAction(intent)
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    override fun onDestroy() {
        runCatching {
            activeRecorder?.release()
        }
        workerExecutor.shutdown()
        super.onDestroy()
    }

    private fun consumeIntentAction(intent: Intent?) {
        if (intent?.action != ProbeNotificationHelper.ACTION_REVIEW_NOTIFICATION) {
            return
        }
        val reason = intent.getStringExtra(ProbeNotificationHelper.EXTRA_NOTIFICATION_REASON)
            .orEmpty()
            .ifBlank { getString(R.string.review_notification_reason_manual) }
        val summary = getString(R.string.notification_tap_summary, reason)
        updateReport(
            currentReport.copy(
                generatedAtMillis = System.currentTimeMillis(),
                notificationSummary = summary,
            ),
        )
        appendLog(summary)
        intent.action = null
    }

    private fun openAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null),
        )
        startActivity(intent)
        appendLog(getString(R.string.log_opened_app_settings))
    }

    private fun runProbe(actionLabel: String, block: MainActivity.() -> ProbeActionResult) {
        runOnWorker(actionLabel) {
            val result = block()
            updateReport(result.report)
            appendLog(result.logMessage)
        }
    }

    private fun runOnWorker(actionLabel: String, block: () -> Unit) {
        workerExecutor.execute {
            runCatching(block).onFailure { error ->
                mainHandler.post {
                    appendLog(
                        getString(
                            R.string.log_action_failed,
                            actionLabel,
                            error.message ?: getString(R.string.unknown_error),
                        ),
                    )
                    Toast.makeText(this, error.message ?: actionLabel, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun startAudioCapture() {
        if (activeRecorder != null) {
            appendLog(getString(R.string.audio_in_progress))
            return
        }
        runOnWorker(getString(R.string.action_recording_microphone_sample)) {
            val (recorder, file) = ProbeEngine.startAudioCapture(this)
            activeRecorder = recorder
            activeAudioFile = file
            appendLog(getString(R.string.log_started_microphone_recording, file.absolutePath))
            mainHandler.postDelayed({
                val summary = runCatching {
                    recorder.stop()
                    recorder.release()
                    getString(R.string.log_recorded_microphone_sample, file.absolutePath, file.length())
                }.getOrElse { error ->
                    runCatching { file.delete() }
                    getString(R.string.log_audio_capture_failed, error.message ?: getString(R.string.unknown_error))
                }
                activeRecorder = null
                activeAudioFile = null
                updateReport(
                    currentReport.copy(
                        generatedAtMillis = System.currentTimeMillis(),
                        audioSummary = summary,
                    ),
                )
                appendLog(summary)
            }, 5000)
        }
    }

    private fun updateReport(report: ProbeReport) {
        currentReport = report
        preferenceStore.saveReport(report)
        mainHandler.post { refreshUi() }
    }

    private fun refreshUi() {
        currentReport = preferenceStore.loadReport()
        preferenceStore.saveEndpoint(currentEndpoint())
        languageToggleButton.text = getString(
            if (AppLocaleManager.isChinese(this)) {
                R.string.switch_to_english
            } else {
                R.string.switch_to_chinese
            },
        )
        coverageText.text = buildCoverageText()
        profileText.text = ProbeEngine.buildAppProfile(this)
        reportText.text = currentReport.format(this).ifBlank { getString(R.string.empty_report) }
        logText.text = eventLogStore.getAll().ifBlank { getString(R.string.empty_log) }
    }

    private fun buildCoverageText(): String {
        val base = ProbeEngine.buildDetectorCoverageSummary(this)
        val serviceState = if (ProbeForegroundService.isRunning()) {
            getString(R.string.foreground_service_state_running)
        } else {
            getString(R.string.foreground_service_state_idle)
        }
        return "$base\n${getString(R.string.foreground_service_state_line, serviceState)}"
    }

    private fun appendLog(message: String) {
        eventLogStore.append(message)
        mainHandler.post { refreshUi() }
    }

    private fun currentEndpoint(): String {
        return endpointInput.text?.toString()?.trim().orEmpty()
    }

    private data class ProbeActionResult(
        val report: ProbeReport,
        val logMessage: String,
    )
}
