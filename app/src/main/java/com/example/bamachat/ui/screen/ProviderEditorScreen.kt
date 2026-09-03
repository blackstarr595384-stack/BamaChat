package com.example.bamachat.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.password
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bamachat.data.provider.ProviderAuthenticationType
import com.example.bamachat.data.provider.ProviderConnectionType
import com.example.bamachat.data.provider.ProviderModelDefinition
import com.example.bamachat.ui.component.settings.AdvancedSettingsSection
import com.example.bamachat.ui.component.settings.SettingsChoiceRow
import com.example.bamachat.ui.component.settings.SettingsInfoCard
import com.example.bamachat.ui.component.settings.SettingsSectionTitle
import com.example.bamachat.ui.component.settings.SettingsToggleRow
import com.example.bamachat.ui.component.settings.SettingsTopBar
import com.example.bamachat.ui.component.settings.settingsScreenContentPadding
import com.example.bamachat.ui.provider.displayName
import com.example.bamachat.ui.viewmodel.ProviderEditorEffect
import com.example.bamachat.ui.viewmodel.ProviderDiscoveryUiStatus
import com.example.bamachat.ui.viewmodel.ProviderEditorUiState
import com.example.bamachat.ui.viewmodel.ProviderEditorViewModel

@Composable
fun ProviderEditorScreen(
    onBack: () -> Unit,
    viewModel: ProviderEditorViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var apiKey by remember { mutableStateOf("") }
    var keyVisible by remember { mutableStateOf(false) }
    var advancedExpanded by remember { mutableStateOf(false) }
    var confirmLocalHttp by remember { mutableStateOf(false) }
    var modelId by remember { mutableStateOf("") }
    var modelName by remember { mutableStateOf("") }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                ProviderEditorEffect.Saved -> { apiKey = ""; onBack() }
                is ProviderEditorEffect.Message -> snackbar.showSnackbar(effect.text)
                ProviderEditorEffect.ConfirmLocalHttp -> confirmLocalHttp = true
            }
        }
    }

    DisposableEffect(viewModel) {
        onDispose { viewModel.cancelDiscovery() }
    }

    if (confirmLocalHttp) {
        AlertDialog(
            modifier = Modifier.testTag("provider_local_http_confirm"),
            onDismissRequest = { confirmLocalHttp = false; viewModel.cancelLocalHttpConfirmation() },
            title = { Text("Lokales HTTP bestätigen?") },
            text = { Text("Diese unverschlüsselte Verbindung ist nur für ein bewusst gewähltes Ziel im lokalen Netzwerk vorgesehen.") },
            confirmButton = {
                TextButton(onClick = { confirmLocalHttp = false; viewModel.confirmLocalHttp() }) { Text("Lokal zulassen") }
            },
            dismissButton = {
                TextButton(onClick = { confirmLocalHttp = false; viewModel.cancelLocalHttpConfirmation() }) { Text("Abbrechen") }
            }
        )
    }


    if (state.discoveryModels.isNotEmpty()) {
        ProviderModelImportDialog(
            state = state,
            onDismiss = viewModel::dismissDiscoveredModels,
            onSelectAll = viewModel::selectAllDiscoveredModels,
            onClearSelection = viewModel::clearDiscoveredModelSelection,
            onToggleModel = viewModel::toggleDiscoveredModel,
            onImport = viewModel::importSelectedModels
        )
    }

    Scaffold(
        topBar = { SettingsTopBar(title = if (state.existing) "Anbieter bearbeiten" else "Anbieter hinzufügen", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).imePadding(),
            contentPadding = settingsScreenContentPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.loading) {
                item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() } }
            } else {
                item { SettingsSectionTitle("VERBINDUNG") }
                if (state.builtIn) {
                    item { SettingsInfoCard("Integrierter Anbieter. Zugangsdaten werden weiterhin in den bisherigen KI-Einstellungen verwaltet.") }
                }
                item {
                    OutlinedTextField(
                        value = state.displayName,
                        onValueChange = { value -> viewModel.update { it.copy(displayName = value) } },
                        label = { Text("Anzeigename") },
                        singleLine = true,
                        enabled = !state.builtIn,
                        modifier = Modifier.fillMaxWidth().testTag("provider_name")
                    )
                }
                item {
                    Text("Verbindungstyp", style = MaterialTheme.typography.titleSmall)
                    ProviderConnectionType.entries.forEach { type ->
                        SettingsChoiceRow(
                            title = type.displayName(),
                            description = if (type == ProviderConnectionType.OPENAI_COMPATIBLE) "Standardisierte Chat-API" else "Lokaler Ollama-Dienst",
                            selected = state.connectionType == type,
                            enabled = !state.builtIn,
                            onClick = { viewModel.update { it.copy(connectionType = type) } }
                        )
                    }
                }
                item {
                    OutlinedTextField(
                        value = state.baseUrl,
                        onValueChange = { value -> viewModel.update { it.copy(baseUrl = value, localHttpConfirmed = false) } },
                        label = { Text("Basis-URL") },
                        supportingText = { Text("Öffentliche Ziele benötigen HTTPS.") },
                        enabled = !state.builtIn,
                        modifier = Modifier.fillMaxWidth().testTag("provider_base_url")
                    )
                }
                item {
                    Text("Authentifizierung", style = MaterialTheme.typography.titleSmall)
                    ProviderAuthenticationType.entries.forEach { type ->
                        SettingsChoiceRow(
                            title = type.displayName(),
                            description = if (type == ProviderAuthenticationType.BEARER) "API-Key als Bearer-Token" else "Nur für bestätigte lokale Ziele",
                            selected = state.authenticationType == type,
                            enabled = !state.builtIn,
                            onClick = { viewModel.update { it.copy(authenticationType = type) } }
                        )
                    }
                }
                if (!state.builtIn && state.authenticationType == ProviderAuthenticationType.BEARER) {
                    item {
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { value -> apiKey = value; if (state.removeStoredSecret) viewModel.update { it.copy(removeStoredSecret = false) } },
                            label = { Text(if (state.hasSecret) "API-Key ersetzen" else "API-Key") },
                            supportingText = { Text(if (state.hasSecret && !state.removeStoredSecret) "API-Key gespeichert. Ein leeres Feld behält ihn." else "Der Schlüssel wird verschlüsselt gespeichert.") },
                            visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { keyVisible = !keyVisible }) {
                                    Icon(if (keyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = if (keyVisible) "API-Key verbergen" else "API-Key anzeigen")
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("provider_api_key").semantics {
                                contentDescription = "API-Key sicher eingeben"
                                password()
                            }
                        )
                    }
                    if (state.hasSecret) {
                        item {
                            TextButton(onClick = { apiKey = ""; viewModel.update { it.copy(removeStoredSecret = !it.removeStoredSecret) } }) {
                                Text(if (state.removeStoredSecret) "Gespeicherten API-Key behalten" else "Gespeicherten API-Key entfernen")
                            }
                        }
                    }
                }
                item {
                    SettingsToggleRow("Anbieter aktiv", "Deaktivierte Anbieter bleiben gespeichert.", state.enabled, { enabled -> viewModel.update { it.copy(enabled = enabled) } })
                }
                item { SettingsSectionTitle("MANUELLE MODELLE") }
                if (!state.builtIn) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = modelId, onValueChange = { modelId = it }, label = { Text("Modell-ID") }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(value = modelName, onValueChange = { modelName = it }, label = { Text("Anzeigename (optional)") }, modifier = Modifier.fillMaxWidth())
                            Button(
                                onClick = { if (viewModel.addModel(modelId, modelName)) { modelId = ""; modelName = "" } },
                                modifier = Modifier.fillMaxWidth().testTag("add_manual_model")
                            ) { Icon(Icons.Default.Add, null); Text("  Modell hinzufügen") }
                        }
                    }
                }
                if (state.models.isEmpty()) {
                    item { SettingsInfoCard("Noch keine Modelle hinterlegt. Ein Modell kann manuell hinzugefügt werden.") }
                }
                items(state.models, key = { it.modelId }) { model ->
                    ProviderModelRow(
                        model = model,
                        selected = state.defaultModelId == model.modelId,
                        readOnly = state.builtIn,
                        onSelect = { viewModel.update { it.copy(defaultModelId = model.modelId) } },
                        onEnabledChange = { enabled ->
                            viewModel.update { current ->
                                current.copy(
                                    models = current.models.map { if (it.modelId == model.modelId) it.copy(enabled = enabled) else it },
                                    defaultModelId = current.defaultModelId.takeUnless { it == model.modelId && !enabled }
                                )
                            }
                        },
                        onDelete = { viewModel.removeModel(model.modelId) }
                    )
                }
                if (!state.builtIn) {
                    item {
                        ProviderDiscoverySection(
                            state = state,
                            onTestConnection = viewModel::testConnection,
                            onFetchModels = viewModel::fetchModels,
                            onCancel = viewModel::cancelDiscovery
                        )
                    }
                }
                item {
                    AdvancedSettingsSection(expanded = advancedExpanded, onExpandedChange = { advancedExpanded = it }) {
                        OutlinedTextField(
                            value = state.timeoutSeconds,
                            onValueChange = { value -> viewModel.update { it.copy(timeoutSeconds = value.filter(Char::isDigit)) } },
                            label = { Text("Zeitlimit in Sekunden") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        SettingsToggleRow("Streaming", "Antworten schrittweise empfangen", state.streaming, { viewModel.update { s -> s.copy(streaming = it) } }, enabled = !state.builtIn)
                        SettingsToggleRow("Modellabruf unterstützt", "Technische Fähigkeit, noch ohne Abruf", state.modelDiscovery, { viewModel.update { s -> s.copy(modelDiscovery = it) } }, enabled = !state.builtIn)
                        SettingsToggleRow("Tools unterstützt", "Werkzeugaufrufe sind laut Anbieter möglich", state.tools, { viewModel.update { s -> s.copy(tools = it) } }, enabled = !state.builtIn)
                        SettingsToggleRow("Bilder unterstützt", "Bildinhalte sind laut Anbieter möglich", state.vision, { viewModel.update { s -> s.copy(vision = it) } }, enabled = !state.builtIn)
                    }
                }
                state.errorMessage?.let { message -> item { SettingsInfoCard(message, accent = MaterialTheme.colorScheme.error) } }
                item {
                    Button(
                        onClick = { viewModel.save(apiKey) },
                        enabled = !state.saving,
                        modifier = Modifier.fillMaxWidth().testTag("provider_save")
                    ) { Text(if (state.saving) "Wird gespeichert …" else "Speichern") }
                }
            }
        }
    }
}

@Composable
internal fun ProviderModelImportDialog(
    state: ProviderEditorUiState,
    onDismiss: () -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onToggleModel: (String) -> Unit,
    onImport: () -> Unit
) {
    AlertDialog(
        modifier = Modifier.testTag("provider_model_import_dialog"),
        onDismissRequest = onDismiss,
        title = { Text("Gefundene Modelle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${state.discoveryModels.size} Modelle gefunden.")
                Text(
                    "${state.selectedDiscoveredModelIds.size} von ${state.discoveryModels.size} ausgewählt",
                    modifier = Modifier.testTag("provider_model_selected_count"),
                    style = MaterialTheme.typography.labelLarge
                )
                if (state.discoveryTruncated) {
                    Text(
                        "Die Liste wurde aus Sicherheitsgründen auf 500 Modelle begrenzt.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(
                        onClick = onSelectAll,
                        modifier = Modifier.testTag("provider_models_select_all")
                    ) { Text("Alle auswählen") }
                    TextButton(
                        onClick = onClearSelection,
                        modifier = Modifier.testTag("provider_models_clear_selection")
                    ) { Text("Auswahl aufheben") }
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .testTag("provider_model_import_list")
                ) {
                    items(state.discoveryModels, key = { it.modelId }) { discovered ->
                        val existing = state.models.any { it.modelId == discovered.modelId }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = discovered.modelId in state.selectedDiscoveredModelIds,
                                onCheckedChange = { onToggleModel(discovered.modelId) },
                                enabled = !existing
                            )
                            Column(Modifier.weight(1f)) {
                                Text(discovered.modelId, style = MaterialTheme.typography.bodyMedium)
                                if (existing) {
                                    Text(
                                        "Bereits vorhanden",
                                        modifier = Modifier.testTag("provider_model_already_exists"),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onImport,
                enabled = state.selectedDiscoveredModelIds.isNotEmpty() && !state.importingModels,
                modifier = Modifier.testTag("provider_models_import")
            ) { Text(if (state.importingModels) "Wird importiert …" else "Ausgewählte importieren") }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("provider_models_cancel_import")
            ) { Text("Abbrechen") }
        }
    )
}

@Composable
internal fun ProviderDiscoverySection(
    state: ProviderEditorUiState,
    onTestConnection: () -> Unit,
    onFetchModels: () -> Unit,
    onCancel: () -> Unit
) {
    val running = state.discoveryStatus == ProviderDiscoveryUiStatus.TESTING ||
        state.discoveryStatus == ProviderDiscoveryUiStatus.FETCHING_MODELS
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("provider_discovery_section"),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Verbindung und Modelle", style = MaterialTheme.typography.titleMedium)
            Text(
                "Prüfe die Verbindung oder lade verfügbare Modelle manuell vom Anbieter.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!state.existing) {
                SettingsInfoCard("Speichere den Anbieter zuerst.", accent = MaterialTheme.colorScheme.secondary)
            }
            Button(
                onClick = onTestConnection,
                enabled = state.existing && state.enabled && !running,
                modifier = Modifier.fillMaxWidth().testTag("provider_test_connection")
            ) { Text("Verbindung testen") }
            Button(
                onClick = onFetchModels,
                enabled = state.existing && state.enabled && !running,
                modifier = Modifier.fillMaxWidth().testTag("provider_fetch_models")
            ) { Text("Modelle abrufen") }
            if (running) {
                Row(
                    modifier = Modifier.testTag("provider_discovery_progress"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator()
                    Text(
                        if (state.discoveryStatus == ProviderDiscoveryUiStatus.FETCHING_MODELS) {
                            "Modelle werden abgerufen …"
                        } else {
                            "Verbindung wird geprüft …"
                        },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = onCancel,
                        modifier = Modifier.testTag("provider_cancel_discovery")
                    ) { Text("Abbrechen") }
                }
            } else {
                state.discoveryMessage?.let { message ->
                    SettingsInfoCard(
                        message,
                        modifier = Modifier.testTag("provider_discovery_status"),
                        accent = if (state.discoveryStatus == ProviderDiscoveryUiStatus.ERROR) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.secondary
                        }
                    )
                }
            }
            Text(
                "Geprüft wird die zuletzt gespeicherte Konfiguration am Modell-Endpunkt. Es wird keine Chatnachricht gesendet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ProviderModelRow(
    model: ProviderModelDefinition,
    selected: Boolean,
    readOnly: Boolean,
    onSelect: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Column(Modifier.fillMaxWidth().testTag("provider_model_row"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = onSelect, enabled = model.enabled)
            Column(Modifier.weight(1f)) {
                Text(model.displayName, style = MaterialTheme.typography.titleSmall)
                Text(model.modelId, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!readOnly) {
                Switch(checked = model.enabled, onCheckedChange = onEnabledChange)
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Modell löschen") }
            } else {
                Text("Integriert", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
