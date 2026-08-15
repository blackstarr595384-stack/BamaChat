package com.example.bamachat.data.cloud

import android.content.Context
import androidx.room.Room
import com.example.bamachat.data.local.AccountAuthProvider
import com.example.bamachat.data.local.AccountAuthTransitionRunner
import com.example.bamachat.data.local.AccountTransitionPhase
import com.example.bamachat.data.local.ChatDatabase
import com.example.bamachat.data.local.ChatMessageEntity
import com.example.bamachat.data.local.ChatOwnerScope
import com.example.bamachat.data.local.ChatSessionScopeStore
import com.example.bamachat.data.local.ConversationEntity
import com.example.bamachat.data.local.ConversationWorkspaceStore
import com.example.bamachat.data.local.GuestChatTransitionCoordinator
import com.example.bamachat.data.local.RoomGuestScopeChatCleaner
import com.example.bamachat.data.local.RoomLegacyScopeClaimer
import com.example.bamachat.data.repository.ChatRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class ChatCloudSyncGatewayGateTest {
    private val context = RuntimeEnvironment.getApplication()
    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    private lateinit var scopeStore: ChatSessionScopeStore
    private lateinit var uidProvider: MutableUidProvider
    private lateinit var operationGate: AccountCloudOperationGate
    private lateinit var writer: RecordingWriter
    private lateinit var gateway: ChatCloudSyncGateway
    private lateinit var database: ChatDatabase
    private lateinit var runner: AccountAuthTransitionRunner

    @Before
    fun setUp() {
        prefs.edit().clear().commit()
        scopeStore = ChatSessionScopeStore(prefs)
        uidProvider = MutableUidProvider()
        operationGate = AccountCloudOperationGate()
        database = Room.inMemoryDatabaseBuilder(context, ChatDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repository = ChatRepository(database.chatDao())
        val coordinator = GuestChatTransitionCoordinator(
            scopeStore,
            RoomGuestScopeChatCleaner(database.chatDao()),
            RoomLegacyScopeClaimer(database),
            repository,
            ConversationWorkspaceStore(prefs),
            operationGate
        )
        runner = AccountAuthTransitionRunner(scopeStore, coordinator, operationGate)
        writer = RecordingWriter()
        gateway = ChatCloudSyncGateway(scopeStore, uidProvider, operationGate, writer)
    }

    @After
    fun tearDown() {
        database.close()
        prefs.edit().clear().commit()
    }

    @Test
    fun actualGatewayMethodsRejectGuestForeignPendingAndMissingAuthWithoutWriterCalls() = runTest {
        uidProvider.uid = "uid-a"
        val guest = scopeStore.startNewGuestSession()
        assertFalse(gateway.pushConversation("uid-a", conversation("guest", guest)))
        assertFalse(gateway.pushMessage("uid-a", "guest", message("guest", guest)))
        assertFalse(gateway.softDeleteConversation("uid-a", "guest", guest))

        scopeStore.deactivateSession()
        scopeStore.activateAccount("uid-a")
        assertFalse(gateway.pushConversation("uid-a", conversation("foreign", ChatOwnerScope.account("uid-b"))))
        uidProvider.uid = null
        assertFalse(gateway.pushConversation("uid-a", conversation("account", ChatOwnerScope.account("uid-a"))))
        uidProvider.uid = "uid-a"
        scopeStore.prepareAccountTransition()
        assertFalse(gateway.pushConversation("uid-a", conversation("pending", ChatOwnerScope.account("uid-a"))))

        assertEquals(0, writer.totalCalls)
    }

    @Test
    fun actualGatewayMethodsAndAsyncPathsInvokeWriterForMatchingAccount() = runTest {
        activateAccount()
        val scope = ChatOwnerScope.account("uid-a")
        assertTrue(gateway.pushConversation("uid-a", conversation("conversation", scope)))
        assertTrue(gateway.pushMessage("uid-a", "conversation", message("message", scope)))
        assertTrue(gateway.softDeleteConversation("uid-a", "conversation", scope))
        gateway.pushConversationAsync("uid-a", conversation("async-conversation", scope)).join()
        gateway.pushMessageAsync("uid-a", "conversation", message("async-message", scope)).join()
        gateway.softDeleteConversationAsync("uid-a", "async-conversation", scope).join()
        assertEquals(6, writer.totalCalls)
    }

    @Test
    fun writerFailureReturnsFalseAndDoesNotOpenGate() = runTest {
        activateAccount()
        writer.failure = IllegalStateException("canary-sensitive-message")
        assertFalse(gateway.pushConversation("uid-a", conversation("failure", ChatOwnerScope.account("uid-a"))))
        assertEquals(1, writer.totalCalls)
        runner.authenticate(AccountAuthProvider.GOOGLE) { "uid-b" }
        assertFalse(gateway.pushConversation("uid-a", conversation("blocked", ChatOwnerScope.account("uid-a"))))
        assertEquals(1, writer.totalCalls)
        assertEquals(ChatOwnerScope.account("uid-b"), scopeStore.currentScope())
    }

    @Test
    fun runnerWaitsForInFlightMessageWriteThenPreparedBlocksLaterWrites() = runTest {
        activateAccount()
        writer.blockWrites = true
        val scope = ChatOwnerScope.account("uid-a")
        val write = async { gateway.pushMessage("uid-a", "conversation", message("before", scope)) }
        writer.entered.await()
        val authEntered = CompletableDeferred<Unit>()
        val authRelease = CompletableDeferred<Unit>()
        val transition = async {
            runner.authenticate(AccountAuthProvider.GOOGLE) {
                authEntered.complete(Unit)
                authRelease.await()
                "uid-b"
            }
        }
        yield()
        assertFalse(authEntered.isCompleted)
        writer.release.complete(Unit)
        assertTrue(write.await())
        authEntered.await()
        assertEquals(AccountTransitionPhase.PREPARED, scopeStore.transitionPhase())
        assertFalse(gateway.pushMessage("uid-a", "conversation", message("after", scope)))
        assertEquals(1, writer.totalCalls)
        authRelease.complete(Unit)
        transition.await()
    }

    @Test
    fun runnerWaitsForInFlightSoftDeleteAndBlocksLaterDelete() = runTest {
        activateAccount()
        writer.blockWrites = true
        val scope = ChatOwnerScope.account("uid-a")
        val write = async { gateway.softDeleteConversation("uid-a", "before", scope) }
        writer.entered.await()
        val authEntered = CompletableDeferred<Unit>()
        val authRelease = CompletableDeferred<Unit>()
        val transition = async {
            runner.authenticate(AccountAuthProvider.EMAIL) {
                authEntered.complete(Unit)
                authRelease.await()
                "uid-b"
            }
        }
        yield()
        assertFalse(authEntered.isCompleted)
        writer.release.complete(Unit)
        assertTrue(write.await())
        authEntered.await()
        assertEquals(AccountTransitionPhase.PREPARED, scopeStore.transitionPhase())
        assertFalse(gateway.softDeleteConversation("uid-a", "after", scope))
        assertEquals(1, writer.totalCalls)
        authRelease.complete(Unit)
        transition.await()
    }

    @Test
    fun concurrentWritesAreSerialized() = runTest {
        activateAccount()
        writer.observeConcurrency = true
        val scope = ChatOwnerScope.account("uid-a")
        (1..4).map { index ->
            async { gateway.pushConversation("uid-a", conversation("c-$index", scope)) }
        }.awaitAll()
        assertEquals(1, writer.maxConcurrent)
        assertEquals(4, writer.totalCalls)
    }

    @Test
    fun cancelledWriteReleasesLeaseForTransition() = runTest {
        activateAccount()
        writer.blockWrites = true
        val write = async {
            gateway.pushConversation("uid-a", conversation("cancelled", ChatOwnerScope.account("uid-a")))
        }
        writer.entered.await()
        write.cancelAndJoin()
        runner.authenticate(AccountAuthProvider.GOOGLE) { "uid-b" }
        assertEquals(ChatOwnerScope.account("uid-b"), scopeStore.currentScope())
    }

    private fun activateAccount() {
        uidProvider.uid = "uid-a"
        scopeStore.activateAccount("uid-a")
    }

    private fun conversation(id: String, scope: String) =
        ConversationEntity(id, "title", 1L, 1L, ownerScope = scope)

    private fun message(id: String, scope: String) =
        ChatMessageEntity(id, "conversation", "body", true, 1L, ownerScope = scope)

    private class MutableUidProvider : AuthenticatedUidProvider {
        var uid: String? = null
        override fun currentUid(): String? = uid
    }

    private class RecordingWriter : ChatCloudWriter {
        var totalCalls = 0
        var failure: Throwable? = null
        var blockWrites = false
        var observeConcurrency = false
        var concurrent = 0
        var maxConcurrent = 0
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        override suspend fun pushConversation(uid: String, conversation: CloudConversation) = record()
        override suspend fun pushMessage(uid: String, conversationId: String, message: CloudMessage) = record()
        override suspend fun softDeleteConversation(uid: String, conversationId: String) = record()

        private suspend fun record() {
            totalCalls++
            concurrent++
            maxConcurrent = maxOf(maxConcurrent, concurrent)
            try {
                entered.complete(Unit)
                if (blockWrites) release.await()
                if (observeConcurrency) yield()
                failure?.let { throw it }
            } finally {
                concurrent--
            }
        }
    }

    private companion object {
        const val PREFS_NAME = "chat_cloud_sync_gateway_gate_test"
    }
}
