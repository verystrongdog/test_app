package com.verystrongdog.packagesyncprobe

import android.Manifest
import android.graphics.Bitmap
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private lateinit var languageToggleButton: Button
    private lateinit var endpointInput: EditText
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
            updateReport(currentReport.copy(generatedAtMillis = System.currentTimeMillis(), cameraSummary = summary))
            appendLog(summary)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppLocaleManager.applySavedLocale(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        preferenceStore = PreferenceStore(this)
        eventLogStore = EventLogStore(this)
        currentReport = preferenceStore.loadReport()

        languageToggleButton = findViewById(R.id.languageToggleButton)
        endpointInput = findViewById(R.id.endpointInput)
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
        findViewById<Button>(R.id.refreshProfileButton).setOnClickListener {
            refreshUi()
            appendLog(getString(R.string.log_refreshed_app_profile))
        }
        findViewById<Button>(R.id.readContactsButton).setOnClickListener {
            runProbe(getString(R.string.log_read_contacts_snapshot)) {
                currentReport.copy(
                    generatedAtMillis = System.currentTimeMillis(),
                    contactsSummary = ProbeEngine.collectContacts(this),
                )
            }
        }
        findViewById<Button>(R.id.scanImagesButton).setOnClickListener {
            runProbe(getString(R.string.log_scanned_photo_library)) {
                currentReport.copy(
                    generatedAtMillis = System.currentTimeMillis(),
                    imageSummary = ProbeEngine.collectImages(this),
                )
            }
        }
        findViewById<Button>(R.id.scanPackagesButton).setOnClickListener {
            runProbe(getString(R.string.log_enumerated_installed_packages)) {
                currentReport.copy(
                    generatedAtMillis = System.currentTimeMillis(),
                    packageSummary = ProbeEngine.collectPackages(this),
                )
            }
        }
        findViewById<Button>(R.id.captureLocationButton).setOnClickListener {
            runProbe(getString(R.string.log_captured_device_location)) {
                currentReport.copy(
                    generatedAtMillis = System.currentTimeMillis(),
                    locationSummary = ProbeEngine.collectLocation(this),
                )
            }
        }
        findViewById<Button>(R.id.recordAudioButton).setOnClickListener {
            startAudioCapture()
        }
        findViewById<Button>(R.id.cameraPreviewButton).setOnClickListener {
            cameraPreviewLauncher.launch(null)
        }
        val uploadReportButton = findViewById<Button>(R.id.uploadReportButton)
        uploadReportButton.setOnClickListener {
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
                    updateReport(currentReport.copy(generatedAtMillis = System.currentTimeMillis(), lastUploadSummary = summary))
                    appendLog(summary)
                }
            }
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
        findViewById<Button>(R.id.clearLogsButton).setOnClickListener {
            eventLogStore.clear()
            preferenceStore.clearReport()
            currentReport = ProbeReport.empty()
            appendLog(getString(R.string.log_cleared_local_state))
        }

        refreshUi()
    }

    override fun onDestroy() {
        runCatching {
            activeRecorder?.release()
        }
        workerExecutor.shutdown()
        super.onDestroy()
    }

    private fun runProbe(logLabel: String, block: MainActivity.() -> ProbeReport) {
        runOnWorker(logLabel) {
            val report = block()
            updateReport(report)
            appendLog(logLabel)
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
                updateReport(currentReport.copy(generatedAtMillis = System.currentTimeMillis(), audioSummary = summary))
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
        preferenceStore.saveEndpoint(currentEndpoint())
        languageToggleButton.text = getString(
            if (AppLocaleManager.isChinese(this)) {
                R.string.switch_to_english
            } else {
                R.string.switch_to_chinese
            },
        )
        profileText.text = ProbeEngine.buildAppProfile(this)
        reportText.text = currentReport.format(this).ifBlank { getString(R.string.empty_report) }
        logText.text = eventLogStore.getAll().ifBlank { getString(R.string.empty_log) }
    }

    private fun appendLog(message: String) {
        eventLogStore.append(message)
        mainHandler.post { refreshUi() }
    }

    private fun currentEndpoint(): String {
        return endpointInput.text?.toString()?.trim().orEmpty()
    }
}
