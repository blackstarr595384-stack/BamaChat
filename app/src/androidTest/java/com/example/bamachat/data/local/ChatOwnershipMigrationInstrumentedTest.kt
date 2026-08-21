package com.example.bamachat.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bamachat.data.repository.ChatRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatOwnershipMigrationInstrumentedTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ChatDatabase::class.java
    )

    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        context.deleteDatabase(TEST_DB)
        context.deleteDatabase(V2_TEST_DB)
        context.deleteDatabase(KNOWLEDGE_TEST_DB)
        context.getSharedPreferences(TRANSITION_PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun reconstructedVersionTwoMinimalFixtureMigratesValidatesAndPreservesData() {
        context.deleteDatabase(V2_TEST_DB)
        try {
            val databaseFile = context.getDatabasePath(V2_TEST_DB)
            databaseFile.parentFile?.mkdirs()
            SQLiteDatabase.openOrCreateDatabase(databaseFile, null).use { db ->
                db.execSQL("PRAGMA foreign_keys = ON")
                db.execSQL(
                    "CREATE TABLE conversations (id TEXT NOT NULL, title TEXT NOT NULL, " +
                        "createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, personaName TEXT NOT NULL, " +
                        "PRIMARY KEY(id))"
                )
                db.execSQL(
                    "CREATE TABLE chat_messages (id TEXT NOT NULL, conversationId TEXT NOT NULL, text TEXT NOT NULL, " +
                        "isUser INTEGER NOT NULL, timestamp INTEGER NOT NULL, imageUrl TEXT, PRIMARY KEY(id), " +
                        "FOREIGN KEY(conversationId) REFERENCES conversations(id) ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX index_chat_messages_conversationId ON chat_messages(conversationId)")
                db.execSQL(
                    "INSERT INTO conversations(id,title,createdAt,updatedAt,personaName) " +
                        "VALUES('device-v2-conversation','Device v2',1,2,'ASSISTANT')"
                )
                db.execSQL(
                    "INSERT INTO chat_messages(id,conversationId,text,isUser,timestamp,imageUrl) " +
                        "VALUES('device-v2-message','device-v2-conversation','devicev2fixture retained',1,3,NULL)"
                )
                db.execSQL("PRAGMA user_version = 2")
            }

            val database = Room.databaseBuilder(context, ChatDatabase::class.java, V2_TEST_DB)
                .addMigrations(
                    ChatDatabase.MIGRATION_1_2,
                    ChatDatabase.MIGRATION_2_3,
                    ChatDatabase.MIGRATION_3_4,
                    ChatDatabase.MIGRATION_4_5,
                    ChatDatabase.MIGRATION_5_6,
                    ChatDatabase.MIGRATION_6_7,
                    ChatDatabase.MIGRATION_7_8,
                    ChatDatabase.MIGRATION_8_9,
                    ChatDatabase.MIGRATION_9_10,
                    ChatDatabase.MIGRATION_10_11
                )
                .build()
            try {
                database.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { cursor ->
                    assertFalse(cursor.moveToFirst())
                }
                runBlocking {
                    assertEquals(
                        1,
                        database.chatDao().countConversationsForScope(ChatOwnerScope.LEGACY_UNCLASSIFIED)
                    )
                    assertEquals(1, database.chatDao().countMessagesForScope(ChatOwnerScope.LEGACY_UNCLASSIFIED))
                }
                assertEquals(1, ftsCount(database, "devicev2fixture"))
            } finally {
                database.close()
            }
        } finally {
            context.deleteDatabase(V2_TEST_DB)
        }
    }

    @Test
    fun versionTenKnowledgeOnlyIsClaimedByLoggedInUidAndNotBySecondUid() {
        helper.createDatabase(KNOWLEDGE_TEST_DB, 10).apply {
            execSQL(
                "INSERT INTO knowledge_chunks(sourceTitle,content,keywords,sourceType,createdAt) " +
                    "VALUES('Device legacy','device knowledge only','device','text',1)"
            )
            execSQL(
                "INSERT INTO knowledge_edges(fromConcept,relation,toConcept,weight,updatedAt) " +
                    "VALUES('device-source','relates','device-target',0.8,2)"
            )
            close()
        }
        helper.runMigrationsAndValidate(
            KNOWLEDGE_TEST_DB,
            11,
            true,
            ChatDatabase.MIGRATION_10_11
        ).close()

        val database = Room.databaseBuilder(context, ChatDatabase::class.java, KNOWLEDGE_TEST_DB)
            .addMigrations(ChatDatabase.MIGRATION_10_11)
            .build()
        val repository = ChatRepository(database.chatDao())
        val prefs = context.getSharedPreferences(TRANSITION_PREFS, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val scopeStore = ChatSessionScopeStore(prefs)
        val firstScope = scopeStore.activateAccount("device-knowledge-owner")
        var cloudGateClosedDuringClaim = false
        val coordinator = GuestChatTransitionCoordinator(
            scopeStore,
            RoomGuestScopeChatCleaner(database.chatDao()),
            LegacyScopeClaimer { uid ->
                cloudGateClosedDuringClaim = !scopeStore.isCloudSyncAllowed(uid)
                RoomLegacyScopeClaimer(database).claim(uid)
            },
            repository,
            ConversationWorkspaceStore(prefs)
        )

        runBlocking {
            val first = coordinator.completeAuthenticatedTransition("device-knowledge-owner")
            assertEquals(1, first.legacyClaim.claimedKnowledgeChunks)
            assertEquals(1, first.legacyClaim.claimedKnowledgeEdges)
            assertTrue(cloudGateClosedDuringClaim)
            assertEquals(1, database.chatDao().countKnowledgeChunksForScope(firstScope))
            assertEquals(1, database.chatDao().countKnowledgeEdgesForScope(firstScope))
            val repeated = coordinator.completeAuthenticatedTransition("device-knowledge-owner")
            assertEquals(0, repeated.legacyClaim.claimedKnowledgeChunks)
            assertEquals(0, repeated.legacyClaim.claimedKnowledgeEdges)
            scopeStore.deactivateSession()
            val secondScope = scopeStore.activateAccount("device-second-owner")
            val second = coordinator.completeAuthenticatedTransition("device-second-owner")
            assertEquals(0, second.legacyClaim.claimedKnowledgeChunks)
            assertEquals(0, second.legacyClaim.claimedKnowledgeEdges)
            assertEquals(0, database.chatDao().countKnowledgeChunksForScope(secondScope))
            assertEquals(0, database.chatDao().countKnowledgeEdgesForScope(secondScope))
            assertEquals(1, database.chatDao().countKnowledgeChunksForScope(firstScope))
            assertEquals(1, database.chatDao().countKnowledgeEdgesForScope(firstScope))
        }
        database.close()
    }

    @Test
    fun authenticVersion9FixtureMigratesValidatesReopensClaimsAndCleansByScope() {
        helper.createDatabase(TEST_DB, 9).apply {
            execSQL(
                "INSERT INTO conversations(id,title,createdAt,updatedAt,personaName) " +
                    "VALUES('legacy-conversation','Legacy',1,2,'ASSISTANT')"
            )
            execSQL(
                "INSERT INTO chat_messages(id,conversationId,text,isUser,timestamp) " +
                    "VALUES('legacy-message','legacy-conversation','accountfixture text',1,3)"
            )
            execSQL(
                "INSERT INTO chat_messages_fts(message_id,conversation_id,text,is_user,timestamp) " +
                    "VALUES('legacy-message','legacy-conversation','accountfixture text',1,3)"
            )
            execSQL(
                "INSERT INTO knowledge_chunks(sourceTitle,content,keywords,sourceType,createdAt) " +
                    "VALUES('Legacy knowledge','legacy knowledge','legacy','text',4)"
            )
            execSQL(
                "INSERT INTO knowledge_edges(fromConcept,relation,toConcept,weight,updatedAt) " +
                    "VALUES('legacy-source','relates','legacy-target',0.8,5)"
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DB,
            11,
            true,
            ChatDatabase.MIGRATION_9_10,
            ChatDatabase.MIGRATION_10_11
        ).apply {
            query("PRAGMA foreign_key_check").use { cursor -> assertEquals(0, cursor.count) }
            query("PRAGMA index_list('conversations')").use { cursor ->
                val names = buildSet {
                    while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
                }
                assertTrue(names.contains("index_conversations_ownerScope"))
                assertTrue(names.contains("index_conversations_id_ownerScope"))
            }
            query(
                "SELECT ownerScope FROM conversations WHERE id='legacy-conversation'"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(ChatOwnerScope.LEGACY_UNCLASSIFIED, cursor.getString(0))
            }
            query(
                "SELECT ownerScope FROM chat_messages WHERE id='legacy-message'"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(ChatOwnerScope.LEGACY_UNCLASSIFIED, cursor.getString(0))
            }
            query("SELECT message_id FROM chat_messages_fts WHERE chat_messages_fts MATCH 'accountfixture'")
                .use { cursor -> assertTrue(cursor.moveToFirst()) }
            query("SELECT ownerScope FROM knowledge_chunks WHERE sourceTitle='Legacy knowledge'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(ChatOwnerScope.LEGACY_UNCLASSIFIED, cursor.getString(0))
            }
            query("SELECT ownerScope FROM knowledge_edges WHERE fromConcept='legacy-source'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(ChatOwnerScope.LEGACY_UNCLASSIFIED, cursor.getString(0))
            }
            close()
        }

        var database = openDatabase()
        val claim = kotlinx.coroutines.runBlocking {
            RoomLegacyScopeClaimer(database).claim("fixture-account")
        }
        assertEquals(1, claim.claimedConversations)
        assertEquals(1, claim.claimedMessages)
        assertEquals(1, claim.claimedKnowledgeChunks)
        assertEquals(1, claim.claimedKnowledgeEdges)
        kotlinx.coroutines.runBlocking {
            val dao = database.chatDao()
            assertEquals(0, dao.countConversationsForScope(ChatOwnerScope.LEGACY_UNCLASSIFIED))
            assertEquals(0, dao.countMessagesForScope(ChatOwnerScope.LEGACY_UNCLASSIFIED))
            assertEquals(1, dao.countConversationsForScope(ChatOwnerScope.account("fixture-account")))
            assertEquals(1, dao.countMessagesForScope(ChatOwnerScope.account("fixture-account")))
            assertEquals(1, dao.countKnowledgeChunksForScope(ChatOwnerScope.account("fixture-account")))
            assertEquals(1, dao.countKnowledgeEdgesForScope(ChatOwnerScope.account("fixture-account")))
        }
        assertEquals(1, ftsCount(database, "accountfixture"))
        database.close()

        database = openDatabase()
        kotlinx.coroutines.runBlocking {
            val dao = database.chatDao()
            assertEquals(1, dao.countConversationsForScope(ChatOwnerScope.account("fixture-account")))
            assertEquals(1, dao.countMessagesForScope(ChatOwnerScope.account("fixture-account")))
            val secondClaim = RoomLegacyScopeClaimer(database).claim("fixture-account")
            assertEquals(0, secondClaim.claimedConversations)
            assertEquals(0, secondClaim.claimedMessages)
            val guestScope = ChatOwnerScope.guest("fixture-session")
            database.openHelper.writableDatabase.execSQL(
                "INSERT INTO conversations(id,title,createdAt,updatedAt,personaName,ownerScope) " +
                    "VALUES('guest-conversation','Guest',4,5,'ASSISTANT',?)",
                arrayOf(guestScope)
            )
            database.openHelper.writableDatabase.execSQL(
                "INSERT INTO chat_messages(id,conversationId,text,isUser,timestamp,ownerScope) " +
                    "VALUES('guest-message','guest-conversation','guestfixture text',1,6,?)",
                arrayOf(guestScope)
            )
            database.openHelper.writableDatabase.execSQL(
                "INSERT INTO chat_messages_fts(message_id,conversation_id,text,is_user,timestamp) " +
                    "VALUES('guest-message','guest-conversation','guestfixture text',1,6)"
            )
            dao.insertKnowledgeChunk(
                KnowledgeChunkEntity(
                    sourceTitle = "Guest knowledge",
                    content = "guestfixture knowledge",
                    keywords = "guestfixture",
                    createdAt = 7,
                    ownerScope = guestScope
                )
            )
            dao.insertKnowledgeEdge(
                KnowledgeEdgeEntity(
                    fromConcept = "guestfixture",
                    relation = "relates",
                    toConcept = "knowledge",
                    updatedAt = 8,
                    ownerScope = guestScope
                )
            )
            assertEquals(1, ftsCount(database, "guestfixture"))
            assertEquals(1, ftsCount(database, "accountfixture"))
            RoomGuestScopeChatCleaner(dao).clear(guestScope)
            assertEquals(0, dao.countConversationsForScope(guestScope))
            assertEquals(1, dao.countConversationsForScope(ChatOwnerScope.account("fixture-account")))
            assertEquals(0, dao.countKnowledgeChunksForScope(guestScope))
            assertEquals(0, dao.countKnowledgeEdgesForScope(guestScope))
            assertEquals(1, dao.countKnowledgeChunksForScope(ChatOwnerScope.account("fixture-account")))
            assertEquals(1, dao.countKnowledgeEdgesForScope(ChatOwnerScope.account("fixture-account")))
            assertEquals(0, ftsCount(database, "guestfixture"))
            assertEquals(1, ftsCount(database, "accountfixture"))
            val repeatedCleanup = RoomGuestScopeChatCleaner(dao).clear(guestScope)
            assertEquals(0, repeatedCleanup.deletedConversations)
            assertEquals(0, repeatedCleanup.deletedMessages)
        }
        database.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { cursor ->
            assertFalse(cursor.moveToFirst())
        }
        database.close()

        database = openDatabase()
        kotlinx.coroutines.runBlocking {
            val dao = database.chatDao()
            assertEquals(0, dao.countConversationsForScope(ChatOwnerScope.guest("fixture-session")))
            assertEquals(1, dao.countConversationsForScope(ChatOwnerScope.account("fixture-account")))
            val repeatedClaim = RoomLegacyScopeClaimer(database).claim("fixture-account")
            assertEquals(0, repeatedClaim.claimedConversations)
            assertEquals(0, repeatedClaim.claimedMessages)
        }
        assertEquals(0, ftsCount(database, "guestfixture"))
        assertEquals(1, ftsCount(database, "accountfixture"))
        database.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { cursor ->
            assertFalse(cursor.moveToFirst())
        }
        database.close()
    }

    private fun ftsCount(database: ChatDatabase, query: String): Int =
        database.openHelper.readableDatabase.query(
            "SELECT message_id FROM chat_messages_fts WHERE chat_messages_fts MATCH ?",
            arrayOf(query)
        ).use { cursor -> cursor.count }

    private fun openDatabase(): ChatDatabase = Room.databaseBuilder(
        context,
        ChatDatabase::class.java,
        TEST_DB
    ).addMigrations(ChatDatabase.MIGRATION_9_10, ChatDatabase.MIGRATION_10_11).build()

    private companion object {
        const val TEST_DB = "chat-ownership-migration-instrumented"
        const val V2_TEST_DB = "chat-v2-migration-instrumented"
        const val KNOWLEDGE_TEST_DB = "chat-v10-knowledge-migration-instrumented"
        const val TRANSITION_PREFS = "chat-ownership-migration-transition-instrumented"
    }
}
