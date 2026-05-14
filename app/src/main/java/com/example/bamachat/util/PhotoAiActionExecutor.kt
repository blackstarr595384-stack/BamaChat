package com.example.bamachat.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.IOException

data class PhotoAiAdjustments(
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val warmth: Float = 1f
)

sealed class PhotoAiAction(val toolId: PhotoAiToolId) {
    data class Rotate(val degrees: Float) : PhotoAiAction(PhotoAiToolId.ROTATE_FLIP)
    data object Mirror : PhotoAiAction(PhotoAiToolId.ROTATE_FLIP)
    data class Crop(val ratio: Float) : PhotoAiAction(PhotoAiToolId.CROP_RESIZE)
    data class ApplyAdjustments(val values: PhotoAiAdjustments) : PhotoAiAction(PhotoAiToolId.ADJUST_TONE)
    data object AutoEnhance : PhotoAiAction(PhotoAiToolId.AUTO_ENHANCE)
    data object BackgroundRemove : PhotoAiAction(PhotoAiToolId.BACKGROUND_REMOVE)
    data object UpscaleHd : PhotoAiAction(PhotoAiToolId.UPSCALE_HD)
    data object ExportHd : PhotoAiAction(PhotoAiToolId.EXPORT_HD)
}

enum class PhotoAiExecutionStatus {
    APPLIED,
    EXPORTED,
    BLOCKED,
    REQUIRES_CONFIRMATION,
    CLOUD_NOT_READY,
    FAILED
}

data class PhotoAiExecutionResult(
    val status: PhotoAiExecutionStatus,
    val toolId: PhotoAiToolId,
    val message: String,
    val bitmap: Bitmap? = null,
    val exportUri: Uri? = null
)

class PhotoAiActionExecutor(
    private val context: Context,
    private val permissions: PhotoAiPermissionSet
) {
    private val cloudClient = PhotoAiCloudClient(context)

    suspend fun execute(
        action: PhotoAiAction,
        input: Bitmap,
        confirmHighRisk: Boolean = false
    ): PhotoAiExecutionResult {
        val startedAt = System.currentTimeMillis()
        AppTelemetry.logEvent(
            "photo_action_start",
            mapOf("tool" to action.toolId.key)
        )
        val manifest = PhotoAiToolCatalog.findById(action.toolId)
            ?: return finalizeExecution(
                action = action,
                startedAt = startedAt,
                result = PhotoAiExecutionResult(
                status = PhotoAiExecutionStatus.BLOCKED,
                toolId = action.toolId,
                message = "Tool nicht im Katalog: ${action.toolId.key}"
            )
            )

        if (!permissions.canRun(manifest)) {
            val reason = when {
                !permissions.isToolEnabled(action.toolId) ->
                    "Tool '${manifest.label}' ist deaktiviert."
                manifest.isCloudTool && !permissions.allowCloudTools ->
                    "Cloud-Tools sind deaktiviert."
                else -> "Tool '${manifest.label}' ist aktuell nicht erlaubt."
            }
            return finalizeExecution(
                action = action,
                startedAt = startedAt,
                result = PhotoAiExecutionResult(
                    status = PhotoAiExecutionStatus.BLOCKED,
                    toolId = action.toolId,
                    message = reason
                )
            )
        }

        if (permissions.requiresConfirmation(manifest) && !confirmHighRisk) {
            return finalizeExecution(
                action = action,
                startedAt = startedAt,
                result = PhotoAiExecutionResult(
                    status = PhotoAiExecutionStatus.REQUIRES_CONFIRMATION,
                    toolId = action.toolId,
                    message = "Tool '${manifest.label}' benoetigt Bestaetigung."
                )
            )
        }

        val result = when (action) {
            is PhotoAiAction.Rotate -> {
                val result = rotateBitmap(input, action.degrees)
                if (result == null) failure(action.toolId, "Rotation fehlgeschlagen.")
                else success(action.toolId, "Rotation angewendet.", result)
            }
            PhotoAiAction.Mirror -> {
                val result = mirrorBitmap(input)
                if (result == null) failure(action.toolId, "Spiegeln fehlgeschlagen.")
                else success(action.toolId, "Spiegelung angewendet.", result)
            }
            is PhotoAiAction.Crop -> {
                val result = centerCropBitmap(input, action.ratio)
                if (result == null) failure(action.toolId, "Crop fehlgeschlagen.")
                else success(action.toolId, "Crop angewendet.", result)
            }
            is PhotoAiAction.ApplyAdjustments -> {
                val result = applyAdjustments(input, action.values)
                if (result == null) failure(action.toolId, "Filter konnten nicht angewendet werden.")
                else success(action.toolId, "Filter angewendet.", result)
            }
            PhotoAiAction.AutoEnhance -> {
                val result = applyAdjustments(
                    source = input,
                    values = PhotoAiAdjustments(
                        brightness = 0.03f,
                        contrast = 1.10f,
                        saturation = 1.08f,
                        warmth = 1.03f
                    )
                )
                if (result == null) failure(action.toolId, "Auto-Enhance fehlgeschlagen.")
                else success(action.toolId, "Auto-Enhance angewendet.", result)
            }
            PhotoAiAction.BackgroundRemove -> {
                val cloudResult = cloudClient.process(action, input)
                when (cloudResult.status) {
                    PhotoAiCloudStatus.OK -> {
                        val output = cloudResult.bitmap
                        if (output == null) {
                            failure(action.toolId, "Cloud-Antwort ohne Bild.")
                        } else {
                            val provider = cloudResult.provider.ifBlank { "cloud" }
                            val timing = if (cloudResult.processingMs > 0) {
                                " (${cloudResult.processingMs} ms)"
                            } else {
                                ""
                            }
                            success(action.toolId, "Hintergrund entfernt via $provider$timing.", output)
                        }
                    }

                    PhotoAiCloudStatus.NOT_CONFIGURED -> {
                        PhotoAiExecutionResult(
                            status = PhotoAiExecutionStatus.CLOUD_NOT_READY,
                            toolId = action.toolId,
                            message = cloudResult.message
                        )
                    }

                    PhotoAiCloudStatus.ERROR -> {
                        failure(action.toolId, cloudResult.message)
                    }
                }
            }
            PhotoAiAction.UpscaleHd -> {
                val cloudResult = cloudClient.process(action, input)
                when (cloudResult.status) {
                    PhotoAiCloudStatus.OK -> {
                        val output = cloudResult.bitmap
                        if (output == null) {
                            failure(action.toolId, "Cloud-Antwort ohne Bild.")
                        } else {
                            val provider = cloudResult.provider.ifBlank { "cloud" }
                            val timing = if (cloudResult.processingMs > 0) {
                                " (${cloudResult.processingMs} ms)"
                            } else {
                                ""
                            }
                            success(action.toolId, "Upscale abgeschlossen via $provider$timing.", output)
                        }
                    }

                    PhotoAiCloudStatus.NOT_CONFIGURED -> {
                        PhotoAiExecutionResult(
                            status = PhotoAiExecutionStatus.CLOUD_NOT_READY,
                            toolId = action.toolId,
                            message = cloudResult.message
                        )
                    }

                    PhotoAiCloudStatus.ERROR -> {
                        failure(action.toolId, cloudResult.message)
                    }
                }
            }
            PhotoAiAction.ExportHd -> {
                val uri = saveBitmap(input)
                if (uri == null) {
                    failure(action.toolId, "Export fehlgeschlagen.")
                } else {
                    PhotoAiExecutionResult(
                        status = PhotoAiExecutionStatus.EXPORTED,
                        toolId = action.toolId,
                        message = "Bild gespeichert.",
                        exportUri = uri
                    )
                }
            }
        }
        return finalizeExecution(action, startedAt, result)
    }

    private fun finalizeExecution(
        action: PhotoAiAction,
        startedAt: Long,
        result: PhotoAiExecutionResult
    ): PhotoAiExecutionResult {
        val durationMs = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
        AppTelemetry.logTiming(
            name = "photo_action_timing",
            durationMs = durationMs,
            params = mapOf(
                "tool" to action.toolId.key,
                "status" to result.status.name.lowercase()
            )
        )
        when (result.status) {
            PhotoAiExecutionStatus.APPLIED,
            PhotoAiExecutionStatus.EXPORTED -> {
                AppTelemetry.logEvent(
                    "photo_action_success",
                    mapOf("tool" to action.toolId.key)
                )
            }

            PhotoAiExecutionStatus.REQUIRES_CONFIRMATION -> {
                AppTelemetry.logEvent(
                    "photo_action_confirmation_required",
                    mapOf("tool" to action.toolId.key)
                )
            }

            PhotoAiExecutionStatus.BLOCKED,
            PhotoAiExecutionStatus.CLOUD_NOT_READY,
            PhotoAiExecutionStatus.FAILED -> {
                AppTelemetry.logEvent(
                    "photo_action_error",
                    mapOf(
                        "tool" to action.toolId.key,
                        "status" to result.status.name.lowercase()
                    )
                )
            }
        }
        return result
    }

    private fun success(toolId: PhotoAiToolId, message: String, bitmap: Bitmap): PhotoAiExecutionResult {
        return PhotoAiExecutionResult(
            status = PhotoAiExecutionStatus.APPLIED,
            toolId = toolId,
            message = message,
            bitmap = bitmap
        )
    }

    private fun failure(toolId: PhotoAiToolId, message: String): PhotoAiExecutionResult {
        return PhotoAiExecutionResult(
            status = PhotoAiExecutionStatus.FAILED,
            toolId = toolId,
            message = message
        )
    }

    private fun rotateBitmap(source: Bitmap, degrees: Float): Bitmap? {
        return runCatching {
            val matrix = Matrix().apply { postRotate(degrees) }
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        }.getOrNull()
    }

    private fun mirrorBitmap(source: Bitmap): Bitmap? {
        return runCatching {
            val matrix = Matrix().apply { preScale(-1f, 1f) }
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        }.getOrNull()
    }

    private fun centerCropBitmap(source: Bitmap, targetRatio: Float): Bitmap? {
        if (targetRatio <= 0f) return null
        return runCatching {
            val srcRatio = source.width.toFloat() / source.height.toFloat()
            val (cropW, cropH) = if (srcRatio > targetRatio) {
                (source.height * targetRatio).toInt() to source.height
            } else {
                source.width to (source.width / targetRatio).toInt()
            }
            val safeW = cropW.coerceIn(1, source.width)
            val safeH = cropH.coerceIn(1, source.height)
            val x = ((source.width - safeW) / 2).coerceAtLeast(0)
            val y = ((source.height - safeH) / 2).coerceAtLeast(0)
            Bitmap.createBitmap(source, x, y, safeW, safeH)
        }.getOrNull()
    }

    private fun applyAdjustments(source: Bitmap, values: PhotoAiAdjustments): Bitmap? {
        return runCatching {
            val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(buildColorMatrix(values))
            }
            Canvas(output).drawBitmap(source, 0f, 0f, paint)
            output
        }.getOrNull()
    }

    private fun buildColorMatrix(values: PhotoAiAdjustments): ColorMatrix {
        val contrast = values.contrast
        val contrastShift = (0.5f - 0.5f * contrast) * 255f
        val brightnessShift = values.brightness * 255f
        val scaleR = values.warmth.coerceIn(0.6f, 1.8f)
        val scaleB = (2f - values.warmth).coerceIn(0.6f, 1.8f)

        val saturationMatrix = ColorMatrix().apply {
            setSaturation(values.saturation.coerceIn(0f, 2f))
        }
        val warmthMatrix = ColorMatrix(
            floatArrayOf(
                scaleR, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, scaleB, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        val contrastBrightnessMatrix = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, contrastShift + brightnessShift,
                0f, contrast, 0f, 0f, contrastShift + brightnessShift,
                0f, 0f, contrast, 0f, contrastShift + brightnessShift,
                0f, 0f, 0f, 1f, 0f
            )
        )

        return ColorMatrix().apply {
            postConcat(saturationMatrix)
            postConcat(warmthMatrix)
            postConcat(contrastBrightnessMatrix)
        }
    }

    private fun saveBitmap(bitmap: Bitmap): Uri? {
        val resolver = context.contentResolver
        val fileName = "bamachat_edit_${System.currentTimeMillis()}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/BamaChat")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { outputStream ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)) {
                    throw IOException("Bitmap compress failed")
                }
            } ?: throw IOException("Output stream unavailable")

            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } catch (_: Exception) {
            resolver.delete(uri, null, null)
            null
        }
    }
}

fun buildPhotoAiPreviewColorMatrix(values: PhotoAiAdjustments): ColorMatrix {
    val contrast = values.contrast
    val contrastShift = (0.5f - 0.5f * contrast) * 255f
    val brightnessShift = values.brightness * 255f
    val scaleR = values.warmth.coerceIn(0.6f, 1.8f)
    val scaleB = (2f - values.warmth).coerceIn(0.6f, 1.8f)

    val saturationMatrix = ColorMatrix().apply {
        setSaturation(values.saturation.coerceIn(0f, 2f))
    }
    val warmthMatrix = ColorMatrix(
        floatArrayOf(
            scaleR, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, scaleB, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    )
    val contrastBrightnessMatrix = ColorMatrix(
        floatArrayOf(
            contrast, 0f, 0f, 0f, contrastShift + brightnessShift,
            0f, contrast, 0f, 0f, contrastShift + brightnessShift,
            0f, 0f, contrast, 0f, contrastShift + brightnessShift,
            0f, 0f, 0f, 1f, 0f
        )
    )

    return ColorMatrix().apply {
        postConcat(saturationMatrix)
        postConcat(warmthMatrix)
        postConcat(contrastBrightnessMatrix)
    }
}
