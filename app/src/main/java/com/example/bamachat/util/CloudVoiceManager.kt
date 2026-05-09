package com.example.bamachat.util

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class CloudVoiceManager(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()
    private var mediaPlayer: MediaPlayer? = null
    private var tempAudioFile: File? = null

    suspend fun speakWithElevenLabs(
        text: String,
        apiKey: String,
        voiceId: String,
        modelId: String = "eleven_multilingual_v2"
    ): Boolean {
        val cleanText = text.trim()
        if (cleanText.isBlank() || apiKey.isBlank() || voiceId.isBlank()) return false

        val audioBytes = fetchElevenLabsAudio(
            text = cleanText,
            apiKey = apiKey,
            voiceId = voiceId,
            modelId = modelId
        ) ?: return false

        return playAudioBytes(audioBytes)
    }

    suspend fun stop() = withContext(Dispatchers.Main) {
        mediaPlayer?.let { player ->
            runCatching { player.stop() }
            runCatching { player.release() }
        }
        mediaPlayer = null
        clearTempAudioFile()
    }

    fun release() {
        runCatching { mediaPlayer?.release() }
        mediaPlayer = null
        clearTempAudioFile()
    }

    private suspend fun fetchElevenLabsAudio(
        text: String,
        apiKey: String,
        voiceId: String,
        modelId: String
    ): ByteArray? = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("text", text)
            put("model_id", modelId)
            put("voice_settings", JSONObject().apply {
                put("stability", 0.34)
                put("similarity_boost", 0.84)
                put("style", 0.18)
                put("use_speaker_boost", true)
            })
        }
        val request = Request.Builder()
            .url("https://api.elevenlabs.io/v1/text-to-speech/$voiceId?output_format=mp3_44100_128")
            .addHeader("xi-api-key", apiKey)
            .addHeader("Accept", "audio/mpeg")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                response.body?.bytes()
            }
        }.getOrNull()
    }

    private suspend fun playAudioBytes(bytes: ByteArray): Boolean = withContext(Dispatchers.Main) {
        val audioFile = withContext(Dispatchers.IO) {
            File.createTempFile("bamachat_voice_", ".mp3", context.cacheDir).apply {
                writeBytes(bytes)
            }
        }

        runCatching {
            runCatching { mediaPlayer?.release() }
            clearTempAudioFile()
            tempAudioFile = audioFile
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(audioFile.absolutePath)
                setOnCompletionListener {
                    runCatching { release() }
                }
                setOnErrorListener { _, _, _ ->
                    runCatching { release() }
                    true
                }
                prepare()
                start()
            }
            true
        }.getOrElse {
            clearTempAudioFile()
            false
        }
    }

    private fun clearTempAudioFile() {
        val file = tempAudioFile ?: return
        runCatching {
            if (file.exists()) file.delete()
        }
        tempAudioFile = null
    }
}
