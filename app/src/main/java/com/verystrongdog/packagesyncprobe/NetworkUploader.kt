package com.verystrongdog.packagesyncprobe

import android.content.Context
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object NetworkUploader {
    fun uploadJson(context: Context, endpoint: String, payload: JSONObject): String {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 8000
            readTimeout = 8000
            doInput = true
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        return try {
            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(payload.toString())
            }
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
                .take(180)
            if (body.isNotBlank()) {
                context.getString(R.string.network_upload_result_with_body, code, endpoint, body)
            } else {
                context.getString(R.string.network_upload_result, code, endpoint)
            }
        } finally {
            connection.disconnect()
        }
    }
}
