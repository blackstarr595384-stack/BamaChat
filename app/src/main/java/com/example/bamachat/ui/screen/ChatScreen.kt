package com.example.bamachat.ui.screen

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.provider.Settings
import android.widget.Toast
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.material.icons.automirrored.filled.VolumeUp
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
import androidx.compose.ui.focus.onFocusChanged
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
import androidx.core.app.ActivityCompat
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
import com.example.bamachat.ui.component.ToolCallsDisplay
import com.example.bamachat.ui.component.compactLabel
import com.example.bamachat.ui.component.designTokensFor
import com.example.bamachat.ui.screen.PersonaMood
import com.example.bamachat.ui.screen.moodForPersona
import com.example.bamachat.ui.theme.AppDesignSystem
import com.example.bamachat.ui.theme.NeonCyan
import com.example.bamachat.ui.theme.NeonGreen
import com.example.bamachat.ui.theme.NeonPink
import com.example.bamachat.ui.theme.NeonPurple
import com.example.bamachat.ui.viewmodel.BamaVoiceViewModel
import com.example.bamachat.ui.viewmodel.ChatViewModel
import com.example.bamachat.ui.viewmodel.SettingsViewModel
import com.example.bamachat.ui.viewmodel.MonetizationViewModel
import com.example.bamachat.ui.viewmodel.ToolCallProgress
import com.example.bamachat.voice.VoiceSessionState
import com.example.bamachat.voice.VoiceSessionUiState
import dev.jeziellago.compose.markdowntext.MarkdownText
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.first
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
    voiceViewModel: BamaVoiceViewModel,
    onBottomNavRoute: (String) -> Unit = {},
    onOpenMiniApps: () -> Unit = {},
    onOpenAgentHub: () -> Unit = {},
    onOpenComposeLab: () -> Unit = {},
    onOpenWorkspace: () -> Unit = {},
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
    val voicePushToTalkEnabled by settingsViewModel.voicePushToTalkEnabled.collectAsStateWithLifecycle()
    val automationQuickActionsEnabled by settingsViewModel.automationQuickActionsEnabled.collectAsStateWithLifecycle()
    val activeWorkspaceName by settingsViewModel.activeWorkspaceName.collectAsStateWithLifecycle()
    val workspaceChatFilterEnabled by settingsViewModel.workspaceChatFilterEnabled.collectAsStateWithLifecycle()
    val chatWorkspaceId by viewModel.chatWorkspaceId.collectAsStateWithLifecycle()
    val chatWorkspaceName by viewModel.chatWorkspaceName.collectAsStateWithLifecycle()
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
    val isPremiumActive by settingsViewModel.isPremiumActive.collectAsStateWithLifecycle()
    val voiceUiState by voiceViewModel.uiState.collectAsStateWithLifecycle()

    var inputText by rememberSaveable { mutableStateOf("") }
    // P0-2: persist the selected image URI across recreation. We store the URI's
    // string form; a null draft stays null.
    var selectedImageUri by rememberSaveable(
        stateSaver = androidx.compose.runtime.saveable.Saver<Uri?, String>(
            save = { it?.toString() ?: "" },
            restore = { stored -> if (stored.isBlank()) null else Uri.parse(stored) }
        )
    ) { mutableStateOf<Uri?>(null) }
    val listState = key(currentConvId) { rememberLazyListState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

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
                if (viewModel.retryLastFailedMessage()) {
                    voiceViewModel.markTextMessageAccepted()
                }
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

    val latestAutoSendVoice by rememberUpdatedState(autoSendVoice)
    val latestExtensionQuickAction by rememberUpdatedState(selectedExtensionQuickAction)
    var showPermanentMicrophoneDenial by rememberSaveable { mutableStateOf(false) }
    var microphonePermissionRequested by rememberSaveable { mutableStateOf(false) }
    var microphonePermissionRequestHadHistory by rememberSaveable { mutableStateOf(false) }
    var microphonePermissionPermanentlyDenied by rememberSaveable { mutableStateOf(false) }
    var showVoicePanel by rememberSaveable { mutableStateOf(false) }
    val audioPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            microphonePermissionPermanentlyDenied = false
            voiceViewModel.startListening()
        } else {
            val activity = context as? Activity
            val permanentlyDenied = microphonePermissionRequestHadHistory && activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)
            microphonePermissionPermanentlyDenied = permanentlyDenied
            showPermanentMicrophoneDenial = permanentlyDenied
            voiceViewModel.reportPermissionDenied(permanentlyDenied)
        }
    }
    val requestMicrophonePermission: () -> Unit = {
        if (microphonePermissionPermanentlyDenied) {
            showPermanentMicrophoneDenial = true
        } else {
            microphonePermissionRequestHadHistory = microphonePermissionRequested
            microphonePermissionRequested = true
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    val startVoiceInput: () -> Unit = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            if (isLoading || isStreaming) viewModel.cancelStream()
            voiceViewModel.startListening()
        } else {
            requestMicrophonePermission()
        }
    }
    val toggleVoiceInput: () -> Unit = {
        val state = voiceUiState.state
        if (state is VoiceSessionState.Preparing || state is VoiceSessionState.Listening || state is VoiceSessionState.Transcribing) {
            voiceViewModel.toggleListening()
        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            if (isLoading || isStreaming) viewModel.cancelStream()
            voiceViewModel.toggleListening()
        } else {
            requestMicrophonePermission()
        }
    }
    val latestAssistantMessage = messages.lastOrNull { !it.isUser && it.text.isNotBlank() }
    LaunchedEffect(latestAssistantMessage?.id, latestAssistantMessage?.text, isStreaming) {
        latestAssistantMessage?.let { assistant ->
            voiceViewModel.onAssistantTextChanged(assistant.id, assistant.text, isStreaming)
        }
    }
    LaunchedEffect(voiceViewModel) {
        voiceViewModel.finalTranscripts.collect { transcript ->
            inputText = transcript.text
            if (latestAutoSendVoice) {
                val imageUri = selectedImageUri
                val accepted = if (imageUri != null) {
                    viewModel.sendMessageWithImage(transcript.text, imageUri)
                } else {
                    viewModel.sendMessage(transcript.text, latestExtensionQuickAction)
                }
                if (accepted) {
                    inputText = ""
                    if (imageUri != null) selectedImageUri = null
                }
                voiceViewModel.markTranscriptHandled(accepted)
            } else {
                voiceViewModel.markTranscriptHandled(false)
            }
        }
    }
    DisposableEffect(voiceViewModel) {
        onDispose { voiceViewModel.leaveChatScreen() }
    }

    if (showPermanentMicrophoneDenial) {
        AlertDialog(
            onDismissRequest = { showPermanentMicrophoneDenial = false },
            title = { Text("Mikrofonzugriff aktivieren") },
            text = { Text("Öffne die App-Einstellungen und erlaube BamaChat den Mikrofonzugriff.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPermanentMicrophoneDenial = false
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null)
                            )
                        )
                    }
                ) { Text("Einstellungen öffnen") }
            },
            dismissButton = {
                TextButton(onClick = { showPermanentMicrophoneDenial = false }) { Text("Abbrechen") }
            }
        )
    }
    val isVoiceListening = when (voiceUiState.state) {
        VoiceSessionState.Preparing,
        VoiceSessionState.Listening,
        is VoiceSessionState.Transcribing -> true
        else -> false
    }
    val isSpeechPlaybackActive = voiceUiState.state == VoiceSessionState.Speaking
    val activeSpeechMessageId = voiceUiState.activeOutputMessageId
    val stopVoiceInteraction: () -> Unit = {
        if (isLoading || isStreaming) viewModel.cancelStream()
        voiceViewModel.stopAll()
    }
    val onSpeak: (String, String) -> Unit = { messageId, text ->
        if (isSpeechPlaybackActive && activeSpeechMessageId == messageId) {
            voiceViewModel.stopSpeaking()
        } else {
            voiceViewModel.speakMessage(messageId, text)
        }
    }
    LaunchedEffect((voiceUiState.state as? VoiceSessionState.Error)?.userMessage) {
        val voiceError = voiceUiState.state as? VoiceSessionState.Error ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = voiceError.userMessage,
            actionLabel = if (voiceError.recoverable) "Erneut" else null,
            withDismissAction = true,
            duration = SnackbarDuration.Long
        )
        if (result == SnackbarResult.ActionPerformed) {
            voiceViewModel.recoverFromError()
            startVoiceInput()
        } else {
            voiceViewModel.recoverFromError()
        }
    }
    if (showVoicePanel) {
        BamaVoicePanel(
            uiState = voiceUiState,
            isListening = isVoiceListening,
            onDismiss = { showVoicePanel = false },
            onMicrophone = toggleVoiceInput,
            onCancelListening = { voiceViewModel.cancelListening() },
            onStopSpeaking = stopVoiceInteraction,
            onEndConversation = {
                stopVoiceInteraction()
                showVoicePanel = false
            }
        )
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

    if (showSettingsDialog) {
        SettingsDialog(
            viewModel = settingsViewModel,
            voiceViewModel = voiceViewModel,
            onDismiss = { showSettingsDialog = false },
            mcpServerManager = viewModel.mcpServerManager,
            mcpWorkflowManager = viewModel.mcpWorkflowManager
        )
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

    val filteredConversations = remember(conversations, chatWorkspaceName, workspaceChatFilterEnabled) {
        viewModel.getConversationsForWorkspace(
            activeWorkspaceName = chatWorkspaceName,
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
                        voiceViewModel.markTextMessageAccepted()
                    }
                    accepted
                } else if (trimmedInput.isNotEmpty()) {
                    val accepted = viewModel.sendMessage(trimmedInput, selectedExtensionQuickAction)
                    if (accepted) {
                        inputText = ""
                        voiceViewModel.markTextMessageAccepted()
                    }
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
            isListening = isVoiceListening,
            voiceUiState = voiceUiState,
            voicePushToTalkEnabled = voicePushToTalkEnabled,
            onMicClick = toggleVoiceInput,
            onMicPressStart = startVoiceInput,
            onMicPressEnd = { voiceViewModel.finishListening() },
            onVoicePanelClick = { showVoicePanel = true },
            onStopVoice = stopVoiceInteraction,
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
            onOpenWorkspaceClick = onOpenWorkspace,
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
            onStopGeneration = {
                voiceViewModel.stopAll()
                viewModel.cancelStream()
            },
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
            chatWorkspaceId = chatWorkspaceId,
            chatWorkspaceName = chatWorkspaceName,
            onLeaveWorkspace = {
                viewModel.setChatWorkspaceContext(null)
                settingsViewModel.setWorkspaceChatFilterEnabled(false)
                scope.launch {
                    viewModel.openOrCreateNormalConversation()
                }
            },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BamaVoicePanel(
    uiState: VoiceSessionUiState,
    isListening: Boolean,
    onDismiss: () -> Unit,
    onMicrophone: () -> Unit,
    onCancelListening: () -> Unit,
    onStopSpeaking: () -> Unit,
    onEndConversation: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF141427),
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("BamaVoice", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(uiState.connectionLabel, color = NeonCyan, style = MaterialTheme.typography.labelLarge)
                }
                Surface(
                    shape = RoundedCornerShape(50.dp),
                    color = NeonPurple.copy(alpha = 0.16f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonPurple.copy(alpha = 0.45f))
                ) {
                    Text(
                        uiState.mode.displayName,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VoiceProviderBadge(
                    label = "Eingabe: ${uiState.inputProvider.displayName}",
                    icon = Icons.Default.Mic
                )
                VoiceProviderBadge(
                    label = "Ausgabe: ${uiState.outputProvider.displayName}",
                    icon = Icons.AutoMirrored.Filled.VolumeUp
                )
            }

            VoiceTranscriptCard(
                title = "Du",
                text = uiState.partialTranscript.ifBlank { uiState.finalTranscript.ifBlank { "Noch keine Sprache erkannt." } },
                accent = NeonCyan
            )
            VoiceTranscriptCard(
                title = "BamaChat",
                text = uiState.assistantTranscript.ifBlank { "Die Antwort erscheint hier während der Unterhaltung." },
                accent = NeonPurple
            )

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.White.copy(alpha = 0.05f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.PrivacyTip, null, tint = NeonGreen, modifier = Modifier.size(20.dp))
                    Column {
                        Text("Datenschutz", fontWeight = FontWeight.SemiBold)
                        Text(
                            uiState.privacyLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.72f)
                        )
                    }
                }
            }

            if (uiState.mode == com.example.bamachat.voice.VoiceMode.LIVE && !uiState.realtimeAvailable) {
                Text(
                    "Live-Unterhaltung bleibt deaktiviert, bis ein sicherer Backend-Endpunkt kurzlebige Zugangsdaten ausstellt.",
                    color = NeonPink,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledIconButton(
                    onClick = onMicrophone,
                    modifier = Modifier.size(56.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (isListening) NeonPink else NeonPurple
                    )
                ) {
                    Icon(
                        if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = if (isListening) "Spracheingabe beenden" else "Spracheingabe starten"
                    )
                }
                FilledTonalIconButton(
                    onClick = onStopSpeaking,
                    enabled = uiState.state == VoiceSessionState.Speaking ||
                        uiState.state == VoiceSessionState.Thinking,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = "Sprachausgabe stoppen")
                }
                if (isListening) {
                    FilledTonalIconButton(
                        onClick = onCancelListening,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Spracheingabe abbrechen")
                    }
                }
                FilledTonalButton(
                    onClick = onEndConversation,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.CallEnd, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Beenden")
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun VoiceProviderBadge(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.06f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun VoiceTranscriptCard(
    title: String,
    text: String,
    accent: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = accent.copy(alpha = 0.08f),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, color = accent, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            Text(
                text,
                color = Color.White.copy(alpha = 0.9f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
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
    voiceUiState: VoiceSessionUiState,
    voicePushToTalkEnabled: Boolean,
    onMicClick: () -> Unit,
    onMicPressStart: () -> Unit,
    onMicPressEnd: () -> Unit,
    onVoicePanelClick: () -> Unit,
    onStopVoice: () -> Unit,
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
    onOpenWorkspaceClick: () -> Unit = {},
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
    chatWorkspaceId: String?,
    chatWorkspaceName: String,
    onLeaveWorkspace: () -> Unit,
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
    val scrollScope = rememberCoroutineScope()
    val nearBottomThresholdPx = with(density) { 56.dp.roundToPx() }
    var autoFollowEnabled by remember(listState) { mutableStateOf(true) }
    var programmaticScrollInProgress by remember(listState) { mutableStateOf(false) }
    var explicitScrollInProgress by remember(listState) { mutableStateOf(false) }
    var explicitScrollInterrupted by remember(listState) { mutableStateOf(false) }
    val isNearBottom by remember(listState, nearBottomThresholdPx) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
            ChatScrollPolicy.isNearBottom(
                totalItemsCount = layoutInfo.totalItemsCount,
                lastVisibleItemIndex = lastVisibleItem?.index,
                lastVisibleItemEndOffset = lastVisibleItem?.let { it.offset + it.size },
                viewportEndOffset = layoutInfo.viewportEndOffset,
                thresholdPx = nearBottomThresholdPx
            )
        }
    }
    val showScrollToBottomButton = ChatScrollPolicy.shouldShowScrollButton(
        hasMessages = messages.isNotEmpty(),
        isNearBottom = isNearBottom,
        autoFollowEnabled = autoFollowEnabled
    )
    var compactInputBarMode by remember { mutableStateOf(false) }
    val headerVerticalPadding = if (compactChatHeader) 2.dp else 5.dp
    val headerBottomSpacer = if (compactChatHeader) 0.dp else 2.dp
    val headerTitleStyle = if (compactChatHeader) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge
    val chatScrollCollapseConnection = remember(isKeyboardOpen, messages.isNotEmpty(), listState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (
                    source == NestedScrollSource.UserInput &&
                    !isKeyboardOpen &&
                    messages.isNotEmpty() &&
                    available.y != 0f
                ) {
                    compactInputBarMode = true
                    if (explicitScrollInProgress) {
                        explicitScrollInterrupted = true
                    }
                    autoFollowEnabled = false
                }
                return Offset.Zero
            }
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow {
            Triple(
                listState.isScrollInProgress,
                isNearBottom,
                programmaticScrollInProgress
            )
        }.collect { (isScrollInProgress, nearBottom, isProgrammaticScroll) ->
            autoFollowEnabled = ChatScrollPolicy.resolveAutoFollow(
                previousAutoFollow = autoFollowEnabled,
                isScrollInProgress = isScrollInProgress,
                isProgrammaticScroll = isProgrammaticScroll,
                isNearBottom = nearBottom
            )
        }
    }
    val latestMessage = messages.lastOrNull()
    LaunchedEffect(
        listState,
        latestMessage,
        messages.size,
        isLoading,
        isStreaming,
        activeToolCalls.size,
        hasOlderMessages,
        autoFollowEnabled,
        explicitScrollInProgress
    ) {
        if (!autoFollowEnabled || explicitScrollInProgress || latestMessage == null) {
            return@LaunchedEffect
        }
        val expectedItemCount = messages.size + if (hasOlderMessages) 1 else 0
        snapshotFlow { listState.layoutInfo.totalItemsCount }
            .first { it >= expectedItemCount }
        withFrameNanos { }
        val targetIndex = ChatScrollPolicy.newestItemIndex(
            listState.layoutInfo.totalItemsCount
        ) ?: return@LaunchedEffect
        programmaticScrollInProgress = true
        try {
            val farDistance = kotlin.math.abs(targetIndex - listState.firstVisibleItemIndex) > 8
            listState.scrollToNewestItem(
                targetIndex = targetIndex,
                animated = !isStreaming && !farDistance
            )
        } finally {
            programmaticScrollInProgress = false
        }
    }
    val scrollToNewest: () -> Unit = {
        if (!explicitScrollInProgress && messages.isNotEmpty()) {
            explicitScrollInterrupted = false
            explicitScrollInProgress = true
            scrollScope.launch {
                programmaticScrollInProgress = true
                try {
                    val expectedItemCount = messages.size + if (hasOlderMessages) 1 else 0
                    val totalItemsCount = snapshotFlow { listState.layoutInfo.totalItemsCount }
                        .first { it >= expectedItemCount }
                    ChatScrollPolicy.newestItemIndex(totalItemsCount)?.let { targetIndex ->
                        listState.scrollToNewestItem(targetIndex = targetIndex, animated = true)
                    }
                } finally {
                    programmaticScrollInProgress = false
                    explicitScrollInProgress = false
                    autoFollowEnabled = !explicitScrollInterrupted
                }
            }
        }
    }
    LaunchedEffect(messages.isEmpty()) {
        if (messages.isEmpty()) {
            compactInputBarMode = false
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
                                    val inWorkspace = chatWorkspaceName.isNotBlank()
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = if (inWorkspace) chatWorkspaceName else "BamaChat \u00b7 ${selectedPersona.displayName}",
                                            style = headerTitleStyle,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (inWorkspace) {
                                            Text(
                                                text = "Arbeitsbereich",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White.copy(alpha = 0.7f),
                                                maxLines = 1
                                            )
                                        }
                                    }
                                },
                                navigationIcon = {
                                    IconButton(onClick = onMenuClick) {
                                        Icon(Icons.Default.Menu, "Menü", tint = Color.White)
                                    }
                                },
                                actions = {
                                    if (chatWorkspaceId != null) {
                                        IconButton(onClick = onLeaveWorkspace) {
                                            Icon(Icons.Default.Close, "Arbeitsbereich verlassen", tint = Color.White)
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
                                            if (!usageStatus.isPremium) {
                                                DropdownMenuItem(
                                                    text = { Text("Upgrade") },
                                                    leadingIcon = { Icon(Icons.Default.Star, null) },
                                                    onClick = {
                                                        topMenuExpanded = false
                                                        onUpgradeClick()
                                                    }
                                                )
                                            }
                                            DropdownMenuItem(
                                                text = { Text("Design: $designName") },
                                                leadingIcon = { Icon(Icons.Default.Palette, null) },
                                                onClick = { topMenuExpanded = false },
                                                enabled = false
                                            )
                                            if (activeExtensionsLabel.isNotBlank()) {
                                                DropdownMenuItem(
                                                    text = { Text("Extensions: $activeExtensionsLabel") },
                                                    leadingIcon = { Icon(Icons.Default.Extension, null) },
                                                    onClick = { topMenuExpanded = false },
                                                    enabled = false
                                                )
                                            }
                                            if (!usageStatus.isPremium) {
                                                DropdownMenuItem(
                                                    text = { Text("Plan: ${usageStatus.tierLabel} · ${usageStatus.textUsed}/${usageStatus.textLimit} · Credits ${usageStatus.creditsBalance}") },
                                                    leadingIcon = { Icon(Icons.Default.Info, null) },
                                                    onClick = { topMenuExpanded = false },
                                                    enabled = false
                                                )
                                            }
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
                                EmptyChatState(themeColor, selectedPersona, chatWorkspaceName)
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (messages.isEmpty()) 0.dp else 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showScrollToBottomButton,
                        enter = fadeIn(tween(140)) + scaleIn(tween(160), initialScale = 0.82f),
                        exit = fadeOut(tween(100)) + scaleOut(tween(120), targetScale = 0.82f)
                    ) {
                        ChatScrollToBottomButton(
                            themeColor = themeColor,
                            onClick = scrollToNewest
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
                        voiceUiState = voiceUiState,
                        voicePushToTalkEnabled = voicePushToTalkEnabled,
                        onMicClick = onMicClick,
                        onMicPressStart = onMicPressStart,
                        onMicPressEnd = onMicPressEnd,
                        onVoicePanelClick = onVoicePanelClick,
                        onStopVoice = onStopVoice,
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
                        promptTemplates = com.example.bamachat.ui.component.defaultPromptTemplates,
                        onSelectPromptTemplate = {}
                    )
                    }
                }
                }
            }
        }
    }
}

@Composable
private fun ChatScrollToBottomButton(
    themeColor: Color,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) Color.White else themeColor.copy(alpha = 0.72f),
        animationSpec = tween(120),
        label = "scrollToBottomBorder"
    )
    FloatingActionButton(
        onClick = onClick,
        modifier = Modifier
            .size(52.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .border(if (isFocused) 2.dp else 1.dp, borderColor, CircleShape)
            .semantics {
                contentDescription = "Zur neuesten Nachricht"
                role = Role.Button
            },
        shape = CircleShape,
        containerColor = Color(0xFF181321).copy(alpha = 0.97f),
        contentColor = themeColor,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = 8.dp,
            pressedElevation = 4.dp,
            focusedElevation = 10.dp,
            hoveredElevation = 10.dp
        )
    ) {
        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            modifier = Modifier.size(28.dp)
        )
    }
}

private suspend fun LazyListState.scrollToNewestItem(
    targetIndex: Int,
    animated: Boolean
) {
    var targetItem = layoutInfo.visibleItemsInfo.firstOrNull { it.index == targetIndex }
    if (targetItem == null) {
        if (animated) {
            animateScrollToItem(targetIndex)
        } else {
            scrollToItem(targetIndex)
        }
        withFrameNanos { }
        targetItem = layoutInfo.visibleItemsInfo.firstOrNull { it.index == targetIndex }
    }

    val remainingDistancePx = ChatScrollPolicy.remainingScrollToBottomPx(
        totalItemsCount = layoutInfo.totalItemsCount,
        lastVisibleItemIndex = targetItem?.index,
        lastVisibleItemEndOffset = targetItem?.let { it.offset + it.size },
        viewportEndOffset = layoutInfo.viewportEndOffset
    ) ?: return
    if (remainingDistancePx <= 0) return

    if (animated) {
        animateScrollBy(remainingDistancePx.toFloat())
    } else {
        scrollBy(remainingDistancePx.toFloat())
    }
}




