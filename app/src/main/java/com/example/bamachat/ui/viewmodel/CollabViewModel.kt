package com.example.bamachat.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bamachat.R
import com.example.bamachat.data.model.CollabMessage
import com.example.bamachat.data.model.CollabPresence
import com.example.bamachat.data.model.CollabSession
import com.example.bamachat.data.model.CollabWorkspaceState
import com.example.bamachat.util.AppTelemetry
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.gson.Gson
import android.net.Uri
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID
import java.security.SecureRandom
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

@HiltViewModel
class CollabViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {
    enum class SessionRole { OWNER, EDITOR, VIEWER }
    enum class MessageDeliveryStatus { SENDING, SENT, FAILED }

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(getApplication())
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            getApplication(),
            "collab_settings_encrypted",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private var messageListener: ListenerRegistration? = null
    private var sessionListener: ListenerRegistration? = null
    private var presenceListener: ListenerRegistration? = null
    private var workspaceListener: ListenerRegistration? = null

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

    private val _workspaceState = MutableStateFlow(CollabWorkspaceState())
    val workspaceState: StateFlow<CollabWorkspaceState> = _workspaceState.asStateFlow()

    private val _syncStatus = MutableStateFlow(getString(R.string.collab_sync_status_disconnected))
    val syncStatus: StateFlow<String> = _syncStatus.asStateFlow()

    private val _authModeLabel = MutableStateFlow("unbekannt")
    val authModeLabel: StateFlow<String> = _authModeLabel.asStateFlow()

    private val _isLocalOnlyMode = MutableStateFlow(false)
    val isLocalOnlyMode: StateFlow<Boolean> = _isLocalOnlyMode.asStateFlow()

    private val _firebaseStatus = MutableStateFlow("unbekannt")
    val firebaseStatus: StateFlow<String> = _firebaseStatus.asStateFlow()

    private val _providerLabel = MutableStateFlow("OpenRouter")
    val providerLabel: StateFlow<String> = _providerLabel.asStateFlow()

    private val _modelLabel = MutableStateFlow("google/gemma-3-27b-it:free")
    val modelLabel: StateFlow<String> = _modelLabel.asStateFlow()

    private val _lastDetailedError = MutableStateFlow<String?>(null)
    val lastDetailedError: StateFlow<String?> = _lastDetailedError.asStateFlow()

    private val _messageDeliveryStatus = MutableStateFlow<Map<String, MessageDeliveryStatus>>(emptyMap())
    val messageDeliveryStatus: StateFlow<Map<String, MessageDeliveryStatus>> = _messageDeliveryStatus.asStateFlow()
    private val _canWriteMessages = MutableStateFlow(false)
    val canWriteMessages: StateFlow<Boolean> = _canWriteMessages.asStateFlow()
    private val _canEditWorkspace = MutableStateFlow(false)
    val canEditWorkspace: StateFlow<Boolean> = _canEditWorkspace.asStateFlow()
    private val _canUseAi = MutableStateFlow(false)
    val canUseAi: StateFlow<Boolean> = _canUseAi.asStateFlow()
    private val _workspaceConflictMessage = MutableStateFlow<String?>(null)
    val workspaceConflictMessage: StateFlow<String?> = _workspaceConflictMessage.asStateFlow()

    private val _hasMoreMessages = MutableStateFlow(false)
    val hasMoreMessages: StateFlow<Boolean> = _hasMoreMessages.asStateFlow()

    private val _privacyConsentShown = MutableStateFlow(false)
    val privacyConsentShown: StateFlow<Boolean> = _privacyConsentShown.asStateFlow()

    private data class EffectiveUser(
        val uid: String,
        val displayName: String,
        val localDevMode: Boolean
    )

    private class WorkspaceConflictException(
        val remoteUser: String,
        val remoteRevision: Long
    ) : RuntimeException("workspace_conflict")

    data class WorkspaceDiffData(
        val identical: Boolean = true,
        val sharedCount: Int = 0,
        val localOnly: List<String> = emptyList(),
        val remoteOnly: List<String> = emptyList()
    )

    companion object {
        private const val KEY_DEVELOPER_MODE_ENABLED = "developer_mode_enabled"
        private const val KEY_DEVELOPER_REALTIME_COLLAB_TESTING = "developer_realtime_collab_testing"
        private const val KEY_DEVELOPER_REALTIME_COLLAB_PREFER_CLOUD = "developer_realtime_collab_prefer_cloud"
        private const val KEY_LOCAL_DEV_COLLAB_USER_ID = "local_dev_collab_user_id"
        private const val KEY_OFFLINE_QUEUE_PREFIX = "collab_offline_queue_"
        private const val TYPING_THROTTLE_MS = 220L
        private const val RETRY_LOOP_MS = 4500L
        private const val MAX_WORKSPACE_TEXT_LENGTH = 12_000

        private fun workspaceLines(text: String): List<String> {
            return text
                .trim()
                .lines()
                .map { it.trimEnd() }
                .filter { it.isNotBlank() }
                .distinct()
        }

        internal fun buildWorkspaceDiffDataInternal(remoteText: String, localText: String): WorkspaceDiffData {
            val remote = remoteText.trim()
            val local = localText.trim()
            if (remote == local) {
                return WorkspaceDiffData(identical = true)
            }

            val remoteLines = workspaceLines(remote)
            val localLines = workspaceLines(local)
            val remoteSet = remoteLines.toSet()
            val localSet = localLines.toSet()
            val localOnly = localLines.filterNot { remoteSet.contains(it) }
            val remoteOnly = remoteLines.filterNot { localSet.contains(it) }
            val sharedCount = localLines.count { remoteSet.contains(it) }
            return WorkspaceDiffData(
                identical = false,
                sharedCount = sharedCount,
                localOnly = localOnly,
                remoteOnly = remoteOnly
            )
        }

        internal fun mergeWorkspaceTextsInternal(remoteText: String, localText: String): String {
            val remote = remoteText.trim()
            val local = localText.trim()
            if (remote.isBlank()) return local.take(MAX_WORKSPACE_TEXT_LENGTH)
            if (local.isBlank()) return remote.take(MAX_WORKSPACE_TEXT_LENGTH)
            val diffData = buildWorkspaceDiffDataInternal(remote, local)
            if (diffData.identical) return local.take(MAX_WORKSPACE_TEXT_LENGTH)
            if (diffData.localOnly.isEmpty()) return remote.take(MAX_WORKSPACE_TEXT_LENGTH)
            if (diffData.remoteOnly.isEmpty()) return local.take(MAX_WORKSPACE_TEXT_LENGTH)

            return buildString {
                append(remote)
                appendLine()
                appendLine()
                appendLine("---- Lokale Ergänzungen ----")
                diffData.localOnly.forEach { appendLine(it) }
            }.trim().take(MAX_WORKSPACE_TEXT_LENGTH)
        }

        internal fun buildWorkspaceDiffPreviewInternal(remoteText: String, localText: String): String {
            val remote = remoteText.trim()
            val local = localText.trim()
            val diffData = buildWorkspaceDiffDataInternal(remote, local)
            if (diffData.identical) return "Keine Unterschiede."
            if (remote.isBlank() && local.isNotBlank()) return "Remote is empty, local has content."
            if (local.isBlank() && remote.isNotBlank()) return "Local is empty, remote has content."

            return buildString {
                append("Lokal exklusiv: ${diffData.localOnly.size} Zeilen • Remote exklusiv: ${diffData.remoteOnly.size} Zeilen • Gemeinsam: ${diffData.sharedCount}")
                if (diffData.localOnly.isNotEmpty()) {
                    appendLine()
                    append("Lokal: ")
                    append(diffData.localOnly.take(3).joinToString(" | ").take(220))
                }
                if (diffData.remoteOnly.isNotEmpty()) {
                    appendLine()
                    append("Remote: ")
                    append(diffData.remoteOnly.take(3).joinToString(" | ").take(220))
                }
            }.trim()
        }

        private val localSessions = mutableMapOf<String, CollabSession>()
        private val localMessages = mutableMapOf<String, MutableList<CollabMessage>>()
        private val localPresences = mutableMapOf<String, MutableMap<String, CollabPresence>>()
        private val localWorkspace = mutableMapOf<String, CollabWorkspaceState>()
        private val localLock = Any()
    }

    private var lastJoinSessionInput: String = ""
    private var lastJoinInviteInput: String = ""
    private val failedOutbound = mutableMapOf<String, CollabMessage>()
    private val gson = Gson()
    private var retryJob: Job? = null
    private var lastTypingUpdateAt: Long = 0L
    @Volatile
    private var devCloudAuthInFlight = false
    @Volatile
    private var devCloudAuthFallbackToLocal = false

    init {
        refreshDebugInfo()
        refreshFirebaseStatus()
        loadPrivacyConsentStatus()
        startRetryLoop()
    }

    fun clearError() {
        _errorMessage.value = null
        _lastDetailedError.value = null
    }

    fun clearWorkspaceConflict() {
        _workspaceConflictMessage.value = null
    }

    fun acceptPrivacyConsent() {
        _privacyConsentShown.value = true
        prefs.edit().putBoolean("collab_privacy_consent_accepted", true).apply()
    }

    private fun loadPrivacyConsentStatus() {
        _privacyConsentShown.value = prefs.getBoolean("collab_privacy_consent_accepted", false)
    }

    private fun canRoleSendMessages(session: CollabSession, role: SessionRole): Boolean {
        return when (role) {
            SessionRole.OWNER -> true
            SessionRole.EDITOR -> session.editorCanSendMessages
            SessionRole.VIEWER -> false
        }
    }

    private fun canRoleUseAi(session: CollabSession, role: SessionRole): Boolean {
        if (!session.aiEnabled) return false
        return when (role) {
            SessionRole.OWNER -> true
            SessionRole.EDITOR -> session.editorCanUseAi
            SessionRole.VIEWER -> false
        }
    }

    private fun canRoleEditWorkspace(session: CollabSession, role: SessionRole): Boolean {
        return when (role) {
            SessionRole.OWNER -> true
            SessionRole.EDITOR -> session.editorCanEditWorkspace
            SessionRole.VIEWER -> false
        }
    }

    private fun refreshCapabilities(session: CollabSession? = _currentSession.value, userId: String = _currentUserId.value) {
        if (session == null || userId.isBlank() || !session.participants.contains(userId)) {
            _canWriteMessages.value = false
            _canEditWorkspace.value = false
            _canUseAi.value = false
            return
        }
        val role = roleOf(session, userId)
        _canWriteMessages.value = canRoleSendMessages(session, role)
        _canEditWorkspace.value = canRoleEditWorkspace(session, role)
        _canUseAi.value = canRoleUseAi(session, role)
    }

    fun refreshDebugInfo() {
        _providerLabel.value = prefs.getString("ai_provider", "OpenRouter") ?: "OpenRouter"
        _modelLabel.value = prefs.getString("openrouter_model", "google/gemma-3-27b-it:free")
            ?: "google/gemma-3-27b-it:free"
    }

    private fun refreshFirebaseStatus() {
        val appsAvailable = runCatching { FirebaseApp.getApps(getApplication()).isNotEmpty() }.getOrDefault(false)
        val authUser = auth.currentUser
        _firebaseStatus.value = when {
            appsAvailable && authUser != null -> "ready (${authUser.uid.take(8)})"
            appsAvailable -> "initialized (no-user)"
            else -> "not-initialized"
        }
    }

    private fun putDeliveryStatus(messageId: String, status: MessageDeliveryStatus) {
        _messageDeliveryStatus.value = _messageDeliveryStatus.value.toMutableMap().apply {
            put(messageId, status)
        }
    }

    private fun recordDetailedError(prefix: String, details: String?) {
        val clean = details?.trim().orEmpty()
        _lastDetailedError.value = if (clean.isBlank()) prefix else "$prefix: $clean"
    }

    private fun publishAuthMode(user: EffectiveUser?) {
        refreshDebugInfo()
        refreshFirebaseStatus()
        if (user == null) {
            _authModeLabel.value = getString(R.string.collab_auth_mode_not_logged_in)
            _isLocalOnlyMode.value = false
            return
        }
        _isLocalOnlyMode.value = user.localDevMode
        _authModeLabel.value = if (user.localDevMode) {
            "Dev-Local (${user.uid.take(10)})"
        } else {
            "Firebase (${user.uid.take(10)})"
        }
    }

    private fun startRetryLoop() {
        retryJob?.cancel()
        retryJob = viewModelScope.launch {
            while (isActive) {
                delay(RETRY_LOOP_MS)
                retryFailedOutboundNow()
            }
        }
    }

    private fun queueStorageKey(sessionId: String): String = "$KEY_OFFLINE_QUEUE_PREFIX$sessionId"

    private fun getString(resId: Int): String = getApplication<Application>().getString(resId)

    private fun getString(resId: Int, vararg args: Any): String = getApplication<Application>().getString(resId, *args)

    private fun generateSecureInviteCode(length: Int = 12): String {
        val secureRandom = SecureRandom()
        val chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        return (1..length).map { chars[secureRandom.nextInt(chars.length)] }.joinToString("")
    }

    private fun persistOfflineQueue(sessionId: String) {
        if (sessionId.isBlank()) return
        val payload = failedOutbound.values
            .filter { it.id.isNotBlank() && it.text.isNotBlank() }
            .toList()
        if (payload.isEmpty()) {
            prefs.edit().remove(queueStorageKey(sessionId)).apply()
            return
        }
        prefs.edit().putString(queueStorageKey(sessionId), gson.toJson(payload)).apply()
    }

    private fun restoreOfflineQueue(sessionId: String) {
        if (sessionId.isBlank()) return
        val raw = prefs.getString(queueStorageKey(sessionId), "")?.trim().orEmpty()
        if (raw.isBlank()) return
        val restored = runCatching {
            gson.fromJson(raw, Array<CollabMessage>::class.java)?.toList().orEmpty()
        }.getOrDefault(emptyList())
        if (restored.isEmpty()) {
            prefs.edit().remove(queueStorageKey(sessionId)).apply()
            return
        }
        restored.forEach { message ->
            failedOutbound[message.id] = message
            putDeliveryStatus(message.id, MessageDeliveryStatus.FAILED)
        }
        _messages.value = (_messages.value + restored).distinctBy { it.id }.sortedBy { it.timestamp }
    }

    fun retryFailedOutboundNow() {
        val session = _currentSession.value ?: return
        val user = resolveEffectiveUser() ?: return
        if (user.localDevMode) return
        if (failedOutbound.isEmpty()) return
        val pending = failedOutbound.values.toList()
        pending.forEach { failed ->
            viewModelScope.launch {
                runCatching {
                    putDeliveryStatus(failed.id, MessageDeliveryStatus.SENDING)
                    firestore.collection("collab_sessions")
                        .document(session.id)
                        .collection("messages")
                        .document(failed.id)
                        .set(failed)
                        .await()
                    failedOutbound.remove(failed.id)
                    putDeliveryStatus(failed.id, MessageDeliveryStatus.SENT)
                    if (failedOutbound.isEmpty()) {
                        prefs.edit().remove(queueStorageKey(session.id)).apply()
                    } else {
                        persistOfflineQueue(session.id)
                    }
                    _syncStatus.value = getString(R.string.collab_sync_status_offline_synced)
                }.onFailure {
                    putDeliveryStatus(failed.id, MessageDeliveryStatus.FAILED)
                    persistOfflineQueue(session.id)
                }
            }
        }
    }

    fun createSession(title: String) {
        if (!_privacyConsentShown.value) {
            _errorMessage.value = getString(R.string.collab_error_auth_required)
            return
        }
        val user = resolveEffectiveUser()
        if (user == null && tryBootstrapDeveloperCloudAuth { createSession(title) }) {
            return
        }
        publishAuthMode(user)
        if (user == null) {
            _errorMessage.value = getString(R.string.collab_error_auth_required)
            return
        }
        _currentUserId.value = user.uid
        val cleanTitle = title.trim().ifBlank { "Collab Session" }
        val sessionId = UUID.randomUUID().toString().take(8).uppercase()
        val inviteCode = generateSecureInviteCode(12)
        lastJoinSessionInput = sessionId
        lastJoinInviteInput = inviteCode

        if (user.localDevMode) {
            val session = CollabSession(
                id = sessionId,
                title = cleanTitle,
                ownerId = user.uid,
                participants = listOf(user.uid),
                participantRoles = mapOf(user.uid to SessionRole.OWNER.name),
                inviteCode = inviteCode,
                createdAt = System.currentTimeMillis()
            )
            synchronized(localLock) {
                localSessions[sessionId] = session
                localMessages[sessionId] = mutableListOf()
                localPresences[sessionId] = mutableMapOf(
                    user.uid to CollabPresence(
                        userId = user.uid,
                        displayName = user.displayName,
                        active = true,
                        lastSeenAt = System.currentTimeMillis()
                    )
                )
                localWorkspace[sessionId] = CollabWorkspaceState()
            }
            _currentSession.value = session
            _myRole.value = SessionRole.OWNER
            _messages.value = emptyList()
            _workspaceState.value = localWorkspace[sessionId] ?: CollabWorkspaceState()
            _syncStatus.value = getString(R.string.collab_sync_status_local_workspace)
            _presences.value = listOf(
                CollabPresence(
                    userId = user.uid,
                    displayName = user.displayName,
                    active = true,
                    lastSeenAt = System.currentTimeMillis()
                )
            )
            refreshCapabilities(session, user.uid)
            AppTelemetry.logEvent("collab_session_created_local_dev")
            return
        }

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
                _syncStatus.value = "Session verbunden: $sessionId"
                AppTelemetry.logEvent("collab_session_created")
            }.onFailure {
                AppTelemetry.logError("collab_create", it)
                _errorMessage.value = getString(R.string.collab_error_session_creation_failed)
                recordDetailedError("collab_create", it.message)
            }
            _isLoading.value = false
        }
    }

    fun joinSession(sessionIdInput: String, inviteCodeInput: String = "") {
        val user = resolveEffectiveUser()
        if (user == null && tryBootstrapDeveloperCloudAuth { joinSession(sessionIdInput, inviteCodeInput) }) {
            return
        }
        publishAuthMode(user)
        if (user == null) {
            _errorMessage.value = getString(R.string.collab_error_auth_required)
            return
        }
        _errorMessage.value = null
        lastJoinSessionInput = sessionIdInput.trim()
        lastJoinInviteInput = inviteCodeInput.trim().uppercase()
        if (sessionIdInput.trim().isBlank() && inviteCodeInput.trim().isNotBlank()) {
                    _errorMessage.value = getString(R.string.collab_error_invite_code_only)
            return
        }
        _currentUserId.value = user.uid
        val joinTarget = parseJoinInput(sessionIdInput, inviteCodeInput)
        if (joinTarget == null || joinTarget.sessionId.isBlank()) {
            _errorMessage.value = getString(R.string.collab_error_invalid_link)
            return
        }
        val sessionIdCandidates = buildSessionIdCandidates(joinTarget.sessionId)
        if (sessionIdCandidates.isEmpty()) {
            _errorMessage.value = getString(R.string.collab_error_invalid_session_id)
            return
        }
        val inviteCode = joinTarget.inviteCode
        val inviteCodeCandidates = buildInviteCodeCandidates(
            primaryInviteCode = inviteCode,
            rawSessionInput = joinTarget.sessionId
        )

        if (user.localDevMode) {
            synchronized(localLock) {
                val resolvedSessionIdById = sessionIdCandidates.firstOrNull { localSessions.containsKey(it) }
                val sessionFromId = resolvedSessionIdById?.let { localSessions[it] }
                val sessionByInvite = if (sessionFromId == null) {
                    localSessions.entries.firstOrNull { entry ->
                        inviteCodeCandidates.any { code -> code.equals(entry.value.inviteCode, ignoreCase = true) }
                    }
                } else null
                val resolvedSessionId = resolvedSessionIdById ?: sessionByInvite?.key
                val existing = sessionFromId ?: sessionByInvite?.value
                if (existing == null) {
                    _errorMessage.value = getString(R.string.collab_error_session_not_found)
                    return
                }
                if (resolvedSessionId == null) {
                    _errorMessage.value = getString(R.string.collab_error_session_not_found)
                    return
                }
                if (inviteCode.isNotBlank() &&
                    existing.inviteCode.isNotBlank() &&
                    inviteCode.trim().uppercase() != existing.inviteCode.uppercase()
                ) {
                    _errorMessage.value = getString(R.string.collab_error_invalid_code)
                    return
                }
                val updatedParticipants = (existing.participants + user.uid).distinct()
                val updatedRoles = existing.participantRoles.toMutableMap().apply {
                    putIfAbsent(user.uid, SessionRole.EDITOR.name)
                }
                val updated = existing.copy(
                    participants = updatedParticipants,
                    participantRoles = updatedRoles
                )
                lastJoinSessionInput = resolvedSessionId
                lastJoinInviteInput = existing.inviteCode
                localSessions[resolvedSessionId] = updated
                val presenceMap = localPresences.getOrPut(resolvedSessionId) { mutableMapOf() }
                presenceMap[user.uid] = CollabPresence(
                    userId = user.uid,
                    displayName = user.displayName,
                    active = true,
                    lastSeenAt = System.currentTimeMillis()
                )
                _currentSession.value = updated
                _messages.value = localMessages[resolvedSessionId].orEmpty().toList()
                _workspaceState.value = localWorkspace[resolvedSessionId] ?: CollabWorkspaceState()
                _presences.value = presenceMap.values.sortedByDescending { it.active }
                _myRole.value = roleOf(updated, user.uid)
                _syncStatus.value = getString(R.string.collab_sync_status_local_connected_with_id, resolvedSessionId)
                refreshCapabilities(updated, user.uid)
            }
            AppTelemetry.logEvent("collab_session_joined_local_dev")
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            runCatching {
                var resolvedSessionId: String? = null
                var existing: CollabSession? = null
                for (candidateId in sessionIdCandidates) {
                    val candidateSnapshot = firestore.collection("collab_sessions")
                        .document(candidateId)
                        .get()
                        .await()
                    val candidateSession = candidateSnapshot.toObject(CollabSession::class.java)
                    if (candidateSession != null) {
                        resolvedSessionId = candidateId
                        existing = candidateSession
                        break
                    }
                }
                if (existing == null && inviteCodeCandidates.isNotEmpty()) {
                    for (candidateInvite in inviteCodeCandidates) {
                        val querySnapshot = firestore.collection("collab_sessions")
                            .whereEqualTo("inviteCode", candidateInvite)
                            .limit(1)
                            .get()
                            .await()
                        val first = querySnapshot.documents.firstOrNull()
                        val candidateSession = first?.toObject(CollabSession::class.java)
                        if (candidateSession != null) {
                            resolvedSessionId = first.id
                            existing = candidateSession
                            break
                        }
                    }
                }
                if (existing == null) {
                    _errorMessage.value = getString(R.string.collab_error_session_not_found)
                    return@runCatching
                }
                val sessionId = resolvedSessionId ?: return@runCatching
                val docRef = firestore.collection("collab_sessions").document(sessionId)
                lastJoinSessionInput = sessionId
                lastJoinInviteInput = existing.inviteCode

                if (inviteCode.isNotBlank() &&
                    existing.inviteCode.isNotBlank() &&
                    inviteCode.trim().uppercase() != existing.inviteCode.uppercase()
                ) {
                    _errorMessage.value = getString(R.string.collab_error_invalid_code)
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
                bindSession(resolvedSessionId)
                setOwnPresence(active = true)
                _syncStatus.value = "Session verbunden: $resolvedSessionId"
                AppTelemetry.logEvent("collab_session_joined")
            }.onFailure {
                AppTelemetry.logError("collab_join", it)
                val details = it.message?.take(140).orEmpty()
                recordDetailedError("collab_join", details)
                val mapped = when {
                    details.contains("PERMISSION_DENIED", ignoreCase = true) ->
                        getString(R.string.collab_error_no_metadata_access)
                    else -> ""
                }
                _errorMessage.value = if (mapped.isNotBlank()) {
                    mapped
                } else if (details.isNotBlank()) {
                    "Session-Beitritt fehlgeschlagen: $details"
                } else {
                    "Session-Beitritt fehlgeschlagen."
                }
                _syncStatus.value = "Join fehlgeschlagen"
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
        val looksLikeLink =
            raw.startsWith("bamachat://", ignoreCase = true) ||
                raw.startsWith("http://", ignoreCase = true) ||
                raw.startsWith("https://", ignoreCase = true)
        if (looksLikeLink) {
            return runCatching {
                val uri = Uri.parse(raw)
                val sessionFromQuery = queryParamIgnoreCase(uri, "session").orEmpty().trim()
                val sessionFromPath = uri.pathSegments
                    .firstOrNull { it.isNotBlank() && !it.equals("collab", ignoreCase = true) }
                    .orEmpty()
                    .trim()
                val sessionFromHost = uri.host
                    .orEmpty()
                    .trim()
                    .takeIf {
                        it.isNotBlank() &&
                            !it.equals("collab", ignoreCase = true) &&
                            !it.equals("collab-link", ignoreCase = true)
                    }
                val session = when {
                    sessionFromQuery.isNotBlank() -> sessionFromQuery
                    sessionFromPath.isNotBlank() -> sessionFromPath
                    !sessionFromHost.isNullOrBlank() -> sessionFromHost
                    else -> ""
                }
                val invite = queryParamIgnoreCase(uri, "invite").orEmpty().trim().uppercase()
                if (session.isBlank()) {
                    null
                } else {
                JoinTarget(
                    sessionId = session,
                    inviteCode = if (inviteCodeInput.isBlank()) invite else inviteCodeInput.trim().uppercase()
                )
                }
            }.getOrNull()
        }
        return JoinTarget(
            sessionId = raw,
            inviteCode = inviteCodeInput.trim().uppercase()
        )
    }

    private fun queryParamIgnoreCase(uri: Uri, key: String): String? {
        val match = uri.queryParameterNames.firstOrNull { it.equals(key, ignoreCase = true) } ?: return null
        return uri.getQueryParameter(match)
    }

    private fun buildSessionIdCandidates(sessionId: String): List<String> {
        val trimmed = sessionId.trim()
        if (trimmed.isBlank()) return emptyList()
        return linkedSetOf(trimmed, trimmed.uppercase(), trimmed.lowercase())
            .filter { isValidFirestoreId(it) }
            .toList()
    }

    private fun buildInviteCodeCandidates(
        primaryInviteCode: String,
        rawSessionInput: String
    ): List<String> {
        val normalizedPrimary = primaryInviteCode.trim().uppercase()
        val normalizedSessionInput = rawSessionInput.trim().uppercase()
        return linkedSetOf(normalizedPrimary, normalizedSessionInput)
            .filter { isLikelyInviteCode(it) }
            .toList()
    }

    private fun isLikelyInviteCode(value: String): Boolean {
        if (value.isBlank()) return false
        if (value.length !in 6..16) return false
        return value.all { it.isLetterOrDigit() }
    }

    private fun isValidFirestoreId(id: String): Boolean {
        val trimmed = id.trim()
        if (trimmed.isBlank()) return false
        if (trimmed == "." || trimmed == "..") return false
        return !trimmed.contains("/")
    }

    fun sendMessage(text: String, isAi: Boolean = false) {
        val user = resolveEffectiveUser()
        if (user == null && tryBootstrapDeveloperCloudAuth()) {
            return
        }
        publishAuthMode(user)
        val session = _currentSession.value
        _errorMessage.value = null
        if (user == null || session == null) return
        if (!session.participants.contains(user.uid)) {
            _errorMessage.value = getString(R.string.collab_error_not_participant)
            return
        }
        val role = roleOf(session, user.uid)
        if (isAi && !canRoleUseAi(session, role)) {
            _errorMessage.value = if (!session.aiEnabled) {
                getString(R.string.collab_error_ai_disabled)
            } else {
                getString(R.string.collab_error_ai_locked)
            }
            return
        }
        if (!isAi && !canRoleSendMessages(session, role)) {
            _errorMessage.value = when (role) {
                SessionRole.VIEWER -> getString(R.string.collab_error_viewer_write)
                else -> getString(R.string.collab_error_listener)
            }
            return
        }

        val cleanText = text.trim()
        if (cleanText.isBlank()) return
        val messageId = UUID.randomUUID().toString()
        val msg = CollabMessage(
            id = messageId,
            authorId = user.uid,
            authorName = if (isAi) "Bama AI" else user.displayName,
            text = cleanText,
            timestamp = System.currentTimeMillis(),
            isAi = isAi
        )

        if (user.localDevMode) {
            synchronized(localLock) {
                val list = localMessages.getOrPut(session.id) { mutableListOf() }
                list.add(msg)
                _messages.value = list.toList()
                putDeliveryStatus(messageId, MessageDeliveryStatus.SENT)
                failedOutbound.remove(messageId)
                _syncStatus.value = getString(R.string.collab_sync_status_local_msg_sent_with_count, _messages.value.size)
            }
            return
        }

        putDeliveryStatus(messageId, MessageDeliveryStatus.SENDING)
        failedOutbound.remove(messageId)
        _messages.value = (_messages.value.filterNot { it.id == messageId } + msg).sortedBy { it.timestamp }

        viewModelScope.launch {
                _syncStatus.value = getString(R.string.collab_msg_status_sending)
            runCatching {
                firestore.collection("collab_sessions")
                    .document(session.id)
                    .collection("messages")
                    .document(messageId)
                    .set(msg)
                    .await()
                putDeliveryStatus(messageId, MessageDeliveryStatus.SENT)
                failedOutbound.remove(messageId)
                persistOfflineQueue(session.id)
                _syncStatus.value = "Nachricht gesendet (${_messages.value.size})"
            }.onFailure {
                AppTelemetry.logError("collab_send", it)
                val details = it.message?.take(140).orEmpty()
                failedOutbound[messageId] = msg
                putDeliveryStatus(messageId, MessageDeliveryStatus.FAILED)
                persistOfflineQueue(session.id)
                recordDetailedError("collab_send", details)
                _errorMessage.value = if (details.isNotBlank()) {
                    "Nachricht konnte nicht gesendet werden: $details"
                } else {
                    "Nachricht konnte nicht gesendet werden."
                }
                _syncStatus.value = getString(R.string.collab_msg_status_failed)
            }
        }
    }

    fun updateWorkspaceText(text: String, force: Boolean = false) {
        val user = resolveEffectiveUser()
        if (user == null && tryBootstrapDeveloperCloudAuth()) {
            return
        }
        publishAuthMode(user)
        val session = _currentSession.value ?: return
        if (user == null) return
        if (!session.participants.contains(user.uid)) return
        val role = roleOf(session, user.uid)
        if (!canRoleEditWorkspace(session, role)) {
            _errorMessage.value = if (role == SessionRole.EDITOR) {
                getString(R.string.collab_error_workspace_edit_disabled)
            } else {
                getString(R.string.collab_error_workspace_edit_locked)
            }
            return
        }

        val payload = CollabWorkspaceState(
            text = text.take(MAX_WORKSPACE_TEXT_LENGTH),
            updatedBy = user.uid,
            updatedAt = System.currentTimeMillis(),
            revision = (_workspaceState.value.revision + 1).coerceAtLeast(1L),
            baseRevision = _workspaceState.value.revision.coerceAtLeast(0L)
        )

        if (user.localDevMode) {
            synchronized(localLock) {
                localWorkspace[session.id] = payload
                _workspaceState.value = payload
                _syncStatus.value = getString(R.string.collab_sync_status_local_synced)
                _workspaceConflictMessage.value = null
            }
            return
        }

        viewModelScope.launch {
            runCatching {
                val workspaceRef = firestore.collection("collab_sessions")
                    .document(session.id)
                    .collection("workspace")
                    .document("state")
                val knownRevision = _workspaceState.value.revision.coerceAtLeast(0L)
                firestore.runTransaction { transaction ->
                    val remote = transaction.get(workspaceRef).toObject(CollabWorkspaceState::class.java)
                        ?: CollabWorkspaceState()
                    val remoteRevision = remote.revision.coerceAtLeast(0L)
                    val remoteUpdatedBy = remote.updatedBy.trim()
                    val remoteChangedByOther =
                        remoteRevision > knownRevision &&
                            remoteUpdatedBy.isNotBlank() &&
                            !remoteUpdatedBy.equals(user.uid, ignoreCase = true)
                    if (remoteChangedByOther && !force) {
                        throw WorkspaceConflictException(remoteUpdatedBy, remoteRevision)
                    }
                    val nextPayload = CollabWorkspaceState(
                        text = text.take(MAX_WORKSPACE_TEXT_LENGTH),
                        updatedBy = user.uid,
                        updatedAt = System.currentTimeMillis(),
                        revision = remoteRevision + 1L,
                        baseRevision = knownRevision
                    )
                    transaction.set(workspaceRef, nextPayload)
                    nextPayload
                }.await()
            }.onSuccess { resolvedPayload ->
                _workspaceState.value = resolvedPayload
                _workspaceConflictMessage.value = null
                _syncStatus.value = getString(R.string.collab_sync_status_workspace_synced)
            }.onFailure {
                if (it is WorkspaceConflictException) {
                    _workspaceConflictMessage.value =
                        "Konflikt erkannt: ${it.remoteUser.ifBlank { "anderer Nutzer" }} hat Revision ${it.remoteRevision} gespeichert."
                    _syncStatus.value = getString(R.string.collab_conflict_title)
                    return@onFailure
                }
                AppTelemetry.logError("collab_workspace_update", it)
                _syncStatus.value = getString(R.string.collab_error_workspace_sync)
            }
        }
    }

    fun forceWorkspaceOverwrite(text: String) {
        updateWorkspaceText(text = text, force = true)
    }

    fun buildWorkspaceDiffData(localDraft: String): WorkspaceDiffData {
        return buildWorkspaceDiffDataInternal(_workspaceState.value.text, localDraft)
    }

    fun buildWorkspaceDiffPreview(localDraft: String): String {
        return buildWorkspaceDiffPreviewInternal(_workspaceState.value.text, localDraft)
    }

    fun mergeWorkspaceTexts(localDraft: String): String {
        return mergeWorkspaceTextsInternal(_workspaceState.value.text, localDraft)
    }

    fun updateSessionPolicy(
        aiEnabled: Boolean? = null,
        editorCanUseAi: Boolean? = null,
        editorCanSendMessages: Boolean? = null,
        editorCanEditWorkspace: Boolean? = null
    ) {
        val current = _currentSession.value ?: return
        val me = resolveEffectiveUser()?.uid
            ?: run {
                if (tryBootstrapDeveloperCloudAuth {
                        updateSessionPolicy(
                            aiEnabled = aiEnabled,
                            editorCanUseAi = editorCanUseAi,
                            editorCanSendMessages = editorCanSendMessages,
                            editorCanEditWorkspace = editorCanEditWorkspace
                        )
                    }
                ) {
                    return
                }
                return
            }
        if (current.ownerId != me) {
            _errorMessage.value = getString(R.string.collab_error_owner_only_policy)
            return
        }
        val patched = current.copy(
            aiEnabled = aiEnabled ?: current.aiEnabled,
            editorCanUseAi = editorCanUseAi ?: current.editorCanUseAi,
            editorCanSendMessages = editorCanSendMessages ?: current.editorCanSendMessages,
            editorCanEditWorkspace = editorCanEditWorkspace ?: current.editorCanEditWorkspace
        )
        if (patched == current) return

        if (isLocalDevSession(current.id)) {
            synchronized(localLock) {
                localSessions[current.id] = patched
            }
            _currentSession.value = patched
            _myRole.value = roleOf(patched, me)
            refreshCapabilities(patched, me)
            _syncStatus.value = getString(R.string.collab_sync_status_policy_local_updated)
            AppTelemetry.logEvent("collab_policy_updated_local_dev")
            return
        }

        viewModelScope.launch {
            runCatching {
                firestore.collection("collab_sessions")
                    .document(current.id)
                    .update(
                        mapOf(
                            "aiEnabled" to patched.aiEnabled,
                            "editorCanUseAi" to patched.editorCanUseAi,
                            "editorCanSendMessages" to patched.editorCanSendMessages,
                            "editorCanEditWorkspace" to patched.editorCanEditWorkspace
                        )
                    )
                    .await()
                _syncStatus.value = getString(R.string.collab_sync_status_policy_updated)
                AppTelemetry.logEvent("collab_policy_updated")
            }.onFailure {
                AppTelemetry.logError("collab_policy_updated", it)
                _errorMessage.value = getString(R.string.collab_error_policy_update_failed)
            }
        }
    }

    fun setParticipantRole(userId: String, role: SessionRole) {
        val current = _currentSession.value ?: return
        val me = resolveEffectiveUser()?.uid
            ?: run {
                if (tryBootstrapDeveloperCloudAuth { setParticipantRole(userId, role) }) {
                    return
                }
                return
            }
        if (current.ownerId != me) {
            _errorMessage.value = getString(R.string.collab_error_owner_only_roles)
            return
        }
        if (!current.participants.contains(userId)) {
            _errorMessage.value = getString(R.string.collab_error_participant_not_found)
            return
        }
        if (userId == me && role != SessionRole.OWNER) {
            _errorMessage.value = getString(R.string.collab_error_owner_cannot_demote)
            return
        }

        if (isLocalDevSession(current.id)) {
            synchronized(localLock) {
                val updatedRoles = current.participantRoles.toMutableMap().apply {
                    put(userId, role.name)
                }
                val updated = current.copy(participantRoles = updatedRoles)
                localSessions[current.id] = updated
                _currentSession.value = updated
                _myRole.value = roleOf(updated, me)
                refreshCapabilities(updated, me)
            }
            AppTelemetry.logEvent("collab_role_updated_local_dev")
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
                _errorMessage.value = getString(R.string.collab_error_role_change_failed)
            }
        }
    }

    fun rotateInviteCode() {
        val current = _currentSession.value ?: return
        val me = resolveEffectiveUser()?.uid
            ?: run {
                if (tryBootstrapDeveloperCloudAuth { rotateInviteCode() }) {
                    return
                }
                return
            }
        if (current.ownerId != me) {
            _errorMessage.value = getString(R.string.collab_error_owner_only_renew_invite)
            return
        }
        val newCode = generateSecureInviteCode(12)
        if (isLocalDevSession(current.id)) {
            synchronized(localLock) {
                val updated = current.copy(inviteCode = newCode)
                localSessions[current.id] = updated
                _currentSession.value = updated
            }
            AppTelemetry.logEvent("collab_invite_rotated_local_dev")
            return
        }
        viewModelScope.launch {
            runCatching {
                firestore.collection("collab_sessions")
                    .document(current.id)
                    .update("inviteCode", newCode)
                    .await()
                AppTelemetry.logEvent("collab_invite_rotated")
            }.onFailure {
                AppTelemetry.logError("collab_invite_rotated", it)
                _errorMessage.value = getString(R.string.collab_error_invite_renew_failed)
            }
        }
    }

    fun removeParticipant(userId: String) {
        val current = _currentSession.value ?: return
        val me = resolveEffectiveUser()?.uid
            ?: run {
                if (tryBootstrapDeveloperCloudAuth { removeParticipant(userId) }) {
                    return
                }
                return
            }
        if (current.ownerId != me) {
            _errorMessage.value = getString(R.string.collab_error_owner_only_remove)
            return
        }
        if (userId == me) {
            _errorMessage.value = getString(R.string.collab_error_owner_cannot_remove_self)
            return
        }

        if (isLocalDevSession(current.id)) {
            synchronized(localLock) {
                val updatedParticipants = current.participants.filterNot { it == userId }
                val updatedRoles = current.participantRoles.toMutableMap().apply { remove(userId) }
                val updated = current.copy(
                    participants = updatedParticipants,
                    participantRoles = updatedRoles
                )
                localSessions[current.id] = updated
                localPresences[current.id]?.remove(userId)
                _currentSession.value = updated
                _presences.value = localPresences[current.id]?.values?.sortedByDescending { it.active }.orEmpty()
                refreshCapabilities(updated, me)
            }
            AppTelemetry.logEvent("collab_remove_participant_local_dev")
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
                _errorMessage.value = getString(R.string.collab_error_participant_removal_failed)
            }
        }
    }

    fun leaveSession() {
        val session = _currentSession.value ?: return
        val user = resolveEffectiveUser()
            ?: run {
                if (tryBootstrapDeveloperCloudAuth { leaveSession() }) {
                    return
                }
                return
            }

        if (user.localDevMode) {
            synchronized(localLock) {
                val remaining = session.participants.filterNot { it == user.uid }
                if (session.ownerId == user.uid) {
                    if (remaining.isEmpty()) {
                        localSessions.remove(session.id)
                        localMessages.remove(session.id)
                        localPresences.remove(session.id)
                        localWorkspace.remove(session.id)
                    } else {
                        val newOwner = remaining.first()
                        val updatedRoles = session.participantRoles.toMutableMap().apply {
                            remove(user.uid)
                            put(newOwner, SessionRole.OWNER.name)
                        }
                        localSessions[session.id] = session.copy(
                            ownerId = newOwner,
                            participants = remaining,
                            participantRoles = updatedRoles
                        )
                    }
                } else {
                    val updatedRoles = session.participantRoles.toMutableMap().apply { remove(user.uid) }
                    localSessions[session.id] = session.copy(
                        participants = remaining,
                        participantRoles = updatedRoles
                    )
                }
                localPresences[session.id]?.remove(user.uid)
            }
            _currentSession.value = null
            _messages.value = emptyList()
            _messageDeliveryStatus.value = emptyMap()
            failedOutbound.clear()
            prefs.edit().remove(queueStorageKey(session.id)).apply()
            _presences.value = emptyList()
            _workspaceState.value = CollabWorkspaceState()
            _myRole.value = SessionRole.VIEWER
            _workspaceConflictMessage.value = null
            refreshCapabilities(null, "")
            _syncStatus.value = getString(R.string.collab_sync_status_disconnected)
            AppTelemetry.logEvent("collab_left_session_local_dev")
            return
        }

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
                _messageDeliveryStatus.value = emptyMap()
                failedOutbound.clear()
                prefs.edit().remove(queueStorageKey(session.id)).apply()
                _presences.value = emptyList()
                _workspaceState.value = CollabWorkspaceState()
                _myRole.value = SessionRole.VIEWER
                _workspaceConflictMessage.value = null
                refreshCapabilities(null, "")
                _syncStatus.value = getString(R.string.collab_sync_status_disconnected)
                AppTelemetry.logEvent("collab_left_session")
            }.onFailure {
                AppTelemetry.logError("collab_leave", it)
                _errorMessage.value = getString(R.string.collab_error_leave_session_failed)
            }
        }
    }

    fun setOwnPresence(active: Boolean) {
        val session = _currentSession.value ?: return
        val user = resolveEffectiveUser()
            ?: run {
                if (tryBootstrapDeveloperCloudAuth { setOwnPresence(active) }) {
                    return
                }
                return
            }
        if (user.localDevMode) {
            synchronized(localLock) {
                val map = localPresences.getOrPut(session.id) { mutableMapOf() }
                map[user.uid] = CollabPresence(
                    userId = user.uid,
                    displayName = user.displayName,
                    active = active,
                    lastSeenAt = System.currentTimeMillis(),
                    typing = false,
                    draftPreview = "",
                    cursorIndex = 0
                )
                _presences.value = map.values.sortedByDescending { it.active }
            }
            return
        }
        val payload = CollabPresence(
            userId = user.uid,
            displayName = user.displayName,
            active = active,
            lastSeenAt = System.currentTimeMillis(),
            typing = false,
            draftPreview = "",
            cursorIndex = 0
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

    fun setTypingState(draftText: String, cursorIndex: Int) {
        val session = _currentSession.value ?: return
        val user = resolveEffectiveUser() ?: return
        val now = System.currentTimeMillis()
        if (now - lastTypingUpdateAt < TYPING_THROTTLE_MS) return
        lastTypingUpdateAt = now

        val preview = draftText.trim().take(32)
        val payload = CollabPresence(
            userId = user.uid,
            displayName = user.displayName,
            active = true,
            lastSeenAt = now,
            typing = draftText.isNotBlank(),
            draftPreview = preview,
            cursorIndex = cursorIndex.coerceAtLeast(0)
        )

        if (user.localDevMode) {
            synchronized(localLock) {
                val map = localPresences.getOrPut(session.id) { mutableMapOf() }
                map[user.uid] = payload
                _presences.value = map.values.sortedByDescending { it.active }
            }
            return
        }

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

    fun retryMessage(messageId: String): Boolean {
        val failed = failedOutbound[messageId] ?: return false
        sendMessage(failed.text, failed.isAi)
        return true
    }

    fun reconnectNow() {
        _errorMessage.value = null
        _syncStatus.value = getString(R.string.collab_sync_status_reconnecting)
        val activeSessionId = _currentSession.value?.id.orEmpty()
        if (activeSessionId.isNotBlank()) {
            bindSession(activeSessionId)
            setOwnPresence(active = true)
            _syncStatus.value = "Reconnect ok: $activeSessionId"
            return
        }
        if (lastJoinSessionInput.isBlank()) {
            _errorMessage.value = getString(R.string.collab_error_no_last_join)
            _syncStatus.value = "Reconnect fehlgeschlagen"
            return
        }
        joinSession(lastJoinSessionInput, lastJoinInviteInput)
    }

    private fun resolveEffectiveUser(): EffectiveUser? {
        auth.currentUser?.let { firebaseUser ->
            devCloudAuthFallbackToLocal = false
            return EffectiveUser(
                uid = firebaseUser.uid,
                displayName = firebaseUser.displayName ?: "User",
                localDevMode = false
            )
        }
        if (!isDeveloperCollabTestingEnabled()) return null
        if (isDeveloperCloudPreferred() && !devCloudAuthFallbackToLocal) {
            // Cloud-first: ohne Firebase-Identity kein geteilter Realtime-Workspace über mehrere Geräte.
            return null
        }
        val stored = prefs.getString(KEY_LOCAL_DEV_COLLAB_USER_ID, "")?.trim().orEmpty()
        val localId = if (stored.isNotBlank()) {
            stored
        } else {
            val generated = "dev-${UUID.randomUUID().toString().take(8)}"
            prefs.edit().putString(KEY_LOCAL_DEV_COLLAB_USER_ID, generated).apply()
            generated
        }
        return EffectiveUser(
            uid = localId,
            displayName = "Dev Tester",
            localDevMode = true
        )
    }

    private fun tryBootstrapDeveloperCloudAuth(onReady: (() -> Unit)? = null): Boolean {
        if (!isDeveloperCollabTestingEnabled()) return false
        if (!isDeveloperCloudPreferred()) return false
        if (auth.currentUser != null) return false

        if (devCloudAuthInFlight) {
            _syncStatus.value = getString(R.string.collab_sync_status_login_running)
            return true
        }

        devCloudAuthInFlight = true
        _isLoading.value = true
        _syncStatus.value = getString(R.string.collab_sync_status_login_running)
        _errorMessage.value = null

        viewModelScope.launch {
            runCatching {
                auth.signInAnonymously().await().user
            }.onSuccess { firebaseUser ->
                if (firebaseUser == null) {
                    devCloudAuthFallbackToLocal = true
                    _syncStatus.value = getString(R.string.collab_error_cloud_login_fallback_local)
                    _errorMessage.value = getString(R.string.collab_error_cloud_login_impossible)
                    recordDetailedError("collab_dev_auth", "anonymous user missing")
                } else {
                    devCloudAuthFallbackToLocal = false
                    _syncStatus.value = "Dev-Cloud verbunden (${firebaseUser.uid.take(8)})"
                    AppTelemetry.logEvent("collab_dev_auth_success")
                    devCloudAuthInFlight = false
                    onReady?.invoke()
                }
            }.onFailure { throwable ->
                devCloudAuthFallbackToLocal = true
                AppTelemetry.logError("collab_dev_auth", throwable)
                recordDetailedError("collab_dev_auth", throwable.message)
                _syncStatus.value = getString(R.string.collab_error_cloud_login_fallback_local)
                _errorMessage.value = getString(R.string.collab_error_cloud_login_failed)
            }
            devCloudAuthInFlight = false
            _isLoading.value = false
            publishAuthMode(resolveEffectiveUser())
        }
        return true
    }

    private fun isDeveloperCollabTestingEnabled(): Boolean {
        return prefs.getBoolean(KEY_DEVELOPER_MODE_ENABLED, false) &&
            prefs.getBoolean(KEY_DEVELOPER_REALTIME_COLLAB_TESTING, false)
    }

    private fun isDeveloperCloudPreferred(): Boolean {
        return prefs.getBoolean(KEY_DEVELOPER_REALTIME_COLLAB_PREFER_CLOUD, true)
    }

    private fun isLocalDevSession(sessionId: String): Boolean {
        return isDeveloperCollabTestingEnabled() && sessionId.isNotBlank() && localSessions.containsKey(sessionId)
    }

    private fun bindSession(sessionId: String) {
        stopListeners()
        restoreOfflineQueue(sessionId)
        listenToSession(sessionId)
        listenToMessages(sessionId)
        listenToPresence(sessionId)
        listenToWorkspace(sessionId)
    }

    fun rebindSession(sessionId: String) {
        clearError()
        bindSession(sessionId)
    }

    private fun listenToSession(sessionId: String) {
        sessionListener = firestore.collection("collab_sessions")
            .document(sessionId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _errorMessage.value = getString(R.string.collab_error_listener)
                    _syncStatus.value = getString(R.string.collab_error_listener)
                    recordDetailedError("session_listener", error.message)
                    return@addSnapshotListener
                }
                val session = snapshot?.toObject(CollabSession::class.java)
                _currentSession.value = session
                val me = resolveEffectiveUser()?.uid.orEmpty()
                _currentUserId.value = me
                _myRole.value = if (session == null) SessionRole.VIEWER else roleOf(session, me)
                refreshCapabilities(session, me)
                if (session == null) {
                    _workspaceConflictMessage.value = null
                }
                _syncStatus.value = if (session != null) "Session aktiv: ${session.id}" else "Session beendet"
            }
    }

    private fun listenToMessages(sessionId: String) {
        messageListener = firestore.collection("collab_sessions")
            .document(sessionId)
            .collection("messages")
            .orderBy("timestamp")
            .limitToLast(50)
            .addSnapshotListener { value, error ->
                if (error != null) {
                    _errorMessage.value = getString(R.string.collab_error_listener)
                    _syncStatus.value = getString(R.string.collab_error_listener)
                    recordDetailedError("message_listener", error.message)
                    return@addSnapshotListener
                }
                val messages = value?.documents.orEmpty()
                    .mapNotNull { it.toObject(CollabMessage::class.java) }
                _messages.value = messages
                _hasMoreMessages.value = (value?.documents?.size ?: 0) >= 50
                val ownId = _currentUserId.value
                if (ownId.isNotBlank()) {
                    val statusMap = _messageDeliveryStatus.value.toMutableMap()
                    messages.filter { it.authorId == ownId }.forEach { message ->
                        statusMap[message.id] = MessageDeliveryStatus.SENT
                        failedOutbound.remove(message.id)
                    }
                    _messageDeliveryStatus.value = statusMap
                    persistOfflineQueue(sessionId)
                }
                _syncStatus.value = getString(R.string.collab_sync_status_live_sync) + ": ${messages.size}"
            }
    }

    private fun listenToPresence(sessionId: String) {
        presenceListener = firestore.collection("collab_sessions")
            .document(sessionId)
            .collection("presence")
            .addSnapshotListener { value, error ->
                if (error != null) {
                    _errorMessage.value = "Presence-Listener Fehler."
                    _syncStatus.value = "Presence-Listener Fehler"
                    recordDetailedError("presence_listener", error.message)
                    return@addSnapshotListener
                }
                val entries = value?.documents.orEmpty()
                    .mapNotNull { it.toObject(CollabPresence::class.java) }
                    .sortedByDescending { it.active }
                _presences.value = entries
            }
    }

    private fun listenToWorkspace(sessionId: String) {
        workspaceListener = firestore.collection("collab_sessions")
            .document(sessionId)
            .collection("workspace")
            .document("state")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _syncStatus.value = "Workspace-Listener Fehler"
                    recordDetailedError("workspace_listener", error.message)
                    return@addSnapshotListener
                }
                _workspaceState.value = snapshot?.toObject(CollabWorkspaceState::class.java) ?: CollabWorkspaceState()
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
        workspaceListener?.remove()
        workspaceListener = null
    }

    override fun onCleared() {
        // 🔴 CRITICAL: Cleanup presence state before destroying listeners
        try {
            viewModelScope.launch {
                runCatching { 
                    setOwnPresence(active = false)
                }.onFailure { e ->
                    AppTelemetry.logError("collab_presence_cleanup_failed", e)
                }
            }
        } catch (e: Exception) {
            AppTelemetry.logError("collab_presence_exception", e)
        }
        
        // Cancel all retry jobs
        retryJob?.cancel()
        retryJob = null
        
        // Stop Firebase listeners (prevents memory leak)
        try {
            stopListeners()
        } catch (e: Exception) {
            AppTelemetry.logError("collab_listener_cleanup_failed", e)
        }
        
        // Clear all state flows to help GC
        _messages.value = emptyList()
        _presences.value = emptyList()
        
        super.onCleared()
    }
}
