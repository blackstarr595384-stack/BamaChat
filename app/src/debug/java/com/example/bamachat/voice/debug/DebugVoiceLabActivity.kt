package com.example.bamachat.voice.debug

import android.content.Context
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.bamachat.ui.theme.BamaChatTheme
import com.example.bamachat.ui.theme.NeonCyan
import com.example.bamachat.ui.theme.NeonPurple
import com.example.bamachat.ui.viewmodel.BamaVoiceViewModel
import com.example.bamachat.voice.VoiceMode
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.collect

@AndroidEntryPoint
class DebugVoiceLabActivity : androidx.fragment.app.FragmentActivity() {
    @Inject
    lateinit var scenarioRepository: DebugVoiceScenarioRepository

    private var previousVoiceMode: String? = null
    private var hadStoredVoiceMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preferences = getSharedPreferences(SETTINGS_PREFERENCES, Context.MODE_PRIVATE)
        hadStoredVoiceMode = preferences.contains(KEY_VOICE_MODE)
        previousVoiceMode = preferences.getString(KEY_VOICE_MODE, null)
        preferences.edit().putString(KEY_VOICE_MODE, VoiceMode.LIVE.storageValue).commit()

        enableEdgeToEdge()
        setContent {
            BamaChatTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    DebugVoiceLabScreen(repository = scenarioRepository)
                }
            }
        }
    }

    override fun onDestroy() {
        val editor = getSharedPreferences(SETTINGS_PREFERENCES, Context.MODE_PRIVATE).edit()
        if (hadStoredVoiceMode) {
            editor.putString(KEY_VOICE_MODE, previousVoiceMode)
        } else {
            editor.remove(KEY_VOICE_MODE)
        }
        editor.apply()
        super.onDestroy()
    }

    companion object {
        private const val SETTINGS_PREFERENCES = "settings"
        private const val KEY_VOICE_MODE = "voice_mode"
    }
}

@Composable
private fun DebugVoiceLabScreen(
    repository: DebugVoiceScenarioRepository,
    voiceViewModel: BamaVoiceViewModel = hiltViewModel()
) {
    val labState by repository.state.collectAsStateWithLifecycle()
    val voiceState by voiceViewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    var simulationExpanded by rememberSaveable { mutableStateOf(true) }
    var simulationDiagnosticsExpanded by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(voiceViewModel) {
        voiceViewModel.realtimeTurns.collect(repository::recordFinalTurn)
    }
    LaunchedEffect(voiceState.state) {
        repository.recordControllerState(voiceState.state)
    }
    DisposableEffect(lifecycleOwner, voiceViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) voiceViewModel.endLiveSession()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            voiceViewModel.endLiveSession()
        }
    }

    Scaffold(containerColor = Color.Transparent) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(Color(0xFF080612), Color(0xFF120A24), Color(0xFF05040A))
                    )
                )
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "BamaVoice Testlabor",
                    style = MaterialTheme.typography.headlineMedium,
                    color = NeonCyan,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Vollständig lokale Debug-Simulation ohne Firebase, OpenAI oder Netzwerk.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.75f)
                )
            }

            item {
                SectionToggleCard(
                    title = "1. Ablauf-Simulation",
                    subtitle = "Deterministische Fake-Realtime-Zustände ohne Audio oder Netzwerk",
                    expanded = simulationExpanded,
                    onToggle = { simulationExpanded = !simulationExpanded }
                )
            }

            if (simulationExpanded) item {
                LabCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Fake-Realtime", color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (labState.fakeEnabled) "Aktiv · unbegrenzte lokale Tests" else "Deaktiviert",
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                        Switch(
                            checked = labState.fakeEnabled,
                            onCheckedChange = { enabled ->
                                if (!enabled) voiceViewModel.endLiveSession()
                                repository.setFakeEnabled(enabled)
                            }
                        )
                    }
                }
            }

            if (simulationExpanded) item {
                LabCard {
                    ScenarioSelector(
                        selected = labState.selectedScenario,
                        onSelected = repository::selectScenario
                    )
                    Spacer(Modifier.height(12.dp))
                    DelaySelector(
                        selected = labState.selectedDelay,
                        onSelected = repository::selectDelay
                    )
                }
            }

            if (simulationExpanded) item {
                LabCard {
                    Text(
                        text = "Aktueller Status",
                        color = NeonPurple,
                        fontWeight = FontWeight.Bold
                    )
                    Text(voiceState.connectionLabel, color = Color.White)
                    Text(
                        voiceState.realtimeTransportStatusLabel,
                        color = Color.White.copy(alpha = 0.75f)
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = labState.fakeEnabled,
                        onClick = {
                            repository.prepareScenarioStart()
                            voiceViewModel.recoverFromError()
                            voiceViewModel.startLiveSession(TEST_PERSONA)
                        }
                    ) {
                        Text("Szenario starten")
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = voiceViewModel::stopSpeaking
                        ) {
                            Text("Unterbrechen")
                        }
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = repository::triggerNetworkFailure
                        ) {
                            Text("Netzwerkfehler")
                        }
                    }
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = voiceViewModel::endLiveSession
                    ) {
                        Text("Sitzung beenden")
                    }
                }
            }

            if (simulationExpanded) item {
                SectionToggleCard(
                    title = "Simulationsdiagnose",
                    subtitle = "Zähler und sichere Statushistorie",
                    expanded = simulationDiagnosticsExpanded,
                    onToggle = { simulationDiagnosticsExpanded = !simulationDiagnosticsExpanded }
                )
            }

            if (simulationExpanded) item {
                FinalMessagesCard(
                    title = "Finale Nutzernachrichten",
                    messages = labState.finalUserMessages
                )
            }
            if (simulationExpanded) item {
                FinalMessagesCard(
                    title = "Finale Assistentennachrichten",
                    messages = labState.finalAssistantMessages
                )
            }

            if (simulationExpanded && simulationDiagnosticsExpanded) item {
                LabCard {
                    Text("Diagnose", color = NeonPurple, fontWeight = FontWeight.Bold)
                    Text("Starts: ${labState.startCount}", color = Color.White)
                    Text("Events: ${labState.eventCount}", color = Color.White)
                    Text("Cleanup: ${labState.cleanupCount}", color = Color.White)
                    Text("Fake-Release: ${labState.releaseCount}", color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    Text("Statushistorie", color = NeonPurple, fontWeight = FontWeight.Bold)
                    if (labState.statusHistory.isEmpty()) {
                        Text("Keine Einträge", color = Color.White.copy(alpha = 0.65f))
                    } else {
                        labState.statusHistory.asReversed().forEach { status ->
                            Text("• $status", color = Color.White.copy(alpha = 0.82f))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            voiceViewModel.endLiveSession()
                            repository.resetStatus()
                        }
                    ) {
                        Text("Status zurücksetzen")
                    }
                }
            }
            item {
                DebugAudioHardwareLab()
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun ScenarioSelector(
    selected: FakeRealtimeScenario,
    onSelected: (FakeRealtimeScenario) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Text("Szenario", color = NeonPurple, fontWeight = FontWeight.Bold)
    OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { expanded = true }) {
        Text(selected.displayName)
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        FakeRealtimeScenario.entries.forEach { scenario ->
            DropdownMenuItem(
                text = { Text(scenario.displayName) },
                onClick = {
                    expanded = false
                    onSelected(scenario)
                }
            )
        }
    }
}

@Composable
private fun DelaySelector(
    selected: FakeRealtimeDelay,
    onSelected: (FakeRealtimeDelay) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Text("Verzögerung", color = NeonPurple, fontWeight = FontWeight.Bold)
    OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { expanded = true }) {
        Text(selected.displayName)
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        FakeRealtimeDelay.entries.forEach { delay ->
            DropdownMenuItem(
                text = { Text(delay.displayName) },
                onClick = {
                    expanded = false
                    onSelected(delay)
                }
            )
        }
    }
}

@Composable
private fun FinalMessagesCard(title: String, messages: List<DebugVoiceFinalMessage>) {
    LabCard {
        Text(title, color = NeonPurple, fontWeight = FontWeight.Bold)
        if (messages.isEmpty()) {
            Text("Noch keine finale Nachricht", color = Color.White.copy(alpha = 0.65f))
        } else {
            messages.forEach { message ->
                Text("• ${message.text}", color = Color.White.copy(alpha = 0.82f))
            }
        }
    }
}

@Composable
internal fun LabCard(content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            content = content
        )
    }
}

private const val TEST_PERSONA = "BamaVoice Testpersona"
