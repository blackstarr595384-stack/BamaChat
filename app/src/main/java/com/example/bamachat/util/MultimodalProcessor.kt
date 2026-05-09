package com.example.bamachat.util

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

data class MultimodalAsset(
    val title: String,
    val category: Category,
    val extractedText: String = "",
    val sourceType: String = "unknown"
) {
    enum class Category {
        IMAGE,
        TEXT_DOC,
        DOCX,
        XLSX,
        PDF,
        AUDIO,
        VIDEO,
        UNKNOWN
    }
}

object MultimodalProcessor {
    suspend fun parse(context: Context, uri: Uri): MultimodalAsset = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val title = queryDisplayName(resolver, uri) ?: "Datei"
        val mime = resolver.getType(uri).orEmpty().lowercase(Locale.getDefault())
        val ext = title.substringAfterLast('.', "").lowercase(Locale.getDefault())

        val category = when {
            mime.startsWith("image/") || ext in setOf("png", "jpg", "jpeg", "webp") -> MultimodalAsset.Category.IMAGE
            mime.startsWith("audio/") || ext in setOf("mp3", "wav", "m4a", "ogg") -> MultimodalAsset.Category.AUDIO
            mime.startsWith("video/") || ext in setOf("mp4", "mov", "mkv", "webm") -> MultimodalAsset.Category.VIDEO
            mime.contains("pdf") || ext == "pdf" -> MultimodalAsset.Category.PDF
            ext == "docx" -> MultimodalAsset.Category.DOCX
            ext == "xlsx" -> MultimodalAsset.Category.XLSX
            mime.startsWith("text/") || ext in setOf("txt", "md", "csv", "json") -> MultimodalAsset.Category.TEXT_DOC
            else -> MultimodalAsset.Category.UNKNOWN
        }

        when (category) {
            MultimodalAsset.Category.TEXT_DOC -> {
                val text = resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
                MultimodalAsset(title, category, sanitizeText(text), "text")
            }
            MultimodalAsset.Category.DOCX -> {
                val text = extractDocxText(resolver, uri)
                MultimodalAsset(title, category, sanitizeText(text), "docx")
            }
            MultimodalAsset.Category.XLSX -> {
                val text = extractXlsxText(resolver, uri)
                MultimodalAsset(title, category, sanitizeText(text), "xlsx")
            }
            MultimodalAsset.Category.PDF -> {
                var text = extractPdfText(resolver, uri)
                var sourceType = "pdf"
                if (text.length < 40) {
                    val ocrText = extractPdfTextWithOcr(context, resolver, uri)
                    if (ocrText.isNotBlank()) {
                        text = ocrText
                        sourceType = "pdf_ocr"
                    }
                }
                MultimodalAsset(title, category, sanitizeText(text), sourceType)
            }
            else -> MultimodalAsset(title, category, "", category.name.lowercase(Locale.getDefault()))
        }
    }

    private fun extractDocxText(resolver: ContentResolver, uri: Uri): String {
        return resolver.openInputStream(uri)?.use { stream ->
            ZipInputStream(BufferedInputStream(stream)).use { zip ->
                var entry: ZipEntry?
                while (zip.nextEntry.also { entry = it } != null) {
                    val name = entry?.name ?: continue
                    if (name == "word/document.xml") {
                        val xml = zip.bufferedReader().readText()
                        return xml.replace(Regex("<[^>]+>"), " ")
                    }
                }
                ""
            }
        }.orEmpty()
    }

    private fun extractXlsxText(resolver: ContentResolver, uri: Uri): String {
        return resolver.openInputStream(uri)?.use { stream ->
            ZipInputStream(BufferedInputStream(stream)).use { zip ->
                val collected = StringBuilder()
                var entry: ZipEntry?
                while (zip.nextEntry.also { entry = it } != null) {
                    val name = entry?.name ?: continue
                    if (name.startsWith("xl/sharedStrings") || name.startsWith("xl/worksheets/sheet")) {
                        val xml = zip.bufferedReader().readText()
                        collected.append(' ')
                        collected.append(xml.replace(Regex("<[^>]+>"), " "))
                    }
                }
                collected.toString()
            }
        }.orEmpty()
    }

    private fun extractPdfText(resolver: ContentResolver, uri: Uri): String {
        return runCatching {
            resolver.openInputStream(uri)?.use { input ->
                PDDocument.load(input).use { document ->
                    PDFTextStripper().getText(document)
                }
            }.orEmpty()
        }.getOrDefault("")
    }

    private suspend fun extractPdfTextWithOcr(
        context: Context,
        resolver: ContentResolver,
        uri: Uri
    ): String {
        val localFile = copyUriToCacheFile(context, resolver, uri, ".pdf") ?: return ""
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            val pfd = ParcelFileDescriptor.open(localFile, ParcelFileDescriptor.MODE_READ_ONLY)
            pfd.use { fd ->
                PdfRenderer(fd).use { renderer ->
                    val maxPages = minOf(renderer.pageCount, 3)
                    val text = StringBuilder()
                    for (index in 0 until maxPages) {
                        renderer.openPage(index).use { page ->
                            val scale = 2.0f
                            val width = (page.width * scale).toInt().coerceIn(480, 1800)
                            val height = (page.height * scale).toInt().coerceIn(480, 2400)
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val result = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
                            if (result.text.isNotBlank()) {
                                text.append(' ').append(result.text)
                            }
                            bitmap.recycle()
                        }
                    }
                    text.toString()
                }
            }
        } catch (_: Exception) {
            ""
        } finally {
            recognizer.close()
            runCatching { localFile.delete() }
        }
    }

    private fun copyUriToCacheFile(
        context: Context,
        resolver: ContentResolver,
        uri: Uri,
        suffix: String
    ): File? {
        return runCatching {
            val file = File.createTempFile("bamachat_mm_", suffix, context.cacheDir)
            resolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return null
            file
        }.getOrNull()
    }

    suspend fun extractImageText(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return@withContext try {
            val image = InputImage.fromFilePath(context, uri)
            val result = recognizer.process(image).await()
            sanitizeText(result.text).take(8_000)
        } catch (_: Exception) {
            ""
        } finally {
            recognizer.close()
        }
    }

    suspend fun detectLanguageCode(text: String): String? = withContext(Dispatchers.IO) {
        val normalized = text.trim()
        if (normalized.length < 12) return@withContext null

        val options = LanguageIdentificationOptions.Builder()
            .setConfidenceThreshold(0.5f)
            .build()
        val identifier = LanguageIdentification.getClient(options)
        return@withContext try {
            val code = identifier.identifyLanguage(normalized.take(5_000)).await()
            code.takeIf { it.isNotBlank() && it != "und" }?.lowercase(Locale.ROOT)
        } catch (_: Exception) {
            null
        } finally {
            identifier.close()
        }
    }

    private fun sanitizeText(text: String): String =
        text.replace(Regex("\\s+"), " ").trim().take(200_000)

    private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? {
        return runCatching {
            val cursor = resolver.query(uri, null, null, null, null)
            cursor?.use {
                val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && it.moveToFirst()) it.getString(idx) else null
            }
        }.getOrNull()
    }
}
