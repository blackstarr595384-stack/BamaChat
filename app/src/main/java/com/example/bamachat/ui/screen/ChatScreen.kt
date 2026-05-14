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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bamachat.data.local.ConversationEntity
import com.example.bamachat.data.model.ChatMessage
import com.example.bamachat.ui.theme.AppDesignPalette
import com.example.bamachat.ui.theme.AppDesignSystem
import com.example.bamachat.ui.viewmodel.ChatViewModel
import com.example.bamachat.ui.viewmodel.SettingsViewModel
import com.example.bamachat.ui.viewmodel.MonetizationViewModel
import com.example.bamachat.util.CloudVoiceManager
import com.example.bamachat.util.PlayBillingManager
import dev.jeziellago.compose.markdowntext.MarkdownText
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private data class PersonaMood(
    val gradientTop: Color,
    val gradientBottom: Color,
    val cardSurface: Color,
    val userBubbleStart: Color,
    val userBubbleEnd: Color,
    val accent: Color
)

private enum class ChatDesignPreset {
    CURRENT,
    GLASS,
    EDITORIAL,
    DASHBOARD;

    companion object {
        fun fromSetting(value: String): ChatDesignPreset = when (value) {
            "Glassmorphism Pro" -> GLASS
            "Editorial Bold" -> EDITORIAL
            "Neo Dashboard" -> DASHBOARD
            else -> CURRENT
        }
    }
}

private data class ChatDesignTokens(
    val titleSizeSp: Int,
    val subtitleSizeSp: Int,
    val listHorizontalPadding: Dp,
    val listVerticalSpacing: Dp,
    val headerShadow: Dp,
    val chipCornerRadius: Dp,
    val chipAlpha: Float,
    val bubbleSurfaceAlpha: Float,
    val inputCornerRadius: Dp,
    val userBubbleRoundness: Dp,
    val assistantBubbleRoundness: Dp,
    val bubbleMaxWidth: Dp,
    val bubbleShadow: Dp
)

private fun designTokensFor(preset: ChatDesignPreset): ChatDesignTokens = when (preset) {
    ChatDesignPreset.GLASS -> ChatDesignTokens(
        titleSizeSp = 25,
        subtitleSizeSp = 11,
        listHorizontalPadding = 14.dp,
        listVerticalSpacing = 12.dp,
        headerShadow = 8.dp,
        chipCornerRadius = 24.dp,
        chipAlpha = 0.2f,
        bubbleSurfaceAlpha = 0.66f,
        inputCornerRadius = 30.dp,
        userBubbleRoundness = 18.dp,
        assistantBubbleRoundness = 18.dp,
        bubbleMaxWidth = 316.dp,
        bubbleShadow = 5.dp
    )
    ChatDesignPreset.EDITORIAL -> ChatDesignTokens(
        titleSizeSp = 28,
        subtitleSizeSp = 12,
        listHorizontalPadding = 16.dp,
        listVerticalSpacing = 14.dp,
        headerShadow = 10.dp,
        chipCornerRadius = 14.dp,
        chipAlpha = 0.14f,
        bubbleSurfaceAlpha = 0.9f,
        inputCornerRadius = 18.dp,
        userBubbleRoundness = 10.dp,
        assistantBubbleRoundness = 10.dp,
        bubbleMaxWidth = 340.dp,
        bubbleShadow = 8.dp
    )
    ChatDesignPreset.DASHBOARD -> ChatDesignTokens(
        titleSizeSp = 24,
        subtitleSizeSp = 11,
        listHorizontalPadding = 12.dp,
        listVerticalSpacing = 10.dp,
        headerShadow = 12.dp,
        chipCornerRadius = 10.dp,
        chipAlpha = 0.18f,
        bubbleSurfaceAlpha = 0.84f,
        inputCornerRadius = 16.dp,
        userBubbleRoundness = 12.dp,
        assistantBubbleRoundness = 12.dp,
        bubbleMaxWidth = 320.dp,
        bubbleShadow = 7.dp
    )
    ChatDesignPreset.CURRENT -> ChatDesignTokens(
        titleSizeSp = 22,
        subtitleSizeSp = 11,
        listHorizontalPadding = 14.dp,
        listVerticalSpacing = 10.dp,
        headerShadow = 12.dp,
        chipCornerRadius = 50.dp,
        chipAlpha = 0.16f,
        bubbleSurfaceAlpha = 0.86f,
        inputCornerRadius = 28.dp,
        userBubbleRoundness = 20.dp,
        assistantBubbleRoundness = 20.dp,
        bubbleMaxWidth = 300.dp,
        bubbleShadow = 8.dp
    )
}

private fun compactLabel(items: List<String>, maxItems: Int = 2): String {
    if (items.isEmpty()) return ""
    val unique = items.map { it.trim() }.filter { it.isNotBlank() }.distinct()
    if (unique.isEmpty()) return ""
    if (unique.size <= maxItems) return unique.joinToString(", ")
    val visible = unique.take(maxItems).joinToString(", ")
    return "$visible +${unique.size - maxItems}"
}

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    settingsViewModel: SettingsViewModel,
    onOpenMiniApps: () -> Unit = {},
    onOpenAgentHub: () -> Unit = {},
    onOpenComposeLab: () -> Unit = {}
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
    @Suppress("UNUSED_VARIABLE") val voiceChatMode by settingsViewModel.voiceChatMode.collectAsStateWithLifecycle()
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
    val speechRecognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }
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
            override fun onError(error: Int) { isListeningState.value = false }
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
                            inputText = ""
                            val imageUri = selectedImageUri
                            if (imageUri != null) {
                                viewModel.sendMessageWithImage(recognizedText, imageUri)
                                selectedImageUri = null
                            } else {
                                viewModel.sendMessage(recognizedText)
                            }
                        }
                    }
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
        SettingsDialog(viewModel = settingsViewModel, onDismiss = { showSettingsDialog = false })
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
            selectedImageUri = selectedImageUri,
            onClearImage = { selectedImageUri = null },
            isListening = isListening,
            voicePushToTalkEnabled = voicePushToTalkEnabled,
            onMicClick = {
                if (isListening) speechRecognizer.stopListening()
                else if (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    speechRecognizer.startListening(recognizerIntent)
                } else {
                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            onMicPressStart = {
                if (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    if (!isListening) speechRecognizer.startListening(recognizerIntent)
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
            activeExtensionNames = activeExtensionNames,
            lastAppliedExtensionNames = lastAppliedExtensionNames,
            selectedExtensionQuickAction = selectedExtensionQuickAction,
            onSelectExtensionQuickAction = { viewModel.setExtensionQuickAction(it) }
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
    activeExtensionNames: List<String>,
    lastAppliedExtensionNames: List<String>,
    selectedExtensionQuickAction: ChatViewModel.ExtensionQuickAction,
    onSelectExtensionQuickAction: (ChatViewModel.ExtensionQuickAction) -> Unit
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
                            EmptyState(themeColor, selectedPersona)
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
                    selectedExtensionQuickAction = selectedExtensionQuickAction,
                    onSelectExtensionQuickAction = onSelectExtensionQuickAction
                )
            }
        }
    }
}

@Composable
private fun EmptyState(themeColor: Color, persona: ChatViewModel.Persona) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .shadow(20.dp, CircleShape, spotColor = themeColor)
                .background(
                    Brush.radialGradient(listOf(themeColor, themeColor.copy(alpha = 0.3f))),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(persona.emoji, fontSize = 44.sp)
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "Hallo! Ich bin dein ${persona.displayName}",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Worüber möchtest du reden?",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 14.sp
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatInputBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: (String) -> Boolean,
    onImageGen: () -> Unit,
    onUpload: () -> Unit,
    selectedImageUri: Uri?,
    onClearImage: () -> Unit,
    isListening: Boolean,
    voicePushToTalkEnabled: Boolean,
    onMicClick: () -> Unit,
    onMicPressStart: () -> Unit,
    onMicPressEnd: () -> Unit,
    themeColor: Color,
    surfaceColor: Color,
    isLoading: Boolean,
    designTokens: ChatDesignTokens,
    connectChatBottomBars: Boolean,
    glassEffectsEnabled: Boolean,
    uiCornerRoundnessScale: Float,
    uiShadowIntensityScale: Float,
    uiSurfaceOpacity: Float,
    selectedExtensionQuickAction: ChatViewModel.ExtensionQuickAction,
    onSelectExtensionQuickAction: (ChatViewModel.ExtensionQuickAction) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val topRadius = (24f * uiCornerRoundnessScale).coerceIn(14f, 34f).dp
    val bottomRadius = (if (connectChatBottomBars) 0f else 14f * uiCornerRoundnessScale).coerceAtLeast(0f).dp
    val baseAlpha = if (glassEffectsEnabled) 0.74f else 0.9f
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .navigationBarsPadding()
            .imePadding()
            .animateContentSize(animationSpec = tween(durationMillis = 180, easing = LinearOutSlowInEasing)),
        color = surfaceColor.copy(alpha = (baseAlpha * uiSurfaceOpacity).coerceIn(0.55f, 1f)),
        shadowElevation = (22f * uiShadowIntensityScale).coerceIn(4f, 36f).dp,
        shape = RoundedCornerShape(topStart = topRadius, topEnd = topRadius, bottomStart = bottomRadius, bottomEnd = bottomRadius),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = if (glassEffectsEnabled) 0.14f else 0.08f))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            var localInputValue by remember { mutableStateOf(TextFieldValue(inputText)) }
            var sendLatchUntil by remember { mutableLongStateOf(0L) }
            LaunchedEffect(inputText) {
                if (inputText != localInputValue.text) {
                    localInputValue = TextFieldValue(
                        text = inputText,
                        selection = TextRange(inputText.length)
                    )
                }
            }
            // Image Preview
            if (selectedImageUri != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = selectedImageUri,
                        contentDescription = "Vorschau",
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(surfaceColor.copy(alpha = 0.9f))
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Bild angehängt",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onClearImage, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, "Entfernen", tint = Color.White.copy(alpha = 0.6f))
                    }
                }
            }
            val quickActionOptions = remember {
                listOf(
                    ChatViewModel.ExtensionQuickAction.AUTO,
                    ChatViewModel.ExtensionQuickAction.RESEARCH,
                    ChatViewModel.ExtensionQuickAction.CODE_REVIEW,
                    ChatViewModel.ExtensionQuickAction.PLAN
                )
            }

            val canSend = !isLoading && (localInputValue.text.trim().isNotEmpty() || selectedImageUri != null)
            val sendButtonColor by animateColorAsState(
                targetValue = if (canSend) themeColor else Color(0xFF35383D),
                animationSpec = tween(durationMillis = 160, easing = LinearOutSlowInEasing),
                label = "sendButtonColor"
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    quickActionOptions.forEach { action ->
                        FilterChip(
                            selected = selectedExtensionQuickAction == action,
                            onClick = { onSelectExtensionQuickAction(action) },
                            label = {
                                Text(
                                    text = action.label,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            leadingIcon = if (selectedExtensionQuickAction == action) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else {
                                null
                            }
                        )
                    }
                }
                val triggerSend: () -> Unit = send@{
                    val now = System.currentTimeMillis()
                    if (now < sendLatchUntil) return@send
                    val submitted = localInputValue.text
                    val hasInput = submitted.trim().isNotEmpty() || selectedImageUri != null
                    if (!isLoading && hasInput) {
                        val accepted = onSend(submitted)
                        if (accepted) {
                            sendLatchUntil = now + 220L
                            focusManager.clearFocus(force = true)
                            localInputValue = TextFieldValue("")
                            onInputChange("")
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextField(
                        value = localInputValue,
                        onValueChange = {
                            localInputValue = it
                            onInputChange(it.text)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 46.dp),
                        placeholder = { Text("Schreib was...", color = Color.White.copy(alpha = 0.4f)) },
                        label = { Text("Nachricht", color = Color.White.copy(alpha = 0.7f)) },
                        shape = RoundedCornerShape((designTokens.inputCornerRadius - 6.dp).coerceAtLeast(10.dp)),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = surfaceColor.copy(alpha = 0.95f),
                            unfocusedContainerColor = surfaceColor.copy(alpha = 0.9f),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = themeColor
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { triggerSend() }),
                        maxLines = 4
                    )

                    FilledIconButton(
                        onClick = { triggerSend() },
                        enabled = canSend,
                        modifier = Modifier
                            .size(48.dp)
                            .shadow(
                                elevation = if (canSend) 8.dp else 0.dp,
                                shape = CircleShape,
                                spotColor = themeColor.copy(alpha = 0.45f)
                            ),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = sendButtonColor,
                            contentColor = Color.White,
                            disabledContainerColor = Color(0xFF35383D),
                            disabledContentColor = Color.White.copy(alpha = 0.7f)
                        )
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                "Senden",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .semantics {
                                role = Role.Button
                                contentDescription = "Spracherkennung starten"
                            }
                            .clip(CircleShape)
                            .background(if (isListening) themeColor.copy(alpha = 0.25f) else surfaceColor.copy(alpha = 0.92f))
                            .then(
                                if (voicePushToTalkEnabled) {
                                    Modifier.pointerInput(isListening) {
                                        detectTapGestures(
                                            onPress = {
                                                onMicPressStart()
                                                tryAwaitRelease()
                                                onMicPressEnd()
                                            }
                                        )
                                    }
                                } else {
                                    Modifier.clickable { onMicClick() }
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isListening) VoiceVisualizer(themeColor)
                        else Icon(Icons.Default.Mic, "Diktieren", tint = Color.White.copy(alpha = 0.7f))
                    }

                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .semantics {
                                role = Role.Button
                                contentDescription = "Bild hochladen"
                            }
                            .clip(CircleShape)
                            .background(surfaceColor.copy(alpha = 0.92f))
                            .clickable { onUpload() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.AttachFile, "Hochladen", tint = Color.White.copy(alpha = 0.7f))
                    }

                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .semantics {
                                role = Role.Button
                                contentDescription = "Bild generieren"
                            }
                            .clip(CircleShape)
                            .background(surfaceColor.copy(alpha = 0.92f))
                            .clickable { onImageGen() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.AutoFixHigh, "Bild generieren", tint = themeColor.copy(alpha = 0.85f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(
    message: ChatMessage,
    onSpeak: (String) -> Unit,
    themeColor: Color,
    surfaceColor: Color,
    fontSize: Float,
    showTimestamps: Boolean = true,
    animateIn: Boolean = true,
    animationDelayMs: Int = 0,
    designPreset: ChatDesignPreset,
    designTokens: ChatDesignTokens
) {
    val isUser = message.isUser
    var visible by remember(message.id) { mutableStateOf(!animateIn) }
    LaunchedEffect(message.id, animateIn) {
        if (animateIn) {
            kotlinx.coroutines.delay(animationDelayMs.toLong())
            visible = true
        }
    }
    val bubbleAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 320),
        label = "bubbleAlpha"
    )
    val bubbleShift by animateDpAsState(
        targetValue = if (visible) 0.dp else 10.dp,
        animationSpec = tween(durationMillis = 320),
        label = "bubbleShift"
    )
    val bubbleShape = if (isUser) {
        RoundedCornerShape(
            topStart = designTokens.userBubbleRoundness,
            topEnd = 4.dp,
            bottomEnd = designTokens.userBubbleRoundness,
            bottomStart = designTokens.userBubbleRoundness
        )
    } else {
        RoundedCornerShape(
            topStart = 4.dp,
            topEnd = designTokens.assistantBubbleRoundness,
            bottomEnd = designTokens.assistantBubbleRoundness,
            bottomStart = designTokens.assistantBubbleRoundness
        )
    }
    val bubbleBrush = if (isUser) {
        when (designPreset) {
            ChatDesignPreset.GLASS -> Brush.horizontalGradient(
                listOf(Color(0xFF4F8CFF), Color(0xFF7F7FD5), Color(0xFF43C6AC))
            )
            ChatDesignPreset.EDITORIAL -> Brush.horizontalGradient(
                listOf(Color(0xFFB35134), Color(0xFF8E3D30))
            )
            ChatDesignPreset.DASHBOARD -> Brush.horizontalGradient(
                listOf(Color(0xFF0E7490), Color(0xFF2563EB))
            )
            ChatDesignPreset.CURRENT -> Brush.horizontalGradient(
                listOf(themeColor, themeColor.copy(alpha = 0.75f))
            )
        }
    } else {
        when (designPreset) {
            ChatDesignPreset.GLASS -> Brush.verticalGradient(
                listOf(surfaceColor.copy(alpha = 0.72f), surfaceColor.copy(alpha = 0.56f))
            )
            ChatDesignPreset.EDITORIAL -> Brush.verticalGradient(
                listOf(surfaceColor.copy(alpha = 0.98f), surfaceColor.copy(alpha = 0.92f))
            )
            ChatDesignPreset.DASHBOARD -> Brush.verticalGradient(
                listOf(surfaceColor.copy(alpha = 0.9f), surfaceColor.copy(alpha = 0.82f))
            )
            ChatDesignPreset.CURRENT -> Brush.verticalGradient(
                listOf(surfaceColor.copy(alpha = 0.98f), surfaceColor.copy(alpha = 0.8f))
            )
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = bubbleShift),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isUser) {
            // AI Avatar
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        when (designPreset) {
                            ChatDesignPreset.GLASS -> Brush.radialGradient(listOf(Color(0xFF8BB7FF), Color(0xFF4F8CFF)))
                            ChatDesignPreset.EDITORIAL -> Brush.radialGradient(listOf(Color(0xFFD17A52), Color(0xFF8E3D30)))
                            ChatDesignPreset.DASHBOARD -> Brush.radialGradient(listOf(Color(0xFF22D3EE), Color(0xFF0E7490)))
                            ChatDesignPreset.CURRENT -> Brush.radialGradient(listOf(themeColor, themeColor.copy(alpha = 0.5f)))
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
        }

        Surface(
            modifier = Modifier
                .widthIn(max = designTokens.bubbleMaxWidth)
                .shadow(designTokens.bubbleShadow, bubbleShape, spotColor = if (isUser) themeColor else surfaceColor.copy(alpha = 0.6f)),
            shape = bubbleShape,
            color = Color.Transparent
        ) {
            Box(modifier = Modifier.background(bubbleBrush).padding(14.dp).graphicsLayer(alpha = bubbleAlpha)) {
                Column {
                    // Generated Image or Uploaded Image
                    if (message.imageUrl != null) {
                        if (isUser) {
                            UploadedImageCard(message.imageUrl, message.text, themeColor)
                        } else {
                            GeneratedImageCard(message.imageUrl, message.text, themeColor)
                        }
                    } else if (isUser) {
                        Text(
                            text = message.text,
                            color = Color.White,
                            fontSize = fontSize.sp,
                            lineHeight = (fontSize * 1.35f).sp,
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        if (message.text.isBlank()) {
                            BlinkingDot(themeColor)
                        } else {
                            MarkdownText(
                                markdown = message.text,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = Color(0xFFEDEEF0),
                                    fontSize = fontSize.sp,
                                    lineHeight = (fontSize * 1.5f).sp
                                )
                            )
                            if (message.sources.isNotEmpty()) {
                                Spacer(Modifier.height(10.dp))
                                SourcesSection(
                                    sources = message.sources,
                                    fetchedAtIso = message.webFetchedAtIso,
                                    themeColor = themeColor
                                )
                            }
                        }
                        if (message.text.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { onSpeak(message.text) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    @Suppress("DEPRECATION")
                                    Icon(Icons.Default.VolumeUp, "Vorlesen",
                                        tint = themeColor, modifier = Modifier.size(16.dp))
                                }
                                if (showTimestamps) {
                                    Text(
                                        text = SimpleDateFormat("HH:mm", Locale.getDefault())
                                            .format(Date(message.timestamp)),
                                        color = Color.White.copy(alpha = 0.4f),
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (isUser) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier.size(32.dp).clip(CircleShape).background(Color(0xFF35383D)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

private fun sanitizeForSpeech(text: String): String {
    return text
        .replace(Regex("```[\\s\\S]*?```"), " ")
        .replace(Regex("`([^`]+)`"), "$1")
        .replace(Regex("\\[(.*?)\\]\\((.*?)\\)"), "$1")
        .replace(Regex("https?://\\S+"), " ")
        .replace(Regex("Quellen \\(Live-Recherche\\):[\\s\\S]*"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}

@Composable
private fun SourcesSection(
    sources: List<com.example.bamachat.data.model.ChatSource>,
    fetchedAtIso: String?,
    themeColor: Color
) {
    val context = LocalContext.current
    val fetchedLabel = fetchedAtIso?.takeIf { it.isNotBlank() }?.let { "Stand: $it" }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Live-Quellen",
            color = themeColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        if (fetchedLabel != null) {
            Text(
                text = fetchedLabel,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 10.sp
            )
        }
        sources.take(4).forEachIndexed { index, source ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.White.copy(alpha = 0.06f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val openIntent = Intent(Intent.ACTION_VIEW, Uri.parse(source.url))
                        context.startActivity(openIntent)
                    }
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.Public,
                            contentDescription = null,
                            tint = themeColor,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "${index + 1}. ${source.title}",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    if (source.snippet.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = source.snippet,
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 10.sp,
                            maxLines = 3,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Suppress("UNUSED_PARAMETER")
@Composable
private fun UploadedImageCard(imageUrl: String, caption: String, _themeColor: Color) {
    Column {
        // Image
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1E2024)),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = caption,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
        }

        // Caption text
        if (caption.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                caption,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(Modifier.height(4.dp))
        Text(
            text = SimpleDateFormat("HH:mm", Locale.getDefault())
                .format(Date(System.currentTimeMillis())),
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 10.sp
        )
    }
}

@Composable
private fun GeneratedImageCard(imageUrl: String, prompt: String, themeColor: Color) {
    var imageLoaded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column {
        // Image
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1E2024)),
            contentAlignment = Alignment.Center
        ) {
            if (!imageLoaded) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = themeColor,
                        strokeWidth = 3.dp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Generiere Bild...",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                }
            }
            coil.compose.AsyncImage(
                model = imageUrl,
                contentDescription = prompt,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        val shareIntent = Intent(Intent.ACTION_VIEW, Uri.parse(imageUrl))
                        context.startActivity(shareIntent)
                    },
                onSuccess = { imageLoaded = true },
                onError = { imageLoaded = true }
            )
        }

        // Prompt text
        if (prompt.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Prompt: $prompt",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 11.sp,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = {
                    val shareIntent = Intent(Intent.ACTION_VIEW, Uri.parse(imageUrl))
                    context.startActivity(shareIntent)
                },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, "Öffnen",
                    tint = themeColor, modifier = Modifier.size(16.dp))
            }
            Text(
                text = SimpleDateFormat("HH:mm", Locale.getDefault())
                    .format(Date(System.currentTimeMillis())),
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun BlinkingDot(themeColor: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "blink")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "alpha"
    )
    Box(
        modifier = Modifier.size(8.dp).clip(CircleShape).background(themeColor.copy(alpha = alpha))
    )
}

@Composable
private fun TypingIndicator(themeColor: Color, animated: Boolean = true) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(32.dp).clip(CircleShape)
                .background(Brush.radialGradient(listOf(themeColor, themeColor.copy(alpha = 0.5f)))),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(8.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF272A2F)),
            shape = RoundedCornerShape(4.dp, 20.dp, 20.dp, 20.dp)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                repeat(3) { i ->
                    if (animated) {
                        val infiniteTransition = rememberInfiniteTransition(label = "dot$i")
                        val scale by infiniteTransition.animateFloat(
                            initialValue = 0.5f, targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                tween(600, delayMillis = i * 150), RepeatMode.Reverse
                            ), label = "scale$i"
                        )
                        Box(
                            modifier = Modifier
                                .size((6 * scale).dp)
                                .clip(CircleShape)
                                .background(themeColor.copy(alpha = scale))
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(themeColor.copy(alpha = 0.7f))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceVisualizer(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "voice")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Reverse),
        label = "progress"
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(3) { index ->
            val height = 4.dp + (16.dp * (progress * (index + 1) % 1f))
            Box(modifier = Modifier.width(3.dp).height(height).background(color, RoundedCornerShape(2.dp)))
        }
    }
}

@Composable
private fun ChatDrawer(
    conversations: List<ConversationEntity>,
    currentId: String?,
    themeColor: Color,
    palette: AppDesignPalette,
    glassEffectsEnabled: Boolean,
    cornerRoundnessScale: Float,
    shadowIntensityScale: Float,
    surfaceOpacity: Float,
    onSelect: (String) -> Unit,
    onNewChat: () -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit
) {
    var renamingId by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }

    val drawerCorner = (24f * cornerRoundnessScale).coerceIn(14f, 34f).dp
    val drawerShadow = (18f * shadowIntensityScale).coerceIn(4f, 34f).dp
    val drawerAlphaTop = if (glassEffectsEnabled) 0.96f else 1f
    val drawerAlphaMid = if (glassEffectsEnabled) 0.92f else 1f
    val drawerAlphaBottom = if (glassEffectsEnabled) 0.95f else 1f

    ModalDrawerSheet(
        modifier = Modifier
            .fillMaxWidth(0.82f)
            .shadow(drawerShadow, RoundedCornerShape(topEnd = drawerCorner, bottomEnd = drawerCorner))
            .clip(RoundedCornerShape(topEnd = drawerCorner, bottomEnd = drawerCorner))
            .border(
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = if (glassEffectsEnabled) 0.12f else 0.07f)),
                shape = RoundedCornerShape(topEnd = drawerCorner, bottomEnd = drawerCorner)
            ),
        drawerContainerColor = Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            palette.chatBgTop.copy(alpha = 0.96f),
                            palette.chatBgTop.copy(alpha = drawerAlphaTop * surfaceOpacity),
                            palette.chatBgMid.copy(alpha = drawerAlphaMid * surfaceOpacity),
                            palette.chatBgBottom.copy(alpha = drawerAlphaBottom * surfaceOpacity)
                        )
                    )
                )
        ) {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                palette.chatHeaderStart.copy(alpha = 0.98f),
                                palette.chatHeaderMid.copy(alpha = 0.95f),
                                palette.chatHeaderEnd.copy(alpha = 0.98f)
                            )
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .statusBarsPadding()
            ) {
                Column {
                    Text("BamaChat", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Deine Chats", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                }
            }

            // New Chat Button
            Button(
                onClick = onNewChat,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = palette.accentStrong),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp, pressedElevation = 3.dp)
            ) {
                Icon(Icons.Default.Add, null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("Neuer Chat", color = Color.White, fontWeight = FontWeight.SemiBold)
            }

            // Chat List
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(conversations, key = { it.id }) { conv ->
                    ConversationRow(
                        conv = conv,
                        isActive = conv.id == currentId,
                        themeColor = themeColor,
                        palette = palette,
                        glassEffectsEnabled = glassEffectsEnabled,
                        cornerRoundnessScale = cornerRoundnessScale,
                        shadowIntensityScale = shadowIntensityScale,
                        surfaceOpacity = surfaceOpacity,
                        onClick = { onSelect(conv.id) },
                        onRename = {
                            renamingId = conv.id
                            renameText = conv.title
                        },
                        onDelete = { onDelete(conv.id) }
                    )
                }
            }

            // Credit Tag
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "made by Mamadou Dian Baldé w/AI",
                    color = Color.White.copy(alpha = 0.35f),
                    fontSize = 10.sp
                )
            }
        }
    }

    // Rename Dialog
    renamingId?.let { id ->
        AlertDialog(
            onDismissRequest = { renamingId = null },
            title = { Text("Chat umbenennen") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRename(id, renameText)
                    renamingId = null
                }) { Text("Speichern") }
            },
            dismissButton = {
                TextButton(onClick = { renamingId = null }) { Text("Abbrechen") }
            }
        )
    }
}

@Composable
private fun ConversationRow(
    conv: ConversationEntity,
    isActive: Boolean,
    themeColor: Color,
    palette: AppDesignPalette,
    glassEffectsEnabled: Boolean,
    cornerRoundnessScale: Float,
    shadowIntensityScale: Float,
    surfaceOpacity: Float,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val rowCorner = (14f * cornerRoundnessScale).coerceIn(10f, 24f).dp
    val rowShadowBase = if (isActive) 10f else 2f
    val rowShadow = (rowShadowBase * shadowIntensityScale).coerceIn(1.2f, 20f).dp
    val inactiveAlpha = if (glassEffectsEnabled) 0.05f else 0.08f
    val activeAlpha = (if (glassEffectsEnabled) 0.72f else 0.88f) * surfaceOpacity

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(rowShadow, RoundedCornerShape(rowCorner))
            .clip(RoundedCornerShape(rowCorner))
            .clickable { onClick() },
        color = if (isActive) {
            palette.chatAssistantSurface.copy(alpha = activeAlpha.coerceIn(0.55f, 1f))
        } else {
            Color.White.copy(alpha = inactiveAlpha)
        },
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isActive) themeColor.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.12f)
        ),
        shape = RoundedCornerShape(rowCorner)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Chat,
                null,
                tint = if (isActive) themeColor else Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    conv.title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    SimpleDateFormat("dd.MM. HH:mm", Locale.getDefault()).format(Date(conv.updatedAt)),
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 10.sp
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.MoreVert, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Umbenennen") },
                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                        onClick = { onRename(); menuExpanded = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Löschen") },
                        leadingIcon = { Icon(Icons.Default.Delete, null) },
                        onClick = { onDelete(); menuExpanded = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun PremiumPaywallDialog(
    settingsViewModel: SettingsViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val billingReady by settingsViewModel.billingReady.collectAsStateWithLifecycle()
    val purchaseInProgress by settingsViewModel.purchaseInProgress.collectAsStateWithLifecycle()
    val isPremiumActive by settingsViewModel.isPremiumActive.collectAsStateWithLifecycle()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("BamaChat Premium", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (isPremiumActive) "Premium ist bereits aktiv. Du hast unbegrenzte Nutzung."
                    else "Free-Plan-Limit erreicht. Mit Premium werden Tageslimits entfernt und neue Profi-Funktionen freigeschaltet.",
                    fontSize = 13.sp
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = {
                            val activity = context as? android.app.Activity ?: return@AssistChip
                            settingsViewModel.startSubscriptionCheckout(activity, PlayBillingManager.PLAN_PRO)
                        },
                        label = { Text("Basic") },
                        enabled = !isPremiumActive && billingReady && !purchaseInProgress
                    )
                    AssistChip(
                        onClick = {
                            val activity = context as? android.app.Activity ?: return@AssistChip
                            settingsViewModel.startSubscriptionCheckout(activity, PlayBillingManager.PLAN_PRO)
                        },
                        label = { Text("Pro") },
                        enabled = !isPremiumActive && billingReady && !purchaseInProgress
                    )
                    AssistChip(
                        onClick = {
                            val activity = context as? android.app.Activity ?: return@AssistChip
                            settingsViewModel.startSubscriptionCheckout(activity, PlayBillingManager.PLAN_EXPERT)
                        },
                        label = { Text("Expert") },
                        enabled = !isPremiumActive && billingReady && !purchaseInProgress
                    )
                }

                if (!billingReady) {
                    Text(
                        "Play Billing ist aktuell nicht bereit. Prüfe Play Store/Tester-Konto oder nutze vorübergehend den lokalen Premium-Test in Einstellungen.",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                settingsViewModel.refreshBillingState()
                onDismiss()
            }) { Text("Schließen") }
        }
    )
}

