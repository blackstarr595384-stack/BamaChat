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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
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
import com.example.bamachat.ui.component.ChatDesignPreset
import com.example.bamachat.ui.component.ChatDesignTokens
import com.example.bamachat.ui.component.ChatDrawer
import com.example.bamachat.ui.component.ChatInputBar
import com.example.bamachat.ui.component.EmptyChatState
import com.example.bamachat.ui.component.PremiumPaywallDialog
import com.example.bamachat.ui.component.TypingIndicator
import com.example.bamachat.ui.component.compactLabel
import com.example.bamachat.ui.component.designTokensFor
import com.example.bamachat.ui.component.sanitizeForSpeech
import com.example.bamachat.ui.theme.AppDesignSystem
import com.example.bamachat.ui.viewmodel.ChatViewModel
import com.example.bamachat.ui.viewmodel.SettingsViewModel
import com.example.bamachat.ui.viewmodel.MonetizationViewModel
import com.example.bamachat.ui.viewmodel.ToolCallProgress
import com.example.bamachat.ui.viewmodel.ToolCallStatus
import com.example.bamachat.util.CloudVoiceManager
import dev.jeziellago.compose.markdowntext.MarkdownText
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.io.File

private data class PersonaMood(
    val gradientTop: Color,
    val gradientBottom: Color,
    val cardSurface: Color,
    val userBubbleStart: Color,
    val userBubbleEnd: Color,
    val accent: Color
)

private fun moodForPersona(
    persona: ChatViewModel.Persona,
    baseAccent: Color,
    sentiment: String
): PersonaMood {
    val accent = when (sentiment) {
        "positive" -> Color(0xFF0FB57A)
        "negative" -> Color(0xFFE8505B)
        else -> baseAccent
    }
    return when (persona) {
        ChatViewModel.Persona.DEVELOPER -> PersonaMood(
            gradientTop = Color(0xFF0F1424),
            gradientBottom = Color(0xFF131A2B),
            cardSurface = Color(0xFF1A2236),
            userBubbleStart = accent,
            userBubbleEnd = Color(0xFF3D7DFF),
            accent = Color(0xFF4CC9FF)
        )
        ChatViewModel.Persona.TEACHER -> PersonaMood(
            gradientTop = Color(0xFF1C1522),
            gradientBottom = Color(0xFF221A2B),
            cardSurface = Color(0xFF2C2038),
            userBubbleStart = accent,
            userBubbleEnd = Color(0xFF9F6DFF),
            accent = Color(0xFFE6C15A)
        )
        ChatViewModel.Persona.CHEF -> PersonaMood(
            gradientTop = Color(0xFF2A1612),
            gradientBottom = Color(0xFF2F1D15),
            cardSurface = Color(0xFF3A261C),
            userBubbleStart = accent,
            userBubbleEnd = Color(0xFFE27A3D),
            accent = Color(0xFFFFB157)
        )
        ChatViewModel.Persona.FITNESS -> PersonaMood(
            gradientTop = Color(0xFF101B16),
            gradientBottom = Color(0xFF13231B),
            cardSurface = Color(0xFF1D3127),
            userBubbleStart = accent,
            userBubbleEnd = Color(0xFF37C887),
            accent = Color(0xFF6FE5B1)
        )
        ChatViewModel.Persona.TRANSLATOR -> PersonaMood(
            gradientTop = Color(0xFF101D26),
            gradientBottom = Color(0xFF142530),
            cardSurface = Color(0xFF1E3442),
            userBubbleStart = accent,
            userBubbleEnd = Color(0xFF41A1FF),
            accent = Color(0xFF79C8FF)
        )
        ChatViewModel.Persona.THERAPIST -> PersonaMood(
            gradientTop = Color(0xFF121E1B),
            gradientBottom = Color(0xFF152721),
            cardSurface = Color(0xFF22372F),
            userBubbleStart = accent,
            userBubbleEnd = Color(0xFF57BFA2),
            accent = Color(0xFF9ADCCB)
        )
        ChatViewModel.Persona.CUSTOM -> PersonaMood(
            gradientTop = Color(0xFF1A1623),
            gradientBottom = Color(0xFF1D1B2B),
            cardSurface = Color(0xFF2B2440),
            userBubbleStart = accent,
            userBubbleEnd = Color(0xFF7E6DFF),
            accent = Color(0xFFBAA8FF)
        )
        else -> PersonaMood(
            gradientTop = Color(0xFF10151F),
            gradientBottom = Color(0xFF161B26),
            cardSurface = Color(0xFF202838),
            userBubbleStart = accent,
            userBubbleEnd = Color(0xFF4E7BFF),
            accent = Color(0xFF82A6FF)
        )
    }
}

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
    val ttsEnabled by settingsViewModel.ttsEnabled.collectAsStateWithLifecycle()
    val ttsSpeed by settingsViewModel.ttsSpeed.collectAsStateWithLifecycle()
    val ttsPitch by settingsViewModel.ttsPitch.collectAsStateWithLifecycle()
    val ttsProVoiceEnabled by settingsViewModel.ttsProVoiceEnabled.collectAsStateWithLifecycle()
    val cloudVoiceEnabled by settingsViewModel.cloudVoiceEnabled.collectAsStateWithLifecycle()
    val elevenLabsApiKey by settingsViewModel.elevenLabsApiKey.collectAsStateWithLifecycle()
    val elevenLabsVoiceId by settingsViewModel.elevenLabsVoiceId.collectAsStateWithLifecycle()
    val elevenLabsModelId by settingsViewModel.elevenLabsModelId.collectAsStateWithLifecycle()
    val voicePushToTalkEnabled by settingsViewModel.voicePushToTalkEnabled.collectAsStateWithLifecycle()
    val voiceChatMode by settingsViewModel.voiceChatMode.collectAsStateWithLifecycle()
    val automationQuickActionsEnabled by settingsViewModel.automationQuickActionsEnabled.collectAsStateWithLifecycle()
    val activeWorkspaceName by settingsViewModel.activeWorkspaceName.collectAsStateWithLifecycle()
    val workspaceChatFilterEnabled by settingsViewModel.workspaceChatFilterEnabled.collectAsStateWithLifecycle()
    val autoSendVoice by settingsViewModel.autoSendVoice.collectAsStateWithLifecycle()
    val showTimestamps by settingsViewModel.showTimestamps.collectAsStateWithLifecycle()
    val bubbleAnimations by settingsViewModel.bubbleAnimations.collectAsStateWithLifecycle()
    val uiDesignPreset by settingsViewModel.uiDesignPreset.collectAsStateWithLifecycle()
    val compactChatHeader by settingsViewModel.compactChatHeader.collectAsStateWithLifecycle()
    val connectChatBottomBars by settingsViewModel.connectChatBottomBars.collectAsStateWithLifecycle()
    val glassEffectsEnabled by settingsViewModel.glassEffectsEnabled.collectAsStateWithLifecycle()
    val uiCornerRoundnessScale by settingsViewModel.uiCornerRoundnessScale.collectAsStateWithLifecycle()
    val uiShadowIntensityScale by settingsViewModel.uiShadowIntensityScale.collectAsStateWithLifecycle()
    val uiSurfaceOpacity by settingsViewModel.uiSurfaceOpacity.collectAsStateWithLifecycle()
    val isPremiumActive by settingsViewModel.isPremiumActive.collectAsStateWithLifecycle()
    @Suppress("UNUSED_VARIABLE") val notificationsEnabled by settingsViewModel.notificationsEnabled.collectAsStateWithLifecycle()

    var inputText by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
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

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
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
    DisposableEffect(Unit) {
        lateinit var ttsInstance: TextToSpeech
        ttsInstance = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val langCode = settingsViewModel.language.value
                val locale = when (langCode) {
                    "en" -> Locale.ENGLISH
                    "fr" -> Locale.FRENCH
                    "es" -> Locale("es")
                    "tr" -> Locale("tr")
                    "ar" -> Locale("ar")
                    else -> Locale.GERMAN
                }
                ttsInstance.language = locale
                ttsInstance.setSpeechRate(ttsSpeed)
                ttsInstance.setPitch(ttsPitch)
            }
        }
        tts = ttsInstance
        onDispose {
            ttsInstance.stop()
            ttsInstance.shutdown()
            cloudVoiceManager.release()
        }
    }
    LaunchedEffect(ttsSpeed, ttsPitch) {
        tts?.setSpeechRate(ttsSpeed)
        tts?.setPitch(ttsPitch)
    }
    val onSpeak: (String) -> Unit = { text ->
        val speakText = sanitizeForSpeech(text)
        scope.launch {
            val useCloudVoice = ttsProVoiceEnabled &&
                cloudVoiceEnabled &&
                elevenLabsApiKey.isNotBlank() &&
                elevenLabsVoiceId.isNotBlank()
            if (useCloudVoice) {
                val cloudOk = runCatching {
                    cloudVoiceManager.speakWithElevenLabs(
                        text = speakText,
                        apiKey = elevenLabsApiKey,
                        voiceId = elevenLabsVoiceId,
                        modelId = elevenLabsModelId
                    )
                }.getOrDefault(false)
                if (cloudOk) return@launch
            }
            if (ttsEnabled) {
                tts?.speak(speakText, TextToSpeech.QUEUE_FLUSH, null, null)
            }
        }
    }

    // Auto-speak last AI message when TTS enabled (track by ID to avoid re-speaking)
    var lastSpokenMessageId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(
        messages.size,
        ttsEnabled,
        ttsProVoiceEnabled,
        cloudVoiceEnabled,
        elevenLabsApiKey,
        elevenLabsVoiceId
    ) {
        val canSpeak = ttsEnabled || (ttsProVoiceEnabled && cloudVoiceEnabled && elevenLabsApiKey.isNotBlank() && elevenLabsVoiceId.isNotBlank())
        if (canSpeak && messages.isNotEmpty()) {
            val last = messages.last()
            if (!last.isUser && last.text.isNotBlank() && last.id != lastSpokenMessageId) {
                lastSpokenMessageId = last.id
                onSpeak(last.text)
            }
        }
    }

    // STT
    val isListeningState = remember { mutableStateOf(false) }
    var isListening by isListeningState
    var lastPartialUpdateAt by remember { mutableLongStateOf(0L) }
    var lastVoiceAutoSendAt by remember { mutableLongStateOf(0L) }
    var lastVoiceAutoSendText by remember { mutableStateOf("") }
    var hasHadVoiceExchange by remember { mutableStateOf(false) }
    val speechRecognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }
    val speechRecognitionAvailable = remember { SpeechRecognizer.isRecognitionAvailable(context) }
    val recognizerIntent = remember {
        val langCode = settingsViewModel.language.value
        val locale = when (langCode) {
            "en" -> Locale.ENGLISH
            "fr" -> Locale.FRENCH
            "es" -> Locale("es")
            "tr" -> Locale("tr")
            "ar" -> Locale("ar")
            else -> Locale.GERMAN
        }
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, locale.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
    }
    val audioPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            speechRecognizer.startListening(recognizerIntent)
        } else {
            Toast.makeText(
                context,
                "Mikrofon-Berechtigung wurde abgelehnt.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    DisposableEffect(Unit) {
        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { isListeningState.value = true }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { isListeningState.value = false }
            override fun onError(error: Int) {
                isListeningState.value = false
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
                val data = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!data.isNullOrEmpty()) {
                    val recognizedText = data[0].trim()
                    inputText = recognizedText
                    if (autoSendVoice) {
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
        speechRecognizer.setRecognitionListener(listener)
        onDispose { speechRecognizer.destroy() }
    }
    // Sync state back to Compose
    LaunchedEffect(isListeningState.value) { isListening = isListeningState.value }

    // Continuous voice mode: re-trigger listening when AI finishes streaming
    LaunchedEffect(isStreaming, voiceChatMode) {
        if (voiceChatMode && !isStreaming && hasHadVoiceExchange) {
            val audioOk = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            if (audioOk) speechRecognizer.startListening(recognizerIntent)
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

    // Auto-scroll
    var autoScrollTailId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(messages.lastOrNull()?.id, isStreaming) {
        val tailId = messages.lastOrNull()?.id ?: return@LaunchedEffect
        if (tailId != autoScrollTailId || isStreaming) {
            autoScrollTailId = tailId
            scope.launch {
                val targetIndex = messages.lastIndex
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
                if (inputText.isNotBlank()) {
                    viewModel.generateImage(inputText)
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
                } else if (isListening) {
                    speechRecognizer.stopListening()
                } else if (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    Toast.makeText(context, "Sprich jetzt...", Toast.LENGTH_SHORT).show()
                    speechRecognizer.startListening(recognizerIntent)
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
                    if (!isListening) {
                        Toast.makeText(context, "Sprich jetzt...", Toast.LENGTH_SHORT).show()
                        speechRecognizer.startListening(recognizerIntent)
                    }
                } else {
                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            onMicPressEnd = {
                if (isListening) speechRecognizer.stopListening()
            },
            themeColor = themeColor,
            personaMood = personaMood,
            fontSize = fontSize,
            selectedPersona = selectedPersona,
            aiProvider = aiProvider,
            onPersonaClick = { showPersonaDialog = true },
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
            _onClearClick = { viewModel.clearChat() },
            onSpeak = onSpeak,
            listState = listState,
            snackbarHostState = snackbarHostState,
            showTimestamps = showTimestamps,
            bubbleAnimations = bubbleAnimations,
            usageStatus = usageStatus,
            onUpgradeClick = { viewModel.openPaywall() },
            hasOlderMessages = hasOlderMessages,
            onLoadOlderMessages = { viewModel.loadOlderMessages() },
            uiDesignPreset = uiDesignPreset,
            compactChatHeader = compactChatHeader,
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
@OptIn(ExperimentalMaterial3Api::class)
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
    onPersonaClick: () -> Unit,
    onSearchClick: () -> Unit,
    onMenuClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onMiniAppsClick: () -> Unit,
    onAgentHubClick: () -> Unit,
    onComposeLabClick: () -> Unit,
    onShareClick: () -> Unit,
    _onClearClick: () -> Unit,
    onSpeak: (String) -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
    snackbarHostState: SnackbarHostState,
    showTimestamps: Boolean,
    bubbleAnimations: Boolean,
    usageStatus: MonetizationViewModel.UsageStatus,
    onUpgradeClick: () -> Unit,
    hasOlderMessages: Boolean,
    onLoadOlderMessages: () -> Unit,
    uiDesignPreset: String,
    compactChatHeader: Boolean,
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
    val headerVerticalPadding = if (compactChatHeader) 4.dp else 8.dp
    val headerBottomSpacer = if (compactChatHeader) 1.dp else 6.dp
    val headerTitleStyle = if (compactChatHeader) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge

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
    var topMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Color.Transparent
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues).background(backgroundGradient)) {
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
                                        maxLines = 1
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
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = headerVerticalPadding),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(designTokens.chipCornerRadius),
                                    color = Color.White.copy(alpha = designTokens.chipAlpha),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                                ) {
                                    Text(
                                        text = "Provider: $aiProvider",
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(designTokens.chipCornerRadius),
                                    color = Color.Black.copy(alpha = 0.24f),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        when (designPreset) {
                                            ChatDesignPreset.GLASS -> Color(0xFF9FCBFF).copy(alpha = 0.7f)
                                            ChatDesignPreset.EDITORIAL -> Color(0xFFFFB08A).copy(alpha = 0.7f)
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
                    if (messages.isEmpty() && !isLoading) {
                        Box(modifier = Modifier.fillMaxSize().graphicsLayer(alpha = messageContentAlpha)) {
                            EmptyChatState(themeColor, selectedPersona)
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(alpha = messageContentAlpha),
                            contentPadding = PaddingValues(horizontal = designTokens.listHorizontalPadding, vertical = 12.dp),
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
                                    themeColor = themeColor,
                                    surfaceColor = personaMood.cardSurface,
                                    fontSize = fontSize,
                                    showTimestamps = showTimestamps,
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
                    if (messageOverlayAlpha > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = messageOverlayAlpha))
                        )
                    }
                }

                // Input bar
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
                    designTokens = designTokens,
                    connectChatBottomBars = connectChatBottomBars,
                    glassEffectsEnabled = glassEffectsEnabled,
                    uiCornerRoundnessScale = uiCornerScale,
                    uiShadowIntensityScale = uiShadowScale,
                    uiSurfaceOpacity = surfaceOpacity,
                    automationQuickActionsEnabled = automationQuickActionsEnabled,
                    selectedExtensionQuickAction = selectedExtensionQuickAction,
                    onSelectExtensionQuickAction = onSelectExtensionQuickAction,
                    promptTemplates = com.example.bamachat.ui.component.defaultPromptTemplates,
                    onSelectPromptTemplate = {}
                )
            }
        }
    }
}

@Composable
private fun ToolCallsDisplay(activeToolCalls: List<ToolCallProgress>, themeColor: Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        activeToolCalls.forEach { tc ->
            val icon = when (tc.status) {
                ToolCallStatus.RUNNING -> "◌"
                ToolCallStatus.DONE -> "✓"
                ToolCallStatus.ERROR -> "✗"
            }
            val surface = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = surface,
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(icon, fontSize = 14.sp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = tc.toolName,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = tc.arguments.take(80),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (tc.status == ToolCallStatus.RUNNING) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = themeColor
                        )
                    }
                }
            }
        }
    }
}
