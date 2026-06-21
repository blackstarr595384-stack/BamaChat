package com.example.bamachat.util

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class CloudVoiceManager(private val context: Context) {
    enum class VoiceStyle {
        NATURAL,
        CLEAR
    }

    enum class Provider(
        val storageValue: String,
        val displayName: String,
        val docsUrl: String
    ) {
        ELEVENLABS(
            storageValue = "elevenlabs",
            displayName = "ElevenLabs",
            docsUrl = "https://elevenlabs.io/docs/api-reference/text-to-speech/convert"
        ),
        PIPER(
            storageValue = "piper",
            displayName = "Piper",
            docsUrl = "https://github.com/OHF-Voice/piper1-gpl/blob/main/docs/API_HTTP.md"
        );

        companion object {
            fun fromStorage(value: String?): Provider {
                return entries.firstOrNull { it.storageValue == value?.trim()?.lowercase() } ?: ELEVENLABS
            }
        }
    }

    data class CloudVoiceConfig(
        val provider: Provider,
        val apiKey: String = "",
        val voiceId: String = "",
        val modelId: String = "",
        val piperEndpoint: String = "",
        val piperVoiceName: String = ""
    )

    private data class AudioPayload(
        val bytes: ByteArray,
        val fileExtension: String
    )

    companion object {
        private const val TAG = "CloudVoiceManager"
        private const val DEFAULT_VOICE_ID = "JBFqnCBsd6RMkjVDRZzb"
        private const val DEFAULT_MODEL_ID = "eleven_multilingual_v2"
        private val SURROUNDING_QUOTES = charArrayOf('"', '\'', '`', '\u2018', '\u2019', '\u201C', '\u201D')
        private val API_KEY_PREFIX_REGEX = Regex("(?i)^\\s*(bearer|xi-api-key|x-api-key|api-key)\\s*[:=]?\\s*")
        private val VOICE_ID_PREFIX_REGEX = Regex("(?i)^\\s*voice[_ -]?id\\s*[:=]?\\s*")
        private val MODEL_ID_PREFIX_REGEX = Regex("(?i)^\\s*model[_ -]?id\\s*[:=]?\\s*")
        private val ENDPOINT_PREFIX_REGEX = Regex("(?i)^\\s*(endpoint|url|uri|host|piper[_ -]?endpoint)\\s*[:=]?\\s*")
        private val PIPER_VOICE_PREFIX_REGEX = Regex("(?i)^\\s*(voice[_ -]?name|piper[_ -]?voice|voice)\\s*[:=]?\\s*")
        private val WHITESPACE_REGEX = Regex("\\s+")

        fun resolveCloudVoiceConfig(
            providerValue: String,
            elevenLabsApiKey: String,
            elevenLabsVoiceId: String,
            elevenLabsModelId: String,
            piperEndpoint: String,
            piperVoiceName: String
        ): CloudVoiceConfig? {
            return when (Provider.fromStorage(providerValue)) {
                Provider.ELEVENLABS -> resolveElevenLabsConfig(
                    apiKey = elevenLabsApiKey,
                    voiceId = elevenLabsVoiceId,
                    modelId = elevenLabsModelId
                )
                Provider.PIPER -> resolvePiperConfig(
                    endpoint = piperEndpoint,
                    voiceName = piperVoiceName
                )
            }
        }

        fun resolveElevenLabsConfig(
            apiKey: String,
            voiceId: String,
            modelId: String = DEFAULT_MODEL_ID
        ): CloudVoiceConfig? {
            val cleanApiKey = normalizeElevenLabsApiKey(apiKey)
            if (cleanApiKey.isBlank()) return null

            return CloudVoiceConfig(
                provider = Provider.ELEVENLABS,
                apiKey = cleanApiKey,
                voiceId = normalizeElevenLabsVoiceId(voiceId).ifBlank { DEFAULT_VOICE_ID },
                modelId = normalizeElevenLabsModelId(modelId).ifBlank { DEFAULT_MODEL_ID }
            )
        }

        fun resolvePiperConfig(
            endpoint: String,
            voiceName: String = ""
        ): CloudVoiceConfig? {
            val cleanEndpoint = normalizePiperEndpoint(endpoint)
            if (cleanEndpoint.isBlank()) return null

            return CloudVoiceConfig(
                provider = Provider.PIPER,
                piperEndpoint = cleanEndpoint,
                piperVoiceName = normalizePiperVoiceName(voiceName)
            )
        }

        private fun normalizeElevenLabsApiKey(raw: String): String {
            return unwrapPastedValue(raw)
                .replace(API_KEY_PREFIX_REGEX, "")
                .trim()
                .trim(*SURROUNDING_QUOTES)
                .replace(WHITESPACE_REGEX, "")
        }

        private fun normalizeElevenLabsVoiceId(raw: String): String {
            return unwrapPastedValue(raw)
                .replace(VOICE_ID_PREFIX_REGEX, "")
                .trim()
                .trim(*SURROUNDING_QUOTES)
                .replace(WHITESPACE_REGEX, "")
        }

        private fun normalizeElevenLabsModelId(raw: String): String {
            return unwrapPastedValue(raw)
                .replace(MODEL_ID_PREFIX_REGEX, "")
                .trim()
                .trim(*SURROUNDING_QUOTES)
                .replace(WHITESPACE_REGEX, "")
        }

        private fun normalizePiperEndpoint(raw: String): String {
            val withoutPrefix = unwrapPastedValue(raw)
                .replace(ENDPOINT_PREFIX_REGEX, "")
                .trim()
                .trim(*SURROUNDING_QUOTES)
                .trim()

            if (withoutPrefix.isBlank()) return ""

            val withScheme = if (withoutPrefix.contains("://")) {
                withoutPrefix
            } else {
                "http://$withoutPrefix"
            }

            return withScheme.trimEnd('/')
        }

        private fun normalizePiperVoiceName(raw: String): String {
            return unwrapPastedValue(raw)
                .replace(PIPER_VOICE_PREFIX_REGEX, "")
                .trim()
                .trim(*SURROUNDING_QUOTES)
                .trim()
        }

        private fun unwrapPastedValue(raw: String): String {
            return raw
                .trim()
                .trim(*SURROUNDING_QUOTES)
                .replace("\uFEFF", "")
                .replace('\u00A0', ' ')
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private var mediaPlayer: MediaPlayer? = null
    private var tempAudioFile: File? = null

    @Volatile
    private var activeRequest: Call? = null

    @Volatile
    private var isPlayingAudio: Boolean = false

    @Volatile
    private var lastError: String? = null

    fun isSpeaking(): Boolean = isPlayingAudio

    fun lastErrorMessage(): String? = lastError

    suspend fun speak(
        text: String,
        config: CloudVoiceConfig,
        voiceStyle: VoiceStyle = VoiceStyle.NATURAL
    ): Boolean {
        lastError = null

        val cleanText = text.trim()
        if (cleanText.isBlank()) {
            setError("Kein Text für Sprachausgabe vorhanden.")
            return false
        }

        cancelActiveRequest()
        val payload = when (config.provider) {
            Provider.ELEVENLABS -> fetchElevenLabsAudio(
                text = cleanText,
                apiKey = config.apiKey,
                voiceId = config.voiceId,
                modelId = config.modelId,
                voiceStyle = voiceStyle
            )
            Provider.PIPER -> fetchPiperAudio(
                text = cleanText,
                endpoint = config.piperEndpoint,
                voiceName = config.piperVoiceName
            )
        } ?: return false

        return playAudioBytes(payload.bytes, payload.fileExtension)
    }

    suspend fun stop() = withContext(Dispatchers.Main) {
        mediaPlayer?.let { player ->
            runCatching { player.stop() }
            runCatching { player.release() }
        }
        cancelActiveRequest()
        mediaPlayer = null
        isPlayingAudio = false
        clearTempAudioFile()
    }

    fun release() {
        runCatching { mediaPlayer?.release() }
        cancelActiveRequest()
        mediaPlayer = null
        isPlayingAudio = false
        clearTempAudioFile()
    }

    private suspend fun fetchElevenLabsAudio(
        text: String,
        apiKey: String,
        voiceId: String,
        modelId: String,
        voiceStyle: VoiceStyle
    ): AudioPayload? = withContext(Dispatchers.IO) {
        val (stability, similarityBoost, style) = when (voiceStyle) {
            VoiceStyle.NATURAL -> Triple(0.45, 0.86, 0.22)
            VoiceStyle.CLEAR -> Triple(0.68, 0.78, 0.06)
        }

        val payload = JSONObject().apply {
            put("text", text)
            put("model_id", modelId)
            put("voice_settings", JSONObject().apply {
                put("stability", stability)
                put("similarity_boost", similarityBoost)
                put("style", style)
                put("use_speaker_boost", true)
            })
        }

        val request = Request.Builder()
            .url("https://api.elevenlabs.io/v1/text-to-speech/$voiceId?output_format=mp3_44100_128")
            .addHeader("xi-api-key", apiKey)
            .addHeader("Accept", "audio/mpeg")
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        runWithActiveRequest(request) { response, responseBody ->
            if (!response.isSuccessful) {
                val errorText = runCatching {
                    responseBody?.string().orEmpty().take(400)
                }.getOrDefault("")

                val message = mapElevenLabsHttpError(response.code, errorText)
                setError(message)
                Log.w(TAG, "$message Antwort: $errorText")
                null
            } else {
                val bytes = responseBody?.bytes()
                if (bytes == null || bytes.isEmpty()) {
                    setError("ElevenLabs hat keine Audiodaten geliefert.")
                    Log.w(TAG, "ElevenLabs Antwort war erfolgreich, aber leer.")
                    null
                } else {
                    AudioPayload(bytes = bytes, fileExtension = ".mp3")
                }
            }
        }
    }

    private suspend fun fetchPiperAudio(
        text: String,
        endpoint: String,
        voiceName: String
    ): AudioPayload? = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("text", text)
            if (voiceName.isNotBlank()) {
                put("voice", voiceName)
            }
        }

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("Accept", "audio/wav")
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        runWithActiveRequest(request) { response, responseBody ->
            if (!response.isSuccessful) {
                val errorText = runCatching {
                    responseBody?.string().orEmpty().take(400)
                }.getOrDefault("")

                val message = mapPiperHttpError(response.code, errorText)
                setError(message)
                Log.w(TAG, "$message Antwort: $errorText")
                null
            } else {
                val bytes = responseBody?.bytes()
                if (bytes == null || bytes.isEmpty()) {
                    setError("Piper hat keine Audiodaten geliefert.")
                    Log.w(TAG, "Piper Antwort war erfolgreich, aber leer.")
                    null
                } else {
                    AudioPayload(bytes = bytes, fileExtension = ".wav")
                }
            }
        }
    }

    private suspend fun runWithActiveRequest(
        request: Request,
        block: (response: Response, responseBody: ResponseBody?) -> AudioPayload?
    ): AudioPayload? = withContext(Dispatchers.IO) {
        runCatching {
            val call = client.newCall(request)
            activeRequest = call
            try {
                call.execute().use { response ->
                    block(response, response.body)
                }
            } finally {
                if (activeRequest === call) {
                    activeRequest = null
                }
            }
        }.getOrElse { throwable ->
            if (throwable is CancellationException) throw throwable
            val message = "Cloud-Voice Netzwerk-/Audiofehler: ${throwable.message ?: throwable.javaClass.simpleName}"
            setError(message)
            Log.w(TAG, message, throwable)
            null
        }
    }

    private fun mapElevenLabsHttpError(code: Int, errorText: String): String {
        val errorCode = extractErrorCode(errorText)
        val detail = extractErrorDetail(errorText)
        return when {
            errorCode == "invalid_api_key" -> "ElevenLabs API-Key ist ungültig oder falsch formatiert."
            errorCode == "voice_not_found" || code == 404 -> "ElevenLabs Voice-ID wurde nicht gefunden."
            errorCode == "model_not_found" -> "ElevenLabs Model-ID ist ungültig."
            errorCode == "too_many_concurrent_requests" -> "Zu viele gleichzeitige ElevenLabs-Anfragen. Kurz warten und erneut versuchen."
            errorCode == "system_busy" -> "ElevenLabs ist gerade ausgelastet. Bitte kurz erneut versuchen."
            errorCode == "quota_exceeded" -> "ElevenLabs Guthaben oder Kontingent ist aufgebraucht."
            code == 429 -> "ElevenLabs meldet gerade ein Limit oder zu viele gleichzeitige Anfragen."
            code == 400 -> detail?.let {
                "ElevenLabs Anfrage ungültig: $it"
            } ?: "ElevenLabs Anfrage ungültig. Prüfe API-Key, Voice-ID und Model-ID."
            code == 401 -> "ElevenLabs API-Key ist ungültig oder fehlt."
            code == 403 -> "ElevenLabs Zugriff verweigert. Prüfe Account, Abo oder Voice-Zugriff."
            else -> detail?.let {
                "ElevenLabs Fehler HTTP $code: $it"
            } ?: "ElevenLabs Fehler HTTP $code."
        }
    }

    private fun mapPiperHttpError(code: Int, errorText: String): String {
        val detail = extractErrorDetail(errorText)
        return when (code) {
            400 -> detail?.let {
                "Piper Anfrage ungültig: $it"
            } ?: "Piper Anfrage ungültig. Prüfe Endpoint und Voice-Name."
            404 -> "Piper Endpoint wurde nicht gefunden. Prüfe URL und Port."
            422 -> detail?.let {
                "Piper konnte den Text nicht verarbeiten: $it"
            } ?: "Piper konnte den Text nicht verarbeiten."
            500 -> detail?.let {
                "Piper Serverfehler: $it"
            } ?: "Piper Serverfehler."
            else -> detail?.let {
                "Piper Fehler HTTP $code: $it"
            } ?: "Piper Fehler HTTP $code."
        }
    }

    private fun extractErrorCode(errorText: String): String? {
        val json = runCatching { JSONObject(errorText) }.getOrNull() ?: return null
        return listOfNotNull(
            extractJsonText(json.opt("detail"), "status", "code"),
            extractJsonText(json.opt("error"), "status", "code"),
            json.optString("status").takeIf { it.isNotBlank() },
            json.optString("code").takeIf { it.isNotBlank() },
            json.optString("error").takeIf { it.isNotBlank() && !it.startsWith("{") }
        )
            .firstOrNull()
            ?.trim()
            ?.lowercase()
    }

    private fun extractErrorDetail(errorText: String): String? {
        val json = runCatching { JSONObject(errorText) }.getOrNull()
        if (json != null) {
            return listOfNotNull(
                extractJsonText(json.opt("detail"), "message", "detail", "reason"),
                extractJsonText(json.opt("error"), "message", "detail", "reason"),
                json.optString("message").takeIf { it.isNotBlank() },
                json.optString("detail").takeIf { it.isNotBlank() && !it.startsWith("{") }
            )
                .firstOrNull()
                ?.replace(WHITESPACE_REGEX, " ")
                ?.take(160)
        }

        return errorText
            .trim()
            .takeIf { it.isNotBlank() }
            ?.replace(WHITESPACE_REGEX, " ")
            ?.take(160)
    }

    private fun extractJsonText(value: Any?, vararg keys: String): String? {
        val json = value as? JSONObject ?: return (value as? String)?.trim()?.takeIf { it.isNotBlank() }
        return keys
            .asSequence()
            .mapNotNull { key ->
                json.optString(key).trim().takeIf { it.isNotBlank() }
            }
            .firstOrNull()
    }

    private fun cancelActiveRequest() {
        val call = activeRequest ?: return
        runCatching { call.cancel() }
        if (activeRequest === call) {
            activeRequest = null
        }
    }

    private suspend fun playAudioBytes(bytes: ByteArray, fileExtension: String): Boolean {
        val audioFile = withContext(Dispatchers.IO) {
            File.createTempFile("bamachat_voice_", fileExtension, context.cacheDir).apply {
                writeBytes(bytes)
            }
        }

        return withContext(Dispatchers.Main) {
            runCatching {
                runCatching { mediaPlayer?.stop() }
                runCatching { mediaPlayer?.release() }
                clearTempAudioFile()

                tempAudioFile = audioFile

                val player = MediaPlayer()
                mediaPlayer = player

                player.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )

                player.setDataSource(audioFile.absolutePath)

                player.setOnCompletionListener { completed ->
                    isPlayingAudio = false
                    runCatching { completed.release() }
                    if (mediaPlayer === completed) {
                        mediaPlayer = null
                        clearTempAudioFile()
                    }
                }

                player.setOnErrorListener { failedPlayer, what, extra ->
                    isPlayingAudio = false
                    setError("Audio-Playback-Fehler ($what/$extra).")
                    runCatching { failedPlayer.release() }
                    if (mediaPlayer === failedPlayer) {
                        mediaPlayer = null
                        clearTempAudioFile()
                    }
                    true
                }

                player.prepare()
                isPlayingAudio = true
                player.start()
                true
            }.getOrElse { throwable ->
                isPlayingAudio = false
                setError("Audio konnte nicht abgespielt werden: ${throwable.message ?: throwable.javaClass.simpleName}")
                Log.w(TAG, "Audio playback failed", throwable)
                clearTempAudioFile()
                false
            }
        }
    }

    private fun setError(message: String) {
        lastError = message
    }

    private fun clearTempAudioFile() {
        val file = tempAudioFile ?: return
        runCatching {
            if (file.exists()) file.delete()
        }
        tempAudioFile = null
    }
}
