package com.example.bamachat.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.example.bamachat.data.api.WeatherApiService
import com.example.bamachat.data.model.LinkPreview
import com.google.android.gms.location.LocationServices
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
        val urlRegex = """(https?://[\w-]+(\.[\w-]+)+(/[^\s]*)?)""".toRegex()
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

    /**
     * Erkennt ob ein Text nach Wetter fragt (DE + EN).
     */
    fun isWeatherQuery(text: String): Boolean {
        val lower = text.lowercase()
        val weatherKeywords = listOf(
            "wetter", "regen", "regnet", "schnee", "schneit", "sonne", "sonnig",
            "temperatur", "kalt", "warm", "heiß", "draußen",
            "weather", "rain", "snow", "temperature", "forecast", "outside"
        )
        return weatherKeywords.any { lower.contains(it) }
    }

    @SuppressLint("MissingPermission") // Permission is checked manually above via ContextCompat.checkSelfPermission
    suspend fun getWeatherData(): String? = withContext(Dispatchers.IO) {
        try {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return@withContext null
            }

            val location = fusedLocationClient.lastLocation.await() ?: return@withContext null
            
            val retrofit = retrofit2.Retrofit.Builder()
                .baseUrl("https://api.open-meteo.com/")
                .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
                .build()
            
            val weatherApi = retrofit.create(WeatherApiService::class.java)
            val response = weatherApi.getWeather(location.latitude, location.longitude)
            
            val weatherDesc = when (response.current_weather.weathercode) {
                0 -> "Klarer Himmel"
                1, 2, 3 -> "Leicht bewölkt"
                45, 48 -> "Nebel"
                51, 53, 55 -> "Nieselregen"
                61, 63, 65 -> "Regen"
                71, 73, 75 -> "Schneefall"
                95 -> "Gewitter"
                else -> "Unbekannt"
            }
            
            "Aktuelles Wetter: $weatherDesc bei ${response.current_weather.temperature}°C."
        } catch (e: Exception) {
            null
        }
    }
}
