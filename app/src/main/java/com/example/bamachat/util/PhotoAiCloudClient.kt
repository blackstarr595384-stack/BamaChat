package com.example.bamachat.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

data class PhotoAiCloudConfig(
    val endpoint: String,
    val token: String
)

enum class PhotoAiCloudStatus {
    OK,
    NOT_CONFIGURED,
    ERROR
}

data class PhotoAiCloudResult(
    val status: PhotoAiCloudStatus,
    val message: String,
    val bitmap: Bitmap? = null,
    val provider: String = "",
    val processingMs: Long = 0L
)

object PhotoAiCloudConfigResolver {
    private const val SETTINGS_PREFS = "settings"
    private const val KEY_PHOTO_AI_CLOUD_ENDPOINT = "photo_ai_cloud_endpoint"
    private const val KEY_PHOTO_AI_CLOUD_API_TOKEN = "photo_ai_cloud_api_token"
    private const val KEY_LIVE_WEB_ENDPOINT = "live_web_endpoint"

    fun resolve(context: Context): PhotoAiCloudConfig {
        val prefs = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        val explicitEndpoint = prefs.getString(KEY_PHOTO_AI_CLOUD_ENDPOINT, "")?.trim().orEmpty()
        val token = SecureSettingsStore.getString(context, prefs, KEY_PHOTO_AI_CLOUD_API_TOKEN).trim()
        val liveWebEndpoint = prefs.getString(KEY_LIVE_WEB_ENDPOINT, "")?.trim().orEmpty()
        val resolvedEndpoint = explicitEndpoint.ifBlank { deriveFromLiveWebEndpoint(liveWebEndpoint) }
        return PhotoAiCloudConfig(
            endpoint = resolvedEndpoint,
            token = token
        )
    }

    fun deriveFromLiveWebEndpoint(liveWebEndpoint: String): String {
        val source = liveWebEndpoint.trim()
        if (source.isBlank()) return ""
        if (source.contains("cloudfunctions.net/webSearch")) {
            return source.replace("cloudfunctions.net/webSearch", "cloudfunctions.net/photoEdit")
        }
        if (source.endsWith("/webSearch")) {
            return source.removeSuffix("/webSearch") + "/photoEdit"
        }
        if (source.contains("websearch-") && source.contains(".a.run.app")) {
            return source.replace("websearch-", "photoedit-")
        }
        return ""
    }
}

class PhotoAiCloudClient(
    private val context: Context,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        private const val MAX_UPLOAD_SIDE = 2048
        private const val UPSCALE_FACTOR = 2.0
        private const val CLOUD_TIMEOUT_MS = 80_000L
    }

    suspend fun process(action: PhotoAiAction, input: Bitmap): PhotoAiCloudResult = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        val actionKey = when (action) {
            PhotoAiAction.BackgroundRemove -> "background_remove"
            PhotoAiAction.UpscaleHd -> "upscale_hd"
            else -> return@withContext PhotoAiCloudResult(
                status = PhotoAiCloudStatus.ERROR,
                message = "Nicht unterstützte Cloud-Aktion."
            )
        }

        val config = PhotoAiCloudConfigResolver.resolve(context)
        if (config.endpoint.isBlank()) {
            return@withContext PhotoAiCloudResult(
                status = PhotoAiCloudStatus.NOT_CONFIGURED,
                message = "Photo-AI-Cloud Endpoint fehlt. In den Einstellungen unter API & Cloud den Photo-AI Endpoint setzen."
            )
        }
        val qualityMode = chooseQualityMode(input)

        val preparedBitmap = prepareBitmapForUpload(input, MAX_UPLOAD_SIDE)
        val encodedImage = encodeImage(preparedBitmap, actionKey)
            ?: return@withContext PhotoAiCloudResult(
                status = PhotoAiCloudStatus.ERROR,
                message = "Bildkodierung fehlgeschlagen."
            )

        val payload = JSONObject().apply {
            put("action", actionKey)
            put("imageBase64", encodedImage.base64)
            put("mimeType", encodedImage.mimeType)
            put("qualityMode", qualityMode)
            if (actionKey == "upscale_hd") {
                put("upscaleFactor", UPSCALE_FACTOR)
            }
        }
        AppTelemetry.logEvent(
            "photo_cloud_request",
            mapOf(
                "action" to actionKey,
                "quality_mode" to qualityMode
            )
        )

        val requestBuilder = Request.Builder()
            .url(config.endpoint)
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))

        if (config.token.isNotBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer ${config.token}")
        }

        return@withContext runCatching {
            withTimeout(CLOUD_TIMEOUT_MS) {
                httpClient.newCall(requestBuilder.build()).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val message = "Photo-Cloud Fehler (${response.code}): ${body.take(140)}"
                        AppTelemetry.logEvent(
                            "photo_cloud_error",
                            mapOf(
                                "action" to actionKey,
                                "code" to response.code.toString()
                            )
                        )
                        return@use PhotoAiCloudResult(
                            status = PhotoAiCloudStatus.ERROR,
                            message = message,
                            processingMs = System.currentTimeMillis() - startedAt
                        )
                    }

                    val json = JSONObject(body)
                    val success = json.optBoolean("success", false)
                    if (!success) {
                        val remoteError = json.optString("error").ifBlank { "Cloud-Antwort ohne success" }
                        AppTelemetry.logEvent(
                            "photo_cloud_error",
                            mapOf(
                                "action" to actionKey,
                                "code" to "remote"
                            )
                        )
                        return@use PhotoAiCloudResult(
                            status = PhotoAiCloudStatus.ERROR,
                            message = remoteError,
                            processingMs = System.currentTimeMillis() - startedAt
                        )
                    }

                    val outputBase64 = json.optString("imageBase64", "")
                    if (outputBase64.isBlank()) {
                        return@use PhotoAiCloudResult(
                            status = PhotoAiCloudStatus.ERROR,
                            message = "Cloud-Antwort enthält kein Bild.",
                            processingMs = System.currentTimeMillis() - startedAt
                        )
                    }
                    val bytes = Base64.decode(outputBase64, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bitmap == null) {
                        return@use PhotoAiCloudResult(
                            status = PhotoAiCloudStatus.ERROR,
                            message = "Cloud-Bild konnte nicht dekodiert werden.",
                            processingMs = System.currentTimeMillis() - startedAt
                        )
                    }
                    val provider = json.optString("provider", "")
                    val remoteMs = json.optJSONObject("meta")?.optLong("processingMs", 0L) ?: 0L
                    val elapsed = System.currentTimeMillis() - startedAt
                    AppTelemetry.logEvent(
                        "photo_cloud_success",
                        mapOf(
                            "action" to actionKey,
                            "provider" to provider.ifBlank { "unknown" }
                        )
                    )
                    PhotoAiCloudResult(
                        status = PhotoAiCloudStatus.OK,
                        message = "Cloud-Aktion angewendet.",
                        bitmap = bitmap,
                        provider = provider,
                        processingMs = if (remoteMs > 0) remoteMs else elapsed
                    )
                }
            }
        }.getOrElse { error ->
            val message = when (error) {
                is TimeoutCancellationException ->
                    "Cloud-Verarbeitung hat zu lange gedauert. Bitte erneut versuchen."

                else -> error.message ?: "Unbekannter Cloud-Fehler"
            }
            AppTelemetry.logEvent(
                "photo_cloud_error",
                mapOf(
                    "action" to actionKey,
                    "code" to if (error is TimeoutCancellationException) "timeout" else "exception"
                )
            )
            PhotoAiCloudResult(
                status = PhotoAiCloudStatus.ERROR,
                message = message,
                processingMs = System.currentTimeMillis() - startedAt
            )
        }
    }

    private data class EncodedImage(
        val base64: String,
        val mimeType: String
    )

    private fun prepareBitmapForUpload(source: Bitmap, maxSide: Int): Bitmap {
        val side = maxOf(source.width, source.height)
        if (side <= maxSide) return source
        val scale = maxSide.toFloat() / side.toFloat()
        val targetWidth = (source.width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (source.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
    }

    private fun encodeImage(bitmap: Bitmap, actionKey: String): EncodedImage? {
        return runCatching {
            val stream = ByteArrayOutputStream()
            val (format, mimeType) = if (actionKey == "background_remove") {
                Bitmap.CompressFormat.PNG to "image/png"
            } else {
                Bitmap.CompressFormat.JPEG to "image/jpeg"
            }
            val quality = if (format == Bitmap.CompressFormat.PNG) 100 else 94
            if (!bitmap.compress(format, quality, stream)) return null
            EncodedImage(
                base64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP),
                mimeType = mimeType
            )
        }.getOrNull()
    }

    private fun chooseQualityMode(bitmap: Bitmap): String {
        val megapixels = (bitmap.width.toDouble() * bitmap.height.toDouble()) / 1_000_000.0
        return when {
            megapixels < 1.5 -> "high"
            megapixels > 8.5 -> "fast"
            else -> "balanced"
        }
    }
}
