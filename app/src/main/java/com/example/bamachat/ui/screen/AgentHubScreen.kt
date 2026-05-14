package com.example.bamachat.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bamachat.ui.viewmodel.SettingsViewModel

private data class PersonaPack(
    val title: String,
    val subtitle: String,
    val preset: String,
    val goal: String,
    val rules: String,
    val style: String,
    val tools: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentHubScreen(
    settingsViewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val enabled by settingsViewModel.agentStudioEnabled.collectAsState()
    val preset by settingsViewModel.agentPreset.collectAsState()
    val name by settingsViewModel.agentName.collectAsState()
    val goal by settingsViewModel.agentGoal.collectAsState()
    val rules by settingsViewModel.agentRules.collectAsState()
    val style by settingsViewModel.agentOutputStyle.collectAsState()
    val tools by settingsViewModel.agentTools.collectAsState()

    var localPreset by remember { mutableStateOf(preset) }
    var localName by remember { mutableStateOf(name) }
    var localGoal by remember { mutableStateOf(goal) }
    var localRules by remember { mutableStateOf(rules) }
    var localStyle by remember { mutableStateOf(style) }
    var localTools by remember { mutableStateOf(tools) }
    var savedHint by remember { mutableStateOf(false) }

    val presets = listOf("Generalist", "Recherche", "Entwickler", "Marketing", "Lager & Logistik")
    val outputStyles = listOf("Klar und präzise", "Analytisch", "Schritt-für-Schritt", "Kreativ", "Kurz mit Bulletpoints")
    val personaPacks = listOf(
        PersonaPack(
            title = "Architect Pack",
            subtitle = "Systemdesign, Trade-offs, Skalierung",
            preset = "Entwickler",
            goal = "Entwickle robuste Architekturen mit klarer Skalierungs- und Risikoanalyse.",
            rules = "Erst Ziele und Constraints, dann Varianten, danach klare Entscheidung mit Trade-offs.",
            style = "Analytisch",
            tools = "Systemdesign, API-Verträge, Failure-Modes, Performance-Checks"
        ),
        PersonaPack(
            title = "Research Pack",
            subtitle = "Fakten, Quellen, Verifikation",
            preset = "Recherche",
            goal = "Liefere belastbare Erkenntnisse mit klarer Quellen- und Unsicherheitsdarstellung.",
            rules = "Keine Halluzinationen. Fakten und Annahmen trennen. Unsicherheit transparent markieren.",
            style = "Klar und präzise",
            tools = "Quellenabgleich, Faktencheck, Evidenzmatrix, Gegenpositionen"
        ),
        PersonaPack(
            title = "Execution Pack",
            subtitle = "Priorisierung und Delivery",
            preset = "Generalist",
            goal = "Bringe Aufgaben in konkrete, ausführbare Schritte mit Prioritäten.",
            rules = "Jeder Plan enthält Owner, Risiko, Reihenfolge und Definition of Done.",
            style = "Schritt-für-Schritt",
            tools = "Roadmapping, Task-Breakdown, Risikoanalyse, Checklisten"
        )
    )

    LaunchedEffect(preset) { localPreset = preset }
    LaunchedEffect(name) { localName = name }
    LaunchedEffect(goal) { localGoal = goal }
    LaunchedEffect(rules) { localRules = rules }
    LaunchedEffect(style) { localStyle = style }
    LaunchedEffect(tools) { localTools = tools }

    val isDirty =
        localPreset != preset ||
            localName != name ||
            localGoal != goal ||
            localRules != rules ||
            localStyle != style ||
            localTools != tools

    val preview = remember(localPreset, localName, localGoal, localRules, localStyle, localTools) {
        """
[Agent-Profil]
Name: ${localName.ifBlank { "Bama Agent" }}
Rolle: ${localPreset.ifBlank { "Generalist" }}

[Ziel]
${localGoal.ifBlank { "Löse Nutzeranfragen zuverlässig." }}

[Regeln]
${localRules.ifBlank { "Antworte korrekt und fokussiert." }}

[Ausgabestil]
${localStyle.ifBlank { "Klar und präzise" }}

[Erlaubte Arbeitsweisen]
${localTools.ifBlank { "Analyse, Problemlösung" }}
""".trim()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Agent Hub", fontWeight = FontWeight.Bold) },
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
                .padding(14.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Status", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(if (enabled) "Agent Studio: Aktiv" else "Agent Studio: Inaktiv", fontSize = 12.sp)
                        Switch(
                            checked = enabled,
                            onCheckedChange = { settingsViewModel.setAgentStudioEnabled(it) }
                        )
                    }
                    Text("Preset: $preset", fontSize = 12.sp)
                    Text("Name: $name", fontSize = 12.sp)
                    Text("Stil: $style", fontSize = 12.sp)
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Agent bearbeiten", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)

                    Text("Preset", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(presets) { item ->
                            AssistChip(
                                onClick = {
                                    localPreset = item
                                    settingsViewModel.applyAgentPreset(item)
                                    savedHint = false
                                },
                                label = { Text(item, fontSize = 11.sp) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = localName,
                        onValueChange = { localName = it; savedHint = false },
                        label = { Text("Agent-Name", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = localGoal,
                        onValueChange = { localGoal = it; savedHint = false },
                        label = { Text("Ziel", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4
                    )
                    OutlinedTextField(
                        value = localRules,
                        onValueChange = { localRules = it; savedHint = false },
                        label = { Text("Regeln", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 5
                    )

                    Text("Ausgabestil", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(outputStyles) { item ->
                            AssistChip(
                                onClick = { localStyle = item; savedHint = false },
                                label = { Text(item, fontSize = 11.sp) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = localTools,
                        onValueChange = { localTools = it; savedHint = false },
                        label = { Text("Arbeitsweisen / Tools", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = isDirty,
                            onClick = {
                                settingsViewModel.setAgentPreset(localPreset)
                                settingsViewModel.setAgentName(localName)
                                settingsViewModel.setAgentGoal(localGoal)
                                settingsViewModel.setAgentRules(localRules)
                                settingsViewModel.setAgentOutputStyle(localStyle)
                                settingsViewModel.setAgentTools(localTools)
                                savedHint = true
                            }
                        ) { Text("Speichern") }
                        Button(
                            enabled = isDirty,
                            onClick = {
                                localPreset = preset
                                localName = name
                                localGoal = goal
                                localRules = rules
                                localStyle = style
                                localTools = tools
                                savedHint = false
                            }
                        ) { Text("Zurücksetzen") }
                    }
                    if (savedHint) {
                        Text("Gespeichert", fontSize = 11.sp, color = Color(0xFF0E9F6E))
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Persona Marketplace", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    personaPacks.forEach { pack ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(pack.title, fontWeight = FontWeight.Medium, fontSize = 12.sp)
                                Text(pack.subtitle, fontSize = 11.sp)
                                Button(
                                    onClick = {
                                        settingsViewModel.setAgentPreset(pack.preset)
                                        settingsViewModel.setAgentName(pack.title)
                                        settingsViewModel.setAgentGoal(pack.goal)
                                        settingsViewModel.setAgentRules(pack.rules)
                                        settingsViewModel.setAgentOutputStyle(pack.style)
                                        settingsViewModel.setAgentTools(pack.tools)
                                        localPreset = pack.preset
                                        localName = pack.title
                                        localGoal = pack.goal
                                        localRules = pack.rules
                                        localStyle = pack.style
                                        localTools = pack.tools
                                        savedHint = true
                                    }
                                ) {
                                    Text("Installieren")
                                }
                            }
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Aktiver System-Prompt", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(preview, fontSize = 11.sp, lineHeight = 16.sp)
                }
            }
        }
    }
}
