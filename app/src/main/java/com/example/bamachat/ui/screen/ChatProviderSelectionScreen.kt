package com.example.bamachat.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bamachat.ui.component.settings.SettingsInfoCard
import com.example.bamachat.ui.component.settings.SettingsSectionTitle
import com.example.bamachat.ui.component.settings.SettingsTopBar
import com.example.bamachat.ui.component.settings.settingsScreenContentPadding
import com.example.bamachat.ui.viewmodel.ChatProviderChoiceUi
import com.example.bamachat.ui.viewmodel.ChatProviderSelectionEffect
import com.example.bamachat.ui.viewmodel.ChatProviderSelectionUiState
import com.example.bamachat.ui.viewmodel.ChatProviderSelectionViewModel

@Composable
fun ChatProviderSelectionScreen(
    onBack: () -> Unit,
    onManageProviders: () -> Unit,
    viewModel: ChatProviderSelectionViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                ChatProviderSelectionEffect.Saved -> onBack()
                is ChatProviderSelectionEffect.Message -> snackbar.showSnackbar(effect.text)
            }
        }
    }

    ChatProviderSelectionContent(
        state = state,
        snackbar = snackbar,
        onBack = onBack,
        onSelectLegacy = viewModel::selectLegacy,
        onSelectOption = viewModel::selectOption,
        onConfirm = viewModel::confirm,
        onManageProviders = onManageProviders
    )
}

@Composable
internal fun ChatProviderSelectionContent(
    state: ChatProviderSelectionUiState,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    onBack: () -> Unit,
    onSelectLegacy: () -> Unit,
    onSelectOption: (String) -> Unit,
    onConfirm: () -> Unit,
    onManageProviders: () -> Unit
) {
    Scaffold(
        modifier = Modifier.testTag("chat_provider_selection_screen"),
        topBar = { SettingsTopBar("Anbieter und Modell", onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
            contentPadding = settingsScreenContentPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SettingsInfoCard("Wähle, womit neue Nachrichten verarbeitet werden.")
            }
            if (state.invalidCurrentSelection) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SettingsInfoCard(
                            "Auswahl nicht verfügbar. ${state.warning.orEmpty()}",
                            modifier = Modifier.testTag("chat_provider_selection_unavailable"),
                            accent = MaterialTheme.colorScheme.error
                        )
                        TextButton(
                            onClick = onSelectLegacy,
                            enabled = !state.confirming,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("chat_provider_switch_to_legacy")
                        ) {
                            Text("Zu BamaChat Standard wechseln")
                        }
                    }
                }
            }
            item { SettingsSectionTitle("BAMACHAT STANDARD") }
            item {
                ChatProviderSelectionRow(
                    title = "BamaChat Standard",
                    subtitle = "Verwendet die normale BamaChat-Anbieterkonfiguration.",
                    badge = "Standard",
                    selected = state.legacySelected,
                    enabled = !state.confirming,
                    testTag = "chat_provider_legacy_option",
                    onClick = onSelectLegacy
                )
            }
            item { SettingsSectionTitle("EIGENE ANBIETER") }
            if (!state.loading && state.choices.isEmpty()) {
                item {
                    SettingsInfoCard(
                        "Du hast noch keine eigenen Anbieter eingerichtet.",
                        modifier = Modifier.testTag("chat_provider_empty_state")
                    )
                }
            }
            itemsIndexed(state.choices, key = { index, _ -> index }) { index, choice ->
                ChatProviderCard(
                    choice = choice,
                    providerIndex = index,
                    confirming = state.confirming,
                    onSelectOption = onSelectOption
                )
            }
            item {
                TextButton(
                    onClick = onManageProviders,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("chat_provider_manage")
                ) {
                    Text("Anbieter verwalten")
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onConfirm,
                        enabled = state.canConfirm && !state.confirming,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("confirm_chat_provider_selection")
                    ) {
                        Text(if (state.confirming) "Wird übernommen …" else "Auswahl übernehmen")
                    }
                    TextButton(
                        onClick = onBack,
                        enabled = !state.confirming,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("cancel_chat_provider_selection")
                    ) {
                        Text("Abbrechen")
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatProviderCard(
    choice: ChatProviderChoiceUi,
    providerIndex: Int,
    confirming: Boolean,
    onSelectOption: (String) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("chat_provider_custom_$providerIndex"),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = choice.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = choice.connectionLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            choice.availabilityLabel?.let { label ->
                Text(
                    text = label,
                    modifier = Modifier.testTag(
                        if (label == "Deaktiviert") "chat_provider_disabled" else "chat_provider_no_models"
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            choice.models.forEachIndexed { modelIndex, model ->
                ChatProviderSelectionRow(
                    title = model.displayName,
                    subtitle = buildString {
                        append("Neue Nachrichten werden über diesen Anbieter verarbeitet.")
                        if (model.defaultModel) append(" · Standardmodell")
                    },
                    badge = if (model.defaultModel) "Standardmodell" else null,
                    selected = model.selected,
                    enabled = model.enabled && !confirming,
                    testTag = "chat_provider_model_${providerIndex}_$modelIndex",
                    onClick = { onSelectOption(model.optionKey) }
                )
            }
            if (choice.models.isEmpty() && choice.availabilityLabel == null) {
                Text(
                    "Keine aktiven Modelle",
                    modifier = Modifier.testTag("chat_provider_no_models"),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun ChatProviderSelectionRow(
    title: String,
    subtitle: String,
    badge: String?,
    selected: Boolean,
    enabled: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    val stateLabel = when {
        !enabled -> "Nicht auswählbar"
        selected -> "Ausgewählt"
        else -> "Nicht ausgewählt"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .testTag(testTag)
            .semantics(mergeDescendants = true) {
                role = Role.RadioButton
                contentDescription = listOfNotNull(title, badge, subtitle, stateLabel).joinToString(". ")
            }
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                badge?.let {
                    Spacer(Modifier.padding(start = 8.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
