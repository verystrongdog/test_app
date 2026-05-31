package com.verystrongdog.packagesyncprobe

import android.annotation.SuppressLint
import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.LocationManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.provider.ContactsContract
import android.provider.MediaStore
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

object ProbeEngine {
    private const val SUSPICIOUS_KEYWORD = "package"
    private val sensitiveDataPermissions = setOf(
        "READ_CONTACTS",
        "ACCESS_FINE_LOCATION",
        "READ_EXTERNAL_STORAGE",
        "READ_MEDIA_IMAGES",
        "READ_MEDIA_VIDEO",
    )
    private val identityPermissions = setOf(
        "READ_PHONE_STATE",
        "POST_NOTIFICATIONS",
        "QUERY_ALL_PACKAGES",
    )
    private val sensorPermissions = setOf(
        "CAMERA",
        "RECORD_AUDIO",
    )

    fun requestedRuntimePermissions(): List<String> {
        val mediaPermission = mediaPermission()
        return buildList {
            add(Manifest.permission.READ_CONTACTS)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.RECORD_AUDIO)
            add(Manifest.permission.CAMERA)
            add(Manifest.permission.READ_PHONE_STATE)
            add(mediaPermission)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    fun buildAppProfile(context: Context): String {
        val packageManager = context.packageManager
        val requestedPermissions = declaredPermissions(context)
        val permissionLines = requestedPermissions.joinToString("\n") { permission ->
            val granted = if (hasPermission(context, fullPermissionName(permission))) {
                context.getString(R.string.permission_granted)
            } else {
                context.getString(R.string.permission_missing)
            }
            context.getString(R.string.permission_line, permission, granted)
        }
        val appLabel = packageManager.getApplicationLabel(context.applicationInfo).toString()
        val suspiciousHit = if (context.packageName.lowercase(Locale.US).contains(SUSPICIOUS_KEYWORD) ||
            appLabel.lowercase(Locale.US).contains(SUSPICIOUS_KEYWORD)
        ) {
            context.getString(R.string.suspicious_keyword_yes, SUSPICIOUS_KEYWORD)
        } else {
            context.getString(R.string.suspicious_keyword_no)
        }
        val networkSurface = if (requestedPermissions.any { it in setOf("INTERNET", "ACCESS_NETWORK_STATE") }) {
            context.getString(R.string.value_yes)
        } else {
            context.getString(R.string.value_no)
        }
        return context.getString(
            R.string.profile_template,
            appLabel,
            context.packageName,
            suspiciousHit,
            permissionGroupSummary(context, requestedPermissions.filter { it in sensitiveDataPermissions }),
            permissionGroupSummary(context, requestedPermissions.filter { it in identityPermissions }),
            permissionGroupSummary(context, requestedPermissions.filter { it in sensorPermissions }),
            networkSurface,
            permissionLines,
        )
    }

    fun buildDetectorCoverageSummary(context: Context): String {
        return listOf(
            context.getString(R.string.coverage_baseline),
            context.getString(R.string.coverage_runtime),
            context.getString(R.string.coverage_outbound),
            context.getString(R.string.coverage_permission_surface),
            context.getString(R.string.coverage_audit),
        ).joinToString("\n")
    }

    fun collectContacts(context: Context): String {
        requirePermission(context, Manifest.permission.READ_CONTACTS)
        var count = 0
        val samples = mutableListOf<String>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
            ),
            null,
            null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC",
        )?.use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                count += 1
                if (samples.size < 3) {
                    samples += cursor.getString(nameIndex)
                }
            }
        }
        val sampleText = samples.takeIf { it.isNotEmpty() }?.joinToString(", ")
            ?: context.getString(R.string.value_none)
        return context.getString(R.string.contacts_summary, count, sampleText)
    }

    fun collectImages(context: Context): String {
        val permission = mediaPermission()
        requirePermission(context, permission)
        var count = 0
        var newestLabel = context.getString(R.string.value_none)
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME),
            null,
            null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC",
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                count += 1
                if (count == 1) {
                    val id = cursor.getLong(idIndex)
                    val name = cursor.getString(nameIndex)
                    newestLabel = "$name (${ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)})"
                }
            }
        }
        return context.getString(R.string.images_summary, count, newestLabel)
    }

    fun collectPackages(context: Context): String {
        val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getInstalledApplications(0)
        }
        val sample = packages.asSequence()
            .map { it.packageName }
            .sorted()
            .take(5)
            .joinToString(", ")
        return context.getString(R.string.packages_summary, packages.size, sample)
    }

    @SuppressLint("MissingPermission")
    fun collectLocation(context: Context): String {
        requirePermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = locationManager.getProviders(true)
        val best = providers.asSequence()
            .mapNotNull { provider -> runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }
        return if (best == null) {
            context.getString(R.string.location_unavailable)
        } else {
            context.getString(
                R.string.location_summary,
                "%.5f".format(best.latitude),
                "%.5f".format(best.longitude),
                best.provider,
            )
        }
    }

    fun collectPhoneState(context: Context): String {
        requirePermission(context, Manifest.permission.READ_PHONE_STATE)
        val telephonyManager = context.getSystemService(TelephonyManager::class.java)
            ?: return context.getString(R.string.phone_state_unavailable)
        val phoneType = when (telephonyManager.phoneType) {
            TelephonyManager.PHONE_TYPE_GSM -> "GSM"
            TelephonyManager.PHONE_TYPE_CDMA -> "CDMA"
            TelephonyManager.PHONE_TYPE_SIP -> "SIP"
            TelephonyManager.PHONE_TYPE_NONE -> "NONE"
            else -> "UNKNOWN"
        }
        val simOperator = telephonyManager.simOperatorName.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.value_none)
        val networkOperator = telephonyManager.networkOperatorName.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.value_none)
        return context.getString(R.string.phone_state_summary, phoneType, simOperator, networkOperator)
    }

    fun saveCameraPreview(context: Context, bitmap: Bitmap): String {
        val file = File(context.cacheDir, "camera_preview_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        return context.getString(R.string.camera_saved_summary, file.absolutePath, file.length())
    }

    fun startAudioCapture(context: Context): Pair<MediaRecorder, File> {
        requirePermission(context, Manifest.permission.RECORD_AUDIO)
        val outputDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir
        val outputFile = File(outputDir, "probe_audio_${System.currentTimeMillis()}.m4a")
        val recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(44100)
            setAudioEncodingBitRate(96000)
            setOutputFile(outputFile.absolutePath)
            prepare()
            start()
        }
        return recorder to outputFile
    }

    fun buildUploadPayload(context: Context, report: ProbeReport): JSONObject {
        return JSONObject()
            .put("appLabel", context.applicationInfo.loadLabel(context.packageManager))
            .put("packageName", context.packageName)
            .put("generatedAtMillis", report.generatedAtMillis)
            .put(
                "detectorCoverage",
                JSONObject()
                    .put("baselineProfiling", true)
                    .put("runtimeObservation", true)
                    .put("outboundSupervision", true)
                    .put("permissionSurface", true)
                    .put("auditSnapshot", true),
            )
            .put("declaredPermissions", JSONArray(declaredPermissions(context)))
            .put("report", JSONObject(report.toJson()))
            .put("notes", context.getString(R.string.upload_payload_note))
    }

    fun mediaPermission(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    fun hasPermission(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun declaredPermissions(context: Context): List<String> {
        val packageManager = context.packageManager
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        }
        return packageInfo.requestedPermissions.orEmpty()
            .sorted()
            .map { it.substringAfterLast('.') }
    }

    private fun fullPermissionName(shortName: String): String {
        return when (shortName) {
            "INTERNET",
            "ACCESS_NETWORK_STATE",
            "READ_CONTACTS",
            "ACCESS_FINE_LOCATION",
            "ACCESS_COARSE_LOCATION",
            "RECORD_AUDIO",
            "CAMERA",
            "QUERY_ALL_PACKAGES",
            "READ_PHONE_STATE",
            "READ_EXTERNAL_STORAGE",
            "READ_MEDIA_IMAGES",
            "READ_MEDIA_VIDEO",
            "POST_NOTIFICATIONS",
            "FOREGROUND_SERVICE",
            "FOREGROUND_SERVICE_DATA_SYNC",
            -> "android.permission.$shortName"

            else -> shortName
        }
    }

    private fun permissionGroupSummary(context: Context, permissions: List<String>): String {
        return permissions.sorted().takeIf { it.isNotEmpty() }?.joinToString(", ")
            ?: context.getString(R.string.value_none)
    }

    private fun requirePermission(context: Context, permission: String) {
        check(hasPermission(context, permission)) {
            context.getString(R.string.missing_permission, permission.substringAfterLast('.'))
        }
    }
}
