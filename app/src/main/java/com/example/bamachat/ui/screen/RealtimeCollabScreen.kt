package com.example.bamachat.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bamachat.ui.viewmodel.ChatViewModel
import com.example.bamachat.ui.viewmodel.CollabViewModel
import kotlinx.coroutines.launch

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
    val scope = rememberCoroutineScope()
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current

    var sessionTitle by remember { mutableStateOf("Meine Session") }
    var joinCode by remember { mutableStateOf("") }
    var inviteCodeInput by remember { mutableStateOf("") }
    var messageInput by remember { mutableStateOf("") }
    val selectedAgents = remember { mutableStateListOf<ChatViewModel.Persona>() }
    val isOwner = session?.ownerId == myUserId
    val canWrite = myRole != CollabViewModel.SessionRole.VIEWER

    DisposableEffect(session?.id) {
        if (session != null) {
            collabViewModel.setOwnPresence(active = true)
        }
        onDispose {
            collabViewModel.setOwnPresence(active = false)
        }
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück", tint = Color.White)
                }
                Text(
                    "Realtime Collaboration",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

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
                        onValueChange = { joinCode = it.uppercase() },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Session-Code oder Invite-Link") },
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
                    Text(
                        "Tipp: Du kannst auch direkt einen bamachat://collab-Link einfügen.",
                        color = Color.White.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (!errorMessage.isNullOrBlank()) {
                        Text(errorMessage.orEmpty(), color = Color(0xFFFFB4AB))
                    }
                    session?.let {
                        val inviteLink = "bamachat://collab?session=${it.id}&invite=${it.inviteCode}"
                        Text("Aktive Session: ${it.id} • ${it.title}", color = Color.White)
                        Text("Meine Rolle: ${myRole.name}", color = Color(0xFF9BE7FF))
                        Text(
                            "Teilnehmer: ${it.participants.size} • Online: ${presences.count { p -> p.active }}",
                            color = Color.White.copy(alpha = 0.85f)
                        )
                        Text("Invite-Code: ${it.inviteCode.ifBlank { "kein Code" }}", color = Color.White)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { clipboard.setText(AnnotatedString(inviteLink)) }) {
                                Text("Invite-Link kopieren")
                            }
                            if (isOwner) {
                                Button(onClick = { collabViewModel.rotateInviteCode() }) {
                                    Text("Code erneuern")
                                }
                            }
                        }
                        if (isOwner) {
                            Text("Owner-Modus aktiv", color = Color(0xFF9BE7FF))
                        }
                        Button(onClick = { collabViewModel.leaveSession() }) {
                            Text("Session verlassen")
                        }
                    }
                }
            }

            Text("Agenten für KI-Hilfe wählen:", color = Color.White, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        label = { Text("${persona.emoji} ${persona.displayName}") }
                    )
                }
            }

            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                    if (session != null) {
                        Text("Presence:", color = Color.White, fontWeight = FontWeight.SemiBold)
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(108.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            items(presences, key = { it.userId }) { presence ->
                                val roleLabel = collabViewModel.roleLabelFor(presence.userId)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = if (presence.active) "🟢" else "⚪",
                                        color = Color.White
                                    )
                                    Text(
                                        text = presence.displayName.ifBlank { presence.userId.take(6) },
                                        color = Color.White
                                    )
                                    Text("[$roleLabel]", color = Color(0xFFBFD8FF))
                                    if (isOwner && presence.userId != myUserId) {
                                        TextButton(
                                            onClick = { collabViewModel.setParticipantRole(presence.userId, CollabViewModel.SessionRole.EDITOR) }
                                        ) { Text("Editor") }
                                        TextButton(
                                            onClick = { collabViewModel.setParticipantRole(presence.userId, CollabViewModel.SessionRole.VIEWER) }
                                        ) { Text("Viewer") }
                                        Button(
                                            onClick = { collabViewModel.removeParticipant(presence.userId) },
                                            modifier = Modifier
                                                .width(110.dp)
                                                .height(34.dp)
                                        ) {
                                            Text("Entfernen")
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(messages, key = { it.id }) { msg ->
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
                                }
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = messageInput,
                onValueChange = { messageInput = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Nachricht an Session") },
                enabled = canWrite && session != null
            )
            if (!canWrite && session != null) {
                Text("Viewer-Modus: Lesen erlaubt, Schreiben gesperrt.", color = Color(0xFFFFD9A8))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        collabViewModel.sendMessage(messageInput)
                        messageInput = ""
                    },
                    modifier = Modifier.weight(1f),
                    enabled = messageInput.isNotBlank() && session != null && canWrite
                ) {
                    Text("Senden")
                }
                Button(
                    onClick = {
                        val prompt = messageInput
                        if (prompt.isBlank()) return@Button
                        scope.launch {
                            chatViewModel.multiAgentViewModel.runCollaboration(
                                userPrompt = prompt,
                                personas = selectedAgents.toList()
                            )
                            val result = chatViewModel.multiAgentViewModel.collaborationResult.value
                            if (result != null) {
                                collabViewModel.sendMessage("KI-Hilfe zu: $prompt", isAi = false)
                                collabViewModel.sendMessage(result.synthesis, isAi = true)
                            }
                            messageInput = ""
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = messageInput.isNotBlank() && session != null && canWrite
                ) {
                    Text("KI Team-Antwort")
                }
            }
        }
    }
}
