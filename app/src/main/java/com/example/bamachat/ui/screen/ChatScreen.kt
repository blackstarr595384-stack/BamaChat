package com.example.bamachat.ui.screen

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.bamachat.data.local.ConversationEntity
import com.example.bamachat.data.model.ChatMessage
import com.example.bamachat.ui.viewmodel.ChatViewModel
import com.example.bamachat.ui.viewmodel.SettingsViewModel
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
    val messages by viewModel.messages.collectAsState()
    val conversations by viewModel.conversations.collectAsState()
    val currentConvId by viewModel.currentConversationId.collectAsState()
    val selectedPersona by viewModel.selectedPersona.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isStreaming by viewModel.isStreaming.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val chatSentiment by viewModel.chatSentiment.collectAsState()
    val usageStatus by viewModel.usageStatus.collectAsState()
    val showPaywall by viewModel.showPaywall.collectAsState()

    val isBiometricEnabled by settingsViewModel.isBiometricEnabled.collectAsState()
    val primaryColorInt by settingsViewModel.primaryColorInt.collectAsState()
    val fontSize by settingsViewModel.fontSize.collectAsState()
    val aiProvider by settingsViewModel.aiProvider.collectAsState()
    val ttsEnabled by settingsViewModel.ttsEnabled.collectAsState()
    val ttsSpeed by settingsViewModel.ttsSpeed.collectAsState()
    @Suppress("UNUSED_VARIABLE") val voiceChatMode by settingsViewModel.voiceChatMode.collectAsState()
    val autoSendVoice by settingsViewModel.autoSendVoice.collectAsState()
    val showTimestamps by settingsViewModel.showTimestamps.collectAsState()
    val bubbleAnimations by settingsViewModel.bubbleAnimations.collectAsState()
    val uiDesignPreset by settingsViewModel.uiDesignPreset.collectAsState()
    val isPremiumActive by settingsViewModel.isPremiumActive.collectAsState()
    @Suppress("UNUSED_VARIABLE") val notificationsEnabled by settingsViewModel.notificationsEnabled.collectAsState()

    var inputText by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val imagePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
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
            }
        }
        tts = ttsInstance
        onDispose { ttsInstance.stop(); ttsInstance.shutdown() }
    }
    LaunchedEffect(ttsSpeed) {
        tts?.setSpeechRate(ttsSpeed)
    }
    val onSpeak: (String) -> Unit = { text ->
        if (ttsEnabled) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    // Auto-speak last AI message when TTS enabled (track by ID to avoid re-speaking)
    var lastSpokenMessageId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(messages.size, ttsEnabled) {
        if (ttsEnabled && messages.isNotEmpty()) {
            val last = messages.last()
            if (!last.isUser && last.text.isNotBlank() && last.id != lastSpokenMessageId) {
                lastSpokenMessageId = last.id
                tts?.speak(last.text, TextToSpeech.QUEUE_FLUSH, null, null)
            }
        }
    }

    // STT
    val isListeningState = remember { mutableStateOf(false) }
    var isListening by isListeningState
    val inputTextState = remember { mutableStateOf("") }
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
                    inputTextState.value = recognizedText
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
                            inputTextState.value = ""
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
                    inputTextState.value = partialText
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
    LaunchedEffect(inputTextState.value) { inputText = inputTextState.value }

    // Theme colors
    val baseColor = Color(primaryColorInt)
    val personaMood = remember(selectedPersona, baseColor, chatSentiment) {
        moodForPersona(selectedPersona, baseColor, chatSentiment)
    }
    val themeColor by animateColorAsState(
        targetValue = personaMood.accent,
        animationSpec = tween(800), label = "themeColor"
    )

    // Auto-scroll
    LaunchedEffect(messages.size, isStreaming) {
        if (messages.isNotEmpty()) {
            scope.launch { listState.animateScrollToItem(messages.size - 1) }
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

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ChatDrawer(
                conversations = conversations,
                currentId = currentConvId,
                themeColor = themeColor,
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
            onSend = {
                val imageUri = selectedImageUri
                if (imageUri != null) {
                    viewModel.sendMessageWithImage(inputText, imageUri)
                    selectedImageUri = null
                } else if (inputText.isNotBlank()) {
                    viewModel.sendMessage(inputText)
                }
                inputText = ""
            },
            onImageGen = {
                if (inputText.isNotBlank()) {
                    viewModel.generateImage(inputText)
                    inputText = ""
                }
            },
            onUpload = { imagePickerLauncher.launch("image/*") },
            selectedImageUri = selectedImageUri,
            onClearImage = { selectedImageUri = null },
            isListening = isListening,
            onMicClick = {
                if (isListening) speechRecognizer.stopListening()
                else speechRecognizer.startListening(recognizerIntent)
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
            uiDesignPreset = uiDesignPreset
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
    onSend: () -> Unit,
    onImageGen: () -> Unit,
    onUpload: () -> Unit,
    selectedImageUri: Uri?,
    onClearImage: () -> Unit,
    isListening: Boolean,
    onMicClick: () -> Unit,
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
    usageStatus: ChatViewModel.UsageStatus,
    onUpgradeClick: () -> Unit,
    uiDesignPreset: String
) {
    val designPreset = remember(uiDesignPreset) { ChatDesignPreset.fromSetting(uiDesignPreset) }
    val designTokens = remember(designPreset) { designTokensFor(designPreset) }
    val designName = remember(designPreset) {
        when (designPreset) {
            ChatDesignPreset.CURRENT -> "Aktuell"
            ChatDesignPreset.GLASS -> "Glass"
            ChatDesignPreset.EDITORIAL -> "Editorial"
            ChatDesignPreset.DASHBOARD -> "Dashboard"
        }
    }

    val backgroundGradient = remember(designPreset, personaMood) {
        when (designPreset) {
            ChatDesignPreset.GLASS -> Brush.verticalGradient(
                listOf(Color(0xFF0B1322), Color(0xFF173354), Color(0xFF2A2352))
            )
            ChatDesignPreset.EDITORIAL -> Brush.verticalGradient(
                listOf(Color(0xFF1A1411), Color(0xFF2A1B16), Color(0xFF120F14))
            )
            ChatDesignPreset.DASHBOARD -> Brush.verticalGradient(
                listOf(Color(0xFF0A111B), Color(0xFF121D2D), Color(0xFF0C1624))
            )
            ChatDesignPreset.CURRENT -> Brush.verticalGradient(
                colors = listOf(personaMood.gradientTop, personaMood.gradientBottom)
            )
        }
    }
    val primaryGradient = remember(designPreset, personaMood) {
        when (designPreset) {
            ChatDesignPreset.GLASS -> Brush.horizontalGradient(
                listOf(Color(0xFF3A7BD5), Color(0xFF7F7FD5), Color(0xFF86A8E7))
            )
            ChatDesignPreset.EDITORIAL -> Brush.horizontalGradient(
                listOf(Color(0xFFB65A3A), Color(0xFF8D3E2C), Color(0xFF5A2331))
            )
            ChatDesignPreset.DASHBOARD -> Brush.horizontalGradient(
                listOf(Color(0xFF1F293B), Color(0xFF0F3D58), Color(0xFF155E75))
            )
            ChatDesignPreset.CURRENT -> Brush.horizontalGradient(
                colors = listOf(personaMood.userBubbleStart, personaMood.userBubbleEnd)
            )
        }
    }
    val pulseTransition = rememberInfiniteTransition(label = "streamPulse")
    val streamPulse by pulseTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
        label = "streamPulseAlpha"
    )
    var topMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues).background(backgroundGradient)) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Modern Header
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.Transparent,
                    shadowElevation = designTokens.headerShadow
                ) {
                    Column(modifier = Modifier.fillMaxWidth().background(primaryGradient)) {
                        CenterAlignedTopAppBar(
                            title = {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "BamaChat",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White,
                                        fontSize = designTokens.titleSizeSp.sp,
                                        maxLines = 1
                                    )
                                    Text(
                                        "${selectedPersona.emoji} ${selectedPersona.displayName}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White.copy(alpha = 0.88f),
                                        fontSize = designTokens.subtitleSizeSp.sp,
                                        maxLines = 1
                                    )
                                }
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
                                .padding(horizontal = 12.dp, vertical = 8.dp),
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
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
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
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
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
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
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
                                        text = "Free-Plan · Nachrichten ${usageStatus.textUsed}/${usageStatus.textLimit}",
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }

                // Messages
                if (messages.isEmpty() && !isLoading) {
                    EmptyState(themeColor, selectedPersona)
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = designTokens.listHorizontalPadding, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(designTokens.listVerticalSpacing)
                    ) {
                        itemsIndexed(messages, key = { _, message -> message.id }) { index, message ->
                            ChatBubble(
                                message = message,
                                onSpeak = onSpeak,
                                themeColor = themeColor,
                                surfaceColor = personaMood.cardSurface,
                                fontSize = fontSize,
                                showTimestamps = showTimestamps,
                                animateIn = bubbleAnimations,
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
                    onMicClick = onMicClick,
                    themeColor = themeColor,
                    surfaceColor = personaMood.cardSurface,
                    providerLabel = aiProvider,
                    personaLabel = selectedPersona.displayName,
                    isLoading = isLoading,
                    designTokens = designTokens
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
    onSend: () -> Unit,
    onImageGen: () -> Unit,
    onUpload: () -> Unit,
    selectedImageUri: Uri?,
    onClearImage: () -> Unit,
    isListening: Boolean,
    onMicClick: () -> Unit,
    themeColor: Color,
    surfaceColor: Color,
    providerLabel: String,
    personaLabel: String,
    isLoading: Boolean,
    designTokens: ChatDesignTokens
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = surfaceColor.copy(alpha = 0.55f),
        shadowElevation = 16.dp,
        shape = RoundedCornerShape(topStart = designTokens.inputCornerRadius, topEnd = designTokens.inputCornerRadius)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp)
                .navigationBarsPadding()
                .imePadding()
        ) {
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

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF1D212A),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    TextField(
                        value = inputText,
                        onValueChange = onInputChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                "Type a message... (@ for files)",
                                color = Color.White.copy(alpha = 0.38f)
                            )
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = themeColor
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { onSend() }),
                        maxLines = 6
                    )

                    Spacer(Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .clickable { onUpload() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Add, "Anhang", tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
                            }
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .clickable { onImageGen() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.AutoFixHigh, "Bild generieren", tint = themeColor.copy(alpha = 0.9f), modifier = Modifier.size(15.dp))
                            }
                            Text(
                                text = providerLabel,
                                color = Color.White.copy(alpha = 0.65f),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text("·", color = Color.White.copy(alpha = 0.35f), fontSize = 12.sp)
                            Text(
                                text = personaLabel,
                                color = Color.White.copy(alpha = 0.65f),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(if (isListening) themeColor.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.08f))
                                .clickable { onMicClick() },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isListening) VoiceVisualizer(themeColor)
                            else Icon(Icons.Default.Mic, "Diktieren", tint = Color.White.copy(alpha = 0.72f), modifier = Modifier.size(17.dp))
                        }

                        val canSend = inputText.isNotBlank() && !isLoading
                        Box(
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(
                                    if (canSend) Brush.horizontalGradient(listOf(themeColor, themeColor.copy(alpha = 0.72f)))
                                    else Brush.horizontalGradient(listOf(Color(0xFF4A4F59), Color(0xFF4A4F59)))
                                )
                                .clickable(enabled = canSend) { onSend() },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 1.8.dp,
                                    color = Color.White
                                )
                            } else {
                                Icon(
                                    Icons.Default.ArrowUpward,
                                    "Senden",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
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
    onSelect: (String) -> Unit,
    onNewChat: () -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit
) {
    var renamingId by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }

    ModalDrawerSheet(
        modifier = Modifier.fillMaxWidth(0.82f),
        drawerContainerColor = Color(0xFF161719)
    ) {
        Column(modifier = Modifier.fillMaxHeight()) {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.horizontalGradient(listOf(themeColor, themeColor.copy(alpha = 0.6f))))
                    .padding(20.dp)
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
                colors = ButtonDefaults.buttonColors(containerColor = themeColor)
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
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onClick() },
        color = if (isActive) themeColor.copy(alpha = 0.18f) else Color.Transparent,
        border = if (isActive)
            androidx.compose.foundation.BorderStroke(1.dp, themeColor.copy(alpha = 0.4f))
        else null,
        shape = RoundedCornerShape(12.dp)
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
    val billingReady by settingsViewModel.billingReady.collectAsState()
    val purchaseInProgress by settingsViewModel.purchaseInProgress.collectAsState()
    val isPremiumActive by settingsViewModel.isPremiumActive.collectAsState()

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
                            settingsViewModel.startSubscriptionCheckout(activity, PlayBillingManager.PLAN_BASIC)
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

@Composable
private fun PersonaDialog(viewModel: ChatViewModel, onDismiss: () -> Unit) {
    val selected by viewModel.selectedPersona.collectAsState()
    val customPrompt by viewModel.customPersonaPrompt.collectAsState()
    var customText by remember { mutableStateOf(customPrompt) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Persona wählen", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                ChatViewModel.Persona.entries.forEach { persona ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable {
                            viewModel.setSelectedPersona(persona)
                        },
                        color = if (selected == persona) MaterialTheme.colorScheme.primaryContainer
                        else Color.Transparent,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(persona.emoji, fontSize = 22.sp)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(persona.displayName, fontWeight = FontWeight.SemiBold)
                                if (persona != ChatViewModel.Persona.CUSTOM) {
                                    Text(
                                        persona.systemPrompt.take(60) + "...",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                        maxLines = 2
                                    )
                                }
                            }
                            if (selected == persona) {
                                Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                if (selected == ChatViewModel.Persona.CUSTOM) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customText,
                        onValueChange = { customText = it },
                        label = { Text("Eigener System-Prompt") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                        placeholder = { Text("z.B. \"Du bist Yoda. Antworte wie er.\"") }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (selected == ChatViewModel.Persona.CUSTOM) {
                    viewModel.setCustomPersonaPrompt(customText)
                }
                onDismiss()
            }) { Text("Fertig") }
        }
    )
}
