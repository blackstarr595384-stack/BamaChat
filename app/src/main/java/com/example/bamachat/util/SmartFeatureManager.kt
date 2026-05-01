package com.example.bamachat.util

import android.content.Context
import com.example.bamachat.data.model.LinkPreview
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

class SmartFeatureManager(private val context: Context) {

    private val languageIdentifier = LanguageIdentification.getClient()

    suspend fun translateText(text: String, targetLanguage: String = TranslateLanguage.GERMAN): String? = withContext(Dispatchers.IO) {
        try {
            val sourceLangCode = languageIdentifier.identifyLanguage(text).await()
            if (sourceLangCode == "und" || sourceLangCode == targetLanguage) return@withContext null

            val options = TranslatorOptions.Builder()
                .setSourceLanguage(sourceLangCode)
                .setTargetLanguage(targetLanguage)
                .build()
            
            val translator = Translation.getClient(options)
            val conditions = DownloadConditions.Builder().requireWifi().build()
            
            translator.downloadModelIfNeeded(conditions).await()
            val result = translator.translate(text).await()
            translator.close()
            result
        } catch (e: Exception) {
            null
        }
    }

    suspend fun fetchLinkPreview(url: String): LinkPreview? = withContext(Dispatchers.IO) {
        try {
            val doc = Jsoup.connect(url).timeout(5000).get()
            val title = doc.select("meta[property=og:title]").attr("content").ifBlank { doc.title() }
            val description = doc.select("meta[property=og:description]").attr("content").ifBlank {
                doc.select("meta[name=description]").attr("content")
            }
            val image = doc.select("meta[property=og:image]").attr("content")
            
            LinkPreview(
                title = title,
                description = description,
                imageUrl = image,
                url = url
            )
        } catch (e: Exception) {
            null
        }
    }

    fun extractUrl(text: String): String? {
        val urlRegex = "(https?://[\\\\w-]+(\\\\.[\\\\w-]+)+(/[\\\\w- ./?%&=]*)?)".toRegex()
        return urlRegex.find(text)?.value
    }

    fun analyzeSentiment(text: String): String {
        val positiveWords = listOf("super", "gut", "toll", "freue", "danke", "love", "nice", "cool")
        val negativeWords = listOf("schlecht", "doof", "ärger", "traurig", "mist", "hate", "bad")
        
        val lowerText = text.lowercase()
        val posCount = positiveWords.count { lowerText.contains(it) }
        val negCount = negativeWords.count { lowerText.contains(it) }
        
        return when {
            posCount > negCount -> "positive"
            negCount > posCount -> "negative"
            else -> "neutral"
        }
    }
}
