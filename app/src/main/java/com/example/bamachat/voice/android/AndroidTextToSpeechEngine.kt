package com.example.bamachat.voice.android

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.example.bamachat.voice.SpeechOutputEngine
import com.example.bamachat.voice.SpeechOutputListener
import com.example.bamachat.voice.SpeechOutputRequest
import com.example.bamachat.voice.VoiceFailure
import com.example.bamachat.voice.VoiceFailureCategory
import com.example.bamachat.voice.VoiceOperationResult
import com.example.bamachat.voice.VoiceOutputProvider
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class AndroidTextToSpeechEngine(context: Context) : SpeechOutputEngine {
    override val provider: VoiceOutputProvider = VoiceOutputProvider.ANDROID

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var textToSpeech: TextToSpeech? = null
    private var initialization: CompletableDeferred<Boolean>? = null
    private var activeUtteranceId: String? = null
    private var activeResult: CompletableDeferred<VoiceOperationResult>? = null
    private var activeListener: SpeechOutputListener? = null

    override suspend fun speak(
        request: SpeechOutputRequest,
        listener: SpeechOutputListener
    ): VoiceOperationResult {
        if (request.text.isBlank()) return VoiceOperationResult.Success
        if (!ensureInitialized()) {
            return VoiceOperationResult.Failure(
                VoiceFailure(
                    VoiceFailureCategory.UNSUPPORTED,
                    "Android-Sprachausgabe ist nicht verfügbar."
                )
            )
        }

        val result = CompletableDeferred<VoiceOperationResult>()
        val utteranceId = "bamavoice-${UUID.randomUUID()}"
        withContext(Dispatchers.Main.immediate) {
            completeActive(cancelledFailure())
            val engine = textToSpeech
            if (engine == null) {
                result.complete(unsupportedFailure())
                return@withContext
            }
            activeUtteranceId = utteranceId
            activeResult = result
            activeListener = listener
            engine.language = Locale.forLanguageTag(request.languageTag)
            engine.setSpeechRate(request.speed.coerceIn(0.5f, 2f))
            engine.setPitch(request.pitch.coerceIn(0.8f, 1.2f))
            engine.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            val status = engine.speak(request.text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            if (status == TextToSpeech.ERROR) {
                completeActive(
                    VoiceOperationResult.Failure(
                        VoiceFailure(
                            VoiceFailureCategory.TEMPORARY_SERVICE_ERROR,
                            "Android-Sprachausgabe konnte nicht gestartet werden."
                        )
                    )
                )
            }
        }
        return result.await()
    }

    override suspend fun stop() = withContext(Dispatchers.Main.immediate) {
        runCatching { textToSpeech?.stop() }
        completeActive(cancelledFailure())
    }

    override suspend fun pause(): VoiceOperationResult = VoiceOperationResult.Failure(
        VoiceFailure(VoiceFailureCategory.UNSUPPORTED, "Pausieren wird von Android TTS nicht unterstützt.")
    )

    override suspend fun resume(): VoiceOperationResult = VoiceOperationResult.Failure(
        VoiceFailure(VoiceFailureCategory.UNSUPPORTED, "Fortsetzen wird von Android TTS nicht unterstützt.")
    )

    override suspend fun release() = withContext(Dispatchers.Main.immediate) {
        completeActive(cancelledFailure())
        runCatching { textToSpeech?.stop() }
        runCatching { textToSpeech?.shutdown() }
        textToSpeech = null
        initialization = null
    }

    private suspend fun ensureInitialized(): Boolean {
        val deferred = withContext(Dispatchers.Main.immediate) {
            initialization?.let { return@withContext it }
            val created = CompletableDeferred<Boolean>()
            initialization = created
            lateinit var engine: TextToSpeech
            engine = TextToSpeech(appContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    engine.setOnUtteranceProgressListener(progressListener)
                    created.complete(true)
                } else {
                    created.complete(false)
                }
            }
            textToSpeech = engine
            created
        }
        return withTimeoutOrNull(INIT_TIMEOUT_MS) { deferred.await() } == true
    }

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            mainHandler.post {
                if (utteranceId == activeUtteranceId) activeListener?.onPlaybackStarted()
            }
        }

        override fun onDone(utteranceId: String?) {
            mainHandler.post {
                if (utteranceId == activeUtteranceId) completeActive(VoiceOperationResult.Success)
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            onError(utteranceId, TextToSpeech.ERROR)
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            mainHandler.post {
                if (utteranceId == activeUtteranceId) {
                    completeActive(
                        VoiceOperationResult.Failure(
                            VoiceFailure(
                                VoiceFailureCategory.TEMPORARY_SERVICE_ERROR,
                                "Android-Sprachausgabe wurde unterbrochen."
                            )
                        )
                    )
                }
            }
        }
    }

    private fun completeActive(result: VoiceOperationResult) {
        activeResult?.takeIf { !it.isCompleted }?.complete(result)
        activeResult = null
        activeUtteranceId = null
        activeListener = null
    }

    private fun cancelledFailure() = VoiceOperationResult.Failure(
        VoiceFailure(VoiceFailureCategory.CANCELLED, "Sprachausgabe gestoppt.")
    )

    private fun unsupportedFailure() = VoiceOperationResult.Failure(
        VoiceFailure(VoiceFailureCategory.UNSUPPORTED, "Android-Sprachausgabe ist nicht verfügbar.")
    )

    companion object {
        private const val INIT_TIMEOUT_MS = 5_000L
    }
}
