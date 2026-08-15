package com.example.bamachat.data.local

import android.content.SharedPreferences
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConversationWorkspaceStore @Inject constructor(
    private val prefs: SharedPreferences
) {
    fun bind(ownerScope: String, conversationId: String, workspaceName: String) {
        requireKnownScope(ownerScope)
        check(prefs.edit().putString(scopedKey(ownerScope, conversationId), workspaceName).commit()) {
            "Workspace-Bindung konnte nicht sicher gespeichert werden."
        }
    }

    fun resolve(ownerScope: String, conversationId: String): String? {
        requireKnownScope(ownerScope)
        return prefs.getString(scopedKey(ownerScope, conversationId), null)
    }

    fun remove(ownerScope: String, conversationId: String) {
        requireKnownScope(ownerScope)
        check(prefs.edit().remove(scopedKey(ownerScope, conversationId)).commit()) {
            "Workspace-Bindung konnte nicht sicher entfernt werden."
        }
    }

    fun removeAllForScope(ownerScope: String) {
        requireKnownScope(ownerScope)
        val prefix = scopedPrefix(ownerScope)
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(prefix) }.forEach(editor::remove)
        check(editor.commit()) { "Workspace-Bindungen konnten nicht sicher entfernt werden." }
    }

    fun migrateUnscopedBindings(conversations: Collection<ConversationEntity>) {
        val editor = prefs.edit()
        var changed = false
        val conversationIds = conversations.mapTo(mutableSetOf()) { it.id }
        conversations.forEach { conversation ->
            requireKnownScope(conversation.ownerScope)
            val legacyKey = legacyKey(conversation.id)
            if (!prefs.contains(legacyKey)) return@forEach
            val targetKey = scopedKey(conversation.ownerScope, conversation.id)
            if (!prefs.contains(targetKey)) {
                prefs.getString(legacyKey, null)?.let { editor.putString(targetKey, it) }
            }
            editor.remove(legacyKey)
            changed = true
        }
        prefs.all.keys
            .filter { key ->
                key.startsWith(LEGACY_PREFIX) &&
                    !key.startsWith(SCOPED_PREFIX) &&
                    key.removePrefix(LEGACY_PREFIX) !in conversationIds
            }
            .forEach { orphanedKey ->
                editor.remove(orphanedKey)
                changed = true
            }
        if (changed) {
            check(editor.commit()) { "Bestehende Workspace-Bindungen konnten nicht sicher migriert werden." }
        }
    }

    internal fun scopedKey(ownerScope: String, conversationId: String): String =
        "${scopedPrefix(ownerScope)}$conversationId"

    private fun scopedPrefix(ownerScope: String): String =
        "${SCOPED_PREFIX}${scopeFingerprint(ownerScope)}_"

    private fun legacyKey(conversationId: String): String = "$LEGACY_PREFIX$conversationId"

    private fun requireKnownScope(ownerScope: String) {
        require(
            ChatOwnerScope.isWritable(ownerScope) || ownerScope == ChatOwnerScope.LEGACY_UNCLASSIFIED
        ) { "Workspace-Bindungen benötigen einen bekannten Owner-Scope." }
    }

    private fun scopeFingerprint(ownerScope: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(ownerScope.toByteArray(Charsets.UTF_8))
            .take(12)
            .joinToString("") { byte -> "%02x".format(byte) }

    private companion object {
        const val LEGACY_PREFIX = "conversation_workspace_name_"
        const val SCOPED_PREFIX = "conversation_workspace_name_scoped_"
    }
}
