package com.example.bamachat.util

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import com.example.bamachat.data.cloud.AuthenticatedUidProvider
import com.example.bamachat.data.cloud.AccountCloudOperationLease
import com.example.bamachat.data.cloud.AccountCloudOperationGate
import com.example.bamachat.data.local.ChatDatabase
import com.example.bamachat.data.local.ChatMessageEntity
import com.example.bamachat.data.local.ChatOwnerScope
import com.example.bamachat.data.local.ChatSessionScopeStore
import com.example.bamachat.data.local.ConversationEntity
import com.example.bamachat.data.model.ChatMessage
import com.example.bamachat.data.repository.ChatRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class BackupManagerScopeSafetyTest {
    private lateinit var database: ChatDatabase
    private lateinit var repository: ChatRepository
    private lateinit var prefs: SharedPreferences
    private lateinit var scopeStore: ChatSessionScopeStore
    private lateinit var uidProvider: MutableUidProvider
    private lateinit var cloudStore: RecordingBackupCloudStore
    private lateinit var manager: BackupManager

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, ChatDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ChatRepository(database.chatDao())
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        scopeStore = ChatSessionScopeStore(prefs)
        uidProvider = MutableUidProvider()
        cloudStore = RecordingBackupCloudStore()
        manager = BackupManager(repository, scopeStore, uidProvider, cloudStore)
    }

    @After
    fun tearDown() {
        database.close()
        prefs.edit().clear().commit()
    }

    @Test
    fun backupRefusesGuestLegacyForeignAccountAndPendingTransition() = runBlocking {
        val guest = scopeStore.startNewGuestSession()
        seed("guest", guest)
        uidProvider.uid = "uid-a"
        assertTrue(manager.backupActiveAccountConversation("guest").isFailure)

        scopeStore.deactivateSession()
        assertTrue(manager.backupActiveAccountConversation("guest").isFailure)

        scopeStore.activateAccount("uid-b")
        assertTrue(manager.backupActiveAccountConversation("guest").isFailure)

        scopeStore.deactivateSession()
        scopeStore.activateAccount("uid-a")
        seed("account", ChatOwnerScope.account("uid-a"))
        scopeStore.prepareAccountTransition()
        assertTrue(manager.backupActiveAccountConversation("account").isFailure)
        assertTrue(cloudStore.writes.isEmpty())
    }

    @Test
    fun backupExportsOnlyRepositoryDataForActiveAccount() = runBlocking {
        val accountA = ChatOwnerScope.account("uid-a")
        val accountB = ChatOwnerScope.account("uid-b")
        seed("account-a", accountA)
        seed("account-b", accountB)
        uidProvider.uid = "uid-a"
        scopeStore.activateAccount("uid-a")

        val result = manager.backupActiveAccountConversation("account-a")

        assertTrue(result.isSuccess)
        val written = cloudStore.writes.single().second
        assertEquals(accountA, written.conversation.ownerScope)
        assertTrue(written.messages.all { it.ownerScope == accountA })
        assertFalse(written.messages.any { it.conversationId == "account-b" })
    }

    @Test
    fun restoreWritesOnlyActiveAccountScopeAndRejectsForeignOrLegacyPayloads() = runBlocking {
        val accountA = ChatOwnerScope.account("uid-a")
        uidProvider.uid = "uid-a"
        scopeStore.activateAccount("uid-a")
        cloudStore.reads["valid"] = backup("restored", accountA)
        cloudStore.reads["foreign"] = backup("foreign", ChatOwnerScope.account("uid-b"))
        cloudStore.reads["legacy"] = backup("legacy", ChatOwnerScope.LEGACY_UNCLASSIFIED)

        assertTrue(manager.restoreActiveAccountBackup("valid").isSuccess)
        assertNotNull(repository.getConversation("restored", accountA))
        assertTrue(manager.restoreActiveAccountBackup("foreign").isFailure)
        assertTrue(manager.restoreActiveAccountBackup("legacy").isFailure)
        assertEquals(1, repository.getAllConversationsForWorkspaceMigration().size)
    }

    @Test
    fun lowLevelStoreRejectsForgedLeaseMissingAuthForeignUidGuestAndPendingBeforeFirestore() = runBlocking {
        val operationGate = AccountCloudOperationGate()
        val lowLevel = FirestoreChatBackupCloudStore(scopeStore, uidProvider, operationGate, firestore = null)
        val accountBackup = backup("account", ChatOwnerScope.account("uid-a"))
        val forgedLease = AccountCloudOperationLease(Any())

        assertThrows(IllegalStateException::class.java) {
            runBlocking { lowLevel.write(forgedLease, "uid-a", accountBackup) }
        }

        operationGate.withCloudOperation { lease ->
            assertThrows(IllegalStateException::class.java) {
                runBlocking { lowLevel.write(lease, "uid-a", accountBackup) }
            }
        }

        uidProvider.uid = "uid-a"
        scopeStore.startNewGuestSession()
        operationGate.withCloudOperation { lease ->
            assertThrows(IllegalStateException::class.java) {
                runBlocking { lowLevel.write(lease, "uid-a", accountBackup) }
            }
        }

        scopeStore.deactivateSession()
        scopeStore.activateAccount("uid-a")
        operationGate.withCloudOperation { lease ->
            assertThrows(IllegalStateException::class.java) {
                runBlocking { lowLevel.write(lease, "uid-b", accountBackup) }
            }
        }

        scopeStore.prepareAccountTransition()
        operationGate.withCloudOperation { lease ->
            assertThrows(IllegalStateException::class.java) {
                runBlocking { lowLevel.read(lease, "uid-a", "backup") }
            }
        }
        Unit
    }

    private suspend fun seed(id: String, scope: String) {
        repository.createConversation(id, ownerScope = scope)
        repository.saveMessage(id, ChatMessage("message-$id", "body", true, 1L), scope)
    }

    private fun backup(id: String, scope: String): AccountChatBackup = AccountChatBackup(
        ConversationEntity(id, "Title", 1L, 1L, ownerScope = scope),
        listOf(ChatMessageEntity("message-$id", id, "body", true, 1L, ownerScope = scope))
    )

    private class MutableUidProvider : AuthenticatedUidProvider {
        var uid: String? = null
        override fun currentUid(): String? = uid
    }

    private class RecordingBackupCloudStore : ChatBackupCloudStore {
        val writes = mutableListOf<Pair<String, AccountChatBackup>>()
        val reads = mutableMapOf<String, AccountChatBackup>()

        override suspend fun write(
            lease: AccountCloudOperationLease,
            requestedUid: String,
            backup: AccountChatBackup
        ): String {
            writes += requestedUid to backup
            return "backup-id"
        }

        override suspend fun read(
            lease: AccountCloudOperationLease,
            requestedUid: String,
            backupId: String
        ): AccountChatBackup? = reads[backupId]
    }

    private companion object {
        const val PREFS_NAME = "backup_manager_scope_safety_test"
    }
}
