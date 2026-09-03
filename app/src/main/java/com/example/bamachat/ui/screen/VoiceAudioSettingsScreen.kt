package com.example.bamachat.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bamachat.ui.component.settings.AdvancedSettingsSection
import com.example.bamachat.ui.component.settings.SettingsChoiceRow
import com.example.bamachat.ui.component.settings.SettingsInfoCard
import com.example.bamachat.ui.component.settings.SettingsNavigationRow
import com.example.bamachat.ui.component.settings.SettingsSectionTitle
import com.example.bamachat.ui.component.settings.SettingsSliderRow
import com.example.bamachat.ui.component.settings.SettingsToggleRow
import com.example.bamachat.ui.component.settings.SettingsTopBar
import com.example.bamachat.ui.component.settings.VoiceModeChoiceCard
import com.example.bamachat.ui.component.settings.settingsScreenContentPadding
import com.example.bamachat.ui.settings.VoiceModeFamily
import com.example.bamachat.ui.settings.VoiceModeUiPolicy
import com.example.bamachat.ui.viewmodel.BamaVoiceViewModel
import com.example.bamachat.ui.viewmodel.SettingsViewModel
import com.example.bamachat.util.McpServerManager
import com.example.bamachat.util.McpWorkflowManager
import com.example.bamachat.voice.RealtimeTurnTaking
import com.example.bamachat.voice.RealtimeVoice
import com.example.bamachat.voice.VoiceInputProvider
import com.example.bamachat.voice.VoiceMode
import com.example.bamachat.voice.VoiceOutputProvider
import com.example.bamachat.voice.VoiceProviderPolicy
import com.example.bamachat.voice.VoiceSessionState
import java.util.Locale

internal data class VoiceAudioSettingsUiState(
    val mode: VoiceMode,
    val inputProvider: VoiceInputProvider,
    val outputProvider: VoiceOutputProvider,
    val autoPlayback: Boolean,
    val handsFree: Boolean,
    val pushToTalk: Boolean,
    val interruptionEnabled: Boolean,
    val providerFallbackEnabled: Boolean,
    val silenceTimeoutMs: Long,
    val speed: Float,
    val pitch: Float,
    val realtimeVoice: RealtimeVoice,
    val realtimeTurnTaking: RealtimeTurnTaking,
    val realtimeAvailable: Boolean,
    val liveSessionActive: Boolean,
    val previewPlaying: Boolean,
    val piperConfigured: Boolean
)

internal data class VoiceAudioSettingsCallbacks(
    val onBack: () -> Unit,
    val onSelectModeFamily: (VoiceModeFamily) -> Unit,
    val onSelectInputProvider: (VoiceInputProvider) -> Unit,
    val onSelectOutputProvider: (VoiceOutputProvider) -> Unit,
    val onAutoPlaybackChange: (Boolean) -> Unit,
    val onHandsFreeChange: (Boolean) -> Unit,
    val onPushToTalkChange: (Boolean) -> Unit,
    val onInterruptionChange: (Boolean) -> Unit,
    val onProviderFallbackChange: (Boolean) -> Unit,
    val onSilenceTimeoutChange: (Long) -> Unit,
    val onSpeedChange: (Float) -> Unit,
    val onPitchChange: (Float) -> Unit,
    val onSelectRealtimeVoice: (RealtimeVoice) -> Unit,
    val onSelectRealtimeTurnTaking: (RealtimeTurnTaking) -> Unit,
    val onPreviewVoice: () -> Unit,
    val onStopPreview: () -> Unit,
    val onOpenLegacyVoiceSettings: () -> Unit
)

@Composable
fun VoiceAudioSettingsScreen(
    settingsViewModel: SettingsViewModel,
    voiceViewModel: BamaVoiceViewModel,
    cloudChatSyncUid: String? = null,
    onBack: () -> Unit,
    mcpServerManager: McpServerManager? = null,
    mcpWorkflowManager: McpWorkflowManager? = null
) {
    val mode by settingsViewModel.voiceMode.collectAsStateWithLifecycle()
    val inputProvider by settingsViewModel.voiceInputProvider.collectAsStateWithLifecycle()
    val outputProvider by settingsViewModel.voiceOutputProvider.collectAsStateWithLifecycle()
    val autoPlayback by settingsViewModel.ttsEnabled.collectAsStateWithLifecycle()
    val handsFree by settingsViewModel.voiceChatMode.collectAsStateWithLifecycle()
    val pushToTalk by settingsViewModel.voicePushToTalkEnabled.collectAsStateWithLifecycle()
    val interruptionEnabled by settingsViewModel.voiceInterruptionEnabled.collectAsStateWithLifecycle()
    val providerFallbackEnabled by settingsViewModel.voiceProviderFallbackEnabled.collectAsStateWithLifecycle()
    val silenceTimeoutMs by settingsViewModel.voiceSilenceTimeoutMs.collectAsStateWithLifecycle()
    val speed by settingsViewModel.ttsSpeed.collectAsStateWithLifecycle()
    val pitch by settingsViewModel.ttsPitch.collectAsStateWithLifecycle()
    val realtimeVoice by settingsViewModel.realtimeVoice.collectAsStateWithLifecycle()
    val realtimeTurnTaking by settingsViewModel.realtimeTurnTaking.collectAsStateWithLifecycle()
    val piperEndpoint by settingsViewModel.piperEndpoint.collectAsStateWithLifecycle()
    val voiceUiState by voiceViewModel.uiState.collectAsStateWithLifecycle()
    val previewPlaying = voiceUiState.activeOutputMessageId == VOICE_PREVIEW_MESSAGE_ID &&
        voiceUiState.state == VoiceSessionState.Speaking
    val latestPreviewPlaying by rememberUpdatedState(previewPlaying)
    var showLegacyVoiceSettings by rememberSaveable { mutableStateOf(false) }
    var pendingRealtimeVoice by remember { mutableStateOf<RealtimeVoice?>(null) }

    DisposableEffect(voiceViewModel) {
        onDispose {
            if (latestPreviewPlaying) voiceViewModel.stopSpeaking()
        }
    }

    pendingRealtimeVoice?.let { requestedVoice ->
        AlertDialog(
            onDismissRequest = { pendingRealtimeVoice = null },
            title = { Text("Live-Sitzung neu starten?") },
            text = {
                Text("Die Stimme kann während einer aktiven Live-Unterhaltung nicht direkt gewechselt werden.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        voiceViewModel.endLiveSession()
                        settingsViewModel.setRealtimeVoice(requestedVoice)
                        pendingRealtimeVoice = null
                    }
                ) {
                    Text("Beenden und wechseln")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRealtimeVoice = null }) {
                    Text("Abbrechen")
                }
            }
        )
    }

    if (showLegacyVoiceSettings) {
        SettingsDialog(
            viewModel = settingsViewModel,
            voiceViewModel = voiceViewModel,
            cloudChatSyncUid = cloudChatSyncUid,
            onDismiss = { showLegacyVoiceSettings = false },
            initialSection = "voice",
            mcpServerManager = mcpServerManager,
            mcpWorkflowManager = mcpWorkflowManager
        )
    }

    VoiceAudioSettingsContent(
        state = VoiceAudioSettingsUiState(
            mode = mode,
            inputProvider = inputProvider,
            outputProvider = outputProvider,
            autoPlayback = autoPlayback,
            handsFree = handsFree,
            pushToTalk = pushToTalk,
            interruptionEnabled = interruptionEnabled,
            providerFallbackEnabled = providerFallbackEnabled,
            silenceTimeoutMs = silenceTimeoutMs,
            speed = speed,
            pitch = pitch,
            realtimeVoice = realtimeVoice,
            realtimeTurnTaking = realtimeTurnTaking,
            realtimeAvailable = voiceUiState.realtimeAvailable,
            liveSessionActive = voiceUiState.liveSessionActive,
            previewPlaying = previewPlaying,
            piperConfigured = VoiceProviderPolicy.isPrivateNetworkEndpoint(piperEndpoint)
        ),
        callbacks = VoiceAudioSettingsCallbacks(
            onBack = onBack,
            onSelectModeFamily = { family ->
                val target = VoiceModeUiPolicy.selectionTarget(family, mode)
                if (target != mode) settingsViewModel.setVoiceMode(target)
            },
            onSelectInputProvider = settingsViewModel::setVoiceInputProvider,
            onSelectOutputProvider = settingsViewModel::setVoiceOutputProvider,
            onAutoPlaybackChange = settingsViewModel::setTtsEnabled,
            onHandsFreeChange = settingsViewModel::setVoiceChatMode,
            onPushToTalkChange = settingsViewModel::setVoicePushToTalkEnabled,
            onInterruptionChange = settingsViewModel::setVoiceInterruptionEnabled,
            onProviderFallbackChange = settingsViewModel::setVoiceProviderFallbackEnabled,
            onSilenceTimeoutChange = settingsViewModel::setVoiceSilenceTimeoutMs,
            onSpeedChange = settingsViewModel::setTtsSpeed,
            onPitchChange = settingsViewModel::setTtsPitch,
            onSelectRealtimeVoice = { selectedVoice ->
                if (voiceUiState.liveSessionActive) {
                    pendingRealtimeVoice = selectedVoice
                } else {
                    settingsViewModel.setRealtimeVoice(selectedVoice)
                }
            },
            onSelectRealtimeTurnTaking = { turnTaking ->
                if (voiceUiState.liveSessionActive) voiceViewModel.endLiveSession()
                settingsViewModel.setRealtimeTurnTaking(turnTaking)
            },
            onPreviewVoice = {
                voiceViewModel.refreshConfiguration()
                voiceViewModel.previewVoice(VOICE_PREVIEW_TEXT)
            },
            onStopPreview = voiceViewModel::stopSpeaking,
            onOpenLegacyVoiceSettings = { showLegacyVoiceSettings = true }
        )
    )
}

@Composable
internal fun VoiceAudioSettingsContent(
    state: VoiceAudioSettingsUiState,
    callbacks: VoiceAudioSettingsCallbacks
) {
    val visibility = VoiceModeUiPolicy.visibility(state.mode)
    var advancedExpanded by rememberSaveable(state.mode.storageValue) { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.testTag("voice_audio_settings_screen"),
        topBar = { SettingsTopBar(title = "Sprache und Audio", onBack = callbacks.onBack) },
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .testTag("voice_audio_settings_list"),
            contentPadding = settingsScreenContentPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "intro") {
                Text(
                    text = "Wähle, wie du mit BamaChat sprichst und Antworten hörst.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item(key = "mode-title") {
                SettingsSectionTitle("SPRACHMODUS")
            }
            VoiceModeUiPolicy.options.forEach { option ->
                item(key = "mode-${option.family.name}") {
                    val selected = VoiceModeUiPolicy.familyFor(state.mode) == option.family
                    val enabled = option.family != VoiceModeFamily.DirectLive ||
                        state.realtimeAvailable || selected
                    VoiceModeChoiceCard(
                        title = option.title,
                        badge = option.badge,
                        description = option.description,
                        hints = option.hints,
                        selected = selected,
                        enabled = enabled,
                        currentStatus = VoiceModeUiPolicy.smartStatus(state.mode)
                            .takeIf { option.family == VoiceModeFamily.Smart && selected },
                        onClick = { callbacks.onSelectModeFamily(option.family) }
                    )
                }
            }
            if (!state.realtimeAvailable && state.mode != VoiceMode.LIVE) {
                item(key = "live-unavailable") {
                    SettingsInfoCard(
                        text = "Direct Live bleibt deaktiviert, bis der sichere BamaVoice-Server eingerichtet ist."
                    )
                }
            }

            when {
                visibility.showSmartOptions -> smartVoiceItems(state, callbacks, advancedExpanded) {
                    advancedExpanded = it
                }
                visibility.showDirectLiveOptions -> directLiveItems(state, callbacks)
                visibility.showLocalOptions -> localVoiceItems(state, callbacks, advancedExpanded) {
                    advancedExpanded = it
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.smartVoiceItems(
    state: VoiceAudioSettingsUiState,
    callbacks: VoiceAudioSettingsCallbacks,
    advancedExpanded: Boolean,
    onAdvancedExpandedChange: (Boolean) -> Unit
) {
    item(key = "smart-input-title") {
        SettingsSectionTitle("MIKROFON UND ERKENNUNG")
    }
    item(key = "smart-input") {
        SettingsChoiceGroup {
            smartInputProviders(state.inputProvider).forEachIndexed { index, provider ->
                SettingsChoiceRow(
                    title = provider.displayName,
                    description = inputProviderDescription(provider),
                    selected = state.inputProvider == provider,
                    onClick = { callbacks.onSelectInputProvider(provider) }
                )
                if (index != smartInputProviders(state.inputProvider).lastIndex) HorizontalDivider()
            }
        }
    }
    item(key = "smart-hands-free") {
        SettingsToggleRow(
            title = "Freisprechen",
            description = "Nach einer Antwort automatisch wieder zuhören",
            checked = state.handsFree,
            onCheckedChange = callbacks.onHandsFreeChange
        )
    }
    item(key = "smart-push-to-talk") {
        SettingsToggleRow(
            title = "Zum Sprechen gedrückt halten",
            description = "Loslassen beendet die Spracheingabe",
            checked = state.pushToTalk,
            onCheckedChange = callbacks.onPushToTalkChange
        )
    }
    item(key = "smart-output-title") {
        SettingsSectionTitle("ANTWORTSTIMME")
    }
    item(key = "smart-output") {
        SettingsChoiceGroup {
            smartOutputProviders().forEachIndexed { index, provider ->
                SettingsChoiceRow(
                    title = provider.displayName,
                    description = outputProviderDescription(provider),
                    selected = state.outputProvider == provider,
                    onClick = { callbacks.onSelectOutputProvider(provider) }
                )
                if (index != smartOutputProviders().lastIndex) HorizontalDivider()
            }
        }
    }
    item(key = "smart-auto-playback") {
        SettingsToggleRow(
            title = "Antworten automatisch vorlesen",
            description = "Beginnt mit der Ausgabe, sobald genügend Text vorliegt",
            checked = state.autoPlayback,
            onCheckedChange = callbacks.onAutoPlaybackChange
        )
    }
    item(key = "smart-speed") {
        SettingsSliderRow(
            title = "Geschwindigkeit",
            valueLabel = String.format(Locale.getDefault(), "%.1f×", state.speed),
            value = state.speed,
            onValueChange = callbacks.onSpeedChange,
            valueRange = 0.5f..2.0f,
            steps = 6
        )
    }
    item(key = "smart-pitch") {
        SettingsSliderRow(
            title = "Tonhöhe",
            valueLabel = String.format(Locale.getDefault(), "%.2f", state.pitch),
            value = state.pitch,
            onValueChange = callbacks.onPitchChange,
            valueRange = 0.8f..1.2f,
            steps = 7
        )
    }
    item(key = "smart-preview") {
        VoicePreviewActions(
            previewPlaying = state.previewPlaying,
            onPreview = callbacks.onPreviewVoice,
            onStop = callbacks.onStopPreview
        )
    }
    item(key = "smart-privacy") {
        SettingsInfoCard(
            text = "Spracheingabe kann durch Android verarbeitet werden. Für das Vorlesen gelten die Hinweise des gewählten Stimmenanbieters."
        )
    }
    item(key = "smart-advanced") {
        AdvancedSettingsSection(
            expanded = advancedExpanded,
            onExpandedChange = onAdvancedExpandedChange
        ) {
            SettingsSliderRow(
                title = "Pause bis zum Absenden",
                valueLabel = String.format(Locale.getDefault(), "%.1f s", state.silenceTimeoutMs / 1_000f),
                value = state.silenceTimeoutMs.toFloat(),
                onValueChange = { callbacks.onSilenceTimeoutChange(it.toLong()) },
                valueRange = 700f..5_000f,
                steps = 42
            )
            SettingsToggleRow(
                title = "Bei Sprachfehlern Gerätefunktionen verwenden",
                description = "Wechselt bei einem Sprachdienstfehler auf einen verfügbaren Gerätepfad",
                checked = state.providerFallbackEnabled,
                onCheckedChange = callbacks.onProviderFallbackChange
            )
            SettingsNavigationRow(
                title = "Providerkonten und technische Stimmen",
                description = "API-Schlüssel, Piper-Server und technische Kennungen verwalten",
                icon = Icons.Default.Settings,
                onClick = callbacks.onOpenLegacyVoiceSettings
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.directLiveItems(
    state: VoiceAudioSettingsUiState,
    callbacks: VoiceAudioSettingsCallbacks
) {
    item(key = "live-experimental") {
        SettingsInfoCard(
            text = "Direct Live ist experimentell. Live-Audio wird zur Verarbeitung an OpenAI übertragen und kann zusätzliche Kosten verursachen. Eine Verbindung startet erst nach deiner ausdrücklichen Aktion im Chat.",
            accent = MaterialTheme.colorScheme.tertiary
        )
    }
    item(key = "live-voice-title") {
        SettingsSectionTitle("REALTIME-STIMME")
    }
    item(key = "live-voice") {
        SettingsChoiceGroup {
            RealtimeVoice.entries.forEachIndexed { index, voice ->
                SettingsChoiceRow(
                    title = voice.displayName,
                    description = if (voice == RealtimeVoice.MARIN) "Empfohlene Live-Stimme" else "Alternative Live-Stimme",
                    selected = state.realtimeVoice == voice,
                    onClick = { callbacks.onSelectRealtimeVoice(voice) }
                )
                if (index != RealtimeVoice.entries.lastIndex) HorizontalDivider()
            }
        }
    }
    item(key = "live-flow-title") {
        SettingsSectionTitle("GESPRÄCHSFLUSS")
    }
    item(key = "live-flow") {
        SettingsChoiceGroup {
            RealtimeTurnTaking.entries.forEachIndexed { index, turnTaking ->
                SettingsChoiceRow(
                    title = turnTakingTitle(turnTaking),
                    description = turnTakingDescription(turnTaking),
                    selected = state.realtimeTurnTaking == turnTaking,
                    onClick = { callbacks.onSelectRealtimeTurnTaking(turnTaking) }
                )
                if (index != RealtimeTurnTaking.entries.lastIndex) HorizontalDivider()
            }
        }
    }
    item(key = "live-interruption") {
        SettingsToggleRow(
            title = "Unterbrechung erlauben",
            description = "Neue Spracheingabe stoppt die laufende Antwort",
            checked = state.interruptionEnabled,
            onCheckedChange = callbacks.onInterruptionChange
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.localVoiceItems(
    state: VoiceAudioSettingsUiState,
    callbacks: VoiceAudioSettingsCallbacks,
    advancedExpanded: Boolean,
    onAdvancedExpandedChange: (Boolean) -> Unit
) {
    item(key = "local-privacy") {
        SettingsInfoCard(
            text = "BamaChat sendet im lokalen Modus kein Audio an den BamaVoice-Server. Die Android-Spracherkennung kann abhängig von deinen Geräteeinstellungen einen System-Onlinedienst verwenden.",
            accent = Color(0xFF4DB6AC)
        )
    }
    item(key = "local-input-title") {
        SettingsSectionTitle("MIKROFON UND ERKENNUNG")
    }
    item(key = "local-input") {
        ReadOnlySettingCard(
            title = "Android-Spracherkennung",
            description = "Im lokalen Modus wird immer der verfügbare Gerätepfad verwendet."
        )
    }
    item(key = "local-output-title") {
        SettingsSectionTitle("LOKALE ANTWORTSTIMME")
    }
    item(key = "local-output") {
        SettingsChoiceGroup {
            listOf(VoiceOutputProvider.ANDROID, VoiceOutputProvider.PIPER).forEachIndexed { index, provider ->
                SettingsChoiceRow(
                    title = provider.displayName,
                    description = if (provider == VoiceOutputProvider.PIPER) {
                        if (state.piperConfigured) "Privater Piper-Server ist konfiguriert" else "Benötigt einen privaten Piper-Server in Erweitert"
                    } else {
                        "Verwendet die installierte Android-Stimme"
                    },
                    selected = if (provider == VoiceOutputProvider.PIPER) {
                        state.outputProvider == VoiceOutputProvider.PIPER
                    } else {
                        state.outputProvider != VoiceOutputProvider.PIPER
                    },
                    onClick = { callbacks.onSelectOutputProvider(provider) }
                )
                if (index == 0) HorizontalDivider()
            }
        }
    }
    item(key = "local-auto-playback") {
        SettingsToggleRow(
            title = "Antworten automatisch vorlesen",
            description = "Liest Antworten mit der gewählten lokalen Stimme vor",
            checked = state.autoPlayback,
            onCheckedChange = callbacks.onAutoPlaybackChange
        )
    }
    item(key = "local-hands-free") {
        SettingsToggleRow(
            title = "Freisprechen",
            description = "Nach einer Antwort automatisch wieder zuhören",
            checked = state.handsFree,
            onCheckedChange = callbacks.onHandsFreeChange
        )
    }
    item(key = "local-push-to-talk") {
        SettingsToggleRow(
            title = "Zum Sprechen gedrückt halten",
            description = "Loslassen beendet die lokale Spracheingabe",
            checked = state.pushToTalk,
            onCheckedChange = callbacks.onPushToTalkChange
        )
    }
    item(key = "local-speed") {
        SettingsSliderRow(
            title = "Geschwindigkeit",
            valueLabel = String.format(Locale.getDefault(), "%.1f×", state.speed),
            value = state.speed,
            onValueChange = callbacks.onSpeedChange,
            valueRange = 0.5f..2.0f,
            steps = 6
        )
    }
    item(key = "local-pitch") {
        SettingsSliderRow(
            title = "Tonhöhe",
            valueLabel = String.format(Locale.getDefault(), "%.2f", state.pitch),
            value = state.pitch,
            onValueChange = callbacks.onPitchChange,
            valueRange = 0.8f..1.2f,
            steps = 7
        )
    }
    item(key = "local-preview") {
        VoicePreviewActions(
            previewPlaying = state.previewPlaying,
            onPreview = callbacks.onPreviewVoice,
            onStop = callbacks.onStopPreview
        )
    }
    item(key = "local-advanced") {
        AdvancedSettingsSection(
            expanded = advancedExpanded,
            onExpandedChange = onAdvancedExpandedChange
        ) {
            SettingsSliderRow(
                title = "Pause bis zum Absenden",
                valueLabel = String.format(Locale.getDefault(), "%.1f s", state.silenceTimeoutMs / 1_000f),
                value = state.silenceTimeoutMs.toFloat(),
                onValueChange = { callbacks.onSilenceTimeoutChange(it.toLong()) },
                valueRange = 700f..5_000f,
                steps = 42
            )
            SettingsNavigationRow(
                title = "Piper technisch einrichten",
                description = "Privaten Server und Stimmenname im Legacy-Bereich verwalten",
                icon = Icons.Default.Settings,
                onClick = callbacks.onOpenLegacyVoiceSettings
            )
        }
    }
}

@Composable
private fun SettingsChoiceGroup(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            content()
        }
    }
}

@Composable
private fun ReadOnlySettingCard(title: String, description: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun VoicePreviewActions(
    previewPlaying: Boolean,
    onPreview: () -> Unit,
    onStop: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = onPreview,
            enabled = !previewPlaying,
            modifier = Modifier.weight(1f)
        ) {
            Text("Stimme testen")
        }
        if (previewPlaying) {
            OutlinedButton(
                onClick = onStop,
                modifier = Modifier.weight(1f)
            ) {
                Text("Ausgabe stoppen")
            }
        }
    }
}

private fun smartInputProviders(current: VoiceInputProvider): List<VoiceInputProvider> = buildList {
    add(VoiceInputProvider.AUTOMATIC)
    if (current == VoiceInputProvider.OPENAI_TRANSCRIPTION) add(VoiceInputProvider.OPENAI_TRANSCRIPTION)
    add(VoiceInputProvider.ANDROID)
}

private fun smartOutputProviders(): List<VoiceOutputProvider> = listOf(
    VoiceOutputProvider.AUTOMATIC,
    VoiceOutputProvider.ELEVENLABS,
    VoiceOutputProvider.PIPER,
    VoiceOutputProvider.ANDROID
)

private fun inputProviderDescription(provider: VoiceInputProvider): String = when (provider) {
    VoiceInputProvider.AUTOMATIC -> "BamaChat wählt einen verfügbaren Gerätepfad"
    VoiceInputProvider.OPENAI_TRANSCRIPTION -> "Bestehende Cloud-Auswahl; Einrichtung bleibt im erweiterten Bereich"
    VoiceInputProvider.ANDROID -> "Verwendet die Android-Spracherkennung"
}

private fun outputProviderDescription(provider: VoiceOutputProvider): String = when (provider) {
    VoiceOutputProvider.AUTOMATIC -> "BamaChat wählt eine verfügbare Ausgabe"
    VoiceOutputProvider.ELEVENLABS -> "Cloud-Stimme; Konto wird im erweiterten Bereich verwaltet"
    VoiceOutputProvider.PIPER -> "Eigener Piper-Server oder lokaler Dienst"
    VoiceOutputProvider.ANDROID -> "Installierte Stimme des Geräts"
    VoiceOutputProvider.OPENAI_LIVE -> "Nur für Direct Live"
}

private fun turnTakingTitle(turnTaking: RealtimeTurnTaking): String = when (turnTaking) {
    RealtimeTurnTaking.SEMANTIC -> "Natürlich"
    RealtimeTurnTaking.FAST -> "Schnell"
    RealtimeTurnTaking.PUSH_TO_TALK -> "Zum Sprechen gedrückt halten"
}

private fun turnTakingDescription(turnTaking: RealtimeTurnTaking): String = when (turnTaking) {
    RealtimeTurnTaking.SEMANTIC -> "Erkennt Gesprächspausen möglichst natürlich"
    RealtimeTurnTaking.FAST -> "Reagiert schneller auf kurze Pausen"
    RealtimeTurnTaking.PUSH_TO_TALK -> "Du beendest jede Spracheingabe selbst"
}

private const val VOICE_PREVIEW_MESSAGE_ID = "voice-preview"
private const val VOICE_PREVIEW_TEXT = "Dies ist eine BamaChat-Stimmprobe."
