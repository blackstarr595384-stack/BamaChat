package com.example.bamachat.ui.screen

import android.animation.ValueAnimator
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.bamachat.ui.theme.AppDesignSystem
import dev.jeziellago.compose.markdowntext.MarkdownText
import kotlinx.coroutines.launch

private data class HelpFaqItem(
    val question: String,
    val answer: String
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HelpCenterScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsPrefs = remember(context) {
        context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
    }
    val designPreset = remember(settingsPrefs) {
        settingsPrefs.getString("ui_design_preset", "Professional")
    }
    val palette = remember(designPreset) { AppDesignSystem.paletteForStored(designPreset) }
    var helpText by remember { mutableStateOf("Lade Anleitung ...") }
    val faqItems = remember {
        listOf(
            HelpFaqItem(
                question = "Wie erstelle ich einen Workspace?",
                answer = "Öffne Einstellungen > Workspaces & Automationen, gib unter Neuer Workspace einen Namen ein und tippe auf Workspace erstellen. Danach kannst du den Workspace direkt aktivieren und optional die Chatliste darauf filtern."
            ),
            HelpFaqItem(
                question = "Was ist der Unterschied zwischen Bildanalyse und Bildgenerierung?",
                answer = "Bildanalyse erklärt ein vorhandenes Bild, das du hochlädst oder aufnimmst. Bildgenerierung erstellt aus deinem Textprompt ein neues Bild über den eingestellten Bilddienst."
            ),
            HelpFaqItem(
                question = "Warum funktioniert Bildgenerierung manchmal nicht?",
                answer = "Die Chat-Bildgenerierung nutzt einen externen Bilddienst. Wenn dieser Dienst nicht erreichbar ist oder Zahlung/Auth verlangt, zeigt BamaFlow eine Fehlermeldung und speichert keine kaputte Bildkarte. Prüfe Einstellungen > KI & Modelle > Bildgenerierung im Chat."
            ),
            HelpFaqItem(
                question = "Brauche ich ein Konto?",
                answer = "Für lokales Testen reicht der Gastmodus. Für Profil, Sync und echte geräteübergreifende Live-Zusammenarbeit ist ein Konto sinnvoll."
            ),
            HelpFaqItem(
                question = "Wo finde ich Hilfe, wenn es noch keine Webseite gibt?",
                answer = "Direkt in dieser App. Du kannst die Anleitung über Teilen verschicken oder über Speichern als Markdown-Datei exportieren. Externe Links sollten erst angezeigt werden, wenn sie wirklich existieren."
            ),
            HelpFaqItem(
                question = "Wie verbinde ich MCP-Tools auf Android?",
                answer = "Öffne Einstellungen > KI & Modelle, trage unter Remote MCP Bridge die Remote MCP URL ein und hinterlege bei Bedarf einen Bridge Token. Aktiviere danach den gewünschten Server im Bereich MCP Server und prüfe die verfügbaren MCP-Tools."
            ),
            HelpFaqItem(
                question = "Wie lade ich andere in eine Session ein?",
                answer = "Erstelle in Live-Zusammenarbeit eine neue Session und kopiere danach den Invite-Link oder den Invite-Code. Andere Personen können mit Session-ID, Invite-Link oder Invite-Code beitreten."
            ),
            HelpFaqItem(
                question = "Was ist der Unterschied zwischen Gastmodus und eingeladenen Teilnehmern?",
                answer = "Gastmodus ist ein lokaler App-Modus zum Testen auf diesem Gerät. Eingeladene Teilnehmer gehören zu einer echten gemeinsamen Session und arbeiten an Nachrichten und Workspace-Inhalten mit."
            ),
            HelpFaqItem(
                question = "Wie arbeite ich mit dem KI-Team im Workspace?",
                answer = "Wähle in einer laufenden Session unter Agenten für KI-Hilfe wählen die gewünschten Agenten aus. Schreibe dann eine Nachricht oder nutze den aktuellen Workspace-Text als Kontext und starte KI-Team-Antwort."
            ),
            HelpFaqItem(
                question = "Was mache ich bei einem Workspace-Konflikt?",
                answer = "Prüfe zuerst den Inline-Diff. Danach kannst du je nach Fall Remote laden, Smart Merge verwenden, den Merge speichern oder den lokalen Stand erzwingen."
            ),
            HelpFaqItem(
                question = "Wie funktionieren Billing und Credits?",
                answer = "Im Bereich Einstellungen > KI & Modelle siehst du deinen Plan, den Billing-Status und den aktuellen Credit-Stand. Dort kannst du auch Pro-, Expert- oder Credit-Produkte starten, sobald Play Billing verbunden ist."
            ),
            HelpFaqItem(
                question = "Wie lösche ich mein Konto?",
                answer = "Die Kontolöschung startest du im Profil. Prüfe vorher, ob du wichtige Inhalte exportieren möchtest."
            )
        )
    }
    var expandedFaqQuestion by remember { mutableStateOf(faqItems.firstOrNull()?.question) }
    val chatSectionRequester = remember { BringIntoViewRequester() }
    val workspaceSectionRequester = remember { BringIntoViewRequester() }
    val mcpSectionRequester = remember { BringIntoViewRequester() }
    val collabSectionRequester = remember { BringIntoViewRequester() }
    val faqSectionRequester = remember { BringIntoViewRequester() }

    LaunchedEffect(Unit) {
        helpText = loadHelpFromAssets(context = context) ?: "Die Hilfe-Datei konnte nicht geladen werden."
    }

    val saveHelpLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                writer.write(helpText)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hilfe & Anleitung", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.heroBg)
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(palette.screenBgTop, palette.screenBgMid, palette.screenBgBottom)
                    )
                )
                .padding(padding)
                .padding(horizontal = 14.dp, vertical = 14.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewRequester(chatSectionRequester),
                shape = RoundedCornerShape(24.dp),
                color = palette.heroBg.copy(alpha = 0.88f),
                border = BorderStroke(1.dp, palette.heroBorder.copy(alpha = 0.72f))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    HelpPill("Start in 30 Sekunden")
                    Text(
                        text = "Du musst nicht alles auf einmal lernen.",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = palette.heroTitle
                    )
                    Text(
                        text = "Wenn du neu bist, reicht dieser Ablauf: Chat öffnen, Persona wählen, Ziel nennen und die Antwort mit einer Rückfrage schärfen.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.heroSubtitle
                    )
                    HelpStepRow(step = "1", title = "Chat starten", body = "Direkt losschreiben oder Sprache nutzen.")
                    HelpStepRow(step = "2", title = "Persona wählen", body = "Z. B. Assistent, Entwickler oder Lehrer.")
                    HelpStepRow(step = "3", title = "Antwort verfeinern", body = "Format, Tiefe oder nächsten Schritt nachfordern.")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        HelpMiniPill("Workspaces")
                        HelpMiniPill("MCP-Tools")
                        HelpMiniPill("Realtime-Collab")
                        HelpMiniPill("KI-Team")
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = palette.surface.copy(alpha = 0.84f),
                border = BorderStroke(1.dp, palette.surfaceBorder.copy(alpha = 0.74f))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Schnellnavigation",
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.textPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HelpJumpChip(label = "Chat") {
                            scope.launch { chatSectionRequester.bringIntoView() }
                        }
                        HelpJumpChip(label = "Workspaces") {
                            scope.launch { workspaceSectionRequester.bringIntoView() }
                        }
                        HelpJumpChip(label = "MCP") {
                            scope.launch { mcpSectionRequester.bringIntoView() }
                        }
                        HelpJumpChip(label = "Collab") {
                            scope.launch { collabSectionRequester.bringIntoView() }
                        }
                        HelpJumpChip(label = "FAQ") {
                            scope.launch { faqSectionRequester.bringIntoView() }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "BamaFlow Hilfe")
                            putExtra(Intent.EXTRA_TEXT, helpText)
                        }
                        context.startActivity(Intent.createChooser(intent, "Hilfe teilen"))
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Text(" Teilen")
                }
                OutlinedButton(
                    onClick = { saveHelpLauncher.launch("BamaFlow-Hilfe.md") },
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f))
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, tint = Color.White)
                    Text(" Speichern", color = Color.White)
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = palette.surface.copy(alpha = 0.86f),
                border = BorderStroke(1.dp, palette.surfaceBorder.copy(alpha = 0.74f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(Color(0xFFB9CCFF), CircleShape)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Gast oder Konto?",
                            style = MaterialTheme.typography.titleSmall,
                            color = palette.textPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Gastmodus ist ideal zum Testen. Für Profil, Sync und Realtime-Collab mit anderen brauchst du ein Konto.",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textSecondary
                        )
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewRequester(workspaceSectionRequester),
                shape = RoundedCornerShape(18.dp),
                color = Color.White.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HelpSectionCard(
                        title = "Workspaces",
                        path = "Einstellungen > Workspaces & Automationen",
                        body = "Lege für jedes Projekt einen eigenen Workspace an, aktiviere ihn direkt in der Liste und filtere auf Wunsch die Chatliste auf genau dieses Projekt.",
                        highlights = listOf(
                            "Neuen Workspace anlegen und sofort aktivieren",
                            "Nur aktive Workspace-Chats für mehr Übersicht",
                            "Schnellaktionen und Tool-Bestätigungen zentral steuern"
                    )
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewRequester(mcpSectionRequester),
                shape = RoundedCornerShape(18.dp),
                color = Color.White.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HelpSectionCard(
                        title = "MCP",
                        path = "Einstellungen > KI & Modelle > Remote MCP Bridge",
                        body = "Auf Android läuft MCP in der Regel über die Remote MCP Bridge. Trage die URL ein, aktiviere den Server und kontrolliere danach die verfügbaren Tools.",
                        highlights = listOf(
                            "Remote MCP URL und optionalen Bridge Token hinterlegen",
                            "Server im Bereich MCP Server aktivieren",
                            "Verfügbare MCP-Tools als Bereitschaftscheck prüfen"
                        )
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewRequester(collabSectionRequester),
                shape = RoundedCornerShape(18.dp),
                color = Color.White.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HelpSectionCard(
                        title = "Realtime-Collab",
                        path = "Realtime Collaboration",
                        body = "Erstelle eine Session, teile Invite-Link oder Invite-Code und arbeite gemeinsam an Nachrichten, Rollen, Policies und dem synchronen Workspace-Text.",
                        highlights = listOf(
                            "Session mit Name erstellen und Link kopieren",
                            "Owner, Editor und Viewer über Policies absichern",
                            "KI-Team mit Nachrichten- oder Workspace-Kontext starten"
                        )
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewRequester(faqSectionRequester),
                shape = RoundedCornerShape(18.dp),
                color = Color.White.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "FAQ",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                    faqItems.forEach { item ->
                        HelpFaqCard(
                            item = item,
                            expanded = expandedFaqQuestion == item.question,
                            onToggle = {
                                expandedFaqQuestion = if (expandedFaqQuestion == item.question) null else item.question
                            }
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.1f))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Vollständige Anleitung",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                    MarkdownText(
                        markdown = helpText,
                        style = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
                    )
                }
            }
        }
    }
}

@Composable
private fun HelpFaqCard(
    item: HelpFaqItem,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.05f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.question,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Antwort ausblenden" else "Antwort einblenden",
                    tint = Color(0xFFB9CCFF)
                )
            }
            // P1-D fix: FAQ respektiert Androids Einstellung "Animationen entfernen".
            if (ValueAnimator.areAnimatorsEnabled()) {
                AnimatedVisibility(
                    visible = expanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Text(
                        text = item.answer,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFD7E4FF)
                    )
                }
            } else if (expanded) {
                Text(
                    text = item.answer,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFD7E4FF)
                )
            }
        }
    }
}

@Composable
private fun HelpJumpChip(label: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = Color(0xFFB9CCFF).copy(alpha = 0.16f),
        border = BorderStroke(1.dp, Color(0xFFB9CCFF).copy(alpha = 0.28f)),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun HelpPill(text: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = Color.White.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            color = Color(0xFFD8E4FF),
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun HelpMiniPill(text: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = Color.White.copy(alpha = 0.06f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            color = Color(0xFFE4ECFF),
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun HelpStepRow(step: String, title: String, body: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(Color(0xFFB9CCFF), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = step,
                color = Color(0xFF163A66),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFD7E4FF)
            )
        }
    }
}

@Composable
private fun HelpSectionCard(
    title: String,
    path: String,
    body: String,
    highlights: List<String>
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.SemiBold
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = Color.White.copy(alpha = 0.05f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = path,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFB9CCFF)
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFD7E4FF)
                )
                highlights.forEach { item ->
                    Text(
                        text = "• $item",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }
        }
    }
}

private fun loadHelpFromAssets(context: android.content.Context): String? {
    return runCatching {
        context.assets.open("help_guide_de.md").bufferedReader().use { it.readText() }
    }.getOrNull()
}
