package com.example.bamachat.service

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.core.content.ContextCompat
import com.example.bamachat.data.ApiClient
import com.example.bamachat.data.OpenRouterChatRequest
import com.example.bamachat.data.OpenRouterImageUrl
import com.example.bamachat.data.OpenRouterMessage
import com.example.bamachat.data.OpenRouterVisionChatRequest
import com.example.bamachat.data.OpenRouterVisionContentPart
import com.example.bamachat.data.OpenRouterVisionMessage
import com.example.bamachat.ui.viewmodel.ApiManager
import com.example.bamachat.util.AudioTranscriptionManager
import com.example.bamachat.util.MultimodalAsset
import com.example.bamachat.util.MultimodalProcessor
import com.example.bamachat.util.VideoKeyframeExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.util.Locale

class MediaService(
    private val app: Application,
    private val apiManager: ApiManager,
    private val knowledgeService: KnowledgeService
) {
    suspend fun analyzeImage(
        systemPrompt: String,
        userText: String,
        imageUri: Uri,
        enableOcr: Boolean = true
    ): MediaAnalysisResult {
        val appContext = app.applicationContext
        val userInstruction = userText.ifBlank { "Beschreibe den Bildinhalt präzise und strukturiert." }
        val ocrContext = if (enableOcr) {
            MultimodalProcessor.extractImageText(appContext, imageUri).takeIf { it.isNotBlank() }
        } else null

        val analysisText = buildString {
            append("Analysiere dieses Bild präzise und antworte auf Deutsch.")
            append("\n\nNutzerhinweis:\n")
            append(userInstruction)
            if (!ocrContext.isNullOrBlank()) {
                append("\n\nZusätzlicher OCR-Text aus dem Bild (kann Fehler enthalten):\n")
                append(ocrContext.take(3_500))
            }
        }

        val imageDataUrl = encodeImageAsDataUrl(imageUri)
        if (imageDataUrl == null) {
            return MediaAnalysisResult(success = false, error = "Bild konnte nicht kodiert werden.")
        }

        val result = apiManager.analyzeImage(systemPrompt, analysisText, imageDataUrl)
        return if (result.success) {
            MediaAnalysisResult(success = true, content = result.content)
        } else {
            MediaAnalysisResult(success = false, error = "Bildanalyse fehlgeschlagen: ${result.error}")
        }
    }

    suspend fun transcribeAudio(uri: Uri, groqApiKey: String): String? {
        if (groqApiKey.isBlank()) return null
        val manager = AudioTranscriptionManager(app)
        return manager.transcribeWithGroq(uri, groqApiKey)
    }

    suspend fun summarizeVideo(uri: Uri): String {
        return VideoKeyframeExtractor.summarize(app, uri)
    }

    data class ImageGenerationRequest(
        val displayPrompt: String,
        val candidateUrls: List<String>
    )

    fun buildImageGenerationRequest(userPrompt: String): ImageGenerationRequest {
        val cleanPrompt = userPrompt.replace(Regex("\\s+"), " ").trim()
        val enhancedPrompt = buildEnhancedImagePrompt(cleanPrompt)
        val encodedPrompt = URLEncoder.encode(enhancedPrompt, "UTF-8")
        val seed = (10_000..99_999).random()
        val (width, height) = chooseImageResolution(cleanPrompt)
        val models = listOf("flux", "flux-realism", "turbo")
        val base = "https://image.pollinations.ai/prompt/$encodedPrompt"
        val urls = models.map { model ->
            "$base?width=$width&height=$height&seed=$seed&model=$model&nologo=true&enhance=true"
        }
        return ImageGenerationRequest(displayPrompt = cleanPrompt, candidateUrls = urls)
    }

    fun isImageQuery(text: String): Boolean {
        val lower = text.lowercase()
        val keywords = listOf(
            "bild", "image", "foto", "photo", "zeichne", "draw",
            "generier", "generat", "erstelle", "create", "male", "paint"
        )
        return keywords.any { lower.contains(it) }
    }

    suspend fun importMultimodal(uri: Uri, groqApiKey: String = ""): String? {
        val appContext = app.applicationContext
        val asset = MultimodalProcessor.parse(appContext, uri)
        return when (asset.category) {
            MultimodalAsset.Category.IMAGE -> "image"
            MultimodalAsset.Category.AUDIO -> {
                val transcript = transcribeAudio(uri, groqApiKey)
                if (!transcript.isNullOrBlank()) {
                    knowledgeService.ingestText(asset.title, "audio_transcript", transcript)
                }
                "audio_imported"
            }
            MultimodalAsset.Category.VIDEO -> {
                val transcript = transcribeAudio(uri, groqApiKey)
                if (!transcript.isNullOrBlank()) {
                    knowledgeService.ingestText(asset.title, "video_transcript", transcript)
                }
                val keyframeSummary = summarizeVideo(uri)
                if (keyframeSummary.isNotBlank()) {
                    knowledgeService.ingestText(asset.title, "video_keyframes", keyframeSummary)
                }
                "video_imported"
            }
            MultimodalAsset.Category.PDF, MultimodalAsset.Category.DOCX,
            MultimodalAsset.Category.XLSX, MultimodalAsset.Category.TEXT_DOC -> {
                val title = knowledgeService.importDocument(uri)
                if (title != null) "document_imported:$title" else null
            }
            else -> null
        }
    }

    private fun buildEnhancedImagePrompt(prompt: String): String {
        val lower = prompt.lowercase(Locale.getDefault())
        val baseStyle = when {
            lower.contains("logo") -> "clean vector logo, minimal, sharp edges, white background"
            lower.contains("portrait") || lower.contains("gesicht") ->
                "portrait photography, natural skin texture, cinematic rim light, 85mm lens"
            lower.contains("anime") || lower.contains("manga") ->
                "high quality anime art, dynamic composition, crisp line art"
            lower.contains("produkt") || lower.contains("product") ->
                "studio product photography, softbox lighting, high detail"
            else -> "ultra detailed, professional composition, realistic lighting, high contrast, sharp focus"
        }
        return "$prompt. Style: $baseStyle. Avoid: blurry, low quality, distorted anatomy, artifacts, extra fingers, unreadable text."
    }

    private fun chooseImageResolution(prompt: String): Pair<Int, Int> {
        val lower = prompt.lowercase(Locale.getDefault())
        return when {
            lower.contains("banner") -> 1536 to 896
            lower.contains("wallpaper") -> 1344 to 768
            lower.contains("portrait") || lower.contains("hochformat") -> 896 to 1344
            else -> 1024 to 1024
        }
    }

    private suspend fun encodeImageAsDataUrl(uri: Uri): String? {
        val bitmap = decodeBitmapFromUri(uri) ?: return null
        val output = ByteArrayOutputStream()
        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)) return null
        val base64 = Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        return "data:image/jpeg;base64,$base64"
    }

    private fun decodeBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            val contentResolver = app.contentResolver
            val source = ImageDecoder.createSource(contentResolver, uri)
            val bitmap = ImageDecoder.decodeBitmap(source)
            val maxSide = 1600
            if (maxOf(bitmap.width, bitmap.height) <= maxSide) return bitmap
            val scale = maxSide.toFloat() / maxOf(bitmap.width, bitmap.height).toFloat()
            val scaledWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
            val scaledHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
        } catch (_: Exception) { null }
    }

    data class MediaAnalysisResult(
        val success: Boolean,
        val content: String = "",
        val error: String = ""
    )
}

private typealias MultimodalAsset = com.example.bamachat.util.MultimodalAsset
