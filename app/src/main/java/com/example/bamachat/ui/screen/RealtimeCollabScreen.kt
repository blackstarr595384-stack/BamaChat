package com.example.bamachat.ui.screen

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bamachat.ui.component.CompactTextAction
import com.example.bamachat.ui.component.CompactTextActionRow
import com.example.bamachat.ui.viewmodel.ChatViewModel
import com.example.bamachat.ui.viewmodel.CollabViewModel
import com.example.bamachat.util.AutomationCatalog
import com.example.bamachat.util.AutomationTemplate
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val KEY_MESSAGE_DRAFT = "collab_message_draft_v2"
private const val KEY_WORKSPACE_DRAFT_PREFIX = "collab_workspace_draft_v2_"
private const val WORKSPACE_SOFT_CHAR_LIMIT = 12_000

private data class AiRetryRequest(
    val prompt: String,
    val personas: List<ChatViewModel.Persona>
)

@Composable
fun RealtimeCollabScreen(
    collabViewModel: CollabViewModel,
    chatViewModel: ChatViewModel,
    onBack: () -> Unit
) {
    val session by collabViewModel.currentSession.collectAsStateWithLifecycle()
    val messages by collabViewModel.messages.collectAsStateWithLifecycle()
    val presences by collabViewModel.presences.collectAsStateWithLifecycle()
    val myUserId by collabViewModel.currentUserId.collectAsStateWithLifecycle()
    val myRole by collabViewModel.myRole.collectAsStateWithLifecycle()
    val isLoading by collabViewModel.isLoading.collectAsStateWithLifecycle()
    val errorMessage by collabViewModel.errorMessage.collectAsStateWithLifecycle()
    val workspaceState by collabViewModel.workspaceState.collectAsStateWithLifecycle()
    val syncStatus by collabViewModel.syncStatus.collectAsStateWithLifecycle()
    val authModeLabel by collabViewModel.authModeLabel.collectAsStateWithLifecycle()
    val firebaseStatus by collabViewModel.firebaseStatus.collectAsStateWithLifecycle()
    val providerLabel by collabViewModel.providerLabel.collectAsStateWithLifecycle()
    val modelLabel by collabViewModel.modelLabel.collectAsStateWithLifecycle()
    val lastDetailedError by collabViewModel.lastDetailedError.collectAsStateWithLifecycle()
    val messageDeliveryStatus by collabViewModel.messageDeliveryStatus.collectAsStateWithLifecycle()
    val canWriteMessages by collabViewModel.canWriteMessages.collectAsStateWithLifecycle()
    val canEditWorkspace by collabViewModel.canEditWorkspace.collectAsStateWithLifecycle()
    val canUseAi by collabViewModel.canUseAi.collectAsStateWithLifecycle()
    val workspaceConflictMessage by collabViewModel.workspaceConflictMessage.collectAsStateWithLifecycle()
    val multiAgentIsRunning by chatViewModel.multiAgentViewModel.isRunning.collectAsStateWithLifecycle()
    val multiAgentError by chatViewModel.multiAgentViewModel.errorMessage.collectAsStateWithLifecycle()
    val isLocalOnlyMode = authModeLabel.startsWith("Dev-Local") || syncStatus.contains("nur dieses Gerät", ignoreCase = true)

    val scope = rememberCoroutineScope()
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    val compactLayout = configuration.screenHeightDp <= 760 || configuration.screenWidthDp <= 392
    val listHorizontalPadding = if (compactLayout) 10.dp else 14.dp
    val listVerticalPadding = if (compactLayout) 10.dp else 14.dp
    val listItemSpacing = if (compactLayout) 8.dp else 10.dp
    val workspaceCardHeight = if (compactLayout) 360.dp else 420.dp
    val workspaceEditorHeight = if (compactLayout) 96.dp else 110.dp
    val footerCornerRadius = if (compactLayout) 18.dp else 20.dp

    var sessionTitle by remember { mutableStateOf("Meine Session") }
    var joinCode by remember { mutableStateOf("") }
    var inviteCodeInput by remember { mutableStateOf("") }
    var messageInput by remember { mutableStateOf(prefs.getString(KEY_MESSAGE_DRAFT, "").orEmpty()) }
    var workspaceDraft by remember { mutableStateOf("") }
    var showSetup by remember { mutableStateOf(true) }
    var showDebug by remember { mutableStateOf(false) }
    var sendLatchUntilMs by remember { mutableLongStateOf(0L) }
    var localAiStatus by remember { mutableStateOf<String?>(null) }
    var lastAiFailedRequest by remember { mutableStateOf<AiRetryRequest?>(null) }
    var pendingToolTemplate by remember { mutableStateOf<AutomationTemplate?>(null) }
    var workspaceSyncJob by remember { mutableStateOf<Job?>(null) }
    var localWorkspaceDirty by remember { mutableStateOf(false) }
    var remoteWorkspaceAheadMessage by remember { mutableStateOf<String?>(null) }
    var lastWorkspaceRevisionSeen by remember { mutableLongStateOf(0L) }

    val selectedAgents = remember { mutableStateListOf<ChatViewModel.Persona>() }
    val isOwner = session?.ownerId == myUserId
    val confirmToolActions = prefs.getBoolean("agent_confirm_tool_actions", true)
    val automationQuickActionsEnabled = prefs.getBoolean("automation_quick_actions_enabled", true)
    val sessionKey = session?.id ?: "none"
    val messagePrompt = messageInput.trim()
    val workspacePrompt = workspaceDraft.trim()
    val aiPrompt = if (messagePrompt.isNotBlank()) messagePrompt else workspacePrompt
    val localWorkspaceLines = if (workspaceDraft.isBlank()) 0 else workspaceDraft.lineSequence().count()
    val remoteWorkspaceLines = if (workspaceState.text.isBlank()) 0 else workspaceState.text.lineSequence().count()
    val workspaceCharCount = workspaceDraft.length
    val workspaceOverSoftLimit = workspaceCharCount > WORKSPACE_SOFT_CHAR_LIMIT
    val workspaceDiffData = remember(workspaceState.text, workspaceDraft) {
        collabViewModel.buildWorkspaceDiffData(workspaceDraft)
    }
    val workspaceDiffPreview = remember(workspaceState.text, workspaceDraft) {
        collabViewModel.buildWorkspaceDiffPreview(workspaceDraft)
    }

    val sendSessionMessage: () -> Unit = send@{
        val now = System.currentTimeMillis()
        if (now < sendLatchUntilMs) return@send
        if (session == null || !canWriteMessages) return@send
        val text = messageInput.trim()
        if (text.isBlank()) return@send
        sendLatchUntilMs = now + 350L
        collabViewModel.sendMessage(text)
        messageInput = ""
        collabViewModel.setTypingState("", 0)
    }

    val runAiRequest: (AiRetryRequest) -> Unit = aiRequest@{ request ->
        if (!canUseAi || session == null || multiAgentIsRunning) return@aiRequest
        chatViewModel.multiAgentViewModel.dismissError()
        localAiStatus = "KI-Team startet ..."
        scope.launch {
            collabViewModel.sendMessage(
                "KI-Team gestartet (${request.personas.size} Agenten).",
                isAi = true
            )
            chatViewModel.multiAgentViewModel.runCollaboration(
                userPrompt = request.prompt,
                personas = request.personas
            )
            val result = chatViewModel.multiAgentViewModel.collaborationResult.value
            if (result != null && result.synthesis.isNotBlank()) {
                localAiStatus = "KI-Team Antwort erhalten."
                lastAiFailedRequest = null
                collabViewModel.sendMessage("KI-Hilfe zu: ${request.prompt}", isAi = true)
                collabViewModel.sendMessage(result.synthesis, isAi = true)
            } else {
                val reason = chatViewModel.multiAgentViewModel.errorMessage.value.orEmpty()
                localAiStatus = if (reason.isNotBlank()) {
                    "KI-Team Fehler: $reason"
                } else {
                    "KI-Team konnte keine Antwort erzeugen."
                }
                lastAiFailedRequest = request
                collabViewModel.sendMessage(
                    if (reason.isNotBlank()) {
                        "KI-Team Fehler: $reason"
                    } else {
                        "KI-Team konnte noch keine Antwort erzeugen. Bitte erneut versuchen."
                    },
                    isAi = true
                )
            }
        }
    }

    val runAutomationTemplate: (AutomationTemplate) -> Unit = { template ->
        val messageContext = messages.takeLast(8).joinToString("\n") {
            "${if (it.isAi) "AI" else it.authorName}: ${it.text.take(220)}"
        }
        val workspaceContext = workspaceDraft.take(2400)
        val prompt = buildString {
            appendLine(template.prompt)
            if (workspaceContext.isNotBlank()) {
                appendLine()
                appendLine("Workspace:")
                appendLine(workspaceContext)
            }
            if (messageContext.isNotBlank()) {
                appendLine()
                appendLine("Letzte Nachrichten:")
                appendLine(messageContext)
            }
        }.trim()
        val personasForRun = if (selectedAgents.isEmpty()) {
            listOf(ChatViewModel.Persona.DEVELOPER, ChatViewModel.Persona.TEACHER)
        } else {
            selectedAgents.toList()
        }
        runAiRequest(AiRetryRequest(prompt, personasForRun))
    }

    DisposableEffect(session?.id) {
        if (session != null) {
            collabViewModel.setOwnPresence(active = true)
        }
        onDispose {
            workspaceSyncJob?.cancel()
            workspaceSyncJob = null
            collabViewModel.setOwnPresence(active = false)
        }
    }

    LaunchedEffect(session?.id) {
        if (session != null) showSetup = false
    }

    LaunchedEffect(sessionKey) {
        workspaceDraft = prefs.getString("$KEY_WORKSPACE_DRAFT_PREFIX$sessionKey", "").orEmpty()
        if (workspaceState.text.isNotBlank()) {
            workspaceDraft = workspaceState.text
        }
        localWorkspaceDirty = false
        remoteWorkspaceAheadMessage = null
        lastWorkspaceRevisionSeen = workspaceState.revision
        workspaceSyncJob?.cancel()
        workspaceSyncJob = null
    }

    LaunchedEffect(workspaceState.text, workspaceState.updatedAt, sessionKey) {
        val remoteRevision = workspaceState.revision
        if (remoteRevision <= lastWorkspaceRevisionSeen) return@LaunchedEffect
        val hasDifferentLocalDraft = workspaceDraft.trim() != workspaceState.text.trim()
        if (hasDifferentLocalDraft && localWorkspaceDirty && canEditWorkspace) {
            remoteWorkspaceAheadMessage =
                "Remote-Update erkannt (Rev $remoteRevision von ${workspaceState.updatedBy.ifBlank { "Unbekannt" }}). Lokaler Entwurf bleibt vorerst erhalten."
        } else {
            workspaceDraft = workspaceState.text
            localWorkspaceDirty = false
            remoteWorkspaceAheadMessage = null
        }
        lastWorkspaceRevisionSeen = remoteRevision
    }

    LaunchedEffect(messageInput) {
        prefs.edit().putString(KEY_MESSAGE_DRAFT, messageInput).apply()
        if (session != null && canWriteMessages) {
            collabViewModel.setTypingState(messageInput, messageInput.length)
        }
    }

    LaunchedEffect(workspaceDraft, sessionKey) {
        prefs.edit().putString("$KEY_WORKSPACE_DRAFT_PREFIX$sessionKey", workspaceDraft).apply()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF101B2F), Color(0xFF1A2C46), Color(0xFF16263A))
                )
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = listHorizontalPadding, vertical = listVerticalPadding),
                contentPadding = PaddingValues(bottom = if (compactLayout) 4.dp else 8.dp),
                verticalArrangement = Arrangement.spacedBy(listItemSpacing)
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Zurück",
                                tint = Color.White
                            )
                        }
                        Text(
                            "Realtime Collaboration",
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Session Setup", color = Color.White, fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = { showSetup = !showSetup }) {
                            Text(if (showSetup) "Ausblenden" else "Einblenden")
                        }
                    }
                }

                if (showSetup || session == null) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White.copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = sessionTitle,
                                    onValueChange = { sessionTitle = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Session-Name") },
                                    singleLine = true
                                )
                                Button(onClick = { collabViewModel.createSession(sessionTitle) }, enabled = !isLoading) {
                                    Text("Neue Session erstellen")
                                }
                                OutlinedTextField(
                                    value = joinCode,
                                    onValueChange = { joinCode = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Session-ID oder Invite-Link") },
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = inviteCodeInput,
                                    onValueChange = { inviteCodeInput = it.uppercase() },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Invite-Code (optional)") },
                                    singleLine = true
                                )
                                Button(onClick = { collabViewModel.joinSession(joinCode, inviteCodeInput) }, enabled = !isLoading) {
                                    Text("Session beitreten")
                                }
                            }
                        }
                    }
                }

                session?.let {
                    item {
                        val inviteLink = "bamachat://collab?session=${it.id}&invite=${it.inviteCode}"
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White.copy(alpha = 0.1f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Aktive Session: ${it.id} • ${it.title}", color = Color.White)
                                Text("Meine Rolle: ${myRole.name}", color = Color(0xFF9BE7FF))
                                Text(
                                    "Teilnehmer: ${it.participants.size} • Online: ${presences.count { p -> p.active }}",
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                                Text("Sync: $syncStatus", color = Color(0xFFBFD8FF))
                                Text("Auth: $authModeLabel", color = Color(0xFFBFD8FF))
                                if (isLocalOnlyMode) {
                                    Text(
                                        "Hinweis: Lokaler Modus ist nicht geräteübergreifend.",
                                        color = Color(0xFFFFD9A8),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Text("Invite-Code: ${it.inviteCode.ifBlank { "kein Code" }}", color = Color.White)
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color.White.copy(alpha = 0.08f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text("Session-Policies", color = Color.White, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            "KI: ${if (it.aiEnabled) "an" else "aus"} • Editor KI: ${if (it.editorCanUseAi) "an" else "aus"} • Editor Chat: ${if (it.editorCanSendMessages) "an" else "aus"} • Editor Workspace: ${if (it.editorCanEditWorkspace) "an" else "aus"}",
                                            color = Color.White.copy(alpha = 0.82f),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        if (isOwner) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("KI im Workspace aktiv", color = Color.White)
                                                Switch(
                                                    checked = it.aiEnabled,
                                                    onCheckedChange = { checked ->
                                                        collabViewModel.updateSessionPolicy(aiEnabled = checked)
                                                    }
                                                )
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("Editoren dürfen KI starten", color = Color.White)
                                                Switch(
                                                    checked = it.editorCanUseAi,
                                                    onCheckedChange = { checked ->
                                                        collabViewModel.updateSessionPolicy(editorCanUseAi = checked)
                                                    },
                                                    enabled = it.aiEnabled
                                                )
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("Editoren dürfen Nachrichten senden", color = Color.White)
                                                Switch(
                                                    checked = it.editorCanSendMessages,
                                                    onCheckedChange = { checked ->
                                                        collabViewModel.updateSessionPolicy(editorCanSendMessages = checked)
                                                    }
                                                )
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("Editoren dürfen Workspace bearbeiten", color = Color.White)
                                                Switch(
                                                    checked = it.editorCanEditWorkspace,
                                                    onCheckedChange = { checked ->
                                                        collabViewModel.updateSessionPolicy(editorCanEditWorkspace = checked)
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                                CompactTextActionRow(
                                    actions = listOfNotNull(
                                        CompactTextAction(
                                            label = "Link kopieren",
                                            onClick = { clipboard.setText(AnnotatedString(inviteLink)) }
                                        ),
                                        if (isOwner) {
                                            CompactTextAction(
                                                label = "Code neu",
                                                onClick = { collabViewModel.rotateInviteCode() }
                                            )
                                        } else {
                                            null
                                        }
                                    )
                                )
                                CompactTextActionRow(
                                    actions = listOf(
                                        CompactTextAction(
                                            label = "Reconnect",
                                            onClick = { collabViewModel.reconnectNow() }
                                        ),
                                        CompactTextAction(
                                            label = "Verlassen",
                                            onClick = { collabViewModel.leaveSession() }
                                        )
                                    )
                                )
                            }
                        }
                    }
                }

                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.08f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Debug", color = Color.White, fontWeight = FontWeight.SemiBold)
                                TextButton(onClick = { showDebug = !showDebug }) {
                                    Text(if (showDebug) "Ausblenden" else "Einblenden")
                                }
                            }
                            CompactTextActionRow(
                                actions = listOf(
                                    CompactTextAction(
                                        label = "Debug neu",
                                        onClick = { collabViewModel.refreshDebugInfo() }
                                    ),
                                    CompactTextAction(
                                        label = "Reconnect",
                                        onClick = { collabViewModel.reconnectNow() }
                                    ),
                                    CompactTextAction(
                                        label = "Queue senden",
                                        onClick = { collabViewModel.retryFailedOutboundNow() }
                                    )
                                )
                            )
                            if (showDebug) {
                                Text("Provider: $providerLabel", color = Color.White)
                                Text("Model: $modelLabel", color = Color.White)
                                Text("Firebase: $firebaseStatus", color = Color.White)
                                Text("Auth: $authModeLabel", color = Color.White)
                                Text("Sync: $syncStatus", color = Color.White)
                                if (!lastDetailedError.isNullOrBlank()) {
                                    Text("Last Error: ${lastDetailedError.orEmpty()}", color = Color(0xFFFFD9A8))
                                }
                            }
                        }
                    }
                }

                if (!errorMessage.isNullOrBlank()) {
                    item {
                        Text(errorMessage.orEmpty(), color = Color(0xFFFFB4AB))
                    }
                }

                item {
                    Text("Agenten für KI-Hilfe wählen:", color = Color.White, fontWeight = FontWeight.SemiBold)
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            ChatViewModel.Persona.DEVELOPER,
                            ChatViewModel.Persona.TEACHER,
                            ChatViewModel.Persona.THERAPIST
                        ).forEach { persona ->
                            val selected = selectedAgents.contains(persona)
                            AssistChip(
                                onClick = {
                                    if (selected) selectedAgents.remove(persona) else selectedAgents.add(persona)
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = if (selected) Color(0xFF3D7DFF).copy(alpha = 0.38f) else Color.White.copy(alpha = 0.08f),
                                    labelColor = Color.White
                                ),
                                label = { Text("${persona.emoji} ${persona.displayName}") }
                            )
                        }
                    }
                }

                item {
                    Text(
                        text = "Ausgewählt: ${selectedAgents.size} Agenten",
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (messagePrompt.isBlank() && workspacePrompt.isNotBlank()) {
                        Text(
                            text = "Hinweis: KI-Team nutzt aktuell den Workspace-Text als Prompt.",
                            color = Color(0xFFBFD8FF),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                if (automationQuickActionsEnabled) {
                    item {
                        Text(
                            text = "Automationen:",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AutomationCatalog.templates.forEach { template ->
                                AssistChip(
                                    onClick = {
                                        if (!canUseAi || session == null || multiAgentIsRunning) return@AssistChip
                                        if (confirmToolActions) {
                                            pendingToolTemplate = template
                                        } else {
                                            runAutomationTemplate(template)
                                        }
                                    },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = Color(0xFF2A4A7A).copy(alpha = 0.35f),
                                        labelColor = Color.White
                                    ),
                                    label = { Text(template.title) }
                                )
                            }
                        }
                    }
                }

                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(workspaceCardHeight),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(if (compactLayout) 8.dp else 10.dp)
                        ) {
                            Text("Gemeinsamer Workspace", color = Color.White, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            OutlinedTextField(
                                value = workspaceDraft,
                                onValueChange = {
                                    workspaceDraft = it
                                    localWorkspaceDirty = true
                                    remoteWorkspaceAheadMessage = null
                                    workspaceSyncJob?.cancel()
                                    workspaceSyncJob = scope.launch {
                                        delay(320)
                                        if (session != null && canEditWorkspace) {
                                            collabViewModel.updateWorkspaceText(workspaceDraft)
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(workspaceEditorHeight),
                                label = { Text("Live-Notiz (synchron auf allen Geräten)") },
                                enabled = canEditWorkspace && session != null
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Lokal: $localWorkspaceLines Zeilen • Remote: $remoteWorkspaceLines",
                                    color = Color.White.copy(alpha = 0.72f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "$workspaceCharCount/$WORKSPACE_SOFT_CHAR_LIMIT Zeichen",
                                    color = if (workspaceOverSoftLimit) Color(0xFFFFC8C8) else Color.White.copy(alpha = 0.72f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            if (workspaceOverSoftLimit) {
                                Text(
                                    text = "Hinweis: Sehr lange Notizen koennen die Sync-Geschwindigkeit reduzieren.",
                                    color = Color(0xFFFFD9A8),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            if (workspaceState.updatedAt > 0L) {
                                Text(
                                    "Zuletzt geändert von ${workspaceState.updatedBy.ifBlank { "Unbekannt" }} • Rev ${workspaceState.revision}",
                                    color = Color.White.copy(alpha = 0.75f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            if (!remoteWorkspaceAheadMessage.isNullOrBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFF2A3E5C).copy(alpha = 0.42f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            remoteWorkspaceAheadMessage.orEmpty(),
                                            color = Color(0xFFBFD8FF),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Text(
                                            workspaceDiffPreview,
                                            color = Color.White.copy(alpha = 0.82f),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        WorkspaceInlineDiffPanel(
                                            diffData = workspaceDiffData,
                                            title = "Inline-Diff (lokal vs remote)",
                                            compact = compactLayout
                                        )
                                        CompactTextActionRow(
                                            actions = listOf(
                                                CompactTextAction(
                                                    label = "Remote laden",
                                                    onClick = {
                                                        workspaceDraft = workspaceState.text
                                                        localWorkspaceDirty = false
                                                        remoteWorkspaceAheadMessage = null
                                                    }
                                                ),
                                                CompactTextAction(
                                                    label = "Smart Merge",
                                                    onClick = {
                                                        val merged = collabViewModel.mergeWorkspaceTexts(workspaceDraft)
                                                        workspaceDraft = merged
                                                        localWorkspaceDirty = false
                                                        remoteWorkspaceAheadMessage = null
                                                        collabViewModel.forceWorkspaceOverwrite(merged)
                                                    }
                                                )
                                            )
                                        )
                                    }
                                }
                            }
                            if (!workspaceConflictMessage.isNullOrBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFF5C2A2A).copy(alpha = 0.38f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            workspaceConflictMessage.orEmpty(),
                                            color = Color(0xFFFFD9A8),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Text(
                                            workspaceDiffPreview,
                                            color = Color.White.copy(alpha = 0.82f),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        WorkspaceInlineDiffPanel(
                                            diffData = workspaceDiffData,
                                            title = "Konflikt-Diff",
                                            compact = compactLayout
                                        )
                                        CompactTextActionRow(
                                            actions = listOf(
                                                CompactTextAction(
                                                    label = "Remote übernehmen",
                                                    onClick = {
                                                        workspaceDraft = workspaceState.text
                                                        localWorkspaceDirty = false
                                                        remoteWorkspaceAheadMessage = null
                                                        collabViewModel.clearWorkspaceConflict()
                                                    }
                                                ),
                                                CompactTextAction(
                                                    label = "Merge speichern",
                                                    onClick = {
                                                        val merged = collabViewModel.mergeWorkspaceTexts(workspaceDraft)
                                                        workspaceDraft = merged
                                                        localWorkspaceDirty = false
                                                        remoteWorkspaceAheadMessage = null
                                                        collabViewModel.forceWorkspaceOverwrite(merged)
                                                        collabViewModel.clearWorkspaceConflict()
                                                    }
                                                )
                                            )
                                        )
                                        CompactTextActionRow(
                                            actions = listOf(
                                                CompactTextAction(
                                                    label = "Lokal erzwingen",
                                                    onClick = {
                                                        localWorkspaceDirty = false
                                                        remoteWorkspaceAheadMessage = null
                                                        collabViewModel.forceWorkspaceOverwrite(workspaceDraft)
                                                        collabViewModel.clearWorkspaceConflict()
                                                    }
                                                )
                                            )
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            if (session != null) {
                                Text("Presence:", color = Color.White, fontWeight = FontWeight.SemiBold)
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(if (compactLayout) 72.dp else 84.dp)
                                ) {
                                    items(presences, key = { it.userId }) { presence ->
                                        val roleLabel = collabViewModel.roleLabelFor(presence.userId)
                                        val typingHint = if (presence.typing && presence.userId != myUserId) {
                                            " tippt: ${presence.draftPreview}"
                                        } else {
                                            ""
                                        }
                                        Text(
                                            "${if (presence.active) "🟢" else "⚪"} ${presence.displayName.ifBlank { presence.userId.take(6) }} [$roleLabel]$typingHint",
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(if (compactLayout) 6.dp else 8.dp)
                            ) {
                                items(messages, key = { it.id }) { msg ->
                                    val mine = msg.authorId == myUserId
                                    val status = if (mine) messageDeliveryStatus[msg.id] else null
                                    Surface(
                                        color = if (msg.isAi) Color(0xFF3D7DFF).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.18f),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text(
                                                text = "${if (msg.isAi) "🤖" else "👤"} ${msg.authorName}",
                                                color = Color.White,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            Text(msg.text, color = Color.White)
                                            if (status != null) {
                                                Spacer(Modifier.height(4.dp))
                                                Text(
                                                    text = when (status) {
                                                        CollabViewModel.MessageDeliveryStatus.SENDING -> "Status: sendet ..."
                                                        CollabViewModel.MessageDeliveryStatus.SENT -> "Status: gesendet"
                                                        CollabViewModel.MessageDeliveryStatus.FAILED -> "Status: fehlgeschlagen"
                                                    },
                                                    color = Color.White.copy(alpha = 0.8f),
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                                if (status == CollabViewModel.MessageDeliveryStatus.FAILED) {
                                                    TextButton(onClick = { collabViewModel.retryMessage(msg.id) }) {
                                                        Text("Erneut senden")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = listHorizontalPadding, vertical = if (compactLayout) 8.dp else 10.dp)
                    .navigationBarsPadding()
                    .imePadding(),
                shape = RoundedCornerShape(footerCornerRadius),
                color = Color(0xFF1D2C43).copy(alpha = 0.94f),
                shadowElevation = 18.dp,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(if (compactLayout) 8.dp else 10.dp),
                    verticalArrangement = Arrangement.spacedBy(if (compactLayout) 6.dp else 8.dp)
                ) {
                    OutlinedTextField(
                        value = messageInput,
                        onValueChange = { messageInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Nachricht an Session") },
                        enabled = canWriteMessages && session != null,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { sendSessionMessage() })
                    )
                    if (!canWriteMessages && session != null) {
                        Text("Viewer-Modus: Lesen erlaubt, Schreiben gesperrt.", color = Color(0xFFFFD9A8))
                    }
                    if (!canUseAi && session != null) {
                        Text("KI-Team ist laut Session-Policy für deine Rolle deaktiviert.", color = Color(0xFFFFD9A8))
                    }
                    if (multiAgentIsRunning) {
                        Text("KI-Team arbeitet ...", color = Color(0xFF9BE7FF))
                    }
                    if (!localAiStatus.isNullOrBlank()) {
                        Text(localAiStatus.orEmpty(), color = Color(0xFFBFD8FF))
                    }
                    if (!multiAgentError.isNullOrBlank()) {
                        Text(multiAgentError.orEmpty(), color = Color(0xFFFFD9A8))
                    }
                    CompactTextActionRow(
                        actions = listOf(
                            CompactTextAction(
                                label = "Senden",
                                onClick = sendSessionMessage,
                                enabled = messagePrompt.isNotBlank() && session != null && canWriteMessages
                            ),
                            CompactTextAction(
                                label = if (multiAgentIsRunning) "KI läuft..." else "KI Team-Antwort",
                                onClick = aiAction@{
                                    if (aiPrompt.isBlank()) return@aiAction
                                    if (messagePrompt.isNotBlank()) {
                                        messageInput = ""
                                        collabViewModel.setTypingState("", 0)
                                    }
                                    val personasForRun = if (selectedAgents.isEmpty()) {
                                        listOf(ChatViewModel.Persona.DEVELOPER, ChatViewModel.Persona.TEACHER)
                                    } else {
                                        selectedAgents.toList()
                                    }
                                    runAiRequest(AiRetryRequest(aiPrompt, personasForRun))
                                },
                                color = Color(0xFF9BE7FF),
                                enabled = aiPrompt.isNotBlank() && session != null && canUseAi && !multiAgentIsRunning
                            )
                        )
                    )
                    if (lastAiFailedRequest != null && !multiAgentIsRunning) {
                        CompactTextActionRow(
                            actions = listOf(
                                CompactTextAction(
                                    label = "Wiederholen",
                                    onClick = { runAiRequest(lastAiFailedRequest!!) },
                                    color = Color(0xFFFFD9A8)
                                )
                            )
                        )
                    }
                }
            }
        }
    }

    val templateToRun = pendingToolTemplate
    if (templateToRun != null) {
        AlertDialog(
            onDismissRequest = { pendingToolTemplate = null },
            title = { Text("Automation ausführen?") },
            text = {
                Text("${templateToRun.title}\n\n${templateToRun.description}")
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingToolTemplate = null
                    runAutomationTemplate(templateToRun)
                }) {
                    Text("Starten")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingToolTemplate = null }) {
                    Text("Abbrechen")
                }
            }
        )
    }
}

@Composable
private fun WorkspaceInlineDiffPanel(
    diffData: CollabViewModel.WorkspaceDiffData,
    title: String,
    compact: Boolean = false
) {
    if (diffData.identical || (diffData.localOnly.isEmpty() && diffData.remoteOnly.isEmpty())) return

    val collapsedPreviewCount = if (compact) 2 else 4
    val expandedPreviewCount = if (compact) 6 else 10
    val canExpand =
        diffData.localOnly.size > collapsedPreviewCount || diffData.remoteOnly.size > collapsedPreviewCount
    var expanded by rememberSaveable(title, diffData.localOnly.size, diffData.remoteOnly.size) {
        mutableStateOf(false)
    }
    val previewCount = if (expanded) expandedPreviewCount else collapsedPreviewCount
    val shownLocalLines = diffData.localOnly.take(previewCount)
    val shownRemoteLines = diffData.remoteOnly.take(previewCount)
    val hiddenLocal = (diffData.localOnly.size - shownLocalLines.size).coerceAtLeast(0)
    val hiddenRemote = (diffData.remoteOnly.size - shownRemoteLines.size).coerceAtLeast(0)

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color.White.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = if (compact) 6.dp else 8.dp, vertical = if (compact) 5.dp else 6.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "$title  (+${diffData.localOnly.size} / -${diffData.remoteOnly.size} / =${diffData.sharedCount})",
                    color = Color.White.copy(alpha = 0.88f),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
                if (canExpand) {
                    TextButton(onClick = { expanded = !expanded }) {
                        Text(if (expanded) "Weniger" else "Mehr")
                    }
                }
            }
            if (shownLocalLines.isNotEmpty()) {
                Text("+ Lokal:", color = Color(0xFFB8F5CC), style = MaterialTheme.typography.bodySmall)
                shownLocalLines.forEach { line ->
                    WorkspaceDiffLine(
                        prefix = "+",
                        text = line,
                        containerColor = Color(0xFF1E4A33).copy(alpha = 0.46f),
                        textColor = Color(0xFFD6FFE4)
                    )
                }
                if (hiddenLocal > 0) {
                    Text(
                        "... +$hiddenLocal weitere lokale Zeilen",
                        color = Color(0xFFB8F5CC),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            if (shownRemoteLines.isNotEmpty()) {
                Text("- Remote:", color = Color(0xFFFFC8C8), style = MaterialTheme.typography.bodySmall)
                shownRemoteLines.forEach { line ->
                    WorkspaceDiffLine(
                        prefix = "-",
                        text = line,
                        containerColor = Color(0xFF5A2B2B).copy(alpha = 0.46f),
                        textColor = Color(0xFFFFE0E0)
                    )
                }
                if (hiddenRemote > 0) {
                    Text(
                        "... +$hiddenRemote weitere Remote-Zeilen",
                        color = Color(0xFFFFC8C8),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkspaceDiffLine(
    prefix: String,
    text: String,
    containerColor: Color,
    textColor: Color
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "$prefix ${text.take(220)}",
            color = textColor,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
        )
    }
}
