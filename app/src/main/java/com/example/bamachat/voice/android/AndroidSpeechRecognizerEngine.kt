package com.example.bamachat.voice.android

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.example.bamachat.voice.SpeechInputConfig
import com.example.bamachat.voice.SpeechInputListener
import com.example.bamachat.voice.SpeechToTextEngine
import com.example.bamachat.voice.VoiceFailure
import com.example.bamachat.voice.VoiceFailureCategory
import com.example.bamachat.voice.VoiceInputProvider
import com.example.bamachat.voice.VoiceOperationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidSpeechRecognizerEngine(
    context: Context,
    private val requireOnDeviceEngine: Boolean
) : SpeechToTextEngine {
    override val provider: VoiceInputProvider = VoiceInputProvider.ANDROID

    private val appContext = context.applicationContext
    private var recognizer: SpeechRecognizer? = null
    private var currentListener: SpeechInputListener? = null
    private var finalDelivered = false

    override fun isAvailable(): Boolean = if (requireOnDeviceEngine && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)
    } else {
        SpeechRecognizer.isRecognitionAvailable(appContext)
    }

    override suspend fun startStreaming(
        config: SpeechInputConfig,
        listener: SpeechInputListener
    ): VoiceOperationResult = withContext(Dispatchers.Main.immediate) {
        if (!isAvailable()) {
            return@withContext VoiceOperationResult.Failure(
                VoiceFailure(
                    VoiceFailureCategory.UNSUPPORTED,
                    if (config.requireOnDevice) {
                        "Für diesen Modus ist kein lokales Android-Sprachpaket verfügbar."
                    } else {
                        "Android-Spracherkennung ist nicht verfügbar."
                    }
                )
            )
        }

        runCatching { recognizer?.cancel() }
        currentListener = listener
        finalDelivered = false

        val activeRecognizer = recognizer ?: createRecognizer().also { recognizer = it }
        activeRecognizer.setRecognitionListener(createRecognitionListener())
        val intent = createRecognizerIntent(config)
        try {
            activeRecognizer.startListening(intent)
            VoiceOperationResult.Success
        } catch (_: SecurityException) {
            currentListener = null
            VoiceOperationResult.Failure(permissionFailure())
        } catch (_: Throwable) {
            currentListener = null
            VoiceOperationResult.Failure(
                VoiceFailure(
                    VoiceFailureCategory.TEMPORARY_SERVICE_ERROR,
                    "Android-Spracherkennung konnte nicht gestartet werden."
                )
            )
        }
    }

    override suspend fun finish() = withContext(Dispatchers.Main.immediate) {
        runCatching { recognizer?.stopListening() }
        Unit
    }

    override suspend fun cancel() = withContext(Dispatchers.Main.immediate) {
        currentListener = null
        finalDelivered = false
        runCatching { recognizer?.cancel() }
        Unit
    }

    override suspend fun release() = withContext(Dispatchers.Main.immediate) {
        currentListener = null
        finalDelivered = false
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    private fun createRecognizer(): SpeechRecognizer =
        if (requireOnDeviceEngine && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
        } else {
            SpeechRecognizer.createSpeechRecognizer(appContext)
        }

    private fun createRecognizerIntent(config: SpeechInputConfig): Intent {
        val silenceTimeout = config.silenceTimeoutMs.coerceIn(500L, 5_000L)
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, config.languageTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, config.languageTag)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, config.requireOnDevice)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, silenceTimeout)
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                (silenceTimeout * 0.65f).toLong().coerceAtLeast(350L)
            )
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS,
                (silenceTimeout * 0.35f).toLong().coerceAtLeast(250L)
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, config.automaticLanguageDetection)
                putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH, config.automaticLanguageDetection)
            }
        }
    }

    private fun createRecognitionListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            currentListener?.onReady()
        }

        override fun onBeginningOfSpeech() = Unit

        override fun onRmsChanged(rmsdB: Float) {
            val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            currentListener?.onInputLevel(normalized)
        }

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            currentListener?.onSpeechEnded()
        }

        override fun onError(error: Int) {
            val listener = currentListener ?: return
            currentListener = null
            finalDelivered = false
            listener.onFailure(mapRecognitionError(error))
        }

        override fun onResults(results: Bundle?) {
            val listener = currentListener ?: return
            if (finalDelivered) return
            finalDelivered = true
            currentListener = null
            val finalText = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
                .trim()
            listener.onFinalTranscript(finalText)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partialText = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
                .trim()
            if (partialText.isNotBlank()) currentListener?.onPartialTranscript(partialText)
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun mapRecognitionError(error: Int): VoiceFailure = when (error) {
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> permissionFailure()
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> VoiceFailure(
            VoiceFailureCategory.OFFLINE,
            "Spracherkennung ist momentan offline."
        )
        SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> VoiceFailure(
            VoiceFailureCategory.RATE_LIMITED,
            "Spracherkennung wurde zu oft gestartet. Bitte kurz warten."
        )
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> VoiceFailure(
            VoiceFailureCategory.UNSUPPORTED,
            "Die ausgewählte Sprache ist für die Spracherkennung nicht verfügbar."
        )
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> VoiceFailure(
            VoiceFailureCategory.TEMPORARY_SERVICE_ERROR,
            "Keine Sprache erkannt. Tippe auf das Mikrofon und versuche es erneut."
        )
        SpeechRecognizer.ERROR_NO_MATCH -> VoiceFailure(
            VoiceFailureCategory.TEMPORARY_SERVICE_ERROR,
            "Ich konnte das Gesprochene nicht sicher erkennen."
        )
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> VoiceFailure(
            VoiceFailureCategory.TEMPORARY_SERVICE_ERROR,
            "Die Spracherkennung ist noch beschäftigt."
        )
        else -> VoiceFailure(
            VoiceFailureCategory.TEMPORARY_SERVICE_ERROR,
            "Spracherkennung wurde unterbrochen."
        )
    }

    private fun permissionFailure() = VoiceFailure(
        VoiceFailureCategory.PERMISSION_DENIED,
        "Mikrofonzugriff ist nicht erlaubt."
    )
}
