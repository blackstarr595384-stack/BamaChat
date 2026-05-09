package com.example.bamachat.ui.screen

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bamachat.data.ApiClient
import com.example.bamachat.ui.viewmodel.SettingsViewModel
import com.example.bamachat.util.PlayBillingManager
import java.util.Locale

@Suppress("UNUSED_VARIABLE", "UNUSED_PARAMETER")
@Composable
fun SettingsDialog(
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit
) {
    val isBiometricEnabled by viewModel.isBiometricEnabled.collectAsState()
    val primaryColor by viewModel.primaryColorInt.collectAsState()
    val fontSize by viewModel.fontSize.collectAsState()
    val multiProvider by viewModel.multiProviderEnabled.collectAsState()
    val openRouterApiKey by viewModel.openRouterApiKey.collectAsState()
    val groqApiKey by viewModel.groqApiKey.collectAsState()
    val cerebrasApiKey by viewModel.cerebrasApiKey.collectAsState()
    val togetherApiKey by viewModel.togetherApiKey.collectAsState()
    val geminiApiKey by viewModel.geminiApiKey.collectAsState()
    val ollamaUrl by viewModel.ollamaUrl.collectAsState()
    val selectedOpenRouterModel by viewModel.selectedOpenRouterModel.collectAsState()
    val openRouterVisionOnlyModels by viewModel.openRouterVisionOnlyModels.collectAsState()
    val agentStudioEnabled by viewModel.agentStudioEnabled.collectAsState()
    val agentPreset by viewModel.agentPreset.collectAsState()
    val agentName by viewModel.agentName.collectAsState()
    val agentGoal by viewModel.agentGoal.collectAsState()
    val agentRules by viewModel.agentRules.collectAsState()
    val agentOutputStyle by viewModel.agentOutputStyle.collectAsState()
    val agentTools by viewModel.agentTools.collectAsState()
    val isPremiumActive by viewModel.isPremiumActive.collectAsState()
    val billingReady by viewModel.billingReady.collectAsState()
    val purchaseInProgress by viewModel.purchaseInProgress.collectAsState()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState()
    val soundEnabled by viewModel.soundEnabled.collectAsState()
    val vibrationEnabled by viewModel.vibrationEnabled.collectAsState()
    val autoSendVoice by viewModel.autoSendVoice.collectAsState()
    val voiceChatMode by viewModel.voiceChatMode.collectAsState()
    val ttsEnabled by viewModel.ttsEnabled.collectAsState()
    val ttsSpeed by viewModel.ttsSpeed.collectAsState()
    val streamingEnabled by viewModel.streamingEnabled.collectAsState()
    val showTimestamps by viewModel.showTimestamps.collectAsState()
    val bubbleAnimations by viewModel.bubbleAnimations.collectAsState()
    val language by viewModel.language.collectAsState()

    val _uriHandler = LocalUriHandler.current
    val context = LocalContext.current

    var expandedSection by remember { mutableStateOf<String?>(null) }

    val colors = listOf(
        0xFF6A11CB, 0xFF2575FC, 0xFF00B894,
        0xFFD63031, 0xFFFDBC40, 0xFF2D3436,
        0xFFE17055, 0xFF0984E3, 0xFF6C5CE7
    )

    val languages = listOf(
        "de" to "Deutsch",
        "en" to "English",
        "pl" to "Polski",
        "fr" to "Français",
        "es" to "Español",
        "tr" to "Türkçe",
        "ar" to "العربية"
    )
    val agentPresets = listOf("Generalist", "Recherche", "Entwickler", "Marketing", "Lager & Logistik")
    val outputStyles = listOf("Klar und präzise", "Analytisch", "Schritt-für-Schritt", "Kreativ", "Kurz mit Bulletpoints")
    val agentPreview = remember(
        agentStudioEnabled,
        agentPreset,
        agentName,
        agentGoal,
        agentRules,
        agentOutputStyle,
        agentTools
    ) {
        viewModel.getAgentPromptPreview()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Einstellungen", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                SettingsSection("Allgemein", expandedSection == "general", onClick = { expandedSection = if (expandedSection == "general") null else "general" }) {
                    SettingRow("Fingerabdruck-Sperre", "App beim Start sichern") {
                        Switch(checked = isBiometricEnabled, onCheckedChange = { viewModel.setBiometricEnabled(it) })
                    }
                    SettingRow("Benachrichtigungen", "Bei neuen KI-Antworten") {
                        Switch(checked = notificationsEnabled, onCheckedChange = {
                            viewModel.setNotificationsEnabled(it)
                            if (it) openNotificationSettings(context)
                        })
                    }
                    SettingRow("Sound-Effekte", "Töne bei Nachrichten") {
                        Switch(checked = soundEnabled, onCheckedChange = { viewModel.setSoundEnabled(it) })
                    }
                    SettingRow("Vibration", "Haptisches Feedback") {
                        Switch(checked = vibrationEnabled, onCheckedChange = { viewModel.setVibrationEnabled(it) })
                    }
                    SettingRow("Sprache", "App-Sprache wählen") {
                        DropdownSelector(
                            value = languages.find { it.first == language }?.second ?: "Deutsch",
                            items = languages.map { it.second },
                            onSelect = { label ->
                                val code = languages.find { it.second == label }?.first ?: "de"
                                viewModel.setLanguage(code)
                            }
                        )
                    }
                }

                SettingsSection("Chat & Darstellung", expandedSection == "chat", onClick = { expandedSection = if (expandedSection == "chat") null else "chat" }) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Akzentfarbe", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                        Spacer(Modifier.width(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(colors) { colorInt ->
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color(colorInt))
                                        .clickable { viewModel.setPrimaryColor(colorInt.toInt()) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (primaryColor == colorInt.toInt()) {
                                        Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Schriftgröße (${fontSize.toInt()} sp)", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                        Slider(
                            value = fontSize,
                            onValueChange = { viewModel.setFontSize(it) },
                            valueRange = 12f..24f,
                            steps = 6
                        )
                    }
                    SettingRow("Zeitstempel anzeigen", "Uhrzeit unter Nachrichten") {
                        Switch(checked = showTimestamps, onCheckedChange = { viewModel.setShowTimestamps(it) })
                    }
                    SettingRow("Bubble-Animationen", "Animierte Nachrichtenblasen") {
                        Switch(checked = bubbleAnimations, onCheckedChange = { viewModel.setBubbleAnimations(it) })
                    }
                    SettingRow("Streaming aktivieren", "Antwort Wort für Wort anzeigen") {
                        Switch(checked = streamingEnabled, onCheckedChange = { viewModel.setStreamingEnabled(it) })
                    }
                }

                SettingsSection("Sprach-Chat", expandedSection == "voice", onClick = { expandedSection = if (expandedSection == "voice") null else "voice" }) {
                    SettingRow("Sprachmodus", "Durchgehend per Sprache chatten") {
                        Switch(checked = voiceChatMode, onCheckedChange = { viewModel.setVoiceChatMode(it) })
                    }
                    SettingRow("Auto-Senden", "Nach Spracheingabe sofort senden") {
                        Switch(checked = autoSendVoice, onCheckedChange = { viewModel.setAutoSendVoice(it) })
                    }
                    SettingRow("Auto-Vorlesen (TTS)", "KI-Antworten automatisch sprechen") {
                        Switch(checked = ttsEnabled, onCheckedChange = { viewModel.setTtsEnabled(it) })
                    }
                    if (ttsEnabled) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("TTS-Geschwindigkeit (${String.format(Locale.getDefault(), "%.1fx", ttsSpeed)})", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                            Slider(
                                value = ttsSpeed,
                                onValueChange = { viewModel.setTtsSpeed(it) },
                                valueRange = 0.5f..2.0f,
                                steps = 6
                            )
                        }
                    }
                }

                SettingsSection("KI-Anbieter", expandedSection == "ai", onClick = { expandedSection = if (expandedSection == "ai") null else "ai" }) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = if (isPremiumActive) Color(0xFF0B3D2D) else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Premium", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = if (isPremiumActive) Color(0xFFB4F2D8) else LocalContentColor.current)
                                    Text(
                                        if (isPremiumActive) "Aktiv: Unbegrenzte Nutzung freigeschaltet"
                                        else "Free-Plan mit Tageslimits aktiv",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 11.sp,
                                        color = if (isPremiumActive) Color(0xFFB4F2D8) else LocalContentColor.current
                                    )
                                }
                                if (isPremiumActive) {
                                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF67E2AE), modifier = Modifier.size(18.dp))
                                }
                            }
                            Text(
                                if (billingReady) "Play Billing verbunden" else "Play Billing noch nicht verbunden",
                                fontSize = 11.sp,
                                color = if (billingReady) Color(0xFF00B894) else Color.Gray
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AssistChip(
                                    onClick = {
                                        val activity = context as? android.app.Activity ?: return@AssistChip
                                        viewModel.startSubscriptionCheckout(activity, PlayBillingManager.PLAN_BASIC)
                                    },
                                    label = { Text("Basic") },
                                    enabled = billingReady && !purchaseInProgress
                                )
                                AssistChip(
                                    onClick = {
                                        val activity = context as? android.app.Activity ?: return@AssistChip
                                        viewModel.startSubscriptionCheckout(activity, PlayBillingManager.PLAN_PRO)
                                    },
                                    label = { Text("Pro") },
                                    enabled = billingReady && !purchaseInProgress
                                )
                                AssistChip(
                                    onClick = {
                                        val activity = context as? android.app.Activity ?: return@AssistChip
                                        viewModel.startSubscriptionCheckout(activity, PlayBillingManager.PLAN_EXPERT)
                                    },
                                    label = { Text("Expert") },
                                    enabled = billingReady && !purchaseInProgress
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { viewModel.refreshBillingState() }) { Text("Billing aktualisieren", fontSize = 11.sp) }
                                if (!billingReady) {
                                    TextButton(onClick = { viewModel.setPremiumActiveForDebug(true) }) { Text("Premium-Test lokal", fontSize = 11.sp) }
                                }
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = if (multiProvider) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Auto-Fallback", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text("Bei Fehler automatisch nächsten Anbieter probieren", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp)
                            }
                            Switch(checked = multiProvider, onCheckedChange = { viewModel.setMultiProviderEnabled(it) }, modifier = Modifier.scale(0.85f))
                        }
                    }

                    ProviderCardMini("Cerebras", "ULTRA schnell (~2000 tok/s)", "https://cloud.cerebras.ai/", cerebrasApiKey, "csk-...") { viewModel.setCerebrasApiKey(it) }
                    ProviderCardMini("Groq", "Sehr schnell, 30 req/min", "https://console.groq.com/keys", groqApiKey, "gsk_...") { viewModel.setGroqApiKey(it) }
                    ProviderCardMini("OpenRouter", "Viele freie Modelle", "https://openrouter.ai/keys", openRouterApiKey, "sk-or-v1-...") { viewModel.setOpenRouterApiKey(it) }

                    if (openRouterApiKey.isNotBlank()) {
                        SettingRow(
                            "Nur Vision-Modelle",
                            "Für Bild-Uploads nur bildfähige Modelle anzeigen"
                        ) {
                            Switch(
                                checked = openRouterVisionOnlyModels,
                                onCheckedChange = { viewModel.setOpenRouterVisionOnlyModels(it) },
                                modifier = Modifier.scale(0.85f)
                            )
                        }

                        val selectableModels = if (openRouterVisionOnlyModels) {
                            ApiClient.OPENROUTER_VISION_MODELS
                        } else {
                            ApiClient.OPENROUTER_FREE_MODELS
                        }

                        Column(modifier = Modifier.padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Modell:", fontWeight = FontWeight.Medium, fontSize = 12.sp)
                            selectableModels.forEach { modelId ->
                                val displayName = ApiClient.FREE_MODEL_DISPLAY_NAMES[modelId] ?: modelId.takeLast(20)
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { viewModel.setSelectedOpenRouterModel(modelId) }) {
                                    RadioButton(selected = selectedOpenRouterModel == modelId, onClick = { viewModel.setSelectedOpenRouterModel(modelId) }, modifier = Modifier.size(20.dp))
                                    Text(displayName, fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    ProviderCardMini("Together AI", "Llama 70B kostenlos", "https://api.together.xyz/settings/api-keys", togetherApiKey, "tgp_v1_...") { viewModel.setTogetherApiKey(it) }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    OutlinedTextField(
                        value = geminiApiKey,
                        onValueChange = { viewModel.setGeminiApiKey(it) },
                        label = { Text("Gemini API-Key", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("AIza...") },
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                    )
                    OutlinedTextField(
                        value = ollamaUrl,
                        onValueChange = { viewModel.setOllamaUrl(it) },
                        label = { Text("Ollama URL", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                    )
                }

                SettingsSection("Agent Studio", expandedSection == "agents", onClick = { expandedSection = if (expandedSection == "agents") null else "agents" }) {
                    SettingRow(
                        "Agent-Studio aktiv",
                        "Erweitert den System-Prompt mit Agent-Profil"
                    ) {
                        Switch(
                            checked = agentStudioEnabled,
                            onCheckedChange = { viewModel.setAgentStudioEnabled(it) },
                            modifier = Modifier.scale(0.85f)
                        )
                    }
                    Text("Agent-Typ", fontWeight = FontWeight.Medium, fontSize = 12.sp)
                    DropdownSelector(
                        value = agentPreset,
                        items = agentPresets,
                        onSelect = { viewModel.applyAgentPreset(it) }
                    )
                    OutlinedTextField(
                        value = agentName,
                        onValueChange = { viewModel.setAgentName(it) },
                        label = { Text("Agent-Name", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                    )
                    OutlinedTextField(
                        value = agentGoal,
                        onValueChange = { viewModel.setAgentGoal(it) },
                        label = { Text("Ziel / Mission", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4,
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                    )
                    OutlinedTextField(
                        value = agentRules,
                        onValueChange = { viewModel.setAgentRules(it) },
                        label = { Text("Regeln / Guardrails", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 5,
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                    )
                    Text("Ausgabestil", fontWeight = FontWeight.Medium, fontSize = 12.sp)
                    DropdownSelector(
                        value = agentOutputStyle,
                        items = outputStyles,
                        onSelect = { viewModel.setAgentOutputStyle(it) }
                    )
                    OutlinedTextField(
                        value = agentTools,
                        onValueChange = { viewModel.setAgentTools(it) },
                        label = { Text("Arbeitsweisen / Tools", fontSize = 12.sp) },
                        placeholder = { Text("z.B. Recherche, Faktencheck, Planen", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4,
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                    )

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Prompt-Vorschau", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Text(
                                text = agentPreview,
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                            )
                        }
                    }
                }

                SettingsSection("Daten & Info", expandedSection == "data", onClick = { expandedSection = if (expandedSection == "data") null else "data" }) {
                    SettingRow("Alle Daten löschen", "Einstellungen und Chats zurücksetzen") {
                        TextButton(onClick = {
                            viewModel.clearAllData()
                        }) { Text("Löschen", color = Color(0xFFD63031)) }
                    }
                    SettingRow("App-Info öffnen", "Berechtigungen verwalten") {
                        TextButton(onClick = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        }) { Text("Öffnen") }
                    }
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.Center) {
                        Text("BamaChat v1.0 — made by Mamadou Dian Baldé w/AI", color = Color.Gray, fontSize = 10.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fertig") }
        }
    )
}

@Composable
private fun SettingsSection(title: String, expanded: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Column(modifier = Modifier.padding(12.dp).animateContentSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClick() }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String? = null, action: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, fontSize = 13.sp)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, fontSize = 11.sp)
        }
        action()
    }
}

@Composable
private fun DropdownSelector(value: String, items: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
        Row(
            modifier = Modifier.clickable { expanded = true },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(value, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item, fontSize = 13.sp) },
                    onClick = { onSelect(item); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun ProviderCardMini(
    name: String,
    subtitle: String,
    signupUrl: String,
    apiKey: String,
    placeholder: String,
    onKeyChange: (String) -> Unit,
) {
    val isConfigured = apiKey.isNotBlank()
    val _uriHandler = LocalUriHandler.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = if (isConfigured) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
        border = if (isConfigured) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                 else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(name, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, fontSize = 10.sp)
                }
                if (isConfigured) Icon(Icons.Default.Check, null, tint = Color(0xFF00B894), modifier = Modifier.size(14.dp))
                IconButton(onClick = { _uriHandler.openUri(signupUrl) }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null, modifier = Modifier.size(14.dp))
                }
            }
            OutlinedTextField(
                value = apiKey,
                onValueChange = onKeyChange,
                label = { Text("API Key", fontSize = 10.sp) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(placeholder, fontSize = 10.sp) },
                textStyle = LocalTextStyle.current.copy(fontSize = 11.sp)
            )
        }
    }
}

private fun openNotificationSettings(context: android.content.Context) {
    val intent = Intent().apply {
        action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    }
    context.startActivity(intent)
}
