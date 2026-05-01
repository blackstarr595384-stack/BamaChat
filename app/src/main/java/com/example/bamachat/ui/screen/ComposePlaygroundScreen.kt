package com.example.bamachat.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bamachat.ui.viewmodel.ComposePlaygroundViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposePlaygroundScreen(
    onBack: () -> Unit,
    onOpenArgumentDemo: (Int) -> Unit,
    playgroundViewModel: ComposePlaygroundViewModel = viewModel()
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var showSheet by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }

    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }

    var rememberCounter by remember { mutableStateOf(0) }
    var saveableCounter by rememberSaveable { mutableStateOf(0) }
    var effectKey by rememberSaveable { mutableStateOf("A") }
    val lifecycleLog = remember { mutableStateListOf<String>() }

    val nameError = name.isNotBlank() && name.length < 3
    val emailError = email.isNotBlank() && !(email.contains("@") && email.contains("."))
    val promptError = prompt.isNotBlank() && prompt.length < 20
    val canSubmit = name.length >= 3 && !emailError && email.isNotBlank() && prompt.length >= 20
    val completionPercent by remember(name, email, prompt) {
        derivedStateOf {
            var score = 0
            if (name.length >= 3) score += 34
            if (!emailError && email.isNotBlank()) score += 33
            if (prompt.length >= 20) score += 33
            score.coerceIn(0, 100)
        }
    }
    val flowUiState by playgroundViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(playgroundViewModel) {
        playgroundViewModel.events.collect { event ->
            when (event) {
                is ComposePlaygroundViewModel.PlaygroundEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                is ComposePlaygroundViewModel.PlaygroundEvent.NavigateToArgumentDemo -> {
                    snackbarHostState.showSnackbar("Navigation-Event: Demo ${event.demoId}")
                    onOpenArgumentDemo(event.demoId)
                }
            }
        }
    }

    val appendLifecycleEvent: (String) -> Unit = { message ->
        lifecycleLog.add(0, message)
        if (lifecycleLog.size > 8) lifecycleLog.removeAt(lifecycleLog.lastIndex)
    }

    LaunchedEffect(effectKey) {
        appendLifecycleEvent("LaunchedEffect -> key=$effectKey")
    }
    DisposableEffect(effectKey) {
        appendLifecycleEvent("DisposableEffect start -> key=$effectKey")
        onDispose {
            appendLifecycleEvent("DisposableEffect dispose -> key=$effectKey")
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Dialog Demo") },
            text = { Text("Das ist ein Compose AlertDialog. Perfekt für kritische Bestätigungen.") },
            confirmButton = { TextButton(onClick = { showDialog = false }) { Text("OK") } }
        )
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("BottomSheet Demo", fontWeight = FontWeight.SemiBold)
                Text("Ideal für schnelle Aktionen, Filtersets oder Upload-Optionen.", fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = {}, label = { Text("Kamera") })
                    AssistChip(onClick = {}, label = { Text("Foto") })
                    AssistChip(onClick = {}, label = { Text("Datei") })
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Compose Playground", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("UI Interactions", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { showSheet = true }) {
                            Icon(Icons.Default.Tune, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("BottomSheet")
                        }
                        Button(onClick = { showDialog = true }) { Text("Dialog") }
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("SharedFlow One-shot Events", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Events aus dem ViewModel für Snackbar/Navigation ohne doppeltes Replaying.",
                        fontSize = 12.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(
                            onClick = { playgroundViewModel.emitInfoEvent() },
                            label = { Text("Info Event") }
                        )
                        AssistChip(
                            onClick = { playgroundViewModel.emitWarningEvent() },
                            label = { Text("Warn Event") }
                        )
                        AssistChip(
                            onClick = { playgroundViewModel.emitNavigateEvent(2) },
                            label = { Text("Nav Event") }
                        )
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Flow + collectAsStateWithLifecycle", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Diese Werte kommen aus einem ViewModel-StateFlow und werden lifecycle-aware gesammelt.",
                        fontSize = 12.sp
                    )
                    Text("Uptime: ${flowUiState.uptimeSeconds}s", fontSize = 12.sp)
                    Text("Status: ${flowUiState.statusText}", fontSize = 12.sp)
                    Text("Queue: ${flowUiState.queuedTasks}", fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(
                            onClick = { playgroundViewModel.toggleOnline() },
                            label = { Text("Online toggeln") }
                        )
                        AssistChip(
                            onClick = { playgroundViewModel.addTask() },
                            label = { Text("Task +1") }
                        )
                        AssistChip(
                            onClick = { playgroundViewModel.clearTasks() },
                            label = { Text("Queue löschen") }
                        )
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("remember vs rememberSaveable", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Teste z. B. Screen-Rotation: remember wird oft zurückgesetzt, rememberSaveable bleibt erhalten.",
                        fontSize = 12.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { rememberCounter++ }) { Text("remember +1") }
                        Text("Wert: $rememberCounter", modifier = Modifier.align(Alignment.CenterVertically))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { saveableCounter++ }) { Text("saveable +1") }
                        Text("Wert: $saveableCounter", modifier = Modifier.align(Alignment.CenterVertically))
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Form Validation", fontWeight = FontWeight.SemiBold)

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it; submitted = false },
                        label = { Text("Name") },
                        isError = nameError,
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = if (nameError) ({ Text("Mindestens 3 Zeichen.") }) else null
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it; submitted = false },
                        label = { Text("E-Mail") },
                        isError = emailError,
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = if (emailError) ({ Text("Bitte gültige E-Mail eingeben.") }) else null
                    )
                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it; submitted = false },
                        label = { Text("Prompt") },
                        isError = promptError,
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4,
                        supportingText = if (promptError) ({ Text("Mindestens 20 Zeichen.") }) else null
                    )

                    Button(
                        onClick = { submitted = true },
                        enabled = canSubmit
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Validieren")
                    }

                    if (submitted && canSubmit) {
                        Text(
                            "Formular ist gültig. Genau dieses Muster nutzt du für robuste Eingabe-Workflows.",
                            fontSize = 12.sp,
                            color = Color(0xFF0E9F6E)
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("derivedStateOf (abgeleiteter Zustand): $completionPercent%", fontSize = 12.sp)
                        LinearProgressIndicator(
                            progress = { completionPercent / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Navigation Arguments", fontWeight = FontWeight.SemiBold)
                    Text("Öffne eine Zielseite mit `demoId` als Route-Argument.", fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (1..3).forEach { id ->
                            AssistChip(
                                onClick = { onOpenArgumentDemo(id) },
                                label = { Text("Demo $id") },
                                leadingIcon = { Icon(Icons.Default.Code, contentDescription = null) }
                            )
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Lifecycle Effects", fontWeight = FontWeight.SemiBold)
                    Text("Key wechseln, um `LaunchedEffect`/`DisposableEffect` live zu sehen.", fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(
                            onClick = { effectKey = "A" },
                            label = { Text("Key A") }
                        )
                        AssistChip(
                            onClick = { effectKey = "B" },
                            label = { Text("Key B") }
                        )
                    }
                    lifecycleLog.forEach { line ->
                        Text("• $line", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeArgumentDemoScreen(
    demoId: Int,
    onBack: () -> Unit
) {
    val title = when (demoId) {
        1 -> "Demo 1: API-Strategie"
        2 -> "Demo 2: UI-Architektur"
        3 -> "Demo 3: Performance"
        else -> "Demo: Unbekannt"
    }
    val description = when (demoId) {
        1 -> "Hier könntest du je ID andere API-Parameter, Provider oder Modelle laden."
        2 -> "Hier könntest du andere Compose-Layouts oder Agent-Presets auswählen."
        3 -> "Hier könntest du Performance-Metriken oder Profiling-Hinweise anzeigen."
        else -> "Das Route-Argument wurde gelesen, aber ist nicht in der Demo-Mapping-Liste."
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Argument Demo", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(title, fontWeight = FontWeight.SemiBold)
                    Text("Route-Argument `demoId` = $demoId", fontSize = 12.sp)
                    Text(description, fontSize = 12.sp)
                }
            }
        }
    }
}
