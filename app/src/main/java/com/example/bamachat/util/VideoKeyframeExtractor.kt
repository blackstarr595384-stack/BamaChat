package com.example.bamachat.util

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

object VideoKeyframeExtractor {
    suspend fun summarize(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.coerceAtLeast(0L)
                ?: 0L
            if (durationMs <= 0L) return@withContext ""

            val sampleCount = 5
            val stepMs = (durationMs / sampleCount).coerceAtLeast(800L)
            val snippets = mutableListOf<String>()
            var t = 0L
            while (t < durationMs && snippets.size < sampleCount) {
                val frame = retriever.getFrameAtTime(t * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (frame != null) {
                    val small = scaleDown(frame, maxSide = 1280)
                    val text = runCatching {
                        recognizer.process(InputImage.fromBitmap(small, 0)).await().text
                    }.getOrDefault("").trim()
                    if (text.isNotBlank()) {
                        snippets += text.replace(Regex("\\s+"), " ").take(180)
                    }
                    if (small != frame) small.recycle()
                    frame.recycle()
                }
                t += stepMs
            }

            buildString {
                append("Video-Metadaten: Dauer ${durationMs / 1000}s. ")
                if (snippets.isEmpty()) {
                    append("Keine klaren Texteinblendungen in Keyframes erkannt.")
                } else {
                    append("Erkannte Keyframe-Texte: ")
                    snippets.distinct().take(5).forEachIndexed { index, snippet ->
                        append("#${index + 1}: $snippet ")
                    }
                }
            }.trim()
        } catch (_: Exception) {
            ""
        } finally {
            runCatching { retriever.release() }
            recognizer.close()
        }
    }

    private fun scaleDown(bitmap: Bitmap, maxSide: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val side = maxOf(w, h)
        if (side <= maxSide) return bitmap
        val scale = maxSide.toFloat() / side.toFloat()
        val nw = (w * scale).toInt().coerceAtLeast(1)
        val nh = (h * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, nw, nh, true)
    }
}
