package com.example.bamachat.ui.screen

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import com.example.bamachat.data.provider.chat.ActiveChatProviderSelection
import com.example.bamachat.ui.component.settings.SettingsInfoCard
import com.example.bamachat.ui.component.settings.SettingsSectionTitle
import com.example.bamachat.ui.component.settings.SettingsTopBar
import com.example.bamachat.ui.component.settings.settingsScreenContentPadding
import com.example.bamachat.ui.viewmodel.ChatProviderSelectionEffect
import com.example.bamachat.ui.viewmodel.ChatProviderSelectionViewModel

@Composable
fun ChatProviderSelectionScreen(
    onBack: () -> Unit,
    viewModel: ChatProviderSelectionViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var pending by remember(state.persistedSelection) { mutableStateOf(state.persistedSelection) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                ChatProviderSelectionEffect.Saved -> onBack()
                is ChatProviderSelectionEffect.Message -> snackbar.showSnackbar(effect.text)
            }
        }
    }

    Scaffold(
        modifier = Modifier.testTag("chat_provider_selection_screen"),
        topBar = { SettingsTopBar("Anbieter für Chat auswählen", onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).imePadding(),
            contentPadding = settingsScreenContentPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { SettingsInfoCard("Die Auswahl gilt für neue Textanfragen. Eigene Anbieter verwenden niemals automatisch einen anderen Anbieter als Ersatz.") }
            state.warning?.let { item { SettingsInfoCard(it, accent = MaterialTheme.colorScheme.error) } }
            item { SettingsSectionTitle("SICHERE STANDARDAUSWAHL") }
            item {
                ChatProviderSelectionRow(
                    title = "Bisherige KI-Einstellungen",
                    subtitle = "Verwendet weiterhin die bestehende Anbieter- und Fallbackkonfiguration",
                    selected = pending === ActiveChatProviderSelection.Legacy,
                    enabled = true,
                    onClick = { pending = ActiveChatProviderSelection.Legacy }
                )
            }
            item { SettingsSectionTitle("EIGENE ANBIETER") }
            if (!state.loading && state.choices.isEmpty()) {
                item { SettingsInfoCard("Keine eigenen Anbieter verfügbar.") }
            }
            itemsIndexed(state.choices, key = { _, choice -> choice.provider.id.value }) { index, choice ->
                Column(Modifier.fillMaxWidth().testTag("chat_provider_choice_$index")) {
                    Text(choice.provider.displayName, style = MaterialTheme.typography.titleMedium)
                    choice.models.forEach { model ->
                        val selection = ActiveChatProviderSelection.Custom(choice.provider.id, model.modelId)
                        ChatProviderSelectionRow(
                            title = model.displayName,
                            subtitle = choice.unavailableReason ?: "Nur Textchat · kein automatischer Fallback",
                            selected = pending == selection,
                            enabled = choice.selectable,
                            onClick = { pending = selection }
                        )
                    }
                    if (choice.models.isEmpty()) {
                        Text(choice.unavailableReason ?: "Kein aktiviertes Modell", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            item {
                Button(
                    onClick = { viewModel.confirm(pending) },
                    enabled = !state.loading,
                    modifier = Modifier.fillMaxWidth().testTag("confirm_chat_provider_selection")
                ) { Text("Auswahl bestätigen") }
            }
        }
    }
}

@Composable
private fun ChatProviderSelectionRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().semantics {
            contentDescription = "$title. $subtitle. ${if (selected) "Ausgewählt" else "Nicht ausgewählt"}"
        },
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick, enabled = enabled)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
