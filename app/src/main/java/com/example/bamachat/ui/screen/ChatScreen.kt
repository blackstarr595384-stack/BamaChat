package com.example.bamachat.ui.screen

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.content.pm.PackageManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bamachat.data.model.ChatMessage
import com.example.bamachat.ui.component.ChatBubble
import com.example.bamachat.ui.component.BamaChatBottomNav
import com.example.bamachat.ui.component.ChatDesignPreset
import com.example.bamachat.ui.component.ChatDesignTokens
import com.example.bamachat.ui.component.ChatDrawer
import com.example.bamachat.ui.component.ChatInputBar
import com.example.bamachat.ui.component.EmptyChatState
import com.example.bamachat.ui.component.PremiumPaywallDialog
import com.example.bamachat.ui.component.TypingIndicator
import com.example.bamachat.ui.component.ToolCallsDisplay
import com.example.bamachat.ui.component.compactLabel
import com.example.bamachat.ui.component.designTokensFor
import com.example.bamachat.ui.component.sanitizeForSpeech
import com.example.bamachat.ui.component.splitSpeechChunks
import com.example.bamachat.ui.screen.PersonaMood
import com.example.bamachat.ui.screen.moodForPersona
import com.example.bamachat.ui.theme.AppDesignSystem
import com.example.bamachat.ui.viewmodel.ChatViewModel
import com.example.bamachat.ui.viewmodel.SettingsViewModel
import com.example.bamachat.ui.viewmodel.MonetizationViewModel
import com.example.bamachat.ui.viewmodel.ToolCallProgress
import com.example.bamachat.util.CloudVoiceManager
import dev.jeziellago.compose.markdowntext.MarkdownText
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.io.File


private fun createChatCameraCaptureUri(context: android.content.Context): Pair<File, Uri>? {
    return runCatching {
        val imageFile = File.createTempFile("bamachat_chat_", ".jpg", context.cacheDir)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            imageFile
        )
        imageFile to uri
    }.getOrNull()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    settingsViewModel: SettingsViewModel,
    onBottomNavRoute: (String) -> Unit = {},
    onOpenMiniApps: () -> Unit = {},
    onOpenAgentHub: () -> Unit = {},
    onOpenComposeLab: () -> Unit = {},
    onSearchClick: () -> Unit = {}
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val currentConvId by viewModel.currentConversationId.collectAsStateWithLifecycle()
    val selectedPersona by viewModel.selectedPersona.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isStreaming by viewModel.isStreaming.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val errorActionLabel by viewModel.errorActionLabel.collectAsStateWithLifecycle()
    val isErrorRetryable by viewModel.isErrorRetryable.collectAsStateWithLifecycle()
    val hasOlderMessages by viewModel.hasOlderMessages.collectAsStateWithLifecycle()
    val chatSentiment by viewModel.chatSentiment.collectAsStateWithLifecycle()
    val usageStatus by viewModel.usageStatus.collectAsStateWithLifecycle()
    val showPaywall by viewModel.showPaywall.collectAsStateWithLifecycle()
    val activeExtensionNames by viewModel.activeExtensionNames.collectAsStateWithLifecycle()
    val lastAppliedExtensionNames by viewModel.lastAppliedExtensionNames.collectAsStateWithLifecycle()
    val selectedExtensionQuickAction by viewModel.selectedExtensionQuickAction.collectAsStateWithLifecycle()
    val activeToolCalls by viewModel.activeToolCalls.collectAsStateWithLifecycle()

    val isBiometricEnabled by settingsViewModel.isBiometricEnabled.collectAsStateWithLifecycle()
    val primaryColorInt by settingsViewModel.primaryColorInt.collectAsStateWithLifecycle()
    val fontSize by settingsViewModel.fontSize.collectAsStateWithLifecycle()
    val aiProvider by settingsViewModel.aiProvider.collectAsStateWithLifecycle()
    val multiProviderEnabled by settingsViewModel.multiProviderEnabled.collectAsStateWithLifecycle()
    val openRouterApiKey by settingsViewModel.openRouterApiKey.collectAsStateWithLifecycle()
    val groqApiKey by settingsViewModel.groqApiKey.collectAsStateWithLifecycle()
    val cerebrasApiKey by settingsViewModel.cerebrasApiKey.collectAsStateWithLifecycle()
    val togetherApiKey by settingsViewModel.togetherApiKey.collectAsStateWithLifecycle()
    val openCodeApiKey by settingsViewModel.openCodeApiKey.collectAsStateWithLifecycle()
    val openCodeEndpoint by settingsViewModel.openCodeEndpoint.collectAsStateWithLifecycle()
    val ttsEnabled by settingsViewModel.ttsEnabled.collectAsStateWithLifecycle()
    val ttsSpeed by settingsViewModel.ttsSpeed.collectAsStateWithLifecycle()
    val ttsPitch by settingsViewModel.ttsPitch.collectAsStateWithLifecycle()
    val ttsVoiceStyle by settingsViewModel.ttsVoiceStyle.collectAsStateWithLifecycle()
    val ttsProVoiceEnabled by settingsViewModel.ttsProVoiceEnabled.collectAsStateWithLifecycle()
    val cloudVoiceEnabled by settingsViewModel.cloudVoiceEnabled.collectAsStateWithLifecycle()
    val cloudVoiceProvider by settingsViewModel.cloudVoiceProvider.collectAsStateWithLifecycle()
    val elevenLabsApiKey by settingsViewModel.elevenLabsApiKey.collectAsStateWithLifecycle()
    val elevenLabsVoiceId by settingsViewModel.elevenLabsVoiceId.collectAsStateWithLifecycle()
    val elevenLabsModelId by settingsViewModel.elevenLabsModelId.collectAsStateWithLifecycle()
    val piperEndpoint by settingsViewModel.piperEndpoint.collectAsStateWithLifecycle()
    val piperVoiceName by settingsViewModel.piperVoiceName.collectAsStateWithLifecycle()
    val voicePushToTalkEnabled by settingsViewModel.voicePushToTalkEnabled.collectAsStateWithLifecycle()
    val voiceChatMode by settingsViewModel.voiceChatMode.collectAsStateWithLifecycle()
    val automationQuickActionsEnabled by settingsViewModel.automationQuickActionsEnabled.collectAsStateWithLifecycle()
    val activeWorkspaceName by settingsViewModel.activeWorkspaceName.collectAsStateWithLifecycle()
    val workspaceChatFilterEnabled by settingsViewModel.workspaceChatFilterEnabled.collectAsStateWithLifecycle()
    val autoSendVoice by settingsViewModel.autoSendVoice.collectAsStateWithLifecycle()
    val showTimestamps by settingsViewModel.showTimestamps.collectAsStateWithLifecycle()
    val liveSourcesVisible by settingsViewModel.showLiveSources.collectAsStateWithLifecycle()
    val bubbleAnimations by settingsViewModel.bubbleAnimations.collectAsStateWithLifecycle()
    val uiDesignPreset by settingsViewModel.uiDesignPreset.collectAsStateWithLifecycle()
    val compactChatHeader by settingsViewModel.compactChatHeader.collectAsStateWithLifecycle()
    val connectChatBottomBars by settingsViewModel.connectChatBottomBars.collectAsStateWithLifecycle()
    val glassEffectsEnabled by settingsViewModel.glassEffectsEnabled.collectAsStateWithLifecycle()
    val uiCornerRoundnessScale by settingsViewModel.uiCornerRoundnessScale.collectAsStateWithLifecycle()
    val uiShadowIntensityScale by settingsViewModel.uiShadowIntensityScale.collectAsStateWithLifecycle()
    val uiSurfaceOpacity by settingsViewModel.uiSurfaceOpacity.collectAsStateWithLifecycle()
    val language by settingsViewModel.language.collectAsStateWithLifecycle()
    val isPremiumActive by settingsViewModel.isPremiumActive.collectAsStateWithLifecycle()

    var inputText by rememberSaveable { mutableStateOf("") }
    // P0-2: persist the selected image URI across recreation. We store the URI's
    // string form; a null draft stays null.
    var selectedImageUri by rememberSaveable(
        stateSaver = androidx.compose.runtime.saveable.Saver<Uri?, String>(
            save = { it?.toString() ?: "" },
            restore = { stored -> if (stored.isBlank()) null else Uri.parse(stored) }
        )
    ) { mutableStateOf<Uri?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val cloudVoiceManager = remember(context) { CloudVoiceManager(context) }

    val imagePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        selectedImageUri = uri
    }
    var pendingCameraCaptureFile by remember { mutableStateOf<File?>(null) }
    var pendingCameraCaptureUri by remember { mutableStateOf<Uri?>(null) }
    val takePictureLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.TakePicture()
    ) { success ->
        val capturedUri = pendingCameraCaptureUri
        val capturedFile = pendingCameraCaptureFile
        pendingCameraCaptureUri = null
        pendingCameraCaptureFile = null
        if (success && capturedUri != null) {
            selectedImageUri = capturedUri
            Toast.makeText(context, "Foto aufgenommen.", Toast.LENGTH_SHORT).show()
        } else {
            capturedFile?.delete()
        }
    }
    val cameraPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val capture = createChatCameraCaptureUri(context)
            if (capture != null) {
                pendingCameraCaptureFile = capture.first
                pendingCameraCaptureUri = capture.second
                takePictureLauncher.launch(capture.second)
            } else {
                Toast.makeText(context, "Kamera konnte nicht vorbereitet werden.", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Kamera-Berechtigung wurde abgelehnt.", Toast.LENGTH_SHORT).show()
        }
    }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showPersonaDialog by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(errorMessage, isErrorRetryable, errorActionLabel) {
        errorMessage?.let {
            val result = snackbarHostState.showSnackbar(
                message = it,
                actionLabel = if (isErrorRetryable) (errorActionLabel ?: "Erneut") else null,
                withDismissAction = true,
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed && isErrorRetryable) {
                viewModel.retryLastFailedMessage()
            }
            viewModel.dismissError()
        }
    }
    LaunchedEffect(isPremiumActive) {
        viewModel.refreshMonetizationState()
    }

    // Biometric lock
    var isAuthenticated by remember { mutableStateOf(!isBiometricEnabled) }
    LaunchedEffect(isBiometricEnabled) {
        if (isBiometricEnabled && !isAuthenticated) {
            triggerBiometric(context) { isAuthenticated = it }
        } else if (!isBiometricEnabled) {
            isAuthenticated = true
        }
    }
    if (!isAuthenticated && isBiometricEnabled) {
        LockScreen(primaryColorInt) { triggerBiometric(context) { isAuthenticated = it } }
        return
    }

    // TTS
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var isTtsReady by remember { mutableStateOf(false) }
    var activeSpeechMessageId by remember { mutableStateOf<String?>(null) }
    var isSpeechPlaybackActive by remember { mutableStateOf(false) }
    val stopActiveDictation = remember { mutableStateOf<() -> Unit>({}) }
    val useClearVoiceStyle = ttsVoiceStyle == SettingsViewModel.TTS_STYLE_CLEAR
    val cloudVoiceRequested = ttsProVoiceEnabled && cloudVoiceEnabled
    val selectedCloudVoiceProvider = remember(cloudVoiceProvider) {
        CloudVoiceManager.Provider.fromStorage(cloudVoiceProvider)
    }
    val cloudVoiceConfig = remember(
        cloudVoiceRequested,
        cloudVoiceProvider,
        elevenLabsApiKey,
        elevenLabsVoiceId,
        elevenLabsModelId,
        piperEndpoint,
        piperVoiceName
    ) {
        if (!cloudVoiceRequested) {
            null
        } else {
            CloudVoiceManager.resolveCloudVoiceConfig(
                providerValue = cloudVoiceProvider,
                elevenLabsApiKey = elevenLabsApiKey,
                elevenLabsVoiceId = elevenLabsVoiceId,
                elevenLabsModelId = elevenLabsModelId,
                piperEndpoint = piperEndpoint,
                piperVoiceName = piperVoiceName
            )
        }
    }
    val ttsLocale = remember(language) {
        when (language) {
            "en" -> Locale.ENGLISH
            "fr" -> Locale.FRENCH
            "es" -> Locale("es")
            "tr" -> Locale("tr")
            "ar" -> Locale("ar")
            else -> Locale.GERMAN
        }
    }
    DisposableEffect(Unit) {
        lateinit var ttsInstance: TextToSpeech
        ttsInstance = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsInstance.language = ttsLocale
                ttsInstance.setSpeechRate(ttsSpeed)
                ttsInstance.setPitch(ttsPitch)
                isTtsReady = true
            } else {
                isTtsReady = false
            }
        }
        tts = ttsInstance
        onDispose {
            isTtsReady = false
            ttsInstance.stop()
            ttsInstance.shutdown()
            cloudVoiceManager.release()
        }
    }
    LaunchedEffect(ttsSpeed, ttsPitch, ttsLocale) {
        tts?.language = ttsLocale
        tts?.setSpeechRate(ttsSpeed)
        tts?.setPitch(ttsPitch)
    }
    val speakWithLocalTts: (String) -> Unit = localSpeak@{ speakText ->
        val engine = tts ?: return@localSpeak
        val maxChunkChars = if (useClearVoiceStyle) 170 else 220
        val pauseMs = if (useClearVoiceStyle) 80L else 140L
        val chunks = splitSpeechChunks(speakText, maxChunkChars = maxChunkChars)
        if (chunks.isEmpty()) return@localSpeak
        runCatching { engine.stop() }
        chunks.forEachIndexed { index, chunk ->
            val queueMode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            engine.speak(
                chunk,
                queueMode,
                null,
                "bamachat_tts_${System.currentTimeMillis()}_$index"
            )
            if (index < chunks.lastIndex) {
                engine.playSilentUtterance(
                    pauseMs,
                    TextToSpeech.QUEUE_ADD,
                    "bamachat_tts_pause_$index"
                )
            }
        }
    }
    val monitorSpeechPlayback: (String?) -> Unit = monitor@{ messageId ->
        if (messageId == null) return@monitor
        scope.launch {
            var observedPlayback = false
            var startWaitedMs = 0L

            while (activeSpeechMessageId == messageId && startWaitedMs < 1500L) {
                val speakingNow = tts?.isSpeaking == true || cloudVoiceManager.isSpeaking()
                if (speakingNow) {
                    observedPlayback = true
                    break
                }
                delay(80)
                startWaitedMs += 80L
            }

            var idleChecks = 0
            while (activeSpeechMessageId == messageId) {
                val speakingNow = tts?.isSpeaking == true || cloudVoiceManager.isSpeaking()
                if (speakingNow) {
                    observedPlayback = true
                    idleChecks = 0
                } else if (observedPlayback) {
                    idleChecks += 1
                    if (idleChecks >= 2) break
                } else {
                    break
                }
                delay(140)
            }

            if (activeSpeechMessageId == messageId) {
                activeSpeechMessageId = null
                isSpeechPlaybackActive = false
            }
        }
    }
    val stopSpeechPlayback: () -> Unit = {
        activeSpeechMessageId = null
        isSpeechPlaybackActive = false
        runCatching { tts?.stop() }
        scope.launch { runCatching { cloudVoiceManager.stop() } }
    }
    val speakMessage: (String?, String, Boolean) -> Unit = { messageId, text, userInitiated ->
        val speakText = sanitizeForSpeech(text)
        scope.launch {
            stopActiveDictation.value.invoke()
            if (messageId != null) {
                activeSpeechMessageId = messageId
                isSpeechPlaybackActive = true
            }
            if (cloudVoiceRequested) {
                val config = cloudVoiceConfig
                if (config == null) {
                    if (activeSpeechMessageId == messageId) {
                        activeSpeechMessageId = null
                        isSpeechPlaybackActive = false
                    }
                    if (userInitiated) {
                        Toast.makeText(
                            context,
                            "${selectedCloudVoiceProvider.displayName} ist aktiviert, aber die Konfiguration ist unvollständig.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    return@launch
                }
                val cloudOk = runCatching {
                    cloudVoiceManager.speak(
                        text = speakText,
                        config = config,
                        voiceStyle = if (useClearVoiceStyle) CloudVoiceManager.VoiceStyle.CLEAR else CloudVoiceManager.VoiceStyle.NATURAL
                    )
                }.getOrDefault(false)

                // P0-3 fix: user may have pressed Stop while we were awaiting the
                // ElevenLabs HTTP fetch. If activeSpeechMessageId no longer matches,
                // the playback must be aborted instead of starting after-the-fact.
                if (cloudOk && activeSpeechMessageId != messageId) {
                    runCatching { cloudVoiceManager.stop() }
                    return@launch
                }

                if (cloudOk) {
                    monitorSpeechPlayback(messageId)
                    return@launch
                }

                if (activeSpeechMessageId == messageId) {
                    activeSpeechMessageId = null
                    isSpeechPlaybackActive = false
                }

                if (userInitiated) {
                    Toast.makeText(
                        context,
                        cloudVoiceManager.lastErrorMessage() ?: "ElevenLabs konnte nicht gestartet werden. Android-Stimme wird nicht genutzt.",
                        Toast.LENGTH_LONG
                    ).show()
                }

                return@launch
            }
            if (ttsEnabled && isTtsReady && tts != null) {
                speakWithLocalTts(speakText)
                monitorSpeechPlayback(messageId)
                return@launch
            }
            if (activeSpeechMessageId == messageId) {
                activeSpeechMessageId = null
                isSpeechPlaybackActive = false
            }
            if (userInitiated) {
                Toast.makeText(
                    context,
                    "Sprachausgabe ist nicht bereit. Prüfe Stimme und Berechtigungen.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    val onSpeak: (String, String) -> Unit = { messageId, text ->
        if (isSpeechPlaybackActive && activeSpeechMessageId == messageId) {
            stopSpeechPlayback()
        } else {
            speakMessage(messageId, text, true)
        }
    }

    // Auto-speak last AI message when TTS enabled (track by ID to avoid re-speaking)
    var lastSpokenMessageId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(
        messages.size,
        ttsEnabled,
        ttsProVoiceEnabled,
        cloudVoiceEnabled,
        cloudVoiceProvider,
        elevenLabsApiKey,
        elevenLabsVoiceId,
        elevenLabsModelId,
        piperEndpoint,
        piperVoiceName
    ) {
        val canSpeak = if (cloudVoiceRequested) {
            cloudVoiceConfig != null
        } else {
            ttsEnabled
        }
        if (canSpeak && messages.isNotEmpty()) {
            val last = messages.last()
            if (!last.isUser && last.text.isNotBlank() && last.id != lastSpokenMessageId) {
                lastSpokenMessageId = last.id
                speakMessage(last.id, last.text, false)
            }
        }
    }

    // STT
    val isListeningState = remember { mutableStateOf(false) }
    var isListening by isListeningState
    var isSpeechStartPending by remember { mutableStateOf(false) }
    var ignoreNextSpeechClientError by remember { mutableStateOf(false) }
    var pushToTalkSessionActive by remember { mutableStateOf(false) }
    var lastPartialUpdateAt by remember { mutableLongStateOf(0L) }
    var lastVoiceAutoSendAt by remember { mutableLongStateOf(0L) }
    var lastVoiceAutoSendText by remember { mutableStateOf("") }
    var hasHadVoiceExchange by remember { mutableStateOf(false) }
    val speechRecognitionAvailable = remember { SpeechRecognizer.isRecognitionAvailable(context) }
    val speechRecognizer = remember(context, speechRecognitionAvailable) {
        if (speechRecognitionAvailable) SpeechRecognizer.createSpeechRecognizer(context) else null
    }
    val recognizerLanguageTag = remember(ttsLocale) {
        ttsLocale.toLanguageTag().ifBlank { ttsLocale.toString() }
    }
    val recognizerIntent = remember(recognizerLanguageTag, voiceChatMode) {
        val completeSilenceMs = if (voiceChatMode) 700L else 1200L
        val possiblyCompleteSilenceMs = if (voiceChatMode) 400L else 800L
        val minimumSpeechMs = if (voiceChatMode) 300L else 600L
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, recognizerLanguageTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, recognizerLanguageTag)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, completeSilenceMs)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, possiblyCompleteSilenceMs)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, minimumSpeechMs)
        }
    }
    val latestAutoSendVoice by rememberUpdatedState(autoSendVoice)
    val latestVoiceChatMode by rememberUpdatedState(voiceChatMode)
    val latestIsStreaming by rememberUpdatedState(isStreaming)
    // P0-6: the recognizer listener is built once for the lifetime of speechRecognizer;
    // it reads the intent through this updated-state so a voiceChatMode toggle is
    // picked up on the very next startListening() without recreating the recognizer.
    val latestRecognizerIntent by rememberUpdatedState(recognizerIntent)
    val startSpeechRecognition: (Boolean) -> Unit = startSpeech@{ showPrompt ->
        val recognizer = speechRecognizer ?: return@startSpeech
        if (isListeningState.value || isSpeechStartPending) return@startSpeech
        isSpeechStartPending = true
        ignoreNextSpeechClientError = false
        scope.launch {
            runCatching { tts?.stop() }
            try {
                cloudVoiceManager.stop()
            } catch (_: Exception) {
            }
            if (showPrompt) {
                Toast.makeText(context, "Sprich jetzt...", Toast.LENGTH_SHORT).show()
            }
            val started = runCatching {
                recognizer.startListening(recognizerIntent)
                true
            }.getOrElse {
                isSpeechStartPending = false
                Toast.makeText(
                    context,
                    "Spracherkennung konnte nicht gestartet werden.",
                    Toast.LENGTH_SHORT
                ).show()
                false
            }
            if (!started) {
                pushToTalkSessionActive = false
            }
        }
    }
    val stopSpeechRecognition: (Boolean) -> Unit = stopSpeech@{ forceCancel ->
        val recognizer = speechRecognizer ?: return@stopSpeech
        if (!isListeningState.value && !isSpeechStartPending) return@stopSpeech
        ignoreNextSpeechClientError = true
        isSpeechStartPending = false
        runCatching {
            if (forceCancel || !isListeningState.value) recognizer.cancel() else recognizer.stopListening()
        }
        if (forceCancel || !isListeningState.value) {
            isListeningState.value = false
        }
    }
    // P0-5 cleanup: wrap the MutableState write in SideEffect so we don't allocate
    // a new lambda + State write on every recomposition.
    SideEffect {
        stopActiveDictation.value = {
            if (isListeningState.value || isSpeechStartPending) {
                stopSpeechRecognition(true)
            }
        }
    }
    val audioPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startSpeechRecognition(true)
        } else {
            Toast.makeText(
                context,
                "Mikrofon-Berechtigung wurde abgelehnt.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    // P0-6: keying on speechRecognizer only — rebuilding the listener every time the
    // user toggles voiceChatMode (which changes recognizerIntent) tore down and
    // recreated the SpeechRecognizer mid-session. The listener captures the current
    // recognizerIntent via closure; the next startListening() picks up the new intent.
    DisposableEffect(speechRecognizer) {
        val recognizer = speechRecognizer
        if (recognizer == null) {
            onDispose {}
        } else {
            val listener = object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isSpeechStartPending = false
                    isListeningState.value = true
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { isListeningState.value = false }
                override fun onError(error: Int) {
                    val ignoreClientError = ignoreNextSpeechClientError && error == SpeechRecognizer.ERROR_CLIENT
                    ignoreNextSpeechClientError = false
                    isSpeechStartPending = false
                    isListeningState.value = false
                    pushToTalkSessionActive = false
                    if (ignoreClientError) return
                    val isSoftSpeechError =
                        error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                            error == SpeechRecognizer.ERROR_NO_MATCH

                    if (latestVoiceChatMode && hasHadVoiceExchange && isSoftSpeechError) {
                        scope.launch {
                            delay(250)
                            val audioOk = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            val localSpeaking = tts?.isSpeaking == true
                            val cloudSpeaking = cloudVoiceManager.isSpeaking()
                            if (
                                audioOk &&
                                !latestIsStreaming &&
                                !localSpeaking &&
                                !cloudSpeaking &&
                                !isListeningState.value &&
                                !isSpeechStartPending
                            ) {
                                isSpeechStartPending = true
                                val restarted = runCatching {
                                    recognizer.startListening(latestRecognizerIntent)
                                    true
                                }.getOrDefault(false)
                                if (!restarted) {
                                    isSpeechStartPending = false
                                }
                            }
                        }
                        return
                    }

                    val errorMsg = when (error) {
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Netzwerk-Zeitüberschreitung"
                        SpeechRecognizer.ERROR_NETWORK -> "Netzwerkfehler"
                        SpeechRecognizer.ERROR_AUDIO -> "Audio-Fehler"
                        SpeechRecognizer.ERROR_CLIENT -> "Client-Fehler"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Keine Sprache erkannt"
                        SpeechRecognizer.ERROR_NO_MATCH -> "Nichts erkannt"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Spracherkennung ausgelastet"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Keine Mikrofon-Berechtigung"
                        else -> "Spracherkennungsfehler ($error)"
                    }
                    android.util.Log.w("ChatScreen", "SpeechRecognizer: $errorMsg")
                    Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                }
                override fun onResults(results: Bundle?) {
                    isSpeechStartPending = false
                    val data = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!data.isNullOrEmpty()) {
                        val recognizedText = data[0].trim()
                        inputText = recognizedText
                        if (latestAutoSendVoice) {
                            if (recognizedText.isNotBlank()) {
                                val now = System.currentTimeMillis()
                                val isDuplicateAutoSend =
                                    recognizedText.equals(lastVoiceAutoSendText, ignoreCase = true) &&
                                        (now - lastVoiceAutoSendAt) < 1500L
                                if (isDuplicateAutoSend) {
                                    isListeningState.value = false
                                    return
                                }
                                lastVoiceAutoSendText = recognizedText
                                lastVoiceAutoSendAt = now
                                val imageUri = selectedImageUri
                                val accepted = if (imageUri != null) {
                                    viewModel.sendMessageWithImage(recognizedText, imageUri)
                                } else {
                                    viewModel.sendMessage(recognizedText)
                                }
                                if (accepted) {
                                    inputText = ""
                                    if (imageUri != null) {
                                        selectedImageUri = null
                                    }
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Sprache erkannt, aber nicht gesendet.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                hasHadVoiceExchange = true
                            }
                        } else {
                            Toast.makeText(context, "Erkannt: $recognizedText", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "Keine Sprache erkannt.", Toast.LENGTH_SHORT).show()
                    }
                    pushToTalkSessionActive = false
                    isListeningState.value = false
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val data = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val partialText = data?.firstOrNull()?.trim().orEmpty()
                    if (partialText.isBlank()) return
                    val now = System.currentTimeMillis()
                    if ((now - lastPartialUpdateAt) >= 120L) {
                        inputText = partialText
                        lastPartialUpdateAt = now
                    }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            }
            recognizer.setRecognitionListener(listener)
            onDispose { recognizer.destroy() }
        }
    }
    // Sync state back to Compose
    // (removed redundant LaunchedEffect: `isListening` is already a delegated MutableState read)

    // Continuous voice mode: re-trigger listening when AI and TTS playback are both finished
    LaunchedEffect(isStreaming, voiceChatMode, hasHadVoiceExchange, messages.lastOrNull()?.id) {
        if (!voiceChatMode || isStreaming || !hasHadVoiceExchange) return@LaunchedEffect

        var waitedMs = 0L
        while ((tts?.isSpeaking == true || cloudVoiceManager.isSpeaking()) && waitedMs < 6000L) {
            delay(120)
            waitedMs += 120L
        }
        if (tts?.isSpeaking == true || cloudVoiceManager.isSpeaking()) return@LaunchedEffect

        val audioOk = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (audioOk && !isListeningState.value && !isSpeechStartPending) {
            isSpeechStartPending = true
            val restarted = runCatching {
                speechRecognizer?.startListening(recognizerIntent)
                true
            }.getOrDefault(false)
            if (!restarted) {
                isSpeechStartPending = false
            }
        }
    }

    // Theme colors
    val baseColor = Color(primaryColorInt)
    val designPalette = remember(uiDesignPreset) { AppDesignSystem.paletteForStored(uiDesignPreset) }
    val personaMood = remember(selectedPersona, baseColor, chatSentiment) {
        moodForPersona(selectedPersona, baseColor, chatSentiment)
    }
    val themeColor by animateColorAsState(
        targetValue = personaMood.accent,
        animationSpec = tween(800), label = "themeColor"
    )

    // Auto-scroll — P1-6: only auto-scroll when the user is already near the tail,
    // or when a brand-new message id just arrived. If the user has scrolled up to
    // re-read older messages, we leave the position alone.
    var autoScrollTailId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(messages.lastOrNull()?.id, isStreaming) {
        val tailId = messages.lastOrNull()?.id ?: return@LaunchedEffect
        val targetIndex = messages.lastIndex
        val isNewTail = tailId != autoScrollTailId
        autoScrollTailId = tailId

        val layoutInfo = listState.layoutInfo
        val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        val distanceFromTail = targetIndex - lastVisibleIndex
        // "Near the tail" = within ~2 items of the bottom, OR list was never scrolled yet.
        val nearTail = lastVisibleIndex < 0 || distanceFromTail <= 2

        // Only follow streaming updates if the user is still pinned near the tail.
        // For brand-new messages, follow when near-tail OR when the new message is the user's own.
        val isOwnLastMessage = messages.lastOrNull()?.isUser == true
        if ((isStreaming && nearTail) || (isNewTail && (nearTail || isOwnLastMessage))) {
            scope.launch {
                val currentIndex = listState.firstVisibleItemIndex
                val farDistance = (targetIndex - currentIndex) > 8
                if (isStreaming || farDistance) {
                    listState.scrollToItem(targetIndex)
                } else {
                    listState.animateScrollToItem(targetIndex)
                }
            }
        }
    }

    if (showSettingsDialog) {
        SettingsDialog(viewModel = settingsViewModel, onDismiss = { showSettingsDialog = false }, mcpServerManager = viewModel.mcpServerManager, mcpWorkflowManager = viewModel.mcpWorkflowManager)
    }
    if (showPersonaDialog) {
        PersonaDialog(viewModel, onDismiss = { showPersonaDialog = false })
    }
    if (showPaywall) {
        PremiumPaywallDialog(
            settingsViewModel = settingsViewModel,
            onDismiss = { viewModel.dismissPaywall() }
        )
    }

    val filteredConversations = remember(conversations, activeWorkspaceName, workspaceChatFilterEnabled) {
        viewModel.getConversationsForWorkspace(
            activeWorkspaceName = activeWorkspaceName,
            onlyActiveWorkspace = workspaceChatFilterEnabled
        )
    }
    val selectableProviders = remember(
        aiProvider,
        openRouterApiKey,
        groqApiKey,
        cerebrasApiKey,
        togetherApiKey,
        openCodeApiKey,
        openCodeEndpoint
    ) {
        buildList {
            if (openRouterApiKey.isNotBlank()) add("OpenRouter")
            if (openCodeApiKey.isNotBlank() && openCodeEndpoint.isNotBlank()) add("OpenCode")
            if (groqApiKey.isNotBlank()) add("Groq")
            if (cerebrasApiKey.isNotBlank()) add("Cerebras")
            if (togetherApiKey.isNotBlank()) add("Together")
            add("Ollama")
            if (aiProvider.isNotBlank()) add(aiProvider)
        }.distinct()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ChatDrawer(
                conversations = filteredConversations,
                currentId = currentConvId,
                themeColor = themeColor,
                palette = designPalette,
                glassEffectsEnabled = glassEffectsEnabled,
                cornerRoundnessScale = uiCornerRoundnessScale,
                shadowIntensityScale = uiShadowIntensityScale,
                surfaceOpacity = uiSurfaceOpacity,
                onSelect = { id ->
                    viewModel.switchConversation(id)
                    scope.launch { drawerState.close() }
                },
                onNewChat = {
                    viewModel.newConversation()
                    scope.launch { drawerState.close() }
                },
                onRename = { id, newTitle -> viewModel.renameConversation(id, newTitle) },
                onDelete = { id -> viewModel.deleteConversation(id) }
            )
        }
    ) {
        ChatContent(
            messages = messages,
            isLoading = isLoading,
            isStreaming = isStreaming,
            inputText = inputText,
            onInputChange = { inputText = it },
            onSend = { submittedText ->
                val trimmedInput = submittedText.trim()
                val imageUri = selectedImageUri
                if (imageUri != null) {
                    val accepted = viewModel.sendMessageWithImage(trimmedInput, imageUri)
                    if (accepted) {
                        selectedImageUri = null
                        inputText = ""
                    }
                    accepted
                } else if (trimmedInput.isNotEmpty()) {
                    val accepted = viewModel.sendMessage(trimmedInput, selectedExtensionQuickAction)
                    if (accepted) inputText = ""
                    accepted
                } else {
                    false
                }
            },
            onImageGen = {
                viewModel.generateImage(inputText)
                if (inputText.isNotBlank()) {
                    inputText = ""
                }
            },
            onUpload = {
                imagePickerLauncher.launch(
                    androidx.activity.result.PickVisualMediaRequest(
                        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                    )
                )
            },
            onTakePhoto = {
                if (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    val capture = createChatCameraCaptureUri(context)
                    if (capture != null) {
                        pendingCameraCaptureFile = capture.first
                        pendingCameraCaptureUri = capture.second
                        takePictureLauncher.launch(capture.second)
                    } else {
                        Toast.makeText(context, "Kamera konnte nicht vorbereitet werden.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            },
            selectedImageUri = selectedImageUri,
            onClearImage = { selectedImageUri = null },
            isListening = isListening,
            voicePushToTalkEnabled = voicePushToTalkEnabled,
            onMicClick = {
                if (!speechRecognitionAvailable) {
                    Toast.makeText(
                        context,
                        "Spracherkennung ist auf diesem Gerät nicht verfügbar.",
                        Toast.LENGTH_SHORT
                    ).show()
                } else if (isListening || isSpeechStartPending) {
                    stopSpeechRecognition(false)
                } else if (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    startSpeechRecognition(true)
                } else {
                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            onMicPressStart = {
                if (!speechRecognitionAvailable) {
                    Toast.makeText(
                        context,
                        "Spracherkennung ist auf diesem Gerät nicht verfügbar.",
                        Toast.LENGTH_SHORT
                    ).show()
                } else if (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    if (!isListening && !isSpeechStartPending) {
                        pushToTalkSessionActive = true
                        startSpeechRecognition(true)
                    }
                } else {
                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            onMicPressEnd = {
                if (pushToTalkSessionActive) {
                    pushToTalkSessionActive = false
                    stopSpeechRecognition(!isListening)
                }
            },
            themeColor = themeColor,
            personaMood = personaMood,
            fontSize = fontSize,
            selectedPersona = selectedPersona,
            aiProvider = aiProvider,
            selectableProviders = selectableProviders,
            multiProviderEnabled = multiProviderEnabled,
            onSelectProvider = { selected ->
                settingsViewModel.setAiProvider(selected)
                if (multiProviderEnabled) {
                    settingsViewModel.setMultiProviderEnabled(false)
                    Toast.makeText(context, "Auto-Fallback deaktiviert: Provider manuell gesetzt.", Toast.LENGTH_SHORT).show()
                }
            },
            onPersonaClick = { showPersonaDialog = true },
            onBottomNavRoute = onBottomNavRoute,
            onSearchClick = onSearchClick,
            onMenuClick = { scope.launch { drawerState.open() } },
            onSettingsClick = { showSettingsDialog = true },
            onMiniAppsClick = onOpenMiniApps,
            onAgentHubClick = onOpenAgentHub,
            onComposeLabClick = onOpenComposeLab,
            onShareClick = {
                val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, viewModel.getChatExportText())
                    type = "text/plain"
                }
                context.startActivity(Intent.createChooser(sendIntent, "Chat exportieren"))
            },
            onStopGeneration = { viewModel.cancelStream() },
            onSpeak = onSpeak,
            activeSpeechMessageId = activeSpeechMessageId,
            isSpeechPlaybackActive = isSpeechPlaybackActive,
            listState = listState,
            snackbarHostState = snackbarHostState,
            showTimestamps = showTimestamps,
            showLiveSources = liveSourcesVisible,
            bubbleAnimations = bubbleAnimations,
            usageStatus = usageStatus,
            onUpgradeClick = { viewModel.openPaywall() },
            hasOlderMessages = hasOlderMessages,
            onLoadOlderMessages = { viewModel.loadOlderMessages() },
            uiDesignPreset = uiDesignPreset,
            compactChatHeader = compactChatHeader,
            activeWorkspaceName = activeWorkspaceName,
            connectChatBottomBars = connectChatBottomBars,
            glassEffectsEnabled = glassEffectsEnabled,
            uiCornerRoundnessScale = uiCornerRoundnessScale,
            uiShadowIntensityScale = uiShadowIntensityScale,
            uiSurfaceOpacity = uiSurfaceOpacity,
            automationQuickActionsEnabled = automationQuickActionsEnabled,
            activeExtensionNames = activeExtensionNames,
            lastAppliedExtensionNames = lastAppliedExtensionNames,
            selectedExtensionQuickAction = selectedExtensionQuickAction,
            onSelectExtensionQuickAction = { viewModel.setExtensionQuickAction(it) },
            activeToolCalls = activeToolCalls
        )
    }
}

private fun triggerBiometric(context: android.content.Context, onResult: (Boolean) -> Unit) {
    val activity = context as? FragmentActivity ?: run {
        onResult(false)
        return
    }
    val executor = ContextCompat.getMainExecutor(context)
    val biometricPrompt = BiometricPrompt(activity, executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onResult(true)
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    onResult(false)
                }
            }
        })
    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("BamaChat Sperre")
        .setSubtitle("Authentifiziere dich")
        .setNegativeButtonText("Abbrechen")
        .build()
    biometricPrompt.authenticate(promptInfo)
}

@Composable
private fun LockScreen(primaryColorInt: Int, onUnlock: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF1A1C1E)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Lock, null, Modifier.size(64.dp), tint = Color(primaryColorInt))
            Spacer(Modifier.height(16.dp))
            Text("Bitte authentifizieren", style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Spacer(Modifier.height(24.dp))
            Button(onClick = onUnlock) { Text("Entsperren") }
        }
    }
}

@Suppress("UNUSED_PARAMETER")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ChatContent(
    messages: List<ChatMessage>,
    isLoading: Boolean,
    isStreaming: Boolean,
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: (String) -> Boolean,
    onImageGen: () -> Unit,
    onUpload: () -> Unit,
    onTakePhoto: () -> Unit,
    selectedImageUri: Uri?,
    onClearImage: () -> Unit,
    isListening: Boolean,
    voicePushToTalkEnabled: Boolean,
    onMicClick: () -> Unit,
    onMicPressStart: () -> Unit,
    onMicPressEnd: () -> Unit,
    themeColor: Color,
    personaMood: PersonaMood,
    fontSize: Float,
    selectedPersona: ChatViewModel.Persona,
    aiProvider: String,
    selectableProviders: List<String>,
    multiProviderEnabled: Boolean,
    onSelectProvider: (String) -> Unit,
    onPersonaClick: () -> Unit,
    onBottomNavRoute: (String) -> Unit,
    onSearchClick: () -> Unit,
    onMenuClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onMiniAppsClick: () -> Unit,
    onAgentHubClick: () -> Unit,
    onComposeLabClick: () -> Unit,
    onShareClick: () -> Unit,
    onStopGeneration: () -> Unit,
    onSpeak: (String, String) -> Unit,
    activeSpeechMessageId: String?,
    isSpeechPlaybackActive: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState,
    snackbarHostState: SnackbarHostState,
    showTimestamps: Boolean,
    showLiveSources: Boolean,
    bubbleAnimations: Boolean,
    usageStatus: MonetizationViewModel.UsageStatus,
    onUpgradeClick: () -> Unit,
    hasOlderMessages: Boolean,
    onLoadOlderMessages: () -> Unit,
    uiDesignPreset: String,
    compactChatHeader: Boolean,
    activeWorkspaceName: String,
    connectChatBottomBars: Boolean,
    glassEffectsEnabled: Boolean,
    uiCornerRoundnessScale: Float,
    uiShadowIntensityScale: Float,
    uiSurfaceOpacity: Float,
    automationQuickActionsEnabled: Boolean,
    activeExtensionNames: List<String>,
    lastAppliedExtensionNames: List<String>,
    selectedExtensionQuickAction: ChatViewModel.ExtensionQuickAction,
    onSelectExtensionQuickAction: (ChatViewModel.ExtensionQuickAction) -> Unit,
    activeToolCalls: List<ToolCallProgress>
) {
    val designPreset = remember(uiDesignPreset) { ChatDesignPreset.fromSetting(uiDesignPreset) }
    val designPalette = remember(uiDesignPreset) { AppDesignSystem.paletteForStored(uiDesignPreset) }
    val designTokens = remember(designPreset) { designTokensFor(designPreset) }
    val designName = remember(designPreset) {
        when (designPreset) {
            ChatDesignPreset.CURRENT -> "Aktuell"
            ChatDesignPreset.GLASS -> "Glass"
            ChatDesignPreset.EDITORIAL -> "Editorial"
            ChatDesignPreset.NOIR -> "Noir"
            ChatDesignPreset.SOLAR -> "Solar"
            ChatDesignPreset.DASHBOARD -> "Dashboard"
        }
    }
    val activeExtensionsLabel = remember(activeExtensionNames) { compactLabel(activeExtensionNames) }
    val lastAppliedExtensionsLabel = remember(lastAppliedExtensionNames) { compactLabel(lastAppliedExtensionNames) }
    val uiCornerScale = uiCornerRoundnessScale.coerceIn(0.7f, 1.4f)
    val uiShadowScale = uiShadowIntensityScale.coerceIn(0.6f, 1.8f)
    val surfaceOpacity = uiSurfaceOpacity.coerceIn(0.55f, 1.0f)
    val density = LocalDensity.current
    val isKeyboardOpen = WindowInsets.ime.getBottom(density) > 0
    var compactInputBarMode by remember { mutableStateOf(false) }
    var compactBottomNavVisible by remember { mutableStateOf(true) }
    val headerVerticalPadding = if (compactChatHeader) 2.dp else 5.dp
    val headerBottomSpacer = if (compactChatHeader) 0.dp else 2.dp
    val headerTitleStyle = if (compactChatHeader) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge
    val chatScrollCollapseConnection = remember(isKeyboardOpen, messages.isNotEmpty()) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (
                    source == NestedScrollSource.UserInput &&
                    !isKeyboardOpen &&
                    messages.isNotEmpty() &&
                    available.y != 0f
                ) {
                    compactInputBarMode = true
                    compactBottomNavVisible = true
                }
                return Offset.Zero
            }
        }
    }
    LaunchedEffect(messages.isEmpty()) {
        if (messages.isEmpty()) {
            compactInputBarMode = false
            compactBottomNavVisible = true
        }
    }
    val backgroundGradient = remember(designPalette) {
        Brush.verticalGradient(
            listOf(
                designPalette.screenBgTop,
                designPalette.screenBgMid,
                designPalette.screenBgBottom
            )
        )
    }
    val primaryGradient = remember(designPalette) {
        Brush.horizontalGradient(
            listOf(
                designPalette.chatHeaderStart,
                designPalette.chatHeaderMid,
                designPalette.chatHeaderEnd
            )
        )
    }
    val pulseTransition = rememberInfiniteTransition(label = "streamPulse")
    val streamPulse by pulseTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
        label = "streamPulseAlpha"
    )
    val messageContentAlpha by animateFloatAsState(
        targetValue = if (isKeyboardOpen) 0.2f else 1f,
        animationSpec = tween(durationMillis = 160, easing = LinearOutSlowInEasing),
        label = "messageContentAlpha"
    )
    val messageOverlayAlpha by animateFloatAsState(
        targetValue = if (isKeyboardOpen) 0.22f else 0f,
        animationSpec = tween(durationMillis = 120, easing = LinearOutSlowInEasing),
        label = "messageOverlayAlpha"
    )
    val chatListBottomPadding = 18.dp
    var topMenuExpanded by remember { mutableStateOf(false) }
    var providerMenuExpanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(backgroundGradient)) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = Color.Transparent
        ) { paddingValues ->
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                Column(modifier = Modifier.fillMaxSize()) {
                AnimatedVisibility(
                    visible = !isKeyboardOpen,
                    enter = fadeIn(tween(180)) + expandVertically(animationSpec = tween(220)),
                    exit = fadeOut(tween(120)) + shrinkVertically(animationSpec = tween(180))
                ) {
                    // Modern Header
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.Transparent,
                        shadowElevation = (designTokens.headerShadow.value * uiShadowScale).dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(primaryGradient)
                        ) {
                            CenterAlignedTopAppBar(
                                title = {
                                    Text(
                                        text = "BamaChat · ${selectedPersona.displayName}",
                                        style = headerTitleStyle,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                navigationIcon = {
                                    IconButton(onClick = onMenuClick) {
                                        Icon(Icons.Default.Menu, "Menü", tint = Color.White)
                                    }
                                },
                                actions = {
                                    if (!usageStatus.isPremium) {
                                        IconButton(onClick = onUpgradeClick) {
                                            Icon(Icons.Default.Star, "Upgrade", tint = Color.White)
                                        }
                                    }
                                    IconButton(onClick = onPersonaClick) {
                                        Icon(Icons.Default.Psychology, "Persona", tint = Color.White)
                                    }
                                    IconButton(onClick = onSearchClick) {
                                        Icon(Icons.Default.Search, "Suche", tint = Color.White)
                                    }
                                    IconButton(onClick = onShareClick) {
                                        Icon(Icons.Default.Share, "Teilen", tint = Color.White)
                                    }
                                    Box {
                                        IconButton(onClick = { topMenuExpanded = true }) {
                                            Icon(Icons.Default.MoreVert, "Mehr", tint = Color.White)
                                        }
                                        DropdownMenu(
                                            expanded = topMenuExpanded,
                                            onDismissRequest = { topMenuExpanded = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Home-Hub") },
                                                leadingIcon = { Icon(Icons.Default.Home, null) },
                                                onClick = {
                                                    topMenuExpanded = false
                                                    onBottomNavRoute("home_hub")
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Mini-Apps") },
                                                leadingIcon = { Icon(Icons.Default.Extension, null) },
                                                onClick = {
                                                    topMenuExpanded = false
                                                    onMiniAppsClick()
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Agent Hub") },
                                                leadingIcon = { Icon(Icons.Default.Psychology, null) },
                                                onClick = {
                                                    topMenuExpanded = false
                                                    onAgentHubClick()
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Compose Lab") },
                                                leadingIcon = { Icon(Icons.Default.Code, null) },
                                                onClick = {
                                                    topMenuExpanded = false
                                                    onComposeLabClick()
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Exportieren") },
                                                leadingIcon = { Icon(Icons.Default.Share, null) },
                                                onClick = {
                                                    topMenuExpanded = false
                                                    onShareClick()
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Einstellungen") },
                                                leadingIcon = { Icon(Icons.Default.Settings, null) },
                                                onClick = {
                                                    topMenuExpanded = false
                                                    onSettingsClick()
                                                }
                                            )
                                        }
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                            )
                            // P1-2: FlowRow so chips wrap on narrow screens instead of overflowing.
                            FlowRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = headerVerticalPadding),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box {
                                    Surface(
                                        shape = RoundedCornerShape(designTokens.chipCornerRadius),
                                        color = Color.White.copy(alpha = designTokens.chipAlpha),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                                        modifier = Modifier
                                            .semantics {
                                                role = Role.Button
                                                contentDescription = "Provider wechseln"
                                            }
                                            .clickable { providerMenuExpanded = true }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Provider: $aiProvider",
                                                color = Color.White,
                                                style = MaterialTheme.typography.labelMedium,
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Icon(
                                                Icons.Default.ArrowDropDown,
                                                contentDescription = "Provider wählen",
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    DropdownMenu(
                                        expanded = providerMenuExpanded,
                                        onDismissRequest = { providerMenuExpanded = false }
                                    ) {
                                        selectableProviders.forEach { providerOption ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        text = if (providerOption == aiProvider) "✓ $providerOption" else providerOption
                                                    )
                                                },
                                                onClick = {
                                                    providerMenuExpanded = false
                                                    onSelectProvider(providerOption)
                                                }
                                            )
                                        }
                                        if (multiProviderEnabled) {
                                            HorizontalDivider()
                                            // P2-4: render the auto-fallback note as a non-clickable
                                            // info row instead of a DropdownMenuItem so it isn't
                                            // mistaken for a selectable provider.
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Info,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp),
                                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                                                )
                                                Text(
                                                    text = "Auto-Fallback ist aktiv",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                                                )
                                            }
                                        }
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(designTokens.chipCornerRadius),
                                    color = Color.Black.copy(alpha = 0.24f),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        when (designPreset) {
                                            ChatDesignPreset.GLASS -> Color(0xFF9FCBFF).copy(alpha = 0.7f)
                                            ChatDesignPreset.EDITORIAL -> Color(0xFFFFB08A).copy(alpha = 0.7f)
                                            ChatDesignPreset.NOIR -> Color(0xFF86A8FF).copy(alpha = 0.7f)
                                            ChatDesignPreset.SOLAR -> Color(0xFFFFC386).copy(alpha = 0.7f)
                                            ChatDesignPreset.DASHBOARD -> Color(0xFF7DD3FC).copy(alpha = 0.7f)
                                            ChatDesignPreset.CURRENT -> Color.White.copy(alpha = 0.35f)
                                        }
                                    )
                                ) {
                                    Text(
                                        text = "Design: $designName",
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1
                                    )
                                }
                                // P1-1: Workspace chip — read-only here. Tap opens settings → workspaces.
                                if (activeWorkspaceName.isNotBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(designTokens.chipCornerRadius),
                                        color = Color.White.copy(alpha = designTokens.chipAlpha),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            Color.White.copy(alpha = 0.22f)
                                        ),
                                        modifier = Modifier.clickable { onSettingsClick() }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.Folder,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = activeWorkspaceName,
                                                color = Color.White,
                                                style = MaterialTheme.typography.labelMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.widthIn(max = 120.dp)
                                            )
                                        }
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(designTokens.chipCornerRadius),
                                    color = personaMood.cardSurface.copy(alpha = designTokens.bubbleSurfaceAlpha),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        themeColor.copy(alpha = if (isStreaming) streamPulse else 0.35f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .clip(CircleShape)
                                                .background(if (isStreaming) themeColor.copy(alpha = streamPulse) else Color(0xFF8A94A8))
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = if (isStreaming) "Streamt" else "Bereit",
                                            color = Color.White.copy(alpha = 0.95f),
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                }
                            }
                            if (activeExtensionsLabel.isNotBlank() || lastAppliedExtensionsLabel.isNotBlank()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 0.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (activeExtensionsLabel.isNotBlank()) {
                                        Surface(
                                            shape = RoundedCornerShape(designTokens.chipCornerRadius),
                                            color = Color.White.copy(alpha = designTokens.chipAlpha),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                                        ) {
                                            Text(
                                                text = "Extensions: $activeExtensionsLabel",
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                color = Color.White,
                                                style = MaterialTheme.typography.labelMedium,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                    if (lastAppliedExtensionsLabel.isNotBlank()) {
                                        Surface(
                                            shape = RoundedCornerShape(designTokens.chipCornerRadius),
                                            color = Color(0xFF15344E).copy(alpha = 0.65f),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, themeColor.copy(alpha = 0.42f))
                                        ) {
                                            Text(
                                                text = "Modus: $lastAppliedExtensionsLabel",
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                color = Color.White,
                                                style = MaterialTheme.typography.labelMedium,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                            if (!usageStatus.isPremium) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 0.dp),
                                    horizontalArrangement = Arrangement.Start
                                ) {
                                    Surface(
                                        modifier = Modifier.clickable { onUpgradeClick() },
                                        shape = RoundedCornerShape(designTokens.chipCornerRadius),
                                        color = Color.White.copy(alpha = designTokens.chipAlpha),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                                    ) {
                                        Text(
                                            text = "${usageStatus.tierLabel}-Plan · Nachrichten ${usageStatus.textUsed}/${usageStatus.textLimit} · Credits ${usageStatus.creditsBalance}",
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(headerBottomSpacer))
                        }
                    }
                }

                // Messages
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    // P1-11: Crossfade between empty state and message list so the first
                    // send doesn't snap-pop from welcome → typing indicator.
                    Crossfade(
                        targetState = messages.isEmpty() && !isLoading,
                        animationSpec = tween(durationMillis = 220),
                        label = "emptyStateCrossfade"
                    ) { showEmpty ->
                        if (showEmpty) {
                            Box(modifier = Modifier.fillMaxSize().graphicsLayer(alpha = messageContentAlpha)) {
                                EmptyChatState(themeColor, selectedPersona)
                            }
                        } else {
                            LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .nestedScroll(chatScrollCollapseConnection)
                                .graphicsLayer(alpha = messageContentAlpha),
                            contentPadding = PaddingValues(
                                start = designTokens.listHorizontalPadding,
                                top = 5.dp,
                                end = designTokens.listHorizontalPadding,
                                bottom = chatListBottomPadding
                            ),
                            verticalArrangement = Arrangement.spacedBy(designTokens.listVerticalSpacing)
                        ) {
                            if (hasOlderMessages) {
                                item(key = "load-older") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 4.dp),
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        AssistChip(
                                            onClick = onLoadOlderMessages,
                                            label = { Text("Ältere Nachrichten laden") }
                                        )
                                    }
                                }
                            }
                            itemsIndexed(
                                items = messages,
                                key = { _, message -> message.id },
                                contentType = { _, message -> if (message.isUser) "user" else "assistant" }
                            ) { index, message ->
                                val isRecentMessage = index >= messages.lastIndex - 12
                                ChatBubble(
                                    message = message,
                                    onSpeak = onSpeak,
                                    isSpeaking = isSpeechPlaybackActive && activeSpeechMessageId == message.id,
                                    themeColor = themeColor,
                                    surfaceColor = personaMood.cardSurface,
                                    fontSize = fontSize,
                                    showTimestamps = showTimestamps,
                                    showLiveSources = showLiveSources,
                                    animateIn = bubbleAnimations && isRecentMessage,
                                    animationDelayMs = (index.coerceAtMost(8) * 22),
                                    designPreset = designPreset,
                                    designTokens = designTokens
                                )
                            }
                            if (isLoading && !isStreaming) {
                                item { TypingIndicator(themeColor, bubbleAnimations) }
                            }
                            if (isLoading && activeToolCalls.isNotEmpty()) {
                                item { ToolCallsDisplay(activeToolCalls = activeToolCalls, themeColor = themeColor) }
                            }
                            }
                        }
                    }
                    if (messageOverlayAlpha > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = messageOverlayAlpha))
                        )
                    }
                }
                Box {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // P1-1: Stop button visible only while generation/streaming is in flight.
                        AnimatedVisibility(
                            visible = isLoading || isStreaming,
                            enter = fadeIn(tween(140)) + expandVertically(animationSpec = tween(160)),
                            exit = fadeOut(tween(100)) + shrinkVertically(animationSpec = tween(140))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(24.dp),
                                    color = Color.Black.copy(alpha = 0.55f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, themeColor.copy(alpha = 0.6f)),
                                    onClick = { onStopGeneration() },
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .semantics {
                                            contentDescription = "Generierung stoppen"
                                            role = Role.Button
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Stop,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = if (isStreaming) "Streaming stoppen" else "Generierung stoppen",
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    }
                                }
                            }
                        }
                        ChatInputBar(
                        inputText = inputText,
                        onInputChange = onInputChange,
                        onSend = onSend,
                        onImageGen = onImageGen,
                        onUpload = onUpload,
                        onTakePhoto = onTakePhoto,
                        selectedImageUri = selectedImageUri,
                        onClearImage = onClearImage,
                        isListening = isListening,
                        voicePushToTalkEnabled = voicePushToTalkEnabled,
                        onMicClick = onMicClick,
                        onMicPressStart = onMicPressStart,
                        onMicPressEnd = onMicPressEnd,
                        themeColor = themeColor,
                        surfaceColor = personaMood.cardSurface,
                        isLoading = isLoading,
                        onStopGeneration = onStopGeneration,
                        designTokens = designTokens,
                        connectChatBottomBars = connectChatBottomBars,
                        glassEffectsEnabled = glassEffectsEnabled,
                        uiCornerRoundnessScale = uiCornerScale,
                        uiShadowIntensityScale = uiShadowScale,
                        uiSurfaceOpacity = surfaceOpacity,
                        automationQuickActionsEnabled = automationQuickActionsEnabled,
                        selectedExtensionQuickAction = selectedExtensionQuickAction,
                        onSelectExtensionQuickAction = onSelectExtensionQuickAction,
                        compactMode = compactInputBarMode,
                        onCompactBottomNavVisibilityChange = { compactBottomNavVisible = it },
                        promptTemplates = com.example.bamachat.ui.component.defaultPromptTemplates,
                        onSelectPromptTemplate = {}
                    )
                    }
                }
                AnimatedVisibility(
                    visible = !isKeyboardOpen && (!compactInputBarMode || compactBottomNavVisible),
                    enter = fadeIn(tween(160)) + expandVertically(animationSpec = tween(180)),
                    exit = fadeOut(tween(120)) + shrinkVertically(animationSpec = tween(150))
                ) {
                    BamaChatBottomNav(
                        currentRoute = "chat",
                        designPreset = uiDesignPreset,
                        onNavigate = onBottomNavRoute,
                        attachedToComposer = true,
                        cornerRoundnessScale = uiCornerScale,
                        shadowIntensityScale = uiShadowScale,
                        surfaceOpacity = surfaceOpacity
                    )
                }
                }
            }
        }
    }
}




