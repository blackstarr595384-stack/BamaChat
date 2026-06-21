package com.example.bamachat.ui.screen

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bamachat.BuildConfig
import com.example.bamachat.data.ApiClient
import com.example.bamachat.ui.component.CompactTextAction
import com.example.bamachat.ui.component.CompactTextActionRow
import com.example.bamachat.ui.component.sanitizeForSpeech
import com.example.bamachat.ui.component.splitSpeechChunks
import com.example.bamachat.util.AgentPresetLibrary
import com.example.bamachat.util.LegalPolicy
import com.example.bamachat.ui.theme.AppDesignPreset
import com.example.bamachat.ui.viewmodel.SettingsViewModel
import com.example.bamachat.util.CloudVoiceManager
import com.example.bamachat.util.MonetizationConfig
import com.example.bamachat.util.PlayBillingManager
import com.example.bamachat.util.McpConnectionStatus
import com.example.bamachat.util.McpServerManager
import com.example.bamachat.util.McpWorkflowManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@Suppress("UNUSED_VARIABLE", "UNUSED_PARAMETER")
@Composable
fun SettingsDialog(
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit,
    initialSection: String? = null,
    mcpServerManager: McpServerManager? = null,
    mcpWorkflowManager: McpWorkflowManager? = null
) {
    val isBiometricEnabled by viewModel.isBiometricEnabled.collectAsState()
    val primaryColor by viewModel.primaryColorInt.collectAsState()
    val fontSize by viewModel.fontSize.collectAsState()
    val multiProvider by viewModel.multiProviderEnabled.collectAsState()
    val openRouterApiKey by viewModel.openRouterApiKey.collectAsState()
    val groqApiKey by viewModel.groqApiKey.collectAsState()
    val cerebrasApiKey by viewModel.cerebrasApiKey.collectAsState()
    val togetherApiKey by viewModel.togetherApiKey.collectAsState()
    val geminiApiKey by viewModel.geminiApiKey.collectAsState()
    val openCodeApiKey by viewModel.openCodeApiKey.collectAsState()
    val openCodeEndpoint by viewModel.openCodeEndpoint.collectAsState()
    val openCodeEndpointWarning by viewModel.openCodeEndpointWarning.collectAsState()
    val openCodeModel by viewModel.openCodeModel.collectAsState()
    val aiProvider by viewModel.aiProvider.collectAsState()
    val ollamaUrl by viewModel.ollamaUrl.collectAsState()
    val liveWebEnabled by viewModel.liveWebEnabled.collectAsState()
    val liveWebEndpoint by viewModel.liveWebEndpoint.collectAsState()
    val liveWebApiToken by viewModel.liveWebApiToken.collectAsState()
    val mcpRemoteUrl by viewModel.mcpRemoteUrl.collectAsState()
    val mcpRemoteUrlWarning by viewModel.mcpRemoteUrlWarning.collectAsState()
    val mcpRemoteToken by viewModel.mcpRemoteToken.collectAsState()
    val liveWebAllowedDomains by viewModel.liveWebAllowedDomains.collectAsState()
    val liveWebPreferGithub by viewModel.liveWebPreferGithub.collectAsState()
    val photoAiCloudEndpoint by viewModel.photoAiCloudEndpoint.collectAsState()
    val photoAiCloudApiToken by viewModel.photoAiCloudApiToken.collectAsState()
    val imageGenerationMode by viewModel.imageGenerationMode.collectAsState()
    val selectedOpenRouterModel by viewModel.selectedOpenRouterModel.collectAsState()
    val openRouterVisionOnlyModels by viewModel.openRouterVisionOnlyModels.collectAsState()
    val agentStudioEnabled by viewModel.agentStudioEnabled.collectAsState()
    val agentPreset by viewModel.agentPreset.collectAsState()
    val agentName by viewModel.agentName.collectAsState()
    val agentGoal by viewModel.agentGoal.collectAsState()
    val agentRules by viewModel.agentRules.collectAsState()
    val agentOutputStyle by viewModel.agentOutputStyle.collectAsState()
    val agentTools by viewModel.agentTools.collectAsState()
    val isPremiumActive by viewModel.isPremiumActive.collectAsState()
    val subscriptionTier by viewModel.subscriptionTier.collectAsState()
    val billingReady by viewModel.billingReady.collectAsState()
    val purchaseInProgress by viewModel.purchaseInProgress.collectAsState()
    val creditsBalance by viewModel.creditsBalance.collectAsState()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState()
    val soundEnabled by viewModel.soundEnabled.collectAsState()
    val vibrationEnabled by viewModel.vibrationEnabled.collectAsState()
    val autoSendVoice by viewModel.autoSendVoice.collectAsState()
    val voiceChatMode by viewModel.voiceChatMode.collectAsState()
    val ttsEnabled by viewModel.ttsEnabled.collectAsState()
    val ttsSpeed by viewModel.ttsSpeed.collectAsState()
    val ttsPitch by viewModel.ttsPitch.collectAsState()
    val ttsVoiceStyle by viewModel.ttsVoiceStyle.collectAsState()
    val ttsProVoiceEnabled by viewModel.ttsProVoiceEnabled.collectAsState()
    val cloudVoiceEnabled by viewModel.cloudVoiceEnabled.collectAsState()
    val cloudVoiceProvider by viewModel.cloudVoiceProvider.collectAsState()
    val elevenLabsApiKey by viewModel.elevenLabsApiKey.collectAsState()
    val elevenLabsVoiceId by viewModel.elevenLabsVoiceId.collectAsState()
    val elevenLabsModelId by viewModel.elevenLabsModelId.collectAsState()
    val piperEndpoint by viewModel.piperEndpoint.collectAsState()
    val piperVoiceName by viewModel.piperVoiceName.collectAsState()
    val streamingEnabled by viewModel.streamingEnabled.collectAsState()
    val showTimestamps by viewModel.showTimestamps.collectAsState()
    val showLiveSources by viewModel.showLiveSources.collectAsState()
    val bubbleAnimations by viewModel.bubbleAnimations.collectAsState()
    val developerModeEnabled by viewModel.developerModeEnabled.collectAsState()
    val developerUnlimitedTraining by viewModel.developerUnlimitedTraining.collectAsState()
    val developerRealtimeCollabTesting by viewModel.developerRealtimeCollabTesting.collectAsState()
    val agentConfirmToolActions by viewModel.agentConfirmToolActions.collectAsState()
    val automationQuickActionsEnabled by viewModel.automationQuickActionsEnabled.collectAsState()
    val privacyStrictModeEnabled by viewModel.privacyStrictModeEnabled.collectAsState()
    val voicePushToTalkEnabled by viewModel.voicePushToTalkEnabled.collectAsState()
    val projectWorkspaces by viewModel.projectWorkspaces.collectAsState()
    val activeWorkspaceId by viewModel.activeWorkspaceId.collectAsState()
    val workspaceChatFilterEnabled by viewModel.workspaceChatFilterEnabled.collectAsState()
    val language by viewModel.language.collectAsState()
    val autoLanguageDetectionEnabled by viewModel.autoLanguageDetectionEnabled.collectAsState()
    val localOcrEnabled by viewModel.localOcrEnabled.collectAsState()
    val uiDesignPreset by viewModel.uiDesignPreset.collectAsState()
    val displayPreset by viewModel.displayPreset.collectAsState()
    val compactChatHeader by viewModel.compactChatHeader.collectAsState()
    val connectChatBottomBars by viewModel.connectChatBottomBars.collectAsState()
    val glassEffectsEnabled by viewModel.glassEffectsEnabled.collectAsState()
    val uiCornerRoundnessScale by viewModel.uiCornerRoundnessScale.collectAsState()
    val uiShadowIntensityScale by viewModel.uiShadowIntensityScale.collectAsState()
    val uiSurfaceOpacity by viewModel.uiSurfaceOpacity.collectAsState()
    val guestAutoClearOnAccountSignIn by viewModel.guestAutoClearOnAccountSignIn.collectAsState()
    val guestAutoClearOnSignOut by viewModel.guestAutoClearOnSignOut.collectAsState()
    val cloudPersonaLastSyncAt by viewModel.cloudPersonaLastSyncAt.collectAsState()
    val cloudPersonaLastSyncStatus by viewModel.cloudPersonaLastSyncStatus.collectAsState()

    val _uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val useClearVoiceStyle = ttsVoiceStyle == SettingsViewModel.TTS_STYLE_CLEAR
    val previewScope = rememberCoroutineScope()
    val cloudVoiceManager = remember(context) { CloudVoiceManager(context) }
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
    val ttsLocale = remember(language) { localeForLanguageCode(language) }
    val voicePreviewSamples = remember(language) { voicePreviewSamplesForLanguage(language) }
    var voicePreviewStatus by remember { mutableStateOf("") }
    var voicePreviewPlaying by remember { mutableStateOf(false) }
    var previewTts by remember { mutableStateOf<TextToSpeech?>(null) }
    var voicePreviewJob by remember { mutableStateOf<Job?>(null) }

    DisposableEffect(context) {
        lateinit var ttsInstance: TextToSpeech
        ttsInstance = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsInstance.language = ttsLocale
                ttsInstance.setSpeechRate(ttsSpeed)
                ttsInstance.setPitch(ttsPitch)
            }
        }
        previewTts = ttsInstance
        onDispose {
            runCatching { ttsInstance.stop() }
            runCatching { ttsInstance.shutdown() }
            runCatching { cloudVoiceManager.release() }
            previewTts = null
        }
    }

    LaunchedEffect(ttsLocale, ttsSpeed, ttsPitch) {
        previewTts?.language = ttsLocale
        previewTts?.setSpeechRate(ttsSpeed)
        previewTts?.setPitch(ttsPitch)
    }

    val stopVoicePreview: () -> Unit = {
        voicePreviewJob?.cancel()
        runCatching { previewTts?.stop() }
        previewScope.launch { cloudVoiceManager.stop() }
        voicePreviewPlaying = false
        voicePreviewStatus = ""
    }

    val playVoicePreview: (VoicePreviewSample) -> Unit = playSample@{ sample ->
        val speakText = sanitizeForSpeech(sample.text)
        if (speakText.isBlank()) return@playSample
        voicePreviewJob?.cancel()
        voicePreviewJob = previewScope.launch {
            voicePreviewPlaying = true
            voicePreviewStatus = "Spielt: ${sample.label}"
            var finalStatus = ""
            try {
                runCatching { previewTts?.stop() }
                runCatching { cloudVoiceManager.stop() }

                val maxChunkChars = if (useClearVoiceStyle) 170 else 220
                val pauseMs = if (useClearVoiceStyle) 80L else 130L

                if (cloudVoiceRequested) {
                    val config = cloudVoiceConfig
                    if (config == null) {
                        finalStatus = "${selectedCloudVoiceProvider.displayName} ist aktiviert, aber die Konfiguration ist unvollständig."
                        Toast.makeText(context, finalStatus, Toast.LENGTH_LONG).show()
                        return@launch
                    }
                    val cloudOk = runCatching {
                        cloudVoiceManager.speak(
                            text = speakText,
                            config = config,
                            voiceStyle = if (useClearVoiceStyle) CloudVoiceManager.VoiceStyle.CLEAR else CloudVoiceManager.VoiceStyle.NATURAL
                        )
                    }.getOrDefault(false)
                    if (cloudOk) {
                        while (cloudVoiceManager.isSpeaking()) {
                            delay(140)
                        }
                        return@launch
                    }

                    finalStatus = cloudVoiceManager.lastErrorMessage()
                        ?: "ElevenLabs konnte nicht gestartet werden."
                    Toast.makeText(context, finalStatus, Toast.LENGTH_LONG).show()
                    return@launch
                }

                val engine = previewTts
                if (engine != null) {
                    val chunks = splitSpeechChunks(speakText, maxChunkChars = maxChunkChars)
                    chunks.forEachIndexed { index, chunk ->
                        val queueMode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                        engine.speak(
                            chunk,
                            queueMode,
                            null,
                            "settings_voice_preview_${System.currentTimeMillis()}_$index"
                        )
                        if (index < chunks.lastIndex) {
                            engine.playSilentUtterance(
                                pauseMs,
                                TextToSpeech.QUEUE_ADD,
                                "settings_voice_preview_pause_$index"
                            )
                        }
                    }
                    while (engine.isSpeaking) {
                        delay(120)
                    }
                } else {
                    finalStatus = "Android-Sprachausgabe ist nicht bereit."
                }
            } finally {
                voicePreviewPlaying = false
                voicePreviewStatus = finalStatus
            }
        }
    }

    LaunchedEffect(ttsEnabled) {
        if (!ttsEnabled) stopVoicePreview()
    }

    var expandedSection by remember { mutableStateOf<String?>(null) }
    var newWorkspaceName by remember { mutableStateOf("") }
    // P0-2: confirm-dialog state for workspace deletion
    var workspacePendingDelete by remember { mutableStateOf<com.example.bamachat.util.ProjectWorkspace?>(null) }
    // P1-1: inline rename state — holds the id of the workspace currently being edited
    var renamingWorkspaceId by remember { mutableStateOf<String?>(null) }
    var renamingDraft by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(initialSection) {
        if (!initialSection.isNullOrBlank()) {
            expandedSection = initialSection
        }
    }

    val colors = listOf(
        0xFF6A11CB, 0xFF2575FC, 0xFF00B894,
        0xFFD63031, 0xFFFDBC40, 0xFF2D3436,
        0xFFE17055, 0xFF0984E3, 0xFF6C5CE7
    )
    var customAccentHex by remember(primaryColor) { mutableStateOf(colorToHex(primaryColor)) }
    val parsedCustomAccent = remember(customAccentHex) { parseHexColor(customAccentHex) }

    val languages = listOf(
        "de" to "Deutsch",
        "en" to "English",
        "pl" to "Polski",
        "fr" to "Français",
        "es" to "Español",
        "tr" to "Türkçe",
        "ar" to "العربية"
    )
    val agentPresets = AgentPresetLibrary.labels
    val outputStyles = AgentPresetLibrary.outputStyles
    val designPresets = AppDesignPreset.labels
    val aiProviderOptions = listOf("OpenRouter", "OpenCode", "Groq", "Cerebras", "Together", "Ollama")
    val agentPreview = remember(
        agentStudioEnabled,
        agentPreset,
        agentName,
        agentGoal,
        agentRules,
        agentOutputStyle,
        agentTools
    ) {
        viewModel.getAgentPromptPreview()
    }
    val cloudSyncStatusText = remember(cloudPersonaLastSyncAt, cloudPersonaLastSyncStatus) {
        viewModel.formatCloudSyncStatus(cloudPersonaLastSyncAt, cloudPersonaLastSyncStatus)
    }
    val activeWorkspaceLabel = remember(projectWorkspaces, activeWorkspaceId) {
        projectWorkspaces.firstOrNull { it.id == activeWorkspaceId }?.name ?: "Kein aktiver Workspace"
    }

    LaunchedEffect(Unit) {
        viewModel.refreshCloudSyncStatus()
    }

    AlertDialog(
        onDismissRequest = {
            stopVoicePreview()
            onDismiss()
        },
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Einstellungen", fontWeight = FontWeight.Bold)
                Text(
                    "Design, Modelle, Privatsphäre und Workspaces auf einen Blick",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.66f)
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
            ) {
                SettingsOverviewCard(
                    planLabel = MonetizationConfig.PlanTier.fromKey(subscriptionTier).label,
                    creditsBalance = creditsBalance,
                    providerLabel = aiProvider,
                    workspaceLabel = activeWorkspaceLabel,
                    syncStatus = cloudSyncStatusText,
                    premiumActive = isPremiumActive,
                    billingReady = billingReady
                )

                SettingsSection("Allgemein", expandedSection == "general", onClick = { expandedSection = if (expandedSection == "general") null else "general" }) {
                    SettingRow("Fingerabdruck-Sperre", "App beim Start sichern") {
                        Switch(checked = isBiometricEnabled, onCheckedChange = { viewModel.setBiometricEnabled(it) })
                    }
                    SettingRow("Benachrichtigungen", "Bei neuen KI-Antworten") {
                        Switch(checked = notificationsEnabled, onCheckedChange = {
                            viewModel.setNotificationsEnabled(it)
                            if (it) openNotificationSettings(context)
                        })
                    }
                    SettingRow("Sound-Effekte", "Töne bei Nachrichten") {
                        Switch(checked = soundEnabled, onCheckedChange = { viewModel.setSoundEnabled(it) })
                    }
                    SettingRow("Vibration", "Haptisches Feedback") {
                        Switch(checked = vibrationEnabled, onCheckedChange = { viewModel.setVibrationEnabled(it) })
                    }
                    SettingRow("Sprache", "App-Sprache wählen") {
                        DropdownSelector(
                            value = languages.find { it.first == language }?.second ?: "Deutsch",
                            items = languages.map { it.second },
                            onSelect = { label ->
                                val code = languages.find { it.second == label }?.first ?: "de"
                                viewModel.setLanguage(code)
                            }
                        )
                    }
                }

                SettingsSection("Darstellung", expandedSection == "chat", onClick = { expandedSection = if (expandedSection == "chat") null else "chat" }) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Akzentfarbe", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                        Spacer(Modifier.width(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(colors) { colorInt ->
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color(colorInt))
                                        .clickable { viewModel.setPrimaryColor(colorInt.toInt()) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (primaryColor == colorInt.toInt()) {
                                        Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Schriftgröße (${fontSize.toInt()} sp)", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                        Slider(
                            value = fontSize,
                            onValueChange = { viewModel.setFontSize(it) },
                            valueRange = 12f..24f,
                            steps = 6
                        )
                    }
                    SettingRow("Design-Stil", "5 Design-Varianten live testen") {
                        DropdownSelector(
                            value = uiDesignPreset,
                            items = designPresets,
                            onSelect = { viewModel.setUiDesignPreset(it) }
                        )
                    }
                    SettingRow("Anzeige-Preset", "Kompakt, Standard oder Komfort") {
                        DropdownSelector(
                            value = displayPreset,
                            items = SettingsViewModel.DISPLAY_PRESET_OPTIONS,
                            onSelect = { viewModel.setDisplayPreset(it) }
                        )
                    }
                    SettingRow("Zeitstempel anzeigen", "Uhrzeit unter Nachrichten") {
                        Switch(checked = showTimestamps, onCheckedChange = { viewModel.setShowTimestamps(it) })
                    }
                    SettingRow("Bubble-Animationen", "Animierte Nachrichtenblasen") {
                        Switch(checked = bubbleAnimations, onCheckedChange = { viewModel.setBubbleAnimations(it) })
                    }
                    SettingRow("Streaming aktivieren", "Antwort Wort für Wort anzeigen") {
                        Switch(checked = streamingEnabled, onCheckedChange = { viewModel.setStreamingEnabled(it) })
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    SettingRow("Kompakter Chat-Header", "Weniger Höhe, mehr Platz für Nachrichten") {
                        Switch(checked = compactChatHeader, onCheckedChange = { viewModel.setCompactChatHeader(it) })
                    }
                    SettingRow("Leisten unten verbinden", "Chat-Eingabe und Bottom-Navigation ohne Lücke") {
                        Switch(checked = connectChatBottomBars, onCheckedChange = { viewModel.setConnectChatBottomBars(it) })
                    }
                    SettingRow("Glas-Effekt aktiv", "Frosted-Look für Drawer und Chat-Leisten") {
                        Switch(checked = glassEffectsEnabled, onCheckedChange = { viewModel.setGlassEffectsEnabled(it) })
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Rundungen (${(uiCornerRoundnessScale * 100).toInt()}%)",
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp
                        )
                        Slider(
                            value = uiCornerRoundnessScale,
                            onValueChange = { viewModel.setUiCornerRoundnessScale(it) },
                            valueRange = 0.7f..1.4f,
                            steps = 6
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Schattenstärke (${(uiShadowIntensityScale * 100).toInt()}%)",
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp
                        )
                        Slider(
                            value = uiShadowIntensityScale,
                            onValueChange = { viewModel.setUiShadowIntensityScale(it) },
                            valueRange = 0.6f..1.8f,
                            steps = 11
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Flächen-Deckkraft (${(uiSurfaceOpacity * 100).toInt()}%)",
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp
                        )
                        Slider(
                            value = uiSurfaceOpacity,
                            onValueChange = { viewModel.setUiSurfaceOpacity(it) },
                            valueRange = 0.55f..1.0f,
                            steps = 8
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        CompactTextActionRow(
                            actions = listOf(
                                CompactTextAction(
                                    label = "Anzeige zurücksetzen",
                                    onClick = { viewModel.resetDisplaySettings() }
                                )
                            )
                        )
                    }
                }

                SettingsSection("Sprache & Stimme", expandedSection == "voice", onClick = { expandedSection = if (expandedSection == "voice") null else "voice" }) {
                    SettingRow("Sprachmodus", "Durchgehend per Sprache chatten") {
                        Switch(checked = voiceChatMode, onCheckedChange = { viewModel.setVoiceChatMode(it) })
                    }
                    SettingRow("Auto-Senden", "Nach Spracheingabe sofort senden") {
                        Switch(checked = autoSendVoice, onCheckedChange = { viewModel.setAutoSendVoice(it) })
                    }
                    SettingRow("Push-to-Talk", "Mikrofon per Halten/Loslassen steuern") {
                        Switch(checked = voicePushToTalkEnabled, onCheckedChange = { viewModel.setVoicePushToTalkEnabled(it) })
                    }
                    SettingRow("Auto-Vorlesen (TTS)", "KI-Antworten automatisch sprechen") {
                        Switch(checked = ttsEnabled, onCheckedChange = { viewModel.setTtsEnabled(it) })
                    }
                    if (ttsEnabled) {
                        SettingRow("Pro Voice", "Bessere Stimme + natürlichere Sprechweise") {
                            Switch(
                                checked = ttsProVoiceEnabled,
                                onCheckedChange = { viewModel.setTtsProVoiceEnabled(it) }
                            )
                        }
                        SettingRow("Cloud Voice", "Natürliche Stimme über ElevenLabs oder Piper") {
                            Switch(
                                checked = cloudVoiceEnabled,
                                onCheckedChange = { viewModel.setCloudVoiceEnabled(it) }
                            )
                        }
                        if (cloudVoiceEnabled) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "Provider wählen. Es werden nur die passenden Felder angezeigt.",
                                    fontSize = 10.sp,
                                    color = Color.White.copy(alpha = 0.62f)
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    CloudVoiceManager.Provider.entries.forEach { provider ->
                                        FilterChip(
                                            selected = selectedCloudVoiceProvider == provider,
                                            onClick = { viewModel.setCloudVoiceProvider(provider.storageValue) },
                                            label = { Text(provider.displayName, fontSize = 11.sp) }
                                        )
                                    }
                                }
                            }
                            when (selectedCloudVoiceProvider) {
                                CloudVoiceManager.Provider.ELEVENLABS -> {
                                    OutlinedTextField(
                                        value = elevenLabsApiKey,
                                        onValueChange = { viewModel.setElevenLabsApiKey(it) },
                                        label = { Text("ElevenLabs API-Key", fontSize = 12.sp) },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        placeholder = { Text("sk_...", fontSize = 11.sp) },
                                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                                    )
                                    OutlinedTextField(
                                        value = elevenLabsVoiceId,
                                        onValueChange = { viewModel.setElevenLabsVoiceId(it) },
                                        label = { Text("Voice ID", fontSize = 12.sp) },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                                    )
                                    OutlinedTextField(
                                        value = elevenLabsModelId,
                                        onValueChange = { viewModel.setElevenLabsModelId(it) },
                                        label = { Text("Model ID", fontSize = 12.sp) },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        placeholder = { Text("eleven_multilingual_v2", fontSize = 11.sp) },
                                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                                    )
                                }
                                CloudVoiceManager.Provider.PIPER -> {
                                    OutlinedTextField(
                                        value = piperEndpoint,
                                        onValueChange = { viewModel.setPiperEndpoint(it) },
                                        label = { Text("Piper Endpoint", fontSize = 12.sp) },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        placeholder = { Text("http://192.168.178.162:5000", fontSize = 11.sp) },
                                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                                    )
                                    OutlinedTextField(
                                        value = piperVoiceName,
                                        onValueChange = { viewModel.setPiperVoiceName(it) },
                                        label = { Text("Voice Name (optional)", fontSize = 12.sp) },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        placeholder = { Text("de_DE-thorsten-high", fontSize = 11.sp) },
                                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                                    )
                                    Text(
                                        "Piper läuft typischerweise lokal oder im Heimnetz als HTTP-Server und liefert WAV-Dateien zurück.",
                                        fontSize = 10.sp,
                                        color = Color.White.copy(alpha = 0.62f)
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = {
                                    _uriHandler.openUri(selectedCloudVoiceProvider.docsUrl)
                                }) {
                                    Text("Voice-Docs öffnen", fontSize = 11.sp)
                                }
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Stimmstil (A/B-Test)", fontWeight = FontWeight.Medium, fontSize = 12.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = !useClearVoiceStyle,
                                    onClick = {
                                        viewModel.setTtsVoiceStyle(SettingsViewModel.TTS_STYLE_NATURAL)
                                        viewModel.applyNaturalTtsPreset()
                                    },
                                    label = { Text("Natürlich", fontSize = 11.sp) }
                                )
                                FilterChip(
                                    selected = useClearVoiceStyle,
                                    onClick = {
                                        viewModel.setTtsVoiceStyle(SettingsViewModel.TTS_STYLE_CLEAR)
                                        viewModel.applyClearTtsPreset()
                                    },
                                    label = { Text("Klar/Präzise", fontSize = 11.sp) }
                                )
                            }
                            Text(
                                if (useClearVoiceStyle)
                                    "Klar/Präzise: direkter, kompakter und mit kuerzeren Sprachpausen."
                                else
                                    "Natürlich: weicher Klang mit leicht langsamem Tempo und natürlicheren Pausen.",
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.62f)
                            )
                        }
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Voice-Testmodus", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                Text(
                                    "Teste die aktuelle Stimme mit kurzen Beispielen (beruecksichtigt Speed/Pitch und optional Cloud Voice).",
                                    fontSize = 10.sp,
                                    color = Color.White.copy(alpha = 0.65f)
                                )
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(items = voicePreviewSamples, key = { it.id }) { sample ->
                                        OutlinedButton(
                                            onClick = { playVoicePreview(sample) },
                                            enabled = ttsEnabled,
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Text(sample.label, fontSize = 11.sp)
                                        }
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        if (voicePreviewStatus.isNotBlank()) voicePreviewStatus else "Bereit fuer einen Testlauf.",
                                        fontSize = 10.sp,
                                        color = Color.White.copy(alpha = 0.6f),
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(
                                        onClick = { stopVoicePreview() },
                                        enabled = voicePreviewPlaying
                                    ) {
                                        Text("Stopp", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("TTS-Geschwindigkeit (${String.format(Locale.getDefault(), "%.1fx", ttsSpeed)})", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                            Slider(
                                value = ttsSpeed,
                                onValueChange = { viewModel.setTtsSpeed(it) },
                                valueRange = 0.5f..2.0f,
                                steps = 6
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("TTS-Stimmhöhe (${String.format(Locale.getDefault(), "%.2f", ttsPitch)})", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                            Slider(
                                value = ttsPitch,
                                onValueChange = { viewModel.setTtsPitch(it) },
                                valueRange = 0.8f..1.2f,
                                steps = 7
                            )
                        }
                    }
                }

                SettingsSection("Workspaces & Automationen", expandedSection == "workspaces", onClick = { expandedSection = if (expandedSection == "workspaces") null else "workspaces" }) {
                    Text("Aktiver Projekt-Workspace", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                    projectWorkspaces.forEach { workspace ->
                        val isActive = workspace.id == activeWorkspaceId
                        val isRenaming = renamingWorkspaceId == workspace.id
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 56.dp)
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    if (isRenaming) {
                                        OutlinedTextField(
                                            value = renamingDraft,
                                            onValueChange = { renamingDraft = it },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                            textStyle = LocalTextStyle.current.copy(fontSize = 15.sp),
                                            label = { Text("Name", fontSize = 13.sp) },
                                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                            keyboardActions = KeyboardActions(onDone = {
                                                if (viewModel.renameWorkspace(workspace.id, renamingDraft)) {
                                                    renamingWorkspaceId = null
                                                    renamingDraft = ""
                                                    focusManager.clearFocus()
                                                }
                                            })
                                        )
                                    } else {
                                        Text(workspace.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                        if (workspace.description.isNotBlank()) {
                                            Text(
                                                workspace.description,
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                                            )
                                        }
                                    }
                                }
                                if (isRenaming) {
                                    // P1-1: confirm / cancel rename — full 48 dp IconButtons
                                    IconButton(
                                        onClick = {
                                            if (viewModel.renameWorkspace(workspace.id, renamingDraft)) {
                                                renamingWorkspaceId = null
                                                renamingDraft = ""
                                                focusManager.clearFocus()
                                            }
                                        },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = "Speichern")
                                    }
                                    IconButton(
                                        onClick = {
                                            renamingWorkspaceId = null
                                            renamingDraft = ""
                                            focusManager.clearFocus()
                                        },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Abbrechen")
                                    }
                                } else {
                                    CompactTextActionRow(
                                        actions = listOfNotNull(
                                            CompactTextAction(
                                                label = if (isActive) "Aktiv" else "Aktivieren",
                                                onClick = { viewModel.setActiveWorkspace(workspace.id) }
                                            ),
                                            CompactTextAction(
                                                label = "Umbenennen",
                                                onClick = {
                                                    renamingWorkspaceId = workspace.id
                                                    renamingDraft = workspace.name
                                                }
                                            ),
                                            workspace.id.takeIf { it != "ws-default" }?.let {
                                                CompactTextAction(
                                                    label = "Löschen",
                                                    onClick = { workspacePendingDelete = workspace },
                                                    color = Color(0xFFD63031)
                                                )
                                            }
                                        )
                                    )
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = newWorkspaceName,
                        onValueChange = { newWorkspaceName = it },
                        label = { Text("Neuer Workspace", fontSize = 14.sp) },
                        placeholder = { Text("z.B. Kundenprojekt Alpha", fontSize = 14.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 15.sp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            val name = newWorkspaceName.trim()
                            if (name.isNotBlank() && viewModel.createWorkspace(name)) {
                                newWorkspaceName = ""
                                focusManager.clearFocus()
                            }
                        })
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = newWorkspaceName.trim().isNotBlank(),
                            onClick = {
                                if (viewModel.createWorkspace(newWorkspaceName.trim())) {
                                    newWorkspaceName = ""
                                    focusManager.clearFocus()
                                }
                            },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("Workspace erstellen", fontSize = 14.sp)
                        }
                    }
                    SettingRow("Nur aktive Workspace-Chats", "Chatliste auf aktiven Workspace filtern") {
                        Switch(
                            checked = workspaceChatFilterEnabled,
                            onCheckedChange = { viewModel.setWorkspaceChatFilterEnabled(it) }
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    SettingRow("Schnellaktionen in Chat", "Zeigt Chips oder einen kompakten Smart-Selector") {
                        Switch(
                            checked = automationQuickActionsEnabled,
                            onCheckedChange = { viewModel.setAutomationQuickActionsEnabled(it) }
                        )
                    }
                    SettingRow("Agent-Tool Aktionen bestätigen", "Vor Tool-Ausführung immer bestätigen") {
                        Switch(
                            checked = agentConfirmToolActions,
                            onCheckedChange = { viewModel.setAgentConfirmToolActions(it) },
                            modifier = Modifier.scale(0.85f)
                        )
                    }
                }

                SettingsSection("KI & Modelle", expandedSection == "ai", onClick = { expandedSection = if (expandedSection == "ai") null else "ai" }) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = if (isPremiumActive) Color(0xFF0B3D2D) else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    val tierLabel = MonetizationConfig.PlanTier.fromKey(subscriptionTier).label
                                    Text("Plan: $tierLabel", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = if (isPremiumActive) Color(0xFFB4F2D8) else LocalContentColor.current)
                                    Text(
                                        if (isPremiumActive) "Aktiv: Erweiterte Limits freigeschaltet"
                                        else "Free-Plan mit Tageslimits aktiv",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 11.sp,
                                        color = if (isPremiumActive) Color(0xFFB4F2D8) else LocalContentColor.current
                                    )
                                    Text(
                                        "Credits: $creditsBalance",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 11.sp
                                    )
                                }
                                if (isPremiumActive) {
                                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF67E2AE), modifier = Modifier.size(18.dp))
                                }
                            }
                            Text(
                                if (billingReady) "Play Billing verbunden" else "Play Billing noch nicht verbunden",
                                fontSize = 11.sp,
                                color = if (billingReady) Color(0xFF00B894) else Color.Gray
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AssistChip(
                                    onClick = {
                                        val activity = context as? android.app.Activity ?: return@AssistChip
                                        viewModel.startSubscriptionCheckout(activity, PlayBillingManager.PLAN_PRO)
                                    },
                                    label = { Text("Pro 7,99€") },
                                    enabled = billingReady && !purchaseInProgress
                                )
                                AssistChip(
                                    onClick = {
                                        val activity = context as? android.app.Activity ?: return@AssistChip
                                        viewModel.startSubscriptionCheckout(activity, PlayBillingManager.PLAN_EXPERT)
                                    },
                                    label = { Text("Expert 19,99€") },
                                    enabled = billingReady && !purchaseInProgress
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AssistChip(
                                    onClick = {
                                        val activity = context as? android.app.Activity ?: return@AssistChip
                                        viewModel.startCreditsCheckout(activity, PlayBillingManager.CREDIT_100)
                                    },
                                    label = { Text("100 Credits") },
                                    enabled = billingReady && !purchaseInProgress
                                )
                                AssistChip(
                                    onClick = {
                                        val activity = context as? android.app.Activity ?: return@AssistChip
                                        viewModel.startCreditsCheckout(activity, PlayBillingManager.CREDIT_300)
                                    },
                                    label = { Text("300 Credits") },
                                    enabled = billingReady && !purchaseInProgress
                                )
                                AssistChip(
                                    onClick = {
                                        val activity = context as? android.app.Activity ?: return@AssistChip
                                        viewModel.startCreditsCheckout(activity, PlayBillingManager.CREDIT_1000)
                                    },
                                    label = { Text("1000 Credits") },
                                    enabled = billingReady && !purchaseInProgress
                                )
                            }
                            CompactTextActionRow(
                                actions = listOfNotNull(
                                    CompactTextAction(
                                        label = "Billing aktualisieren",
                                        onClick = { viewModel.refreshBillingState() }
                                    ),
                                    if (!billingReady && BuildConfig.DEBUG) {
                                        CompactTextAction(
                                            label = "Premium-Test lokal",
                                            onClick = { viewModel.setPremiumActiveForDebug(true) }
                                        )
                                    } else null
                                )
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1A2A3A).copy(alpha = 0.95f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp, Color(0xFF43C6AC).copy(alpha = if (aiProvider == "Ollama") 0.7f else 0.2f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.CloudOff, null, tint = if (aiProvider == "Ollama") Color(0xFF43C6AC) else Color.White.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                                    Text("Ollama Offline-Modus", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White)
                                }
                                Switch(
                                    checked = aiProvider == "Ollama",
                                    onCheckedChange = { checked ->
                                        viewModel.setAiProvider(if (checked) "Ollama" else "OpenRouter")
                                    },
                                    modifier = Modifier.scale(0.85f),
                                    colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF43C6AC))
                                )
                            }
                            if (aiProvider == "Ollama") {
                                Text(
                                    "Aktiv - alle Anfragen laufen lokal über Ollama",
                                    fontSize = 11.sp,
                                    color = Color(0xFF43C6AC)
                                )
                            } else {
                                Text(
                                    "Inaktiv - Cloud-Provider werden genutzt",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                            }
                            Text("Ollama Server-URL", fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedTextField(
                                    value = ollamaUrl,
                                    onValueChange = { viewModel.setOllamaUrl(it) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, color = Color.White),
                                    placeholder = { Text("http://192.168.178.162:11434/", fontSize = 11.sp) },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF43C6AC),
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                                    )
                                )
                            }
                        }
                    }

                    if (mcpServerManager != null) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("Remote MCP Bridge", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                Text(
                                    "HTTP JSON-RPC Endpoint für MCP (statt lokalem npx auf Android)",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 10.sp,
                                    color = Color.White.copy(alpha = 0.65f)
                                )
                                OutlinedTextField(
                                    value = mcpRemoteUrl,
                                    onValueChange = { viewModel.setMcpRemoteUrl(it) },
                                    label = { Text("Remote MCP URL", fontSize = 11.sp) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    placeholder = { Text("https://your-mcp-bridge.example.com/mcp", fontSize = 10.sp) },
                                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                                )
                                if (mcpRemoteUrlWarning.isNotBlank()) {
                                    Text(
                                        mcpRemoteUrlWarning,
                                        fontSize = 10.sp,
                                        color = Color(0xFFFFC107)
                                    )
                                }
                                OutlinedTextField(
                                    value = mcpRemoteToken,
                                    onValueChange = { viewModel.setMcpRemoteToken(it) },
                                    label = { Text("Bridge Token (optional)", fontSize = 11.sp) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    placeholder = { Text("Bearer Token", fontSize = 10.sp) },
                                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                                )
                            }
                        }
                        McpServersSection(mcpServerManager, mcpWorkflowManager)
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = if (multiProvider) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Auto-Fallback", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text("Bei Fehler automatisch nächsten Anbieter probieren", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp)
                            }
                            Switch(checked = multiProvider, onCheckedChange = { viewModel.setMultiProviderEnabled(it) }, modifier = Modifier.scale(0.85f))
                        }
                    }

                    if (!multiProvider) {
                        SettingRow(
                            "Aktiver Provider",
                            "Wird genutzt, wenn Auto-Fallback deaktiviert ist"
                        ) {
                            DropdownSelector(
                                value = aiProvider,
                                items = aiProviderOptions,
                                onSelect = { viewModel.setAiProvider(it) }
                            )
                        }
                    }

                    ProviderCardMini("Cerebras", "ULTRA schnell (~2000 tok/s)", "https://cloud.cerebras.ai/", cerebrasApiKey, "csk-...") { viewModel.setCerebrasApiKey(it) }
                    ProviderCardMini("Groq", "Sehr schnell, 30 req/min", "https://console.groq.com/keys", groqApiKey, "gsk_...") { viewModel.setGroqApiKey(it) }
                    ProviderCardMini("OpenRouter", "Viele freie Modelle", "https://openrouter.ai/keys", openRouterApiKey, "sk-or-v1-...") { viewModel.setOpenRouterApiKey(it) }

                    ProviderCardMini(
                        "OpenCode",
                        "OpenCode Zen (x-api-key)",
                        "https://opencode.ai/",
                        openCodeApiKey,
                        "sk-..."
                    ) { viewModel.setOpenCodeApiKey(it) }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            TextButton(onClick = { viewModel.setOpenCodeEndpoint("https://opencode.ai/zen/v1/") }) {
                                Text("Zen Endpoint", fontSize = 11.sp)
                            }
                        }
                        item {
                            TextButton(onClick = { viewModel.setOpenCodeModel("claude-sonnet-4-5") }) {
                                Text("Sonnet", fontSize = 11.sp)
                            }
                        }
                        item {
                            TextButton(onClick = { viewModel.setOpenCodeModel("claude-opus-4-5") }) {
                                Text("Opus", fontSize = 11.sp)
                            }
                        }
                        item {
                            TextButton(onClick = { viewModel.setOpenCodeModel("claude-haiku-4-5") }) {
                                Text("Haiku", fontSize = 11.sp)
                            }
                        }
                        item {
                            TextButton(onClick = { viewModel.setOpenCodeModel("gpt-5.3-codex") }) {
                                Text("Codex", fontSize = 11.sp)
                            }
                        }
                        item {
                            TextButton(onClick = { viewModel.setOpenCodeModel("gpt-5.4-mini") }) {
                                Text("GPT", fontSize = 11.sp)
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = customAccentHex,
                            onValueChange = { customAccentHex = it.trim() },
                            label = { Text("Eigene Akzentfarbe", fontSize = 11.sp) },
                            placeholder = { Text("#4E7DE8", fontSize = 10.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                        )
                        Button(
                            onClick = {
                                parsedCustomAccent?.let { colorInt ->
                                    viewModel.setPrimaryColor(colorInt)
                                    customAccentHex = colorToHex(colorInt)
                                }
                            },
                            enabled = parsedCustomAccent != null
                        ) {
                            Text("Anwenden", fontSize = 11.sp)
                        }
                    }
                    if (parsedCustomAccent == null && customAccentHex.isNotBlank()) {
                        Text("Format: #RRGGBB oder #AARRGGBB", fontSize = 10.sp, color = Color(0xFFFFC107))
                    }
                    OutlinedTextField(
                        value = openCodeEndpoint,
                        onValueChange = { viewModel.setOpenCodeEndpoint(it) },
                        label = { Text("OpenCode Endpoint", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("https://opencode.ai/zen/v1/", fontSize = 10.sp) },
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                    )
                    if (openCodeEndpointWarning.isNotBlank()) {
                        Text(openCodeEndpointWarning, fontSize = 10.sp, color = Color(0xFFFFC107))
                    }
                    OutlinedTextField(
                        value = openCodeModel,
                        onValueChange = { viewModel.setOpenCodeModel(it) },
                        label = { Text("OpenCode Modell", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("claude-sonnet-4-5", fontSize = 10.sp) },
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                    )
                    Text(
                        "Key-Verwaltung: opencode.ai/auth -> console.opencode.ai (API Keys)",
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )

                    if (openRouterApiKey.isNotBlank()) {
                        SettingRow(
                            "Nur Vision-Modelle",
                            "Für Bild-Uploads nur bildfähige Modelle anzeigen"
                        ) {
                            Switch(
                                checked = openRouterVisionOnlyModels,
                                onCheckedChange = { viewModel.setOpenRouterVisionOnlyModels(it) },
                                modifier = Modifier.scale(0.85f)
                            )
                        }

                        val selectableModels = if (openRouterVisionOnlyModels) {
                            ApiClient.OPENROUTER_VISION_MODELS
                        } else {
                            ApiClient.OPENROUTER_FREE_MODELS
                        }

                        Column(modifier = Modifier.padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Modell:", fontWeight = FontWeight.Medium, fontSize = 12.sp)
                            selectableModels.forEach { modelId ->
                                val displayName = ApiClient.FREE_MODEL_DISPLAY_NAMES[modelId] ?: modelId.takeLast(20)
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { viewModel.setSelectedOpenRouterModel(modelId) }) {
                                    RadioButton(selected = selectedOpenRouterModel == modelId, onClick = { viewModel.setSelectedOpenRouterModel(modelId) }, modifier = Modifier.size(20.dp))
                                    Text(displayName, fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    ProviderCardMini("Together AI", "Llama 70B kostenlos", "https://api.together.xyz/settings/api-keys", togetherApiKey, "tgp_v1_...") { viewModel.setTogetherApiKey(it)                     }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    OutlinedTextField(
                        value = geminiApiKey,
                        onValueChange = { viewModel.setGeminiApiKey(it) },
                        label = { Text("Gemini API-Key", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("AIza...") },
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    SettingRow(
                        "Live-Web-Recherche",
                        "Agenten holen bei Bedarf aktuelle Quellen aus dem Internet"
                    ) {
                        Switch(
                            checked = liveWebEnabled,
                            onCheckedChange = { viewModel.setLiveWebEnabled(it) },
                            modifier = Modifier.scale(0.85f)
                        )
                    }
                    SettingRow(
                        "Live-Quellen im Chat",
                        "Zeigt oder versteckt den Quellen-Block unter Antworten"
                    ) {
                        Switch(
                            checked = showLiveSources,
                            onCheckedChange = { viewModel.setShowLiveSources(it) },
                            modifier = Modifier.scale(0.85f)
                        )
                    }
                    SettingRow(
                        "Auto-Spracherkennung",
                        "Erkennt die Eingabesprache und nutzt sie als Antwort-Kontext"
                    ) {
                        Switch(
                            checked = autoLanguageDetectionEnabled,
                            onCheckedChange = { viewModel.setAutoLanguageDetectionEnabled(it) },
                            modifier = Modifier.scale(0.85f)
                        )
                    }
                    SettingRow(
                        "Lokale OCR für Bilder",
                        "Liest Text aus Bildern lokal aus und nutzt ihn bei der Bildanalyse"
                    ) {
                        Switch(
                            checked = localOcrEnabled,
                            onCheckedChange = { viewModel.setLocalOcrEnabled(it) },
                            modifier = Modifier.scale(0.85f)
                        )
                    }

                    if (liveWebEnabled) {
                        OutlinedTextField(
                            value = liveWebEndpoint,
                            onValueChange = { viewModel.setLiveWebEndpoint(it) },
                            label = { Text("Web-Search Function URL", fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = {
                                Text(
                                    "https://websearch-<hash>-ew.a.run.app",
                                    fontSize = 10.sp
                                )
                            },
                            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                        )
                        OutlinedTextField(
                            value = liveWebApiToken,
                            onValueChange = { viewModel.setLiveWebApiToken(it) },
                            label = { Text("Function Access Token (optional)", fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("Bearer Token", fontSize = 10.sp) },
                            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                        )
                        OutlinedTextField(
                            value = liveWebAllowedDomains,
                            onValueChange = { viewModel.setLiveWebAllowedDomains(it) },
                            label = { Text("Domain-Allowlist (CSV)", fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 4,
                            placeholder = { Text("wikipedia.org,reuters.com,tagesschau.de", fontSize = 10.sp) },
                            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                        )
                        SettingRow(
                            "GitHub bevorzugen",
                            "Bei Coding-Fragen zuerst Repos/Issues/Release-Infos nutzen"
                        ) {
                            Switch(
                                checked = liveWebPreferGithub,
                                onCheckedChange = { viewModel.setLiveWebPreferGithub(it) },
                                modifier = Modifier.scale(0.85f)
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        "Photo AI Cloud (Background Remove / Upscale)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text("Bildgenerierung im Chat", fontWeight = FontWeight.Medium, fontSize = 12.sp)
                    DropdownSelector(
                        value = imageGenerationMode,
                        items = SettingsViewModel.IMAGE_GENERATION_MODE_OPTIONS,
                        onSelect = { viewModel.setImageGenerationMode(it) }
                    )
                    Text(
                        "Hinweis: Der externe Bilddienst kann ohne eigene Auth zeitweise Zahlung/Auth verlangen. Bei Deaktiviert zeigt der Chat nur einen Hinweis und erzeugt keine Bildkarte.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = photoAiCloudEndpoint,
                        onValueChange = { viewModel.setPhotoAiCloudEndpoint(it) },
                        label = { Text("Photo-Edit Function URL", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = {
                            Text(
                                "https://europe-west1-<project-id>.cloudfunctions.net/photoEdit",
                                fontSize = 10.sp
                            )
                        },
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                    )
                    OutlinedTextField(
                        value = photoAiCloudApiToken,
                        onValueChange = { viewModel.setPhotoAiCloudApiToken(it) },
                        label = { Text("Photo-Edit Token (optional)", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("Bearer Token", fontSize = 10.sp) },
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    SettingRow(
                        "Entwickler-Modus",
                        "Mehr Trainings- und Testfunktionen"
                    ) {
                        Switch(
                            checked = developerModeEnabled,
                            onCheckedChange = { viewModel.setDeveloperModeEnabled(it) },
                            modifier = Modifier.scale(0.85f)
                        )
                    }
                    if (developerModeEnabled) {
                        SettingRow(
                            "Unlimited Training",
                            "Quotas für Developer-Training aussetzen"
                        ) {
                            Switch(
                                checked = developerUnlimitedTraining,
                                onCheckedChange = { viewModel.setDeveloperUnlimitedTraining(it) },
                                modifier = Modifier.scale(0.85f)
                            )
                        }
                        SettingRow(
                            "Realtime Collab Test",
                            "Collab ohne Login im Developer-Modus testen"
                        ) {
                            Switch(
                                checked = developerRealtimeCollabTesting,
                                onCheckedChange = { viewModel.setDeveloperRealtimeCollabTesting(it) },
                                modifier = Modifier.scale(0.85f)
                            )
                        }
                    }
                }

                SettingsSection("Agenten", expandedSection == "agents", onClick = { expandedSection = if (expandedSection == "agents") null else "agents" }) {
                    SettingRow(
                        "Agent-Studio aktiv",
                        "Erweitert den System-Prompt mit Agent-Profil"
                    ) {
                        Switch(
                            checked = agentStudioEnabled,
                            onCheckedChange = { viewModel.setAgentStudioEnabled(it) },
                            modifier = Modifier.scale(0.85f)
                        )
                    }
                    Text("Agent-Typ", fontWeight = FontWeight.Medium, fontSize = 12.sp)
                    DropdownSelector(
                        value = agentPreset,
                        items = agentPresets,
                        onSelect = { viewModel.applyAgentPreset(it) }
                    )
                    OutlinedTextField(
                        value = agentName,
                        onValueChange = { viewModel.setAgentName(it) },
                        label = { Text("Agent-Name", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                    )
                    OutlinedTextField(
                        value = agentGoal,
                        onValueChange = { viewModel.setAgentGoal(it) },
                        label = { Text("Ziel / Mission", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4,
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                    )
                    OutlinedTextField(
                        value = agentRules,
                        onValueChange = { viewModel.setAgentRules(it) },
                        label = { Text("Regeln / Guardrails", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 5,
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                    )
                    Text("Ausgabestil", fontWeight = FontWeight.Medium, fontSize = 12.sp)
                    DropdownSelector(
                        value = agentOutputStyle,
                        items = outputStyles,
                        onSelect = { viewModel.setAgentOutputStyle(it) }
                    )
                    OutlinedTextField(
                        value = agentTools,
                        onValueChange = { viewModel.setAgentTools(it) },
                        label = { Text("Arbeitsweisen / Tools", fontSize = 12.sp) },
                        placeholder = { Text("z.B. Recherche, Faktencheck, Planen", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4,
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                    )

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Prompt-Vorschau", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Text(
                                text = agentPreview,
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                            )
                        }
                    }
                }

                SettingsSection("Datenschutz & Daten", expandedSection == "data", onClick = { expandedSection = if (expandedSection == "data") null else "data" }) {
                    SettingRow("Persona-Cloud-Sync", cloudSyncStatusText) {
                        CompactTextActionRow(
                            actions = listOf(
                                CompactTextAction(
                                    label = "Aktualisieren",
                                    onClick = { viewModel.refreshCloudSyncStatus() }
                                )
                            )
                        )
                    }
                    SettingRow("Privacy Strict Mode", "Maskiert sensible Inhalte stärker in Logs/Status") {
                        Switch(
                            checked = privacyStrictModeEnabled,
                            onCheckedChange = { viewModel.setPrivacyStrictModeEnabled(it) }
                        )
                    }
                    SettingRow("Rechtliches", "Öffentliche Texte und Kontaktwege") {
                        CompactTextActionRow(
                            actions = listOf(
                                CompactTextAction(
                                    label = "Datenschutz",
                                    onClick = { _uriHandler.openUri(LegalPolicy.PRIVACY_POLICY_URL) }
                                ),
                                CompactTextAction(
                                    label = "AGB",
                                    onClick = { _uriHandler.openUri(LegalPolicy.TERMS_URL) }
                                ),
                                CompactTextAction(
                                    label = "Löschung",
                                    onClick = { _uriHandler.openUri(LegalPolicy.ACCOUNT_DELETION_URL) }
                                ),
                                CompactTextAction(
                                    label = "Support",
                                    onClick = { _uriHandler.openUri(LegalPolicy.SUPPORT_URL) }
                                )
                            )
                        )
                    }
                    SettingRow("Gastdaten bei Konto-Login löschen", "Schützt private Testdaten beim Wechsel auf echtes Konto") {
                        Switch(
                            checked = guestAutoClearOnAccountSignIn,
                            onCheckedChange = { viewModel.setGuestAutoClearOnAccountSignIn(it) }
                        )
                    }
                    SettingRow("Gastdaten beim Abmelden löschen", "Löscht lokale Gast-Chats und Persona-Lernstände") {
                        Switch(
                            checked = guestAutoClearOnSignOut,
                            onCheckedChange = { viewModel.setGuestAutoClearOnSignOut(it) }
                        )
                    }
                    SettingRow("Gast-/Prompt-Daten jetzt löschen", "Löscht nur lokale Session-, Prompt- und Lern-Daten") {
                        CompactTextActionRow(
                            actions = listOf(
                                CompactTextAction(
                                    label = "Bereinigen",
                                    onClick = { viewModel.clearGuestPrivateData() },
                                    color = Color(0xFFE17055)
                                )
                            )
                        )
                    }
                    SettingRow("Lokale Daten löschen", "Einstellungen und Chats auf diesem Gerät zurücksetzen") {
                        CompactTextActionRow(
                            actions = listOf(
                                CompactTextAction(
                                    label = "Löschen",
                                    onClick = { viewModel.clearAllData() },
                                    color = Color(0xFFD63031)
                                )
                            )
                        )
                    }
                    SettingRow("App-Info öffnen", "Berechtigungen verwalten") {
                        CompactTextActionRow(
                            actions = listOf(
                                CompactTextAction(
                                    label = "Öffnen",
                                    onClick = {
                                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = Uri.fromParts("package", context.packageName, null)
                                        }
                                        context.startActivity(intent)
                                    }
                                )
                            )
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.Center) {
                        Text(
                            "BamaChat · Version ${BuildConfig.VERSION_NAME}",
                            color = Color.White.copy(alpha = 0.48f),
                            fontSize = 10.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fertig") }
        }
    )

    // P0-2: confirmation dialog for workspace deletion
    workspacePendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { workspacePendingDelete = null },
            title = { Text("Workspace löschen?") },
            text = {
                Text(
                    "Workspace \"${target.name}\" wirklich löschen? Die zugehörigen Chats bleiben erhalten, " +
                        "werden aber bei aktivem Workspace-Filter ausgeblendet.",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteWorkspace(target.id)
                    workspacePendingDelete = null
                }) { Text("Löschen", color = Color(0xFFD63031)) }
            },
            dismissButton = {
                TextButton(onClick = { workspacePendingDelete = null }) { Text("Abbrechen") }
            }
        )
    }
}

@Composable
private fun SettingsOverviewCard(
    planLabel: String,
    creditsBalance: Int,
    providerLabel: String,
    workspaceLabel: String,
    syncStatus: String,
    premiumActive: Boolean,
    billingReady: Boolean
) {
    val statusLabel = when {
        premiumActive -> "Premium aktiv"
        billingReady -> "Billing bereit"
        else -> "Free-Plan"
    }
    val statusColor = when {
        premiumActive -> Color(0xFF67E2AE)
        billingReady -> Color(0xFFFFD166)
        else -> Color.White.copy(alpha = 0.7f)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Schnellüberblick", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text(
                        "Kontostatus, Provider und Sync auf einen Blick.",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.66f)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = statusColor.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.22f))
                ) {
                    Text(
                        statusLabel,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        color = statusColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    SettingsSummaryChip("Plan", planLabel)
                }
                item {
                    SettingsSummaryChip("Credits", creditsBalance.toString())
                }
                item {
                    SettingsSummaryChip("Provider", providerLabel)
                }
                item {
                    SettingsSummaryChip("Workspace", workspaceLabel)
                }
            }

            Text(
                syncStatus,
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.62f)
            )
        }
    }
}

@Composable
private fun SettingsSummaryChip(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.05f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 92.dp, max = 150.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                fontSize = 12.sp,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SettingsSection(title: String, expanded: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (expanded) 0.36f else 0.24f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
    ) {
        Column(modifier = Modifier.padding(14.dp).animateContentSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClick() }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
            if (expanded) {
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String? = null, action: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, fontSize = 13.sp)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.66f)
                )
            }
        }
        action()
    }
}

@Composable
private fun DropdownSelector(value: String, items: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
        Row(
            modifier = Modifier.clickable { expanded = true },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(value, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item, fontSize = 13.sp) },
                    onClick = { onSelect(item); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun ProviderCardMini(
    name: String,
    subtitle: String,
    signupUrl: String,
    apiKey: String,
    placeholder: String,
    onKeyChange: (String) -> Unit,
) {
    val isConfigured = apiKey.isNotBlank()
    val _uriHandler = LocalUriHandler.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = if (isConfigured) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
        border = if (isConfigured) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                 else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(name, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, fontSize = 10.sp)
                }
                if (isConfigured) Icon(Icons.Default.Check, null, tint = Color(0xFF00B894), modifier = Modifier.size(14.dp))
                IconButton(onClick = { _uriHandler.openUri(signupUrl) }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null, modifier = Modifier.size(14.dp))
                }
            }
            OutlinedTextField(
                value = apiKey,
                onValueChange = onKeyChange,
                label = { Text("API Key", fontSize = 10.sp) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(placeholder, fontSize = 10.sp) },
                textStyle = LocalTextStyle.current.copy(fontSize = 11.sp)
            )
        }
    }
}

@Composable
private fun McpServersSection(
    manager: McpServerManager,
    workflowManager: McpWorkflowManager? = null
) {
    val servers by manager.servers.collectAsState()
    val allTools by manager.allTools.collectAsState()
    val connStates by manager.connectionStates.collectAsState()
    val scope = rememberCoroutineScope()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF1A2A3A).copy(alpha = 0.95f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF9B59B6).copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.Extension, null, tint = Color(0xFF9B59B6), modifier = Modifier.size(18.dp))
                Text("MCP Server", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White)
                Spacer(Modifier.weight(1f))
                Text("${servers.size} Server", fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f))
            }
            servers.forEach { config ->
                val connState = connStates[config.id]
                val isConnected = connState == McpConnectionStatus.CONNECTED
                val isError = connState == McpConnectionStatus.ERROR
                val isRemote = config.command == "remote_http" || config.id == "remote-bridge"
                val isNpxServer = config.command == "npx"
                // npx-basierte Server sind auf Android nicht ausfuehrbar
                val androidUnsupported = isNpxServer

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            config.name,
                            fontSize = 12.sp,
                            color = if (androidUnsupported) Color.White.copy(alpha = 0.35f) else Color.White,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            when {
                                androidUnsupported -> "Nur auf Desktop verfügbar (npx)"
                                isError -> "Fehler beim Verbinden"
                                isConnected -> "Verbunden"
                                else -> "Getrennt"
                            },
                            fontSize = 10.sp,
                            color = when {
                                androidUnsupported -> Color.White.copy(alpha = 0.25f)
                                isError -> Color(0xFFE74C3C)
                                isConnected -> Color(0xFF00B894)
                                else -> Color.White.copy(alpha = 0.4f)
                            }
                        )
                    }
                    Switch(
                        checked = isConnected,
                        onCheckedChange = { enable ->
                            if (!androidUnsupported) {
                                scope.launch {
                                    if (enable) manager.startServer(config.id)
                                    else manager.stopServer(config.id)
                                }
                            }
                        },
                        enabled = !androidUnsupported,
                        modifier = Modifier.scale(0.75f),
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = Color(0xFF9B59B6),
                            disabledUncheckedTrackColor = Color.White.copy(alpha = 0.1f)
                        )
                    )
                }
            }
            if (allTools.isNotEmpty()) {
                Text("Verfügbare MCP-Tools (${allTools.size})", fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f))
                allTools.take(8).forEach { tool ->
                    Text(" • ${tool.name}", fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f))
                }
                if (allTools.size > 8) {
                    Text(" ... +${allTools.size - 8} weitere", fontSize = 10.sp, color = Color.White.copy(alpha = 0.3f))
                }
            }
            if (workflowManager != null) {
                val wfs by workflowManager.workflows.collectAsState()
                if (wfs.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text("Workflows (${wfs.size})", fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f))
                    wfs.forEach { wf ->
                        Text(" • ${wf.name}", fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}

private data class VoicePreviewSample(
    val id: String,
    val label: String,
    val text: String
)

private fun localeForLanguageCode(code: String): Locale {
    return when (code) {
        "en" -> Locale.ENGLISH
        "pl" -> Locale("pl")
        "fr" -> Locale.FRENCH
        "es" -> Locale("es")
        "tr" -> Locale("tr")
        "ar" -> Locale("ar")
        else -> Locale.GERMAN
    }
}

private fun voicePreviewSamplesForLanguage(code: String): List<VoicePreviewSample> {
    return when (code) {
        "en" -> listOf(
            VoicePreviewSample("business", "Business", "Good morning. Your agenda summary: three prioritized tasks, two pending approvals, and one meeting at ten. Shall I start with item one?"),
            VoicePreviewSample("locker", "Casual", "Hey, welcome back. Quick plan? Inbox first, focus block next, meetings after that."),
            VoicePreviewSample("tech", "Tech", "Tech mode: I inspect build logs, isolate the root cause, verify the fix, and return a step-by-step patch plan.")
        )
        "fr" -> listOf(
            VoicePreviewSample("business", "Business", "Bonjour. Votre synthese du jour: trois priorites, deux validations en attente et une reunion a dix heures. Souhaitez-vous commencer par la premiere action?"),
            VoicePreviewSample("locker", "Detendu", "Salut, content de te revoir. Plan express? D abord les messages, ensuite un bloc focus, puis les rendez-vous."),
            VoicePreviewSample("tech", "Tech", "Mode technique: je lis les logs, j isole la cause racine, je valide le correctif et je propose un plan de patch etape par etape.")
        )
        "es" -> listOf(
            VoicePreviewSample("business", "Business", "Buenos dias. Resumen de agenda: tres tareas priorizadas, dos aprobaciones pendientes y una reunion a las diez. Quieres que empecemos por la primera?"),
            VoicePreviewSample("locker", "Casual", "Hola, que bueno verte. Plan rapido: primero bandeja, luego bloque de foco y despues reuniones."),
            VoicePreviewSample("tech", "Tech", "Modo tecnico: reviso logs de build, aislo la causa raiz, valido el fix y te devuelvo un plan de parche paso a paso.")
        )
        "tr" -> listOf(
            VoicePreviewSample("business", "Business", "Gunaydin. Gunun ozetini paylasiyorum: uc oncelikli gorev, iki bekleyen onay ve saat onda bir toplanti. Ilk maddeyle baslayayim mi?"),
            VoicePreviewSample("locker", "Rahat", "Selam, tekrar hos geldin. Mini plan: once gelen kutusu, sonra odak calismasi, ardindan toplantilar."),
            VoicePreviewSample("tech", "Tech", "Teknik mod: build loglarini incelerim, kok nedeni ayiklarim, duzeltmeyi dogrularim ve adim adim patch plani cikaririm.")
        )
        "ar" -> listOf(
            VoicePreviewSample("business", "عمل", "صباح الخير. هذا ملخص جدولك: ثلاث مهام ذات اولوية، موافقتان معلقتان، واجتماع عند العاشرة. هل ابدأ بالبند الاول؟"),
            VoicePreviewSample("locker", "خفيف", "اهلا بعودتك. خطة سريعة؟ نبدأ بالبريد ثم تركيز قصير وبعدها الاجتماعات."),
            VoicePreviewSample("tech", "تقني", "الوضع التقني: اراجع سجلات البناء، احدد السبب الجذري، اتحقق من الاصلاح، ثم اقدم خطة تصحيح خطوة بخطوة.")
        )
        else -> listOf(
            VoicePreviewSample("business", "Business", "Guten Morgen. Ihr Tagesueberblick: drei priorisierte Aufgaben, zwei offene Freigaben und ein Termin um zehn Uhr. Soll ich mit Punkt eins beginnen?"),
            VoicePreviewSample("locker", "Locker", "Hey, schoen dass du da bist. Kurzplan? Erst Inbox, dann Fokusblock, danach Termine."),
            VoicePreviewSample("tech", "Tech", "Tech-Modus: Ich pruefe Build-Logs, isoliere die Root-Cause, validiere den Fix und liefere einen Schritt-fuer-Schritt Patch-Plan.")
        )
    }
}

private fun parseHexColor(raw: String): Int? {
    val normalized = raw.trim().removePrefix("#")
    if (normalized.length != 6 && normalized.length != 8) return null
    return runCatching {
        val value = normalized.toLong(16)
        if (normalized.length == 6) {
            (0xFF000000 or value).toInt()
        } else {
            value.toInt()
        }
    }.getOrNull()
}

private fun colorToHex(colorInt: Int): String = "#%08X".format(colorInt)

private fun openNotificationSettings(context: android.content.Context) {
    val intent = Intent().apply {
        action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    }
    context.startActivity(intent)
}
