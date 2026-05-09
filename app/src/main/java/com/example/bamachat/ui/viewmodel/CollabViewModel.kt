package com.example.bamachat.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bamachat.data.model.CollabMessage
import com.example.bamachat.data.model.CollabPresence
import com.example.bamachat.data.model.CollabSession
import com.example.bamachat.util.AppTelemetry
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

class CollabViewModel(application: Application) : AndroidViewModel(application) {
    enum class SessionRole { OWNER, EDITOR, VIEWER }

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private var messageListener: ListenerRegistration? = null
    private var sessionListener: ListenerRegistration? = null
    private var presenceListener: ListenerRegistration? = null

    private val _currentSession = MutableStateFlow<CollabSession?>(null)
    val currentSession: StateFlow<CollabSession?> = _currentSession.asStateFlow()

    private val _messages = MutableStateFlow<List<CollabMessage>>(emptyList())
    val messages: StateFlow<List<CollabMessage>> = _messages.asStateFlow()

    private val _presences = MutableStateFlow<List<CollabPresence>>(emptyList())
    val presences: StateFlow<List<CollabPresence>> = _presences.asStateFlow()

    private val _currentUserId = MutableStateFlow(auth.currentUser?.uid.orEmpty())
    val currentUserId: StateFlow<String> = _currentUserId.asStateFlow()

    private val _myRole = MutableStateFlow(SessionRole.VIEWER)
    val myRole: StateFlow<SessionRole> = _myRole.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun clearError() {
        _errorMessage.value = null
    }

    fun createSession(title: String) {
        val user = auth.currentUser
        if (user == null) {
            _errorMessage.value = "Realtime-Collab benötigt Anmeldung (kein Gastmodus)."
            return
        }
        _currentUserId.value = user.uid
        val cleanTitle = title.trim().ifBlank { "Collab Session" }
        val sessionId = UUID.randomUUID().toString().take(8).uppercase()
        val inviteCode = UUID.randomUUID().toString().replace("-", "").take(8).uppercase()

        viewModelScope.launch {
            _isLoading.value = true
            runCatching {
                val session = CollabSession(
                    id = sessionId,
                    title = cleanTitle,
                    ownerId = user.uid,
                    participants = listOf(user.uid),
                    participantRoles = mapOf(user.uid to SessionRole.OWNER.name),
                    inviteCode = inviteCode,
                    createdAt = System.currentTimeMillis()
                )
                firestore.collection("collab_sessions")
                    .document(sessionId)
                    .set(session)
                    .await()

                bindSession(sessionId)
                setOwnPresence(active = true)
                AppTelemetry.logEvent("collab_session_created")
            }.onFailure {
                AppTelemetry.logError("collab_create", it)
                _errorMessage.value = "Session konnte nicht erstellt werden."
            }
            _isLoading.value = false
        }
    }

    fun joinSession(sessionIdInput: String, inviteCodeInput: String = "") {
        val user = auth.currentUser
        if (user == null) {
            _errorMessage.value = "Realtime-Collab benötigt Anmeldung (kein Gastmodus)."
            return
        }
        _currentUserId.value = user.uid
        val joinTarget = parseJoinInput(sessionIdInput, inviteCodeInput)
        if (joinTarget == null || joinTarget.sessionId.isBlank()) {
            _errorMessage.value = "Bitte Session-ID eingeben."
            return
        }
        val sessionId = joinTarget.sessionId
        val inviteCode = joinTarget.inviteCode

        viewModelScope.launch {
            _isLoading.value = true
            runCatching {
                val docRef = firestore.collection("collab_sessions").document(sessionId)
                val snapshot = docRef.get().await()
                val existing = snapshot.toObject(CollabSession::class.java)
                if (existing == null) {
                    _errorMessage.value = "Session nicht gefunden."
                    return@runCatching
                }

                if (inviteCode.isNotBlank() &&
                    existing.inviteCode.isNotBlank() &&
                    inviteCode.trim().uppercase() != existing.inviteCode.uppercase()
                ) {
                    _errorMessage.value = "Invite-Code ist ungültig."
                    return@runCatching
                }

                if (!existing.participants.contains(user.uid)) {
                    val updatedParticipants = (existing.participants + user.uid).distinct()
                    val updatedRoles = existing.participantRoles.toMutableMap().apply {
                        put(user.uid, SessionRole.EDITOR.name)
                    }
                    docRef.update(
                        mapOf(
                            "participants" to updatedParticipants,
                            "participantRoles" to updatedRoles
                        )
                    ).await()
                }
                bindSession(sessionId)
                setOwnPresence(active = true)
                AppTelemetry.logEvent("collab_session_joined")
            }.onFailure {
                AppTelemetry.logError("collab_join", it)
                _errorMessage.value = "Session-Beitritt fehlgeschlagen."
            }
            _isLoading.value = false
        }
    }

    private data class JoinTarget(
        val sessionId: String,
        val inviteCode: String
    )

    private fun parseJoinInput(sessionIdInput: String, inviteCodeInput: String): JoinTarget? {
        val raw = sessionIdInput.trim()
        if (raw.isBlank()) return null
        if (raw.contains("session=") && (raw.startsWith("bamachat://") || raw.startsWith("http"))) {
            return runCatching {
                val uri = Uri.parse(raw)
                val session = uri.getQueryParameter("session").orEmpty().trim().uppercase()
                val invite = uri.getQueryParameter("invite").orEmpty().trim().uppercase()
                JoinTarget(
                    sessionId = session,
                    inviteCode = if (inviteCodeInput.isBlank()) invite else inviteCodeInput.trim().uppercase()
                )
            }.getOrNull()
        }
        return JoinTarget(
            sessionId = raw.uppercase(),
            inviteCode = inviteCodeInput.trim().uppercase()
        )
    }

    fun sendMessage(text: String, isAi: Boolean = false) {
        val user = auth.currentUser
        val session = _currentSession.value
        if (user == null || session == null) return
        if (!session.participants.contains(user.uid)) {
            _errorMessage.value = "Du bist kein Teilnehmer dieser Session."
            return
        }
        val role = roleOf(session, user.uid)
        if (!isAi && role == SessionRole.VIEWER) {
            _errorMessage.value = "Viewer dürfen nicht schreiben."
            return
        }

        val cleanText = text.trim()
        if (cleanText.isBlank()) return

        viewModelScope.launch {
            runCatching {
                val messageId = UUID.randomUUID().toString()
                val msg = CollabMessage(
                    id = messageId,
                    authorId = user.uid,
                    authorName = if (isAi) "Bama AI" else (user.displayName ?: "User"),
                    text = cleanText,
                    timestamp = System.currentTimeMillis(),
                    isAi = isAi
                )
                firestore.collection("collab_sessions")
                    .document(session.id)
                    .collection("messages")
                    .document(messageId)
                    .set(msg)
                    .await()
            }.onFailure {
                AppTelemetry.logError("collab_send", it)
                _errorMessage.value = "Nachricht konnte nicht gesendet werden."
            }
        }
    }

    fun setParticipantRole(userId: String, role: SessionRole) {
        val current = _currentSession.value ?: return
        val me = auth.currentUser?.uid ?: return
        if (current.ownerId != me) {
            _errorMessage.value = "Nur der Owner darf Rollen ändern."
            return
        }
        if (!current.participants.contains(userId)) {
            _errorMessage.value = "Teilnehmer nicht gefunden."
            return
        }
        if (userId == me && role != SessionRole.OWNER) {
            _errorMessage.value = "Owner-Rolle kann nicht herabgestuft werden."
            return
        }

        viewModelScope.launch {
            runCatching {
                val updatedRoles = current.participantRoles.toMutableMap().apply {
                    put(userId, role.name)
                }
                firestore.collection("collab_sessions")
                    .document(current.id)
                    .update("participantRoles", updatedRoles)
                    .await()
                AppTelemetry.logEvent("collab_role_updated")
            }.onFailure {
                AppTelemetry.logError("collab_role_updated", it)
                _errorMessage.value = "Rolle konnte nicht geändert werden."
            }
        }
    }

    fun rotateInviteCode() {
        val current = _currentSession.value ?: return
        val me = auth.currentUser?.uid ?: return
        if (current.ownerId != me) {
            _errorMessage.value = "Nur der Owner darf Invite-Codes erneuern."
            return
        }
        val newCode = UUID.randomUUID().toString().replace("-", "").take(8).uppercase()
        viewModelScope.launch {
            runCatching {
                firestore.collection("collab_sessions")
                    .document(current.id)
                    .update("inviteCode", newCode)
                    .await()
                AppTelemetry.logEvent("collab_invite_rotated")
            }.onFailure {
                AppTelemetry.logError("collab_invite_rotated", it)
                _errorMessage.value = "Invite-Code konnte nicht erneuert werden."
            }
        }
    }

    fun removeParticipant(userId: String) {
        val current = _currentSession.value ?: return
        val me = auth.currentUser?.uid ?: return
        if (current.ownerId != me) {
            _errorMessage.value = "Nur der Owner darf Teilnehmer entfernen."
            return
        }
        if (userId == me) {
            _errorMessage.value = "Owner kann sich nicht selbst entfernen."
            return
        }

        viewModelScope.launch {
            runCatching {
                val updatedParticipants = current.participants.filterNot { it == userId }
                val updatedRoles = current.participantRoles.toMutableMap().apply {
                    remove(userId)
                }
                firestore.collection("collab_sessions")
                    .document(current.id)
                    .update(
                        mapOf(
                            "participants" to updatedParticipants,
                            "participantRoles" to updatedRoles
                        )
                    )
                    .await()
                firestore.collection("collab_sessions")
                    .document(current.id)
                    .collection("presence")
                    .document(userId)
                    .delete()
                    .await()
                AppTelemetry.logEvent("collab_remove_participant")
            }.onFailure {
                AppTelemetry.logError("collab_remove_participant", it)
                _errorMessage.value = "Teilnehmer konnte nicht entfernt werden."
            }
        }
    }

    fun leaveSession() {
        val session = _currentSession.value ?: return
        val user = auth.currentUser ?: return

        viewModelScope.launch {
            runCatching {
                val remaining = session.participants.filterNot { it == user.uid }
                val sessionRef = firestore.collection("collab_sessions").document(session.id)
                val updatedRoles = session.participantRoles.toMutableMap().apply {
                    remove(user.uid)
                }
                if (session.ownerId == user.uid) {
                    if (remaining.isEmpty()) {
                        sessionRef.delete().await()
                    } else {
                        val newOwner = remaining.first()
                        updatedRoles[newOwner] = SessionRole.OWNER.name
                        sessionRef.update(
                            mapOf(
                                "ownerId" to newOwner,
                                "participants" to remaining,
                                "participantRoles" to updatedRoles
                            )
                        ).await()
                    }
                } else {
                    sessionRef.update(
                        mapOf(
                            "participants" to remaining,
                            "participantRoles" to updatedRoles
                        )
                    ).await()
                }

                sessionRef.collection("presence").document(user.uid).delete().await()
                stopListeners()
                _currentSession.value = null
                _messages.value = emptyList()
                _presences.value = emptyList()
                _myRole.value = SessionRole.VIEWER
                AppTelemetry.logEvent("collab_left_session")
            }.onFailure {
                AppTelemetry.logError("collab_leave", it)
                _errorMessage.value = "Session konnte nicht verlassen werden."
            }
        }
    }

    fun setOwnPresence(active: Boolean) {
        val session = _currentSession.value ?: return
        val user = auth.currentUser ?: return
        val payload = CollabPresence(
            userId = user.uid,
            displayName = user.displayName ?: "User",
            active = active,
            lastSeenAt = System.currentTimeMillis()
        )
        viewModelScope.launch {
            runCatching {
                firestore.collection("collab_sessions")
                    .document(session.id)
                    .collection("presence")
                    .document(user.uid)
                    .set(payload)
                    .await()
            }
        }
    }

    fun roleLabelFor(userId: String): String {
        val session = _currentSession.value ?: return "VIEWER"
        return roleOf(session, userId).name
    }

    private fun bindSession(sessionId: String) {
        stopListeners()
        listenToSession(sessionId)
        listenToMessages(sessionId)
        listenToPresence(sessionId)
    }

    private fun listenToSession(sessionId: String) {
        sessionListener = firestore.collection("collab_sessions")
            .document(sessionId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _errorMessage.value = "Session-Listener Fehler."
                    return@addSnapshotListener
                }
                val session = snapshot?.toObject(CollabSession::class.java)
                _currentSession.value = session
                val me = auth.currentUser?.uid.orEmpty()
                _currentUserId.value = me
                _myRole.value = if (session == null) SessionRole.VIEWER else roleOf(session, me)
            }
    }

    private fun listenToMessages(sessionId: String) {
        messageListener = firestore.collection("collab_sessions")
            .document(sessionId)
            .collection("messages")
            .orderBy("timestamp")
            .addSnapshotListener { value, error ->
                if (error != null) {
                    _errorMessage.value = "Realtime-Listener Fehler."
                    return@addSnapshotListener
                }
                val messages = value?.documents.orEmpty()
                    .mapNotNull { it.toObject(CollabMessage::class.java) }
                _messages.value = messages
            }
    }

    private fun listenToPresence(sessionId: String) {
        presenceListener = firestore.collection("collab_sessions")
            .document(sessionId)
            .collection("presence")
            .addSnapshotListener { value, error ->
                if (error != null) {
                    _errorMessage.value = "Presence-Listener Fehler."
                    return@addSnapshotListener
                }
                val entries = value?.documents.orEmpty()
                    .mapNotNull { it.toObject(CollabPresence::class.java) }
                    .sortedByDescending { it.active }
                _presences.value = entries
            }
    }

    private fun roleOf(session: CollabSession, userId: String): SessionRole {
        if (session.ownerId == userId) return SessionRole.OWNER
        return when (session.participantRoles[userId]?.uppercase()) {
            SessionRole.OWNER.name -> SessionRole.OWNER
            SessionRole.EDITOR.name -> SessionRole.EDITOR
            else -> SessionRole.VIEWER
        }
    }

    private fun stopListeners() {
        messageListener?.remove()
        messageListener = null
        sessionListener?.remove()
        sessionListener = null
        presenceListener?.remove()
        presenceListener = null
    }

    override fun onCleared() {
        runCatching { setOwnPresence(active = false) }
        stopListeners()
        super.onCleared()
    }
}
