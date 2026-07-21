package com.example.bamachat.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bamachat.ui.component.settings.SettingsInfoCard
import com.example.bamachat.ui.component.settings.SettingsNavigationRow
import com.example.bamachat.ui.component.settings.SettingsSectionTitle
import com.example.bamachat.ui.component.settings.SettingsTopBar
import com.example.bamachat.ui.component.settings.settingsScreenContentPadding
import com.example.bamachat.ui.viewmodel.SettingsViewModel

@Composable
fun AiModelSettingsScreen(
    settingsViewModel: SettingsViewModel,
    onBack: () -> Unit,
    onOpenProviderManagement: () -> Unit,
    onOpenLegacySettings: () -> Unit
) {
    val provider by settingsViewModel.aiProvider.collectAsStateWithLifecycle()
    val openRouterModel by settingsViewModel.selectedOpenRouterModel.collectAsStateWithLifecycle()
    val openCodeModel by settingsViewModel.openCodeModel.collectAsStateWithLifecycle()
    val model = when (provider) {
        "OpenRouter" -> openRouterModel
        "OpenCode" -> openCodeModel
        else -> "Anbieterspezifische Auswahl"
    }

    Scaffold(
        modifier = Modifier.testTag("ai_models_screen"),
        topBar = { SettingsTopBar(title = "KI und Modelle", onBack = onBack) },
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).imePadding(),
            contentPadding = settingsScreenContentPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { SettingsSectionTitle("AKTUELLE CHAT-KONFIGURATION") }
            item {
                SettingsInfoCard(
                    text = "Aktiver Anbieter: $provider\nAktuelles Modell: $model"
                )
            }
            item {
                SettingsInfoCard(
                    text = "Eigene Anbieter können hier bereits verwaltet werden. Die Verwendung im Chat wird in einem folgenden Schritt aktiviert.",
                    accent = MaterialTheme.colorScheme.tertiary
                )
            }
            item { SettingsSectionTitle("ANBIETER") }
            item {
                SettingsNavigationRow(
                    modifier = Modifier.testTag("open_provider_management"),
                    title = "Anbieter verwalten",
                    description = "Integrierte und eigene OpenAI-kompatible Anbieter verwalten",
                    icon = Icons.Default.Cloud,
                    onClick = onOpenProviderManagement
                )
            }
            item {
                SettingsNavigationRow(
                    title = "Bisherige KI-Einstellungen",
                    description = "Produktiven Anbieter, Modell und bestehende Zugangsdaten verwalten",
                    icon = Icons.Default.Settings,
                    onClick = onOpenLegacySettings
                )
            }
            item {
                SettingsInfoCard(
                    text = "Cloud-Anbieter benötigen in der Regel Internet und einen eigenen Schlüssel. Lokale Anbieter können im Heimnetz betrieben werden."
                )
            }
        }
    }
}
