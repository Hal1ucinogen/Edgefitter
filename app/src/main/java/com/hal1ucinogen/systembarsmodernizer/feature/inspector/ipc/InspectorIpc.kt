package com.hal1ucinogen.systembarsmodernizer.feature.inspector.ipc

import com.hal1ucinogen.systembarsmodernizer.feature.inspector.model.InspectorReport
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object InspectorIpc {
    const val ACTION_TRIGGER_INSPECT = "com.hal1ucinogen.systembarsmodernizer.ACTION_TRIGGER_INSPECT"
    const val ACTION_INSPECT_RESULT = "com.hal1ucinogen.systembarsmodernizer.ACTION_INSPECT_RESULT"
    const val ACTION_DEACTIVATE_INSPECTOR = "com.hal1ucinogen.systembarsmodernizer.ACTION_DEACTIVATE_INSPECTOR"
    const val EXTRA_PAYLOAD_BYTES = "extra_payload_bytes"
    const val EXTRA_ERROR_MESSAGE = "extra_error_message"
    const val PREF_KEY_INSPECTOR_ACTIVE = "inspector_service_active"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    fun compress(report: InspectorReport): ByteArray {
        val jsonString = json.encodeToString(report)
        val jsonBytes = jsonString.toByteArray(Charsets.UTF_8)
        val bos = ByteArrayOutputStream(jsonBytes.size)
        GZIPOutputStream(bos).use { gzip ->
            gzip.write(jsonBytes)
        }
        return bos.toByteArray()
    }

    fun decompress(bytes: ByteArray): InspectorReport {
        val bis = ByteArrayInputStream(bytes)
        val jsonString = GZIPInputStream(bis).bufferedReader(Charsets.UTF_8).use { it.readText() }
        return json.decodeFromString(jsonString)
    }
}
