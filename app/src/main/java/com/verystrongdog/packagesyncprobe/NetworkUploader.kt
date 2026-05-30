package com.verystrongdog.packagesyncprobe

import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object NetworkUploader {
    fun uploadJson(endpoint: String, payload: JSONObject): String {
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
            "HTTP $code to $endpoint${if (body.isNotBlank()) ", body: $body" else ""}"
        } finally {
            connection.disconnect()
        }
    }
}
