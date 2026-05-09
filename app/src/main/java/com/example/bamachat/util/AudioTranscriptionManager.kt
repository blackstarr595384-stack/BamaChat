package com.example.bamachat.util

import android.content.Context
import android.net.Uri
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class AudioTranscriptionManager(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun transcribeWithGroq(uri: Uri, apiKey: String): String? {
        if (apiKey.isBlank()) return null
        val tempFile = copyToTempFile(uri) ?: return null
        return try {
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("model", "whisper-large-v3-turbo")
                .addFormDataPart("response_format", "json")
                .addFormDataPart(
                    "file",
                    tempFile.name,
                    tempFile.asRequestBody("application/octet-stream".toMediaType())
                )
                .build()

            val request = Request.Builder()
                .url("https://api.groq.com/openai/v1/audio/transcriptions")
                .addHeader("Authorization", "Bearer $apiKey")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string().orEmpty()
                parseTranscriptionResponse(body)
            }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { tempFile.delete() }
        }
    }

    private fun copyToTempFile(uri: Uri): File? {
        return runCatching {
            val file = File.createTempFile("bamachat_audio_", ".bin", context.cacheDir)
            context.contentResolver.openInputStream(uri)?.use { source ->
                file.outputStream().use { sink ->
                    source.copyTo(sink)
                }
            } ?: return null
            file
        }.getOrNull()
    }

    private fun parseTranscriptionResponse(body: String): String? {
        if (body.isBlank()) return null
        return runCatching {
            val json = JSONObject(body)
            json.optString("text", "").takeIf { it.isNotBlank() }
        }.getOrNull()
    }
}
