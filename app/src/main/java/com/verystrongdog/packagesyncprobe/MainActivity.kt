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
        appendLog("Runtime permission request completed: $grantedCount/${results.size} granted")
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
        runOnWorker("Saving camera preview") {
            val summary = ProbeEngine.saveCameraPreview(this, bitmap)
            updateReport(currentReport.copy(generatedAtMillis = System.currentTimeMillis(), cameraSummary = summary))
            appendLog(summary)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        preferenceStore = PreferenceStore(this)
        eventLogStore = EventLogStore(this)
        currentReport = preferenceStore.loadReport()

        endpointInput = findViewById(R.id.endpointInput)
        profileText = findViewById(R.id.profileText)
        reportText = findViewById(R.id.reportText)
        logText = findViewById(R.id.logText)

        endpointInput.setText(preferenceStore.loadEndpoint(getString(R.string.default_endpoint)))

        findViewById<Button>(R.id.requestPermissionsButton).setOnClickListener {
            permissionLauncher.launch(ProbeEngine.requestedRuntimePermissions().toTypedArray())
        }
        findViewById<Button>(R.id.refreshProfileButton).setOnClickListener {
            refreshUi()
            appendLog("Refreshed app profile")
        }
        findViewById<Button>(R.id.readContactsButton).setOnClickListener {
            runProbe("Read contacts snapshot") {
                currentReport.copy(
                    generatedAtMillis = System.currentTimeMillis(),
                    contactsSummary = ProbeEngine.collectContacts(this),
                )
            }
        }
        findViewById<Button>(R.id.scanImagesButton).setOnClickListener {
            runProbe("Scanned photo library") {
                currentReport.copy(
                    generatedAtMillis = System.currentTimeMillis(),
                    imageSummary = ProbeEngine.collectImages(this),
                )
            }
        }
        findViewById<Button>(R.id.scanPackagesButton).setOnClickListener {
            runProbe("Enumerated installed packages") {
                currentReport.copy(
                    generatedAtMillis = System.currentTimeMillis(),
                    packageSummary = ProbeEngine.collectPackages(this),
                )
            }
        }
        findViewById<Button>(R.id.captureLocationButton).setOnClickListener {
            runProbe("Captured device location") {
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
                runOnWorker("Uploading report") {
                    val summary = NetworkUploader.uploadJson(
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
            appendLog("Cleared local report and log state")
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
                    appendLog("$actionLabel failed: ${error.message}")
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
        runOnWorker("Recording microphone sample") {
            val (recorder, file) = ProbeEngine.startAudioCapture(this)
            activeRecorder = recorder
            activeAudioFile = file
            appendLog("Started a 5-second microphone recording to ${file.absolutePath}")
            mainHandler.postDelayed({
                val summary = runCatching {
                    recorder.stop()
                    recorder.release()
                    "Recorded 5-second microphone sample to ${file.absolutePath} (${file.length()} bytes)"
                }.getOrElse { error ->
                    runCatching { file.delete() }
                    "Audio capture failed during stop: ${error.message}"
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
        profileText.text = ProbeEngine.buildAppProfile(this)
        reportText.text = currentReport.format().ifBlank { getString(R.string.empty_report) }
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
