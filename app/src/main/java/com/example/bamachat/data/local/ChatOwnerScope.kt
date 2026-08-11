package com.example.bamachat.data.local

import android.content.SharedPreferences
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ChatOwnerScope {
    const val LEGACY_UNCLASSIFIED = "legacy:unclassified"
    const val NO_ACTIVE_SESSION = "session:none"
    private const val ACCOUNT_PREFIX = "account:"
    private const val GUEST_PREFIX = "guest:"

    fun account(uid: String): String {
        val cleanUid = uid.trim()
        require(cleanUid.isNotBlank()) { "Firebase UID must not be blank" }
        return "$ACCOUNT_PREFIX$cleanUid"
    }

    fun guest(sessionId: String): String {
        val cleanSessionId = sessionId.trim()
        require(cleanSessionId.isNotBlank()) { "Guest session ID must not be blank" }
        return "$GUEST_PREFIX$cleanSessionId"
    }

    fun isAccount(scope: String): Boolean =
        scope.startsWith(ACCOUNT_PREFIX) && scope.length > ACCOUNT_PREFIX.length

    fun isAccountForUid(scope: String, uid: String): Boolean =
        scope == account(uid)

    fun isGuest(scope: String): Boolean =
        scope.startsWith(GUEST_PREFIX) && scope.length > GUEST_PREFIX.length

    fun isWritable(scope: String): Boolean = isAccount(scope) || isGuest(scope)
}

@Singleton
class ChatSessionScopeStore @Inject constructor(
    private val prefs: SharedPreferences
) {
    private val _activeScope = MutableStateFlow(resolveStoredScope())
    val activeScope: StateFlow<String> = _activeScope.asStateFlow()
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (
            key == KEY_ACTIVE_SCOPE ||
            key == KEY_PENDING_GUEST_SCOPE ||
            key == KEY_AUTH_TRANSITION_PENDING
        ) {
            _activeScope.value = resolveStoredScope()
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
    }

    @Synchronized
    fun reconcile(firebaseUid: String?, guestModeEnabled: Boolean): String {
        val pendingGuestScope = pendingGuestScope()
        val resolved = when {
            pendingGuestScope != null -> pendingGuestScope
            !firebaseUid.isNullOrBlank() -> ChatOwnerScope.account(firebaseUid)
            guestModeEnabled -> storedGuestScope() ?: ChatOwnerScope.guest(UUID.randomUUID().toString())
            else -> ChatOwnerScope.NO_ACTIVE_SESSION
        }
        persistActiveScope(resolved, preserveGuestSession = guestModeEnabled || pendingGuestScope != null)
        return resolved
    }

    @Synchronized
    fun startNewGuestSession(): String {
        val scope = ChatOwnerScope.guest(UUID.randomUUID().toString())
        val committed = prefs.edit()
            .putString(KEY_GUEST_SCOPE, scope)
            .putString(KEY_ACTIVE_SCOPE, scope)
            .remove(KEY_PENDING_GUEST_SCOPE)
            .putBoolean(KEY_AUTH_TRANSITION_PENDING, false)
            .commit()
        check(committed) { "Gast-Sitzung konnte nicht sicher gespeichert werden." }
        _activeScope.value = scope
        return scope
    }

    @Synchronized
    fun beginAccountTransitionIfGuest(): String? {
        val current = currentScope()
        if (!ChatOwnerScope.isGuest(current)) return null
        val committed = prefs.edit()
            .putString(KEY_PENDING_GUEST_SCOPE, current)
            .putBoolean(KEY_AUTH_TRANSITION_PENDING, true)
            .commit()
        check(committed) { "Anmeldeübergang konnte nicht sicher vorbereitet werden." }
        return current
    }

    @Synchronized
    fun cancelAccountTransition() {
        val pending = pendingGuestScope()
        val editor = prefs.edit()
            .remove(KEY_PENDING_GUEST_SCOPE)
            .putBoolean(KEY_AUTH_TRANSITION_PENDING, false)
        if (pending != null) editor.putString(KEY_ACTIVE_SCOPE, pending)
        val committed = editor.commit()
        check(committed) { "Anmeldeübergang konnte nicht sicher zurückgesetzt werden." }
        if (pending != null) _activeScope.value = pending
    }

    @Synchronized
    fun completeAccountTransition(uid: String, removedConversationIds: Collection<String>) {
        val accountScope = ChatOwnerScope.account(uid)
        val editor = prefs.edit()
            .putString(KEY_ACTIVE_SCOPE, accountScope)
            .remove(KEY_GUEST_SCOPE)
            .remove(KEY_PENDING_GUEST_SCOPE)
            .putBoolean(KEY_AUTH_TRANSITION_PENDING, false)
        removedConversationIds.forEach { conversationId ->
            editor.remove(workspaceBindingKey(conversationId))
        }
        pendingGuestScope()?.let { editor.remove(currentConversationKey(it)) }
        val committed = editor.commit()
        check(committed) { "Kontositzung konnte nach Gastbereinigung nicht sicher aktiviert werden." }
        _activeScope.value = accountScope
    }

    @Synchronized
    fun activateAccount(uid: String): String {
        check(!isAccountTransitionPending()) { "Kontositzung ist bis zur Gastbereinigung gesperrt." }
        val accountScope = ChatOwnerScope.account(uid)
        val committed = prefs.edit().putString(KEY_ACTIVE_SCOPE, accountScope).commit()
        check(committed) { "Kontositzung konnte nicht sicher gespeichert werden." }
        _activeScope.value = accountScope
        return accountScope
    }

    @Synchronized
    fun deactivateSession() {
        val committed = prefs.edit()
            .putString(KEY_ACTIVE_SCOPE, ChatOwnerScope.NO_ACTIVE_SESSION)
            .remove(KEY_PENDING_GUEST_SCOPE)
            .putBoolean(KEY_AUTH_TRANSITION_PENDING, false)
            .commit()
        check(committed) { "Sitzungsstatus konnte nicht sicher zurückgesetzt werden." }
        _activeScope.value = ChatOwnerScope.NO_ACTIVE_SESSION
    }

    fun currentScope(): String = _activeScope.value

    fun requireWritableActiveScope(): String {
        check(!isAccountTransitionPending()) { "Chat-Schreibzugriff ist während des Anmeldeübergangs gesperrt." }
        return currentScope().also { scope ->
            check(ChatOwnerScope.isWritable(scope)) { "Keine schreibbare Chat-Sitzung aktiv." }
        }
    }

    fun pendingGuestScope(): String? =
        prefs.getString(KEY_PENDING_GUEST_SCOPE, null)
            ?.takeIf(ChatOwnerScope::isGuest)

    fun isAccountTransitionPending(): Boolean =
        prefs.getBoolean(KEY_AUTH_TRANSITION_PENDING, false) && pendingGuestScope() != null

    fun isCloudSyncAllowed(uid: String): Boolean =
        !isAccountTransitionPending() && ChatOwnerScope.isAccountForUid(currentScope(), uid)

    private fun resolveStoredScope(): String {
        val pending = pendingGuestScope()
        if (pending != null) return pending
        return prefs.getString(KEY_ACTIVE_SCOPE, ChatOwnerScope.NO_ACTIVE_SESSION)
            ?.takeIf { it == ChatOwnerScope.NO_ACTIVE_SESSION || ChatOwnerScope.isWritable(it) }
            ?: ChatOwnerScope.NO_ACTIVE_SESSION
    }

    private fun storedGuestScope(): String? =
        prefs.getString(KEY_GUEST_SCOPE, null)?.takeIf(ChatOwnerScope::isGuest)

    private fun persistActiveScope(scope: String, preserveGuestSession: Boolean) {
        val editor = prefs.edit().putString(KEY_ACTIVE_SCOPE, scope)
        if (ChatOwnerScope.isGuest(scope)) {
            editor.putString(KEY_GUEST_SCOPE, scope)
        } else if (!preserveGuestSession) {
            editor.remove(KEY_GUEST_SCOPE)
        }
        check(editor.commit()) { "Chat-Sitzungsstatus konnte nicht sicher gespeichert werden." }
        _activeScope.value = scope
    }

    companion object {
        private const val KEY_ACTIVE_SCOPE = "chat_active_owner_scope"
        private const val KEY_GUEST_SCOPE = "chat_guest_owner_scope"
        private const val KEY_PENDING_GUEST_SCOPE = "chat_pending_guest_owner_scope"
        private const val KEY_AUTH_TRANSITION_PENDING = "chat_account_transition_pending"

        fun currentConversationKey(ownerScope: String): String =
            "current_conversation_id_${ownerScope.replace(':', '_')}"

        fun workspaceBindingKey(conversationId: String): String =
            "conversation_workspace_name_$conversationId"
    }
}
