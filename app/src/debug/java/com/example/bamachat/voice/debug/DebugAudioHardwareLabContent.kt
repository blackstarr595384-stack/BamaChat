package com.example.bamachat.voice.debug

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bamachat.ui.theme.NeonCyan
import com.example.bamachat.ui.theme.NeonPurple

@Composable
internal fun DebugAudioHardwareLab(
    viewModel: DebugAudioHardwareViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    var diagnosticsExpanded by rememberSaveable { mutableStateOf(false) }
    var pendingPermissionAction by remember { mutableStateOf<AudioPermissionAction?>(null) }

    fun execute(action: AudioPermissionAction) {
        when (action) {
            AudioPermissionAction.MICROPHONE_TEST -> viewModel.startMicrophone(permissionGranted = true)
            AudioPermissionAction.LOCAL_CONVERSATION -> viewModel.startLocalConversation(permissionGranted = true)
            AudioPermissionAction.INTERRUPT_AND_LISTEN -> viewModel.interruptAndListen(permissionGranted = true)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val action = pendingPermissionAction
        pendingPermissionAction = null
        if (granted && action != null) {
            execute(action)
        } else {
            viewModel.reportPermissionDenied()
        }
    }

    fun runWithMicrophonePermission(action: AudioPermissionAction) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            execute(action)
        } else {
            pendingPermissionAction = action
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.stopForLifecycle()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopForLifecycle()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionToggleCard(
            title = "2. Audio-Hardwaretest",
            subtitle = "Echtes Mikrofon, Android-Spracherkennung und lokale Geräteausgabe",
            expanded = expanded,
            onToggle = { expanded = !expanded }
        )
        if (expanded) {

    LabCard {
        Text(
            text = "Lokaler Audio-Hardwaretest",
            color = NeonCyan,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Dieser Test verwendet keine OpenAI-Realtime-Sitzung und keinen BamaVoice-Server. " +
                "Die Android-Spracherkennung kann abhängig von den Geräteeinstellungen einen " +
                "System-Onlinedienst verwenden.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.78f)
        )
    }

    LabCard {
        Text("Mikrofon und Spracherkennung", color = NeonPurple, fontWeight = FontWeight.Bold)
        SafeStatusRow("Mikrofonstatus", state.microphoneStatus.displayName)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                modifier = Modifier.weight(1f),
                onClick = { runWithMicrophonePermission(AudioPermissionAction.MICROPHONE_TEST) }
            ) {
                Text("Mikrofon testen")
            }
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = viewModel::stopMicrophone
            ) {
                Text("Stoppen")
            }
        }
        TranscriptField("Erkanntes Teiltranskript", state.partialTranscript)
        TranscriptField("Erkanntes finales Transkript", state.finalTranscript)
    }

    LabCard {
        Text("Lokale Sprachausgabe", color = NeonPurple, fontWeight = FontWeight.Bold)
        SafeStatusRow("Ausgabestatus", state.outputStatus.displayName)
        Text("Geschwindigkeit: ${formatSetting(state.speechSpeed)}×", color = Color.White)
        Slider(
            value = state.speechSpeed,
            onValueChange = viewModel::setSpeechSpeed,
            valueRange = 0.5f..2f
        )
        Text("Tonhöhe: ${formatSetting(state.speechPitch)}×", color = Color.White)
        Slider(
            value = state.speechPitch,
            onValueChange = viewModel::setSpeechPitch,
            valueRange = 0.8f..1.2f
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                modifier = Modifier.weight(1f),
                onClick = viewModel::startSpeechTest
            ) {
                Text("Sprachausgabe testen")
            }
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = viewModel::stopSpeechOutput
            ) {
                Text("Ausgabe stoppen")
            }
        }
    }

    LabCard {
        Text("Lokaler Ende-zu-Ende-Test", color = NeonPurple, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text("Freisprechen", color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(
                    "Nach erfolgreicher Ausgabe einmal erneut zuhören",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
            Switch(checked = state.handsFree, onCheckedChange = viewModel::setHandsFree)
        }
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = { runWithMicrophonePermission(AudioPermissionAction.LOCAL_CONVERSATION) }
        ) {
            Text("Lokales Gespräch starten")
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = { runWithMicrophonePermission(AudioPermissionAction.INTERRUPT_AND_LISTEN) }
            ) {
                Text("Unterbrechen")
            }
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = viewModel::endConversation
            ) {
                Text("Gespräch beenden")
            }
        }
        SafeStatusRow("Gespräch", if (state.conversationActive) "Aktiv" else "Inaktiv")
        TranscriptField("Lokale Testantwort", state.localResponse)
    }

    if (state.errorMessage != null) {
        LabCard {
            Text("Letzte sichere Fehlerkategorie", color = NeonPurple, fontWeight = FontWeight.Bold)
            Text(state.lastErrorCategory.displayName, color = Color.White)
            Text(state.errorMessage.orEmpty(), color = MaterialTheme.colorScheme.error)
            if (state.lastErrorCategory == LocalAudioErrorCategory.PERMISSION_MISSING) {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${context.packageName}")
                            )
                        )
                    }
                ) {
                    Text("App-Einstellungen öffnen")
                }
            }
        }
    }

    SectionToggleCard(
        title = "Detaillierte Diagnose",
        subtitle = "Nur Status und Fehlerkategorien, niemals Transkripttext",
        expanded = diagnosticsExpanded,
        onToggle = { diagnosticsExpanded = !diagnosticsExpanded }
    )
    if (diagnosticsExpanded) {
        LabCard {
            if (state.diagnostics.isEmpty()) {
                Text("Keine Diagnoseeinträge", color = Color.White.copy(alpha = 0.65f))
            } else {
                state.diagnostics.asReversed().forEach { entry ->
                    Text("• $entry", color = Color.White.copy(alpha = 0.82f))
                }
            }
            Spacer(Modifier.height(6.dp))
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = viewModel::clearDiagnostics
            ) {
                Text("Diagnose löschen")
            }
        }
    }
        }
    }
}

@Composable
internal fun SectionToggleCard(
    title: String,
    subtitle: String,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            onClick = onToggle
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start
            ) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
            Text(if (expanded) "−" else "+", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun SafeStatusRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.White.copy(alpha = 0.7f))
        Text(value, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TranscriptField(label: String, value: String) {
    Text(label, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
    Text(
        text = value.ifBlank { "Noch kein Ergebnis" },
        color = Color.White,
        style = MaterialTheme.typography.bodyMedium
    )
}

private fun formatSetting(value: Float): String = String.format(java.util.Locale.GERMAN, "%.1f", value)

private enum class AudioPermissionAction {
    MICROPHONE_TEST,
    LOCAL_CONVERSATION,
    INTERRUPT_AND_LISTEN
}
