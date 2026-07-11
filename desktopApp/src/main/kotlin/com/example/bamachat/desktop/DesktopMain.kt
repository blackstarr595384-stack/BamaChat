package com.example.bamachat.desktop

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Checkbox
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.RadioButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.example.bamachat.shared.core.ExtensionRuntimeOrchestrator
import com.example.bamachat.shared.core.AiChatMessage
import com.example.bamachat.shared.core.AiChatRole
import com.example.bamachat.shared.core.PromptDraft
import com.example.bamachat.shared.core.PromptDrafts
import com.example.bamachat.shared.core.QuickActionInterpreter
import com.example.bamachat.shared.core.QuickActionSuggestion
import com.example.bamachat.shared.core.WorkspaceTextToolkit
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class DesktopSection {
    CHAT,
    WORKSPACE,
    SETTINGS
}

private val DESKTOP_TEMPLATE_TITLES = listOf(
    "Tagesbriefing",
    "Meeting -> ToDos",
    "Release Check",
    "Risiko Scan"
)

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "BamaChat für Windows"
    ) {
        MaterialTheme {
            DesktopRoot()
        }
    }
}

@Composable
private fun DesktopRoot() {
    var activeSection by remember { mutableStateOf(DesktopSection.CHAT) }
    var settings by remember { mutableStateOf(DesktopSettingsStore.load()) }
    var appStatus by remember { mutableStateOf("Desktop bereit.") }
    var workspaceNotes by remember { mutableStateOf("") }
    val cloudGateway = remember { DesktopCloudSyncGateway() }

    fun persistSettings(
        updated: DesktopUserSettings,
        successMessage: String = "Einstellungen gespeichert."
    ) {
        settings = updated
        val saveResult = runCatching { DesktopSettingsStore.save(updated) }
        appStatus = saveResult.fold(
            onSuccess = { successMessage },
            onFailure = { "Speichern fehlgeschlagen: ${it.message ?: "Unbekannter Fehler"}" }
        )
    }

    LaunchedEffect(
        settings.authUid,
        settings.authRefreshToken,
        settings.firebaseApiKey,
        settings.firebaseProjectId
    ) {
        while (true) {
            val current = settings
            if (current.authUid.isBlank() || current.authRefreshToken.isBlank()) break
            val now = System.currentTimeMillis()
            val refreshLeadMs = 25_000L
            val waitMs = (current.authTokenExpiryEpochMs - now - refreshLeadMs)
                .coerceIn(10_000L, 120_000L)
            delay(waitMs)
            val latest = settings
            if (latest.authUid.isBlank() || latest.authRefreshToken.isBlank()) break
            try {
                val refreshed = cloudGateway.refreshAuthTokenIfNeeded(latest)
                if (refreshed != latest) {
                    persistSettings(refreshed, "Cloud-Session automatisch aktualisiert.")
                }
            } catch (expired: CloudSessionExpiredException) {
                val message = expired.message ?: "Cloud-Session abgelaufen. Bitte erneut anmelden."
                persistSettings(latest.clearCloudSession(), message)
                break
            } catch (t: Throwable) {
                appStatus = "Auto-Refresh fehlgeschlagen: ${t.message ?: "Unbekannter Fehler"}"
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    listOf(Color(0xFF101726), Color(0xFF1D2A44))
                )
            )
            .padding(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            LeftSidebar(
                activeSection = activeSection,
                onSelect = { activeSection = it },
                provider = settings.provider,
                cloudAccountLabel = settings.authEmail.ifBlank { "Nicht angemeldet" },
                status = appStatus,
                modifier = Modifier
                    .width(240.dp)
                    .fillMaxHeight()
            )
            Spacer(Modifier.width(14.dp))
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF17233A)
            ) {
                when (activeSection) {
                    DesktopSection.CHAT -> DesktopChatWorkspace(
                        settings = settings,
                        onRequestOpenSettings = { activeSection = DesktopSection.SETTINGS },
                        onStatusChange = { appStatus = it }
                    )
                    DesktopSection.WORKSPACE -> DesktopNotesWorkspace(
                        settings = settings,
                        notes = workspaceNotes,
                        onNotesChange = { workspaceNotes = it },
                        onSettingsChange = { updated, statusMessage ->
                            persistSettings(updated, statusMessage)
                        },
                        onStatusChange = { appStatus = it }
                    )
                    DesktopSection.SETTINGS -> DesktopSettingsView(
                        settings = settings,
                        onSave = { updated ->
                            persistSettings(updated, "Einstellungen gespeichert.")
                        },
                        onSettingsChange = { updated, statusMessage ->
                            persistSettings(updated, statusMessage)
                        },
                        status = appStatus
                    )
                }
            }
        }
    }
}

@Composable
private fun LeftSidebar(
    activeSection: DesktopSection,
    onSelect: (DesktopSection) -> Unit,
    provider: DesktopProvider,
    cloudAccountLabel: String,
    status: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF0F192E)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "BamaChat",
                style = MaterialTheme.typography.h6,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Desktop-Client",
                color = Color.White.copy(alpha = 0.75f),
                style = MaterialTheme.typography.body2
            )
            Text(
                text = "Provider: ${provider.label()}",
                color = Color.White.copy(alpha = 0.72f),
                style = MaterialTheme.typography.caption
            )
            Text(
                text = "Cloud: $cloudAccountLabel",
                color = Color.White.copy(alpha = 0.72f),
                style = MaterialTheme.typography.caption
            )
            Text(
                text = status,
                color = Color.White.copy(alpha = 0.66f),
                style = MaterialTheme.typography.caption
            )
            Text(
                text = "Entwickler: M.D Baldé",
                color = Color.White.copy(alpha = 0.60f),
                style = MaterialTheme.typography.caption
            )
            Spacer(Modifier.height(10.dp))
            SidebarButton(
                label = "Chat",
                icon = Icons.Default.Chat,
                isActive = activeSection == DesktopSection.CHAT
            ) {
                onSelect(DesktopSection.CHAT)
            }
            SidebarButton(
                label = "Arbeitsbereiche",
                icon = Icons.Default.Notes,
                isActive = activeSection == DesktopSection.WORKSPACE
            ) {
                onSelect(DesktopSection.WORKSPACE)
            }
            SidebarButton(
                label = "Einstellungen",
                icon = Icons.Default.Settings,
                isActive = activeSection == DesktopSection.SETTINGS
            ) {
                onSelect(DesktopSection.SETTINGS)
            }
        }
    }
}

@Composable
private fun SidebarButton(
    label: String,
    icon: ImageVector,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val background = if (isActive) Color(0xFF2D4A7A) else Color(0xFF1A2A47)
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = background,
        modifier = Modifier.fillMaxWidth()
    ) {
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            elevation = null,
            colors = ButtonDefaults.buttonColors(
                backgroundColor = Color.Transparent,
                contentColor = Color.White
            )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    modifier = Modifier.size(20.dp),
                    tint = if (isActive) Color.White else Color.White.copy(alpha = 0.7f)
                )
                Text(label, color = Color.White)
            }
        }
    }
}

@Composable
private fun DesktopChatWorkspace(
    settings: DesktopUserSettings,
    onRequestOpenSettings: () -> Unit,
    onStatusChange: (String) -> Unit
) {
    val gateway = remember { DesktopChatGateway() }
    val coroutineScope = rememberCoroutineScope()
    var prompt by remember { mutableStateOf("") }
    var selectedQuickAction by remember { mutableStateOf(QuickActionSuggestion.AUTO) }
    var isSending by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }
    val draftHistory = remember { mutableStateListOf<PromptDraft>() }
    val chatHistory = remember { mutableStateListOf<AiChatMessage>() }
    val quickActionSuggestion = remember(prompt) { QuickActionInterpreter.suggest(prompt) }
    val activeExtensions = remember(settings.enabledExtensionIds) {
        DesktopExtensionCatalog.all
            .filter { settings.enabledExtensionIds.contains(it.id) }
            .map { it.toRuntimeExtension() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Chat-Arbeitsbereich",
            style = MaterialTheme.typography.h5,
            color = Color.White,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "Produktiver Desktop-Chat mit OpenRouter/Ollama und gemeinsamen Schnellaktionen.",
            color = Color.White.copy(alpha = 0.78f),
            style = MaterialTheme.typography.body2
        )
        if (settings.provider == DesktopProvider.OPENROUTER && settings.openRouterApiKey.isBlank()) {
            Surface(
                color = Color(0xFF4B2330),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "OpenRouter API-Key fehlt.",
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onRequestOpenSettings) {
                        Text("Einstellungen")
                    }
                }
            }
        }

        Text(
            text = "Schnellaktionen",
            color = Color.White.copy(alpha = 0.86f),
            style = MaterialTheme.typography.body2,
            fontWeight = FontWeight.SemiBold
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickActionSuggestion.entries.forEach { action ->
                QuickActionButton(
                    action = action,
                    selected = selectedQuickAction == action,
                    onClick = { selectedQuickAction = action }
                )
            }
            Button(
                onClick = { selectedQuickAction = quickActionSuggestion },
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = Color(0xFF1E3256),
                    contentColor = Color.White
                )
            ) {
                Text("Nutze Vorschlag")
            }
        }
        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            label = { Text("Nachricht", color = Color.White.copy(alpha = 0.8f)) },
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 96.dp)
        )
        Text(
            text = "Vorschlag: ${quickActionSuggestion.label()} | Aktiv: ${selectedQuickAction.label()}",
            color = Color.White.copy(alpha = 0.78f),
            style = MaterialTheme.typography.body2
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = !isSending,
                onClick = {
                    val newDraft = PromptDrafts.createOrNull(prompt)
                    if (newDraft != null) {
                        val updated = PromptDrafts.prepend(draftHistory.toList(), newDraft)
                        draftHistory.clear()
                        draftHistory.addAll(updated)
                        onStatusChange("Draft gespeichert.")
                    }
                }
            ) {
                Text("Entwurf speichern")
            }
            Button(
                enabled = chatHistory.isNotEmpty() && !isSending,
                onClick = {
                    chatHistory.clear()
                    localError = null
                    onStatusChange("Chatverlauf geloescht.")
                }
            ) {
                Text("Chat leeren")
            }
            Button(
                enabled = prompt.isNotBlank() && !isSending,
                onClick = {
                    val userText = prompt.trim()
                    if (userText.isEmpty()) return@Button

                    localError = null
                    prompt = ""
                    chatHistory += AiChatMessage(
                        role = AiChatRole.USER,
                        text = userText
                    )

                    val newDraft = PromptDrafts.createOrNull(userText)
                    if (newDraft != null) {
                        val updated = PromptDrafts.prepend(draftHistory.toList(), newDraft)
                        draftHistory.clear()
                        draftHistory.addAll(updated)
                    }

                    isSending = true
                    onStatusChange("Sende Anfrage an ${settings.provider.label()} ...")
                    coroutineScope.launch {
                        try {
                            val runtimeDecision = ExtensionRuntimeOrchestrator.buildRuntimeContext(
                                userText = userText,
                                quickAction = selectedQuickAction,
                                activeExtensions = activeExtensions,
                                templateTitles = DESKTOP_TEMPLATE_TITLES
                            )
                            val reply = gateway.requestAssistantReply(
                                settings = settings,
                                chatHistory = chatHistory.toList(),
                                quickAction = selectedQuickAction,
                                runtimeDecision = runtimeDecision
                            )
                            chatHistory += AiChatMessage(
                                role = AiChatRole.ASSISTANT,
                                text = reply
                            )
                            onStatusChange("Antwort erhalten (${settings.provider.label()}).")
                        } catch (t: Throwable) {
                            val message = t.message ?: "Unbekannter Fehler"
                            localError = message
                            chatHistory += AiChatMessage(
                                role = AiChatRole.ASSISTANT,
                                text = "Fehler: $message"
                            )
                            onStatusChange("Anfrage fehlgeschlagen.")
                        } finally {
                            isSending = false
                        }
                    }
                }
            ) {
                Text("Senden")
            }
            if (isSending) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .height(24.dp)
                        .width(24.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            }
        }

        Text(
            text = "Aktive Erweiterungen: ${
                DesktopExtensionCatalog.all
                    .filter { settings.enabledExtensionIds.contains(it.id) }
                    .joinToString { it.name }
                    .ifBlank { "Keine" }
            }",
            color = Color.White.copy(alpha = 0.72f),
            style = MaterialTheme.typography.caption
        )

        localError?.let { error ->
            Text(
                text = "Letzter Fehler: $error",
                color = Color(0xFFFFB3B3),
                style = MaterialTheme.typography.caption
            )
        }

        Surface(
            modifier = Modifier.fillMaxSize().weight(1f),
            color = Color(0xFF132036),
            shape = RoundedCornerShape(10.dp)
        ) {
            val scrollState = rememberScrollState()
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (chatHistory.isEmpty()) {
                        Text(
                            "Noch kein Chat. Sende oben deine erste Nachricht.",
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    } else {
                        val clipboardManager = LocalClipboardManager.current
                        chatHistory.forEach { item ->
                            val cardColor = if (item.role == AiChatRole.USER) {
                                Color(0xFF2C4C7D)
                            } else {
                                Color(0xFF1E3256)
                            }
                            Surface(
                                color = cardColor,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = item.role.label(),
                                            style = MaterialTheme.typography.caption,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White.copy(alpha = 0.8f)
                                        )
                                        IconButton(
                                            onClick = {
                                                clipboardManager.setText(AnnotatedString(item.text))
                                                onStatusChange("In Zwischenablage kopiert.")
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = "Kopieren",
                                                tint = Color.White.copy(alpha = 0.6f),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = item.text,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
                VerticalScrollbar(
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    adapter = rememberScrollbarAdapter(scrollState)
                )
            }
        }
    }
}

@Composable
private fun DesktopNotesWorkspace(
    settings: DesktopUserSettings,
    notes: String,
    onNotesChange: (String) -> Unit,
    onSettingsChange: (DesktopUserSettings, String) -> Unit,
    onStatusChange: (String) -> Unit
) {
    val cloudGateway = remember { DesktopCloudSyncGateway() }
    val coroutineScope = rememberCoroutineScope()
    var isSyncBusy by remember { mutableStateOf(false) }
    var cloudMeta by remember { mutableStateOf<String?>(null) }
    var cloudError by remember { mutableStateOf<String?>(null) }

    val summary = remember(notes) { WorkspaceTextToolkit.summarize(notes) }
    val actionItems = remember(notes) { WorkspaceTextToolkit.extractActionItems(notes) }
    val quickActionSuggestion = remember(notes) { QuickActionInterpreter.suggest(notes) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Workspace Notes",
            style = MaterialTheme.typography.h5,
            color = Color.White,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "Workspace-Notizen mit Shared-Core Summary/Action-Item-Extraktion.",
            color = Color.White.copy(alpha = 0.78f)
        )
        Text(
            text = "Vorgeschlagene Quick Action: ${quickActionSuggestion.label()}",
            color = Color.White.copy(alpha = 0.78f),
            style = MaterialTheme.typography.body2
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = !isSyncBusy && settings.authUid.isNotBlank(),
                onClick = {
                    isSyncBusy = true
                    cloudError = null
                    onStatusChange("Lade Workspace aus der Cloud ...")
                    coroutineScope.launch {
                        try {
                            val (updatedSettings, snapshot) = cloudGateway.pullWorkspaceSnapshot(settings)
                            onSettingsChange(updatedSettings, "Cloud-Session aktualisiert.")
                            if (snapshot == null) {
                                cloudMeta = "Kein Workspace-Dokument in Cloud."
                                onStatusChange("Kein Cloud-Workspace gefunden.")
                            } else {
                                onNotesChange(snapshot.text)
                                cloudMeta = "Cloud: ${snapshot.updatedAt.orEmpty()} von ${snapshot.updatedBy.orEmpty()}"
                                onStatusChange("Workspace aus Cloud geladen.")
                            }
                        } catch (expired: CloudSessionExpiredException) {
                            val message = expired.message ?: "Cloud-Session abgelaufen. Bitte erneut anmelden."
                            cloudError = message
                            onSettingsChange(cloudGateway.signOut(settings), message)
                            onStatusChange("Cloud-Session abgelaufen.")
                        } catch (t: Throwable) {
                            cloudError = t.message ?: "Unbekannter Fehler"
                            onStatusChange("Cloud-Laden fehlgeschlagen.")
                        } finally {
                            isSyncBusy = false
                        }
                    }
                }
            ) {
                Icon(Icons.Default.Cloud, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Laden")
            }
            Button(
                enabled = !isSyncBusy && settings.authUid.isNotBlank(),
                onClick = {
                    isSyncBusy = true
                    cloudError = null
                    onStatusChange("Speichere Workspace in die Cloud ...")
                    coroutineScope.launch {
                        try {
                            val updatedSettings = cloudGateway.pushWorkspaceSnapshot(settings, notes)
                            onSettingsChange(updatedSettings, "Cloud-Session aktualisiert.")
                            cloudMeta = "Cloud gespeichert: ${java.time.Instant.now()}"
                            onStatusChange("Workspace in Cloud gespeichert.")
                        } catch (expired: CloudSessionExpiredException) {
                            val message = expired.message ?: "Cloud-Session abgelaufen. Bitte erneut anmelden."
                            cloudError = message
                            onSettingsChange(cloudGateway.signOut(settings), message)
                            onStatusChange("Cloud-Session abgelaufen.")
                        } catch (t: Throwable) {
                            cloudError = t.message ?: "Unbekannter Fehler"
                            onStatusChange("Cloud-Speichern fehlgeschlagen.")
                        } finally {
                            isSyncBusy = false
                        }
                    }
                }
            ) {
                Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Speichern")
            }
            Button(
                onClick = {
                    onNotesChange("")
                    onStatusChange("Notizen geleert.")
                }
            ) {
                Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Leeren")
            }
            if (isSyncBusy) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .height(20.dp)
                        .width(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            }
        }
        Text(
            text = if (settings.authEmail.isNotBlank()) {
                "Cloud-Konto: ${settings.authEmail}"
            } else {
                "Cloud-Konto: nicht angemeldet (Login in Settings)."
            },
            color = Color.White.copy(alpha = 0.74f),
            style = MaterialTheme.typography.caption
        )
        cloudMeta?.let {
            Text(
                text = it,
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.caption
            )
        }
        cloudError?.let {
            Text(
                text = "Cloud-Fehler: $it",
                color = Color(0xFFFFB3B3),
                style = MaterialTheme.typography.caption
            )
        }
        Surface(
            color = Color(0xFF132036),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Summary", color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(summary, color = Color.White.copy(alpha = 0.84f))
                Text("Action items", color = Color.White, fontWeight = FontWeight.SemiBold)
                if (actionItems.isEmpty()) {
                    Text("Keine ToDos erkannt.", color = Color.White.copy(alpha = 0.74f))
                } else {
                    actionItems.forEach { item ->
                        Text("• $item", color = Color.White.copy(alpha = 0.84f))
                    }
                }
            }
        }
        OutlinedTextField(
            value = notes,
            onValueChange = onNotesChange,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(),
            label = { Text("Project notes", color = Color.White.copy(alpha = 0.8f)) }
        )
    }
}

private fun QuickActionSuggestion.label(): String = when (this) {
    QuickActionSuggestion.AUTO -> "Auto"
    QuickActionSuggestion.RESEARCH -> "Recherche"
    QuickActionSuggestion.CODE_REVIEW -> "Code prüfen"
    QuickActionSuggestion.PLAN -> "Plan"
}

@Composable
private fun DesktopSettingsView(
    settings: DesktopUserSettings,
    onSave: (DesktopUserSettings) -> Unit,
    onSettingsChange: (DesktopUserSettings, String) -> Unit,
    status: String
) {
    val cloudGateway = remember { DesktopCloudSyncGateway() }
    val coroutineScope = rememberCoroutineScope()
    var authBusy by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }

    var provider by remember(settings) { mutableStateOf(settings.provider) }
    var openRouterApiKey by remember(settings) { mutableStateOf(settings.openRouterApiKey) }
    var openRouterModel by remember(settings) { mutableStateOf(settings.openRouterModel) }
    var ollamaBaseUrl by remember(settings) { mutableStateOf(settings.ollamaBaseUrl) }
    var ollamaModel by remember(settings) { mutableStateOf(settings.ollamaModel) }
    var firebaseApiKey by remember(settings) { mutableStateOf(settings.firebaseApiKey) }
    var firebaseProjectId by remember(settings) { mutableStateOf(settings.firebaseProjectId) }
    var googleOAuthClientId by remember(settings) { mutableStateOf(settings.googleOAuthClientId) }
    var googleOAuthClientSecret by remember(settings) { mutableStateOf(settings.googleOAuthClientSecret) }
    var encryptCloudSession by remember(settings) { mutableStateOf(settings.encryptCloudSession) }
    var loginEmail by remember(settings) { mutableStateOf(settings.authEmail) }
    var loginPassword by remember { mutableStateOf("") }
    var enabledExtensionIds by remember(settings) {
        mutableStateOf(settings.enabledExtensionIds)
    }

    val scrollState = rememberScrollState()
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Desktop Settings",
                style = MaterialTheme.typography.h5,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Provider, Modelle und aktivierte Extensions fuer den Desktop-Chat.",
                color = Color.White.copy(alpha = 0.78f)
            )
            Text(
                text = "Status: $status",
                color = Color.White.copy(alpha = 0.72f),
                style = MaterialTheme.typography.caption
            )

            Surface(
                color = Color(0xFF132036),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Provider", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ProviderRadio(
                            selected = provider == DesktopProvider.OPENROUTER,
                            label = DesktopProvider.OPENROUTER.label(),
                            onSelect = { provider = DesktopProvider.OPENROUTER }
                        )
                        ProviderRadio(
                            selected = provider == DesktopProvider.OLLAMA,
                            label = DesktopProvider.OLLAMA.label(),
                            onSelect = { provider = DesktopProvider.OLLAMA }
                        )
                    }
                    Divider(color = Color.White.copy(alpha = 0.2f))
                    OutlinedTextField(
                        value = openRouterApiKey,
                        onValueChange = { openRouterApiKey = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("OpenRouter API-Key", color = Color.White.copy(alpha = 0.8f)) }
                    )
                    OutlinedTextField(
                        value = openRouterModel,
                        onValueChange = { openRouterModel = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("OpenRouter Modell", color = Color.White.copy(alpha = 0.8f)) }
                    )
                    OutlinedTextField(
                        value = ollamaBaseUrl,
                        onValueChange = { ollamaBaseUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Ollama Base URL", color = Color.White.copy(alpha = 0.8f)) }
                    )
                    OutlinedTextField(
                        value = ollamaModel,
                        onValueChange = { ollamaModel = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Ollama Modell", color = Color.White.copy(alpha = 0.8f)) }
                    )
                    Divider(color = Color.White.copy(alpha = 0.2f))
                    Text("Firebase Cloud", color = Color.White, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = firebaseApiKey,
                        onValueChange = { firebaseApiKey = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Firebase Web API-Key", color = Color.White.copy(alpha = 0.8f)) }
                    )
                    OutlinedTextField(
                        value = firebaseProjectId,
                        onValueChange = { firebaseProjectId = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Firebase Project-ID", color = Color.White.copy(alpha = 0.8f)) }
                    )
                    OutlinedTextField(
                        value = googleOAuthClientId,
                        onValueChange = { googleOAuthClientId = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Google OAuth Client-ID", color = Color.White.copy(alpha = 0.8f)) }
                    )
                    OutlinedTextField(
                        value = googleOAuthClientSecret,
                        onValueChange = { googleOAuthClientSecret = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Google OAuth Client-Secret (optional)", color = Color.White.copy(alpha = 0.8f)) },
                        visualTransformation = PasswordVisualTransformation()
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(
                            checked = encryptCloudSession,
                            onCheckedChange = { encryptCloudSession = it }
                        )
                        Text(
                            "Cloud-Session lokal verschluesseln (ID/Refresh-Token)",
                            color = Color.White.copy(alpha = 0.84f)
                        )
                    }
                }
            }

            Surface(
                color = Color(0xFF132036),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Extensions", color = Color.White, fontWeight = FontWeight.SemiBold)
                    DesktopExtensionCatalog.all.forEach { preset ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Checkbox(
                                checked = enabledExtensionIds.contains(preset.id),
                                onCheckedChange = { checked ->
                                    enabledExtensionIds = if (checked) {
                                        enabledExtensionIds + preset.id
                                    } else {
                                        enabledExtensionIds - preset.id
                                    }
                                }
                            )
                            Column {
                                Text(preset.name, color = Color.White)
                                Text(
                                    preset.description,
                                    color = Color.White.copy(alpha = 0.72f),
                                    style = MaterialTheme.typography.caption
                                )
                            }
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        onSave(
                            DesktopUserSettings(
                                provider = provider,
                                openRouterApiKey = openRouterApiKey.trim(),
                                openRouterModel = openRouterModel.trim().ifBlank { DEFAULT_OPENROUTER_MODEL },
                                ollamaBaseUrl = ollamaBaseUrl.trim().ifBlank { DEFAULT_OLLAMA_BASE_URL },
                                ollamaModel = ollamaModel.trim().ifBlank { DEFAULT_OLLAMA_MODEL },
                                enabledExtensionIds = enabledExtensionIds,
                                firebaseApiKey = firebaseApiKey.trim(),
                                firebaseProjectId = firebaseProjectId.trim(),
                                googleOAuthClientId = googleOAuthClientId.trim(),
                                googleOAuthClientSecret = googleOAuthClientSecret.trim(),
                                authEmail = settings.authEmail,
                                authUid = settings.authUid,
                                authIdToken = settings.authIdToken,
                                authRefreshToken = settings.authRefreshToken,
                                authTokenExpiryEpochMs = settings.authTokenExpiryEpochMs,
                                encryptCloudSession = encryptCloudSession
                            )
                        )
                    }
                ) {
                    Text("Speichern")
                }
                Button(
                    onClick = {
                        provider = DesktopUserSettings().provider
                        openRouterApiKey = DesktopUserSettings().openRouterApiKey
                        openRouterModel = DesktopUserSettings().openRouterModel
                        ollamaBaseUrl = DesktopUserSettings().ollamaBaseUrl
                        ollamaModel = DesktopUserSettings().ollamaModel
                        firebaseApiKey = DesktopUserSettings().firebaseApiKey
                        firebaseProjectId = DesktopUserSettings().firebaseProjectId
                        googleOAuthClientId = DesktopUserSettings().googleOAuthClientId
                        googleOAuthClientSecret = DesktopUserSettings().googleOAuthClientSecret
                        encryptCloudSession = DesktopUserSettings().encryptCloudSession
                        enabledExtensionIds = DesktopUserSettings().enabledExtensionIds
                    }
                ) {
                    Text("Defaults")
                }
            }

            Surface(
                color = Color(0xFF132036),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Cloud Login", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = if (settings.authUid.isNotBlank()) {
                            "Angemeldet als ${settings.authEmail.ifBlank { settings.authUid }}"
                        } else {
                            "Nicht angemeldet"
                        },
                        color = Color.White.copy(alpha = 0.78f)
                    )
                    OutlinedTextField(
                        value = loginEmail,
                        onValueChange = { loginEmail = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("E-Mail", color = Color.White.copy(alpha = 0.8f)) }
                    )
                    OutlinedTextField(
                        value = loginPassword,
                        onValueChange = { loginPassword = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Passwort", color = Color.White.copy(alpha = 0.8f)) },
                        visualTransformation = PasswordVisualTransformation()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = !authBusy,
                            onClick = {
                                authBusy = true
                                authError = null
                                val baseSettings = settings.copy(
                                    firebaseApiKey = firebaseApiKey.trim(),
                                    firebaseProjectId = firebaseProjectId.trim(),
                                    googleOAuthClientId = googleOAuthClientId.trim(),
                                    googleOAuthClientSecret = googleOAuthClientSecret.trim(),
                                    encryptCloudSession = encryptCloudSession
                                )
                                coroutineScope.launch {
                                    try {
                                        val updated = cloudGateway.signInWithEmailPassword(
                                            settings = baseSettings,
                                            email = loginEmail.trim(),
                                            password = loginPassword
                                        )
                                        loginPassword = ""
                                        onSettingsChange(updated, "Cloud-Login erfolgreich.")
                                    } catch (t: Throwable) {
                                        authError = t.message ?: "Unbekannter Fehler"
                                    } finally {
                                        authBusy = false
                                    }
                                }
                            }
                        ) {
                            Text("Login")
                        }
                        Button(
                            enabled = !authBusy,
                            onClick = {
                                authBusy = true
                                authError = null
                                val baseSettings = settings.copy(
                                    firebaseApiKey = firebaseApiKey.trim(),
                                    firebaseProjectId = firebaseProjectId.trim(),
                                    googleOAuthClientId = googleOAuthClientId.trim(),
                                    googleOAuthClientSecret = googleOAuthClientSecret.trim(),
                                    encryptCloudSession = encryptCloudSession
                                )
                                coroutineScope.launch {
                                    try {
                                        val updated = cloudGateway.signUpWithEmailPassword(
                                            settings = baseSettings,
                                            email = loginEmail.trim(),
                                            password = loginPassword
                                        )
                                        loginPassword = ""
                                        onSettingsChange(updated, "Cloud-Registrierung erfolgreich.")
                                    } catch (t: Throwable) {
                                        authError = t.message ?: "Unbekannter Fehler"
                                    } finally {
                                        authBusy = false
                                    }
                                }
                            }
                        ) {
                            Text("Registrieren")
                        }
                        Button(
                            enabled = !authBusy && googleOAuthClientId.trim().isNotBlank(),
                            onClick = {
                                authBusy = true
                                authError = null
                                val baseSettings = settings.copy(
                                    firebaseApiKey = firebaseApiKey.trim(),
                                    firebaseProjectId = firebaseProjectId.trim(),
                                    googleOAuthClientId = googleOAuthClientId.trim(),
                                    googleOAuthClientSecret = googleOAuthClientSecret.trim(),
                                    encryptCloudSession = encryptCloudSession
                                )
                                coroutineScope.launch {
                                    try {
                                        val updated = cloudGateway.signInWithGoogleOAuthBrowser(baseSettings)
                                        loginEmail = updated.authEmail
                                        loginPassword = ""
                                        onSettingsChange(updated, "Google-Login erfolgreich.")
                                    } catch (t: Throwable) {
                                        authError = t.message ?: "Unbekannter Fehler"
                                    } finally {
                                        authBusy = false
                                    }
                                }
                            }
                        ) {
                            Text("Google Login")
                        }
                        Button(
                            enabled = !authBusy && settings.authUid.isNotBlank(),
                            onClick = {
                                val cleared = cloudGateway.signOut(settings)
                                onSettingsChange(cleared, "Cloud-Session beendet.")
                                loginPassword = ""
                            }
                        ) {
                            Text("Logout")
                        }
                        if (authBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .height(20.dp)
                                    .width(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        }
                    }
                    authError?.let { message ->
                        Text(
                            text = "Auth-Fehler: $message",
                            color = Color(0xFFFFB3B3),
                            style = MaterialTheme.typography.caption
                        )
                    }
                }
            }

            Surface(
                color = Color(0xFF132036),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Hinweise", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text(
                        "OpenRouter braucht einen gueltigen API-Key. Ollama braucht einen lokal laufenden Server. Fuer Cloud-Sync sind Firebase API-Key + Project-ID + Login noetig. Fuer Google-Login verwende bevorzugt einen OAuth Client vom Typ 'Desktop App'.",
                        color = Color.White.copy(alpha = 0.78f)
                    )
                }
            }
        }
        VerticalScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            adapter = rememberScrollbarAdapter(scrollState)
        )
    }
}

@Composable
private fun QuickActionButton(
    action: QuickActionSuggestion,
    selected: Boolean,
    onClick: () -> Unit
) {
    val background = if (selected) Color(0xFF2D4A7A) else Color(0xFF1A2A47)
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            backgroundColor = background,
            contentColor = Color.White
        )
    ) {
        Text(action.label())
    }
}

@Composable
private fun ProviderRadio(
    selected: Boolean,
    label: String,
    onSelect: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label, color = Color.White)
    }
}

private fun DesktopProvider.label(): String = when (this) {
    DesktopProvider.OPENROUTER -> "OpenRouter"
    DesktopProvider.OLLAMA -> "Ollama"
}

private fun AiChatRole.label(): String = when (this) {
    AiChatRole.SYSTEM -> "System"
    AiChatRole.USER -> "User"
    AiChatRole.ASSISTANT -> "Assistant"
}
