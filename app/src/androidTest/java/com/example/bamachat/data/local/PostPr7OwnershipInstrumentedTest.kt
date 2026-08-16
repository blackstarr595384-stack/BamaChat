package com.example.bamachat.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bamachat.data.model.ChatMessage
import com.example.bamachat.data.repository.ChatRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PostPr7OwnershipInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: ChatDatabase
    private lateinit var repository: ChatRepository
    private lateinit var scopeStore: ChatSessionScopeStore
    private lateinit var workspaceStore: ConversationWorkspaceStore

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, ChatDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ChatRepository(database.chatDao())
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        scopeStore = ChatSessionScopeStore(prefs)
        workspaceStore = ConversationWorkspaceStore(prefs)
    }

    @After
    fun tearDown() {
        database.close()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun repeatedGuestEntryReusesScopeAndKeepsChatAndKnowledgeReachable() = runBlocking {
        val firstScope = scopeStore.startNewGuestSession()
        repository.createConversation("device-guest", ownerScope = firstScope)
        repository.saveMessage(
            "device-guest",
            ChatMessage("device-message", "device guest text", true, 1L),
            firstScope
        )
        repository.saveKnowledgeChunk(firstScope, "Device guest", "device guest knowledge", "device")
        scopeStore.prepareAccountTransition()

        val repeatedScope = scopeStore.startNewGuestSession()

        assertEquals(firstScope, repeatedScope)
        assertEquals(AccountTransitionPhase.NONE, scopeStore.transitionPhase())
        assertFalse(scopeStore.isAccountTransitionPending())
        assertNotNull(repository.getConversation("device-guest", repeatedScope))
        assertEquals(1, repository.getMessages("device-guest", repeatedScope).first().size)
        assertEquals(1, repository.searchKnowledge(repeatedScope, "device").size)
    }

    @Test
    fun cancellationKeepsGuestDataAndSuccessfulTransitionRemovesOnlyGuestScope() = runBlocking {
        val guestScope = scopeStore.startNewGuestSession()
        val accountScope = ChatOwnerScope.account("device-account")
        repository.createConversation("device-transition", ownerScope = guestScope)
        repository.saveMessage(
            "device-transition",
            ChatMessage("device-transition-message", "guest transition text", true, 2L),
            guestScope
        )
        repository.saveKnowledgeChunk(guestScope, "Guest", "guest transition knowledge", "transition")
        repository.saveKnowledgeEdge(guestScope, "guest", "relates", "transition")
        repository.saveKnowledgeChunk(accountScope, "Account", "account retained knowledge", "retained")
        repository.saveKnowledgeEdge(accountScope, "account", "relates", "retained")

        scopeStore.prepareAccountTransition()
        scopeStore.cancelAccountTransition()
        assertNotNull(repository.getConversation("device-transition", guestScope))
        assertEquals(1, repository.searchKnowledge(guestScope, "transition").size)

        scopeStore.prepareAccountTransition()
        GuestChatTransitionCoordinator(
            scopeStore,
            RoomGuestScopeChatCleaner(database.chatDao()),
            RoomLegacyScopeClaimer(database),
            repository,
            workspaceStore
        ).completeAuthenticatedTransition("device-account")

        assertNull(repository.getConversation("device-transition", guestScope))
        assertEquals(0, database.chatDao().countKnowledgeChunksForScope(guestScope))
        assertEquals(0, database.chatDao().countKnowledgeEdgesForScope(guestScope))
        assertEquals(1, database.chatDao().countKnowledgeChunksForScope(accountScope))
        assertEquals(1, database.chatDao().countKnowledgeEdgesForScope(accountScope))
        assertEquals(1, repository.searchKnowledge(accountScope, "retained").size)
    }

    private companion object {
        const val PREFS_NAME = "post_pr7_ownership_instrumented_test_preferences"
    }
}
