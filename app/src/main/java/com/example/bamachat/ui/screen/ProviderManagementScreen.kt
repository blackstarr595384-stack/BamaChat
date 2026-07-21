package com.example.bamachat.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bamachat.data.provider.ProviderDefinition
import com.example.bamachat.data.provider.ProviderId
import com.example.bamachat.ui.component.settings.SettingsInfoCard
import com.example.bamachat.ui.component.settings.SettingsSectionTitle
import com.example.bamachat.ui.component.settings.SettingsTopBar
import com.example.bamachat.ui.component.settings.settingsScreenContentPadding
import com.example.bamachat.ui.provider.displayName
import com.example.bamachat.ui.provider.secretSummary
import com.example.bamachat.ui.viewmodel.ProviderListItemUi
import com.example.bamachat.ui.viewmodel.ProviderManagementEffect
import com.example.bamachat.ui.viewmodel.ProviderManagementViewModel

@Composable
fun ProviderManagementScreen(
    onBack: () -> Unit,
    onAddProvider: () -> Unit,
    onOpenProvider: (ProviderId) -> Unit,
    viewModel: ProviderManagementViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var deleteCandidate by remember { mutableStateOf<ProviderDefinition?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ProviderManagementEffect.Message -> snackbar.showSnackbar(effect.text)
                is ProviderManagementEffect.OpenProvider -> onOpenProvider(effect.providerId)
            }
        }
    }

    deleteCandidate?.let { provider ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Anbieter löschen?") },
            text = { Text("${provider.displayName} sowie zugehörige Modelle und der gespeicherte Schlüssel werden entfernt.") },
            confirmButton = {
                TextButton(onClick = { deleteCandidate = null; viewModel.delete(provider.id) }) { Text("Löschen") }
            },
            dismissButton = { TextButton(onClick = { deleteCandidate = null }) { Text("Abbrechen") } }
        )
    }

    Scaffold(
        modifier = Modifier.testTag("provider_management_screen"),
        topBar = { SettingsTopBar(title = "Anbieter verwalten", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = settingsScreenContentPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SettingsInfoCard("Die produktive Chat-Auswahl verwendet vorerst weiterhin die bisherigen KI-Einstellungen.")
            }
            item {
                Button(
                    onClick = onAddProvider,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("add_provider")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("  Anbieter hinzufügen")
                }
            }
            if (state.loading) {
                item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() } }
            }
            state.errorMessage?.let { message -> item { SettingsInfoCard(message, accent = MaterialTheme.colorScheme.error) } }
            val builtIns = state.providers.filter { it.definition.builtIn }
            val custom = state.providers.filterNot { it.definition.builtIn }
            if (builtIns.isNotEmpty()) item { SettingsSectionTitle("INTEGRIERTE ANBIETER") }
            itemsIndexed(builtIns, key = { _, item -> item.definition.id.value }) { index, item ->
                ProviderCard(
                    item = item,
                    index = index,
                    onOpen = { onOpenProvider(item.definition.id) },
                    onEnabledChange = { viewModel.setEnabled(item.definition.id, it) }
                )
            }
            item { SettingsSectionTitle("EIGENE ANBIETER") }
            if (!state.loading && custom.isEmpty()) {
                item { SettingsInfoCard("Noch keine eigenen Anbieter. Füge einen OpenAI-kompatiblen oder lokalen Anbieter hinzu.") }
            }
            itemsIndexed(custom, key = { _, item -> item.definition.id.value }) { index, item ->
                ProviderCard(
                    item = item,
                    index = builtIns.size + index,
                    onOpen = { onOpenProvider(item.definition.id) },
                    onEnabledChange = { viewModel.setEnabled(item.definition.id, it) },
                    onDuplicate = { viewModel.duplicate(item.definition.id) },
                    onDelete = { deleteCandidate = item.definition }
                )
            }
        }
    }
}

@Composable
internal fun ProviderCard(
    item: ProviderListItemUi,
    index: Int,
    onOpen: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onDuplicate: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    val provider = item.definition
    Surface(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().testTag("provider_card_$index"),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(provider.displayName, style = MaterialTheme.typography.titleMedium)
                    Text(if (provider.builtIn) "Integriert" else "Eigener Anbieter", color = MaterialTheme.colorScheme.primary)
                }
                Switch(
                    checked = provider.enabled,
                    onCheckedChange = onEnabledChange,
                    modifier = Modifier.semantics { contentDescription = "${provider.displayName} ${if (provider.enabled) "deaktivieren" else "aktivieren"}" }
                )
            }
            Text(provider.connectionType.displayName(), style = MaterialTheme.typography.bodyMedium)
            Text(provider.baseUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${item.modelCount} Modelle · ${provider.defaultModelId ?: "Kein Standardmodell"}", style = MaterialTheme.typography.bodySmall)
            Text(provider.secretSummary(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (onDuplicate != null || onDelete != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    onDuplicate?.let {
                        IconButton(onClick = it, modifier = Modifier.testTag("provider_duplicate")) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Anbieter duplizieren")
                        }
                    }
                    onDelete?.let {
                        IconButton(onClick = it, modifier = Modifier.testTag("provider_delete")) {
                            Icon(Icons.Default.Delete, contentDescription = "Anbieter löschen")
                        }
                    }
                }
            }
        }
    }
}
