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
        hasValidSuffix(scope, ACCOUNT_PREFIX)

    fun isAccountForUid(scope: String, uid: String): Boolean =
        scope == account(uid)

    fun isGuest(scope: String): Boolean =
        hasValidSuffix(scope, GUEST_PREFIX)

    fun isWritable(scope: String): Boolean = isAccount(scope) || isGuest(scope)

    private fun hasValidSuffix(scope: String, prefix: String): Boolean {
        if (!scope.startsWith(prefix)) return false
        val suffix = scope.removePrefix(prefix)
        return suffix.isNotBlank() && suffix == suffix.trim() && suffix.none(Char::isWhitespace)
    }
}

enum class AccountTransitionPhase {
    NONE,
    PREPARED,
    AUTHENTICATED,
    GUEST_CLEANUP_COMPLETE,
    LEGACY_CLAIM_COMPLETE,
    WORKSPACE_MIGRATION_COMPLETE,
    ACCOUNT_ACTIVATED;

    fun isResumable(): Boolean =
        ordinal in AUTHENTICATED.ordinal..WORKSPACE_MIGRATION_COMPLETE.ordinal
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
            isAccountTransitionPending() -> ChatOwnerScope.NO_ACTIVE_SESSION
            !firebaseUid.isNullOrBlank() -> ChatOwnerScope.account(firebaseUid)
            guestModeEnabled -> storedGuestScope() ?: ChatOwnerScope.guest(UUID.randomUUID().toString())
            else -> ChatOwnerScope.NO_ACTIVE_SESSION
        }
        persistActiveScope(resolved, preserveGuestSession = guestModeEnabled || pendingGuestScope != null)
        return resolved
    }

    @Synchronized
    fun startNewGuestSession(): String {
        check(transitionPhase().ordinal <= AccountTransitionPhase.PREPARED.ordinal) {
            "Ein bestätigter Kontoübergang darf nicht durch eine Gast-Sitzung ersetzt werden."
        }
        val scope = storedGuestScope() ?: ChatOwnerScope.guest(UUID.randomUUID().toString())
        val committed = prefs.edit()
            .putString(KEY_GUEST_SCOPE, scope)
            .putString(KEY_ACTIVE_SCOPE, scope)
            .remove(KEY_PENDING_GUEST_SCOPE)
            .remove(KEY_PENDING_ACCOUNT_UID)
            .putString(KEY_TRANSITION_PHASE, AccountTransitionPhase.NONE.name)
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
        prepareAccountTransition()
        return current
    }

    @Synchronized
    fun prepareAccountTransition() {
        val current = currentScope()
        val phase = transitionPhase()
        if (phase != AccountTransitionPhase.NONE) return
        val committed = prefs.edit()
            .apply {
                if (ChatOwnerScope.isGuest(current)) putString(KEY_PENDING_GUEST_SCOPE, current)
                else remove(KEY_PENDING_GUEST_SCOPE)
            }
            .remove(KEY_PENDING_ACCOUNT_UID)
            .putString(KEY_TRANSITION_PHASE, AccountTransitionPhase.PREPARED.name)
            .putBoolean(KEY_AUTH_TRANSITION_PENDING, true)
            .commit()
        check(committed) { "Anmeldeübergang konnte nicht sicher vorbereitet werden." }
    }

    @Synchronized
    fun cancelAccountTransition() {
        check(transitionPhase() == AccountTransitionPhase.PREPARED) {
            "Ein bestätigter Kontoübergang darf nicht als Auth-Abbruch zurückgesetzt werden."
        }
        val pending = pendingGuestScope()
        val editor = prefs.edit()
            .remove(KEY_PENDING_GUEST_SCOPE)
            .remove(KEY_PENDING_ACCOUNT_UID)
            .putString(KEY_TRANSITION_PHASE, AccountTransitionPhase.NONE.name)
            .putBoolean(KEY_AUTH_TRANSITION_PENDING, false)
        if (pending != null) editor.putString(KEY_ACTIVE_SCOPE, pending)
        val committed = editor.commit()
        check(committed) { "Anmeldeübergang konnte nicht sicher zurückgesetzt werden." }
        if (pending != null) _activeScope.value = pending
    }

    @Synchronized
    fun bindPreparedAccountUid(uid: String) {
        val cleanUid = uid.trim()
        require(cleanUid.isNotBlank()) { "Firebase UID must not be blank" }
        check(transitionPhase() == AccountTransitionPhase.PREPARED) {
            "Die authentifizierte UID darf nur an einen vorbereiteten Übergang gebunden werden."
        }
        pendingAccountUid()?.let { existingUid ->
            if (existingUid != cleanUid) throw PendingAccountUidConflictException()
            return
        }
        check(
            prefs.edit()
                .putString(KEY_PENDING_ACCOUNT_UID, cleanUid)
                .putBoolean(KEY_AUTH_TRANSITION_PENDING, true)
                .commit()
        ) { "Authentifizierte UID konnte nicht sicher an den Übergang gebunden werden." }
    }

    @Synchronized
    fun beginAuthenticatedTransition(uid: String): AccountTransitionPhase {
        val cleanUid = uid.trim()
        require(cleanUid.isNotBlank()) { "Firebase UID must not be blank" }
        val currentPhase = transitionPhase()
        val existingUid = pendingAccountUid()
        if (currentPhase == AccountTransitionPhase.NONE && existingUid != null) {
            throw PendingAccountUidConflictException()
        }
        if (existingUid != null) {
            if (existingUid != cleanUid) throw PendingAccountUidConflictException()
        }
        if (currentPhase.ordinal >= AccountTransitionPhase.AUTHENTICATED.ordinal) {
            check(currentPhase.isResumable()) { "Der gespeicherte Kontoübergang ist nicht resumierbar." }
            return currentPhase
        }

        val editor = prefs.edit()
            .putString(KEY_PENDING_ACCOUNT_UID, cleanUid)
            .putString(KEY_TRANSITION_PHASE, AccountTransitionPhase.AUTHENTICATED.name)
            .putBoolean(KEY_AUTH_TRANSITION_PENDING, true)
        if (currentPhase == AccountTransitionPhase.NONE && ChatOwnerScope.isGuest(currentScope())) {
            editor.putString(KEY_PENDING_GUEST_SCOPE, currentScope())
        }
        check(editor.commit()) { "Bestätigter Kontoübergang konnte nicht sicher gespeichert werden." }
        _activeScope.value = resolveStoredScope()
        return AccountTransitionPhase.AUTHENTICATED
    }

    @Synchronized
    fun markTransitionPhase(uid: String, phase: AccountTransitionPhase) {
        require(phase.ordinal in AccountTransitionPhase.GUEST_CLEANUP_COMPLETE.ordinal..
            AccountTransitionPhase.WORKSPACE_MIGRATION_COMPLETE.ordinal) {
            "Ungültige persistente Übergangsphase."
        }
        val cleanUid = uid.trim()
        check(pendingAccountUid() == cleanUid) { "Der Übergang gehört nicht zum aktuellen Konto." }
        check(phase.ordinal >= transitionPhase().ordinal) { "Übergangsphasen dürfen nicht zurückgesetzt werden." }
        check(
            prefs.edit()
                .putString(KEY_TRANSITION_PHASE, phase.name)
                .putBoolean(KEY_AUTH_TRANSITION_PENDING, true)
                .commit()
        ) { "Übergangsphase konnte nicht sicher gespeichert werden." }
    }

    @Synchronized
    fun completeAccountTransition(uid: String) {
        val accountScope = ChatOwnerScope.account(uid)
        check(pendingAccountUid() == uid.trim()) { "Der Übergang gehört nicht zum aktuellen Konto." }
        check(transitionPhase() == AccountTransitionPhase.WORKSPACE_MIGRATION_COMPLETE) {
            "Kontositzung darf erst nach Gastbereinigung, Legacy-Zuordnung und Workspace-Migration aktiviert werden."
        }
        val editor = prefs.edit()
            .putString(KEY_ACTIVE_SCOPE, accountScope)
            .remove(KEY_GUEST_SCOPE)
            .remove(KEY_PENDING_GUEST_SCOPE)
            .remove(KEY_PENDING_ACCOUNT_UID)
            .putString(KEY_TRANSITION_PHASE, AccountTransitionPhase.NONE.name)
            .putString(KEY_LAST_COMPLETED_TRANSITION_PHASE, AccountTransitionPhase.ACCOUNT_ACTIVATED.name)
            .putBoolean(KEY_AUTH_TRANSITION_PENDING, false)
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
        check(!isAccountTransitionPending()) { "Ein laufender Kontoübergang darf nicht deaktiviert werden." }
        val committed = prefs.edit()
            .putString(KEY_ACTIVE_SCOPE, ChatOwnerScope.NO_ACTIVE_SESSION)
            .remove(KEY_PENDING_GUEST_SCOPE)
            .remove(KEY_PENDING_ACCOUNT_UID)
            .putString(KEY_TRANSITION_PHASE, AccountTransitionPhase.NONE.name)
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

    fun pendingAccountUid(): String? =
        prefs.getString(KEY_PENDING_ACCOUNT_UID, null)?.trim()?.takeIf { it.isNotBlank() }

    fun transitionPhase(): AccountTransitionPhase =
        prefs.getString(KEY_TRANSITION_PHASE, null)
            ?.let { stored -> runCatching { AccountTransitionPhase.valueOf(stored) }.getOrNull() }
            ?: if (prefs.getBoolean(KEY_AUTH_TRANSITION_PENDING, false)) {
                AccountTransitionPhase.PREPARED
            } else {
                AccountTransitionPhase.NONE
            }

    fun isAccountTransitionPending(): Boolean = transitionPhase() != AccountTransitionPhase.NONE

    fun canCancelAccountTransition(): Boolean = transitionPhase() == AccountTransitionPhase.PREPARED

    @Synchronized
    fun resetConflictingTransitionAfterSignOut() {
        check(isAccountTransitionPending()) { "Kein Kontoübergang aktiv." }
        val guestScope = pendingGuestScope()
        val editor = prefs.edit()
            .remove(KEY_PENDING_GUEST_SCOPE)
            .remove(KEY_PENDING_ACCOUNT_UID)
            .putString(KEY_TRANSITION_PHASE, AccountTransitionPhase.NONE.name)
            .putBoolean(KEY_AUTH_TRANSITION_PENDING, false)
            .putBoolean(KEY_AUTH_SECURITY_CONFLICT, true)
        if (guestScope != null) {
            editor.putString(KEY_ACTIVE_SCOPE, guestScope)
        } else {
            editor.putString(KEY_ACTIVE_SCOPE, ChatOwnerScope.NO_ACTIVE_SESSION)
        }
        check(editor.commit()) { "Sicherheitskonflikt konnte nicht zurückgesetzt werden." }
        _activeScope.value = guestScope ?: ChatOwnerScope.NO_ACTIVE_SESSION
    }

    @Synchronized
    fun consumeSecurityConflictNotice(): Boolean {
        val pending = prefs.getBoolean(KEY_AUTH_SECURITY_CONFLICT, false)
        if (pending) prefs.edit().remove(KEY_AUTH_SECURITY_CONFLICT).apply()
        return pending
    }

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
        private const val KEY_PENDING_ACCOUNT_UID = "chat_pending_account_uid"
        private const val KEY_AUTH_TRANSITION_PENDING = "chat_account_transition_pending"
        private const val KEY_TRANSITION_PHASE = "chat_account_transition_phase"
        private const val KEY_LAST_COMPLETED_TRANSITION_PHASE = "chat_last_completed_transition_phase"
        private const val KEY_AUTH_SECURITY_CONFLICT = "chat_auth_security_conflict"

        fun currentConversationKey(ownerScope: String): String =
            "current_conversation_id_${ownerScope.replace(':', '_')}"

    }
}

class PendingAccountUidConflictException : IllegalStateException(
    "Die Anmeldung konnte nicht sicher fortgesetzt werden. Bitte erneut anmelden."
)
