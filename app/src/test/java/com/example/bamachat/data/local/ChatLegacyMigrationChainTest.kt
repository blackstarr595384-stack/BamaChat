package com.example.bamachat.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.bamachat.data.repository.ChatRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class ChatLegacyMigrationChainTest {
    private val context = RuntimeEnvironment.getApplication()

    @Before
    fun removeOldDatabase() {
        context.deleteDatabase(DATABASE_NAME)
    }

    @After
    fun cleanUpDatabase() {
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun authenticVersionSevenFixtureMigratesToSchemaEleven() = runBlocking {
        assertLegacyFixtureMigrates(7)
    }

    @Test
    fun versionTenKnowledgeFixtureMigratesToSchemaEleven() = runBlocking {
        createFixture(10)

        val database = openMigratedDatabase()
        val sqlite = database.openHelper.writableDatabase
        assertEquals(11, sqlite.version)
        assertEquals(0, rowCount(sqlite, "PRAGMA foreign_key_check"))
        assertEquals(
            ChatOwnerScope.LEGACY_UNCLASSIFIED,
            scalarString(sqlite, "SELECT ownerScope FROM knowledge_chunks WHERE sourceTitle = 'Legacy knowledge'")
        )
        assertEquals(
            ChatOwnerScope.LEGACY_UNCLASSIFIED,
            scalarString(sqlite, "SELECT ownerScope FROM knowledge_edges WHERE fromConcept = 'legacy-source'")
        )
        assertTrue(indexExists(sqlite, "index_knowledge_chunks_ownerScope"))
        assertTrue(indexExists(sqlite, "index_knowledge_edges_ownerScope_fromConcept_relation_toConcept"))
        database.close()
    }

    @Test
    fun versionTenKnowledgeOnlyIsClaimedByCurrentAccountExactlyOnce() = runBlocking {
        createVersionTenKnowledgeOnlyFixture()

        val database = openMigratedDatabase()
        val repository = ChatRepository(database.chatDao())
        val prefs = context.getSharedPreferences(TRANSITION_PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val scopeStore = ChatSessionScopeStore(prefs)
        val accountScope = scopeStore.activateAccount("knowledge-owner")
        var cloudGateClosedDuringClaim = false
        val coordinator = GuestChatTransitionCoordinator(
            scopeStore = scopeStore,
            cleaner = RoomGuestScopeChatCleaner(database.chatDao()),
            legacyClaimer = LegacyScopeClaimer { uid ->
                cloudGateClosedDuringClaim = !scopeStore.isCloudSyncAllowed(uid)
                RoomLegacyScopeClaimer(database).claim(uid)
            },
            repository = repository,
            workspaceStore = ConversationWorkspaceStore(prefs)
        )

        assertEquals(0, repository.legacyConversationCount())
        assertEquals(0, repository.legacyMessageCount())
        assertEquals(1, repository.legacyKnowledgeChunkCount())
        assertEquals(1, repository.legacyKnowledgeEdgeCount())
        val firstClaim = coordinator.completeAuthenticatedTransition("knowledge-owner")
        assertEquals(1, firstClaim.legacyClaim.claimedKnowledgeChunks)
        assertEquals(1, firstClaim.legacyClaim.claimedKnowledgeEdges)
        assertTrue(cloudGateClosedDuringClaim)
        assertEquals(0, repository.legacyKnowledgeChunkCount())
        assertEquals(0, repository.legacyKnowledgeEdgeCount())
        assertEquals(1, database.chatDao().countKnowledgeChunksForScope(accountScope))
        assertEquals(1, database.chatDao().countKnowledgeEdgesForScope(accountScope))

        val repeatedClaim = coordinator.completeAuthenticatedTransition("knowledge-owner")
        assertEquals(0, repeatedClaim.legacyClaim.claimedKnowledgeChunks)
        assertEquals(0, repeatedClaim.legacyClaim.claimedKnowledgeEdges)
        assertThrows(IllegalStateException::class.java) {
            runBlocking { coordinator.completeAuthenticatedTransition("other-owner") }
        }

        scopeStore.deactivateSession()
        val otherScope = scopeStore.activateAccount("other-owner")
        val otherClaim = coordinator.completeAuthenticatedTransition("other-owner")
        assertEquals(0, otherClaim.legacyClaim.claimedKnowledgeChunks)
        assertEquals(0, otherClaim.legacyClaim.claimedKnowledgeEdges)
        assertEquals(0, database.chatDao().countKnowledgeChunksForScope(otherScope))
        assertEquals(0, database.chatDao().countKnowledgeEdgesForScope(otherScope))
        assertEquals(1, database.chatDao().countKnowledgeChunksForScope(accountScope))
        assertEquals(1, database.chatDao().countKnowledgeEdgesForScope(accountScope))
        prefs.edit().clear().commit()
        database.close()
    }

    private fun createVersionTenKnowledgeOnlyFixture() {
        createFixture(10)
        val current = Room.databaseBuilder(context, ChatDatabase::class.java, DATABASE_NAME)
            .addMigrations(ChatDatabase.MIGRATION_10_11)
            .allowMainThreadQueries()
            .build()
        val db = current.openHelper.writableDatabase
        db.execSQL("DELETE FROM chat_messages")
        db.execSQL("DELETE FROM conversations")
        db.execSQL("PRAGMA foreign_keys = OFF")
        downgradeKnowledgeToVersionTen(db)
        db.execSQL("PRAGMA user_version = 10")
        current.close()
    }

    private suspend fun assertLegacyFixtureMigrates(startVersion: Int) {
        createFixture(startVersion)

        val database = openMigratedDatabase()
        val sqlite = database.openHelper.writableDatabase
        assertEquals(11, sqlite.version)
        assertFinalConversationMessageSchema(sqlite)
        assertEquals(1L, scalarLong(sqlite, "SELECT COUNT(*) FROM conversations"))
        assertEquals(1L, scalarLong(sqlite, "SELECT COUNT(*) FROM chat_messages"))
        assertEquals(1L, scalarLong(sqlite, "SELECT COUNT(*) FROM knowledge_chunks"))
        assertEquals(1L, scalarLong(sqlite, "SELECT COUNT(*) FROM knowledge_edges"))
        assertEquals(
            ChatOwnerScope.LEGACY_UNCLASSIFIED,
            scalarString(sqlite, "SELECT ownerScope FROM conversations WHERE id = 'legacy-conversation'")
        )
        assertEquals(
            ChatOwnerScope.LEGACY_UNCLASSIFIED,
            scalarString(sqlite, "SELECT ownerScope FROM chat_messages WHERE id = 'legacy-message'")
        )
        assertEquals(
            ChatOwnerScope.LEGACY_UNCLASSIFIED,
            scalarString(sqlite, "SELECT ownerScope FROM knowledge_chunks WHERE sourceTitle = 'Legacy knowledge'")
        )
        assertEquals(1L, scalarLong(sqlite, "SELECT COUNT(*) FROM chat_messages_fts WHERE chat_messages_fts MATCH 'migrationfixture'"))

        val claim = RoomLegacyScopeClaimer(database).claim("migration-account")
        val accountScope = ChatOwnerScope.account("migration-account")
        assertEquals(1, claim.claimedConversations)
        assertEquals(1, claim.claimedMessages)
        assertEquals(1, claim.claimedKnowledgeChunks)
        assertEquals(1, claim.claimedKnowledgeEdges)
        assertEquals(1, database.chatDao().countConversationsForScope(accountScope))
        assertEquals(1, database.chatDao().countMessagesForScope(accountScope))
        assertEquals(1, database.chatDao().countKnowledgeChunksForScope(accountScope))
        assertEquals(1, database.chatDao().countKnowledgeEdgesForScope(accountScope))
        assertEquals(1, ChatRepository(database.chatDao()).searchKnowledge(accountScope, "legacy").size)
        database.close()
    }

    private fun createFixture(version: Int) {
        val current = Room.databaseBuilder(context, ChatDatabase::class.java, DATABASE_NAME)
            .allowMainThreadQueries()
            .build()
        val db = current.openHelper.writableDatabase
        db.execSQL(
            "INSERT INTO conversations(id,title,createdAt,updatedAt,personaName,ownerScope) " +
                "VALUES('legacy-conversation','Legacy',1,2,'ASSISTANT',?)",
            arrayOf(ChatOwnerScope.LEGACY_UNCLASSIFIED)
        )
        db.execSQL(
            "INSERT INTO chat_messages(id,conversationId,text,isUser,timestamp,ownerScope) " +
                "VALUES('legacy-message','legacy-conversation','migrationfixture text',1,3,?)",
            arrayOf(ChatOwnerScope.LEGACY_UNCLASSIFIED)
        )
        db.execSQL(
            "INSERT INTO chat_messages_fts(message_id,conversation_id,text,is_user,timestamp) " +
                "VALUES('legacy-message','legacy-conversation','migrationfixture text',1,3)"
        )
        db.execSQL(
            "INSERT INTO knowledge_chunks(sourceTitle,content,keywords,sourceType,createdAt,ownerScope) " +
                "VALUES('Legacy knowledge','legacy content','legacy','text',4,?)",
            arrayOf(ChatOwnerScope.LEGACY_UNCLASSIFIED)
        )
        db.execSQL(
            "INSERT INTO knowledge_edges(fromConcept,relation,toConcept,weight,updatedAt,ownerScope) " +
                "VALUES('legacy-source','relates','legacy-target',0.8,5,?)",
            arrayOf(ChatOwnerScope.LEGACY_UNCLASSIFIED)
        )

        db.execSQL("PRAGMA foreign_keys = OFF")
        if (version <= 9) downgradeChatsToVersionNine(db)
        if (version <= 10) downgradeKnowledgeToVersionTen(db)
        if (version <= 7) {
            db.execSQL("DROP TABLE IF EXISTS chat_messages_fts")
            db.execSQL("DROP TABLE IF EXISTS provider_models")
            db.execSQL("DROP TABLE IF EXISTS providers")
        }
        db.execSQL("PRAGMA user_version = $version")
        current.close()
    }

    private fun downgradeChatsToVersionNine(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE conversations_legacy (id TEXT NOT NULL, title TEXT NOT NULL, createdAt INTEGER NOT NULL, " +
                "updatedAt INTEGER NOT NULL, personaName TEXT NOT NULL DEFAULT 'ASSISTANT', PRIMARY KEY(id))"
        )
        db.execSQL(
            "INSERT INTO conversations_legacy(id,title,createdAt,updatedAt,personaName) " +
                "SELECT id,title,createdAt,updatedAt,personaName FROM conversations"
        )
        db.execSQL(
            "CREATE TABLE chat_messages_legacy (id TEXT NOT NULL, conversationId TEXT NOT NULL, text TEXT NOT NULL, " +
                "isUser INTEGER NOT NULL, timestamp INTEGER NOT NULL, imageUrl TEXT, sourcesJson TEXT, " +
                "webFetchedAtIso TEXT, PRIMARY KEY(id), FOREIGN KEY(conversationId) REFERENCES conversations_legacy(id) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        db.execSQL(
            "INSERT INTO chat_messages_legacy(id,conversationId,text,isUser,timestamp,imageUrl,sourcesJson,webFetchedAtIso) " +
                "SELECT id,conversationId,text,isUser,timestamp,imageUrl,sourcesJson,webFetchedAtIso FROM chat_messages"
        )
        db.execSQL("DROP TABLE chat_messages")
        db.execSQL("DROP TABLE conversations")
        db.execSQL("ALTER TABLE conversations_legacy RENAME TO conversations")
        db.execSQL("ALTER TABLE chat_messages_legacy RENAME TO chat_messages")
        db.execSQL("CREATE INDEX index_chat_messages_conversationId ON chat_messages(conversationId)")
    }

    private fun downgradeKnowledgeToVersionTen(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE knowledge_chunks_v10 (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, sourceTitle TEXT NOT NULL, " +
                "content TEXT NOT NULL, keywords TEXT NOT NULL, sourceType TEXT NOT NULL, createdAt INTEGER NOT NULL)"
        )
        db.execSQL(
            "INSERT INTO knowledge_chunks_v10(id,sourceTitle,content,keywords,sourceType,createdAt) " +
                "SELECT id,sourceTitle,content,keywords,sourceType,createdAt FROM knowledge_chunks"
        )
        db.execSQL(
            "CREATE TABLE knowledge_edges_v10 (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, fromConcept TEXT NOT NULL, " +
                "relation TEXT NOT NULL, toConcept TEXT NOT NULL, weight REAL NOT NULL, updatedAt INTEGER NOT NULL)"
        )
        db.execSQL(
            "INSERT INTO knowledge_edges_v10(id,fromConcept,relation,toConcept,weight,updatedAt) " +
                "SELECT id,fromConcept,relation,toConcept,weight,updatedAt FROM knowledge_edges"
        )
        db.execSQL("DROP TABLE knowledge_chunks")
        db.execSQL("DROP TABLE knowledge_edges")
        db.execSQL("ALTER TABLE knowledge_chunks_v10 RENAME TO knowledge_chunks")
        db.execSQL("ALTER TABLE knowledge_edges_v10 RENAME TO knowledge_edges")
        db.execSQL("CREATE INDEX index_knowledge_chunks_sourceTitle ON knowledge_chunks(sourceTitle)")
        db.execSQL("CREATE INDEX index_knowledge_chunks_createdAt ON knowledge_chunks(createdAt)")
        db.execSQL("CREATE INDEX index_knowledge_edges_fromConcept ON knowledge_edges(fromConcept)")
        db.execSQL("CREATE INDEX index_knowledge_edges_toConcept ON knowledge_edges(toConcept)")
        db.execSQL(
            "CREATE UNIQUE INDEX index_knowledge_edges_fromConcept_relation_toConcept " +
                "ON knowledge_edges(fromConcept,relation,toConcept)"
        )
    }

    private fun openMigratedDatabase(): ChatDatabase =
        Room.databaseBuilder(context, ChatDatabase::class.java, DATABASE_NAME)
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
            .allowMainThreadQueries()
            .build()
            .also { it.openHelper.writableDatabase }

    private fun assertFinalConversationMessageSchema(database: SupportSQLiteDatabase) {
        assertEquals(0, rowCount(database, "PRAGMA foreign_key_check"))
        assertEquals(
            listOf(
                "conversationId->id@conversations",
                "ownerScope->ownerScope@conversations"
            ),
            foreignKeyMappings(database, "chat_messages")
        )
        assertTrue(indexExists(database, "index_conversations_ownerScope"))
        assertTrue(indexExists(database, "index_conversations_id_ownerScope"))
        assertTrue(indexExists(database, "index_chat_messages_conversationId"))
        assertTrue(indexExists(database, "index_chat_messages_ownerScope"))
        assertTrue(indexExists(database, "index_chat_messages_conversationId_ownerScope"))
        assertFalse(tableExists(database, "conversations_persona_default_backup"))
        assertFalse(tableExists(database, "conversations_persona_default_rebuilt"))
        assertFalse(tableExists(database, "chat_messages_persona_default_backup"))
        assertFalse(tableExists(database, "chat_messages_persona_default_rebuilt"))
    }

    private fun foreignKeyMappings(database: SupportSQLiteDatabase, table: String): List<String> =
        database.query("PRAGMA foreign_key_list(`$table`)").use { cursor ->
            val tableIndex = cursor.getColumnIndexOrThrow("table")
            val fromIndex = cursor.getColumnIndexOrThrow("from")
            val toIndex = cursor.getColumnIndexOrThrow("to")
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        "${cursor.getString(fromIndex)}->${cursor.getString(toIndex)}@" +
                            cursor.getString(tableIndex)
                    )
                }
            }.sorted()
        }

    private fun scalarLong(database: SupportSQLiteDatabase, query: String): Long =
        database.query(query).use { cursor ->
            cursor.moveToFirst()
            cursor.getLong(0)
        }

    private fun scalarString(database: SupportSQLiteDatabase, query: String): String =
        database.query(query).use { cursor ->
            cursor.moveToFirst()
            cursor.getString(0)
        }

    private fun rowCount(database: SupportSQLiteDatabase, query: String): Int =
        database.query(query).use { cursor -> cursor.count }

    private fun indexExists(database: SupportSQLiteDatabase, name: String): Boolean =
        database.query("SELECT 1 FROM sqlite_master WHERE type = 'index' AND name = ?", arrayOf(name)).use {
            it.moveToFirst()
        }

    private fun tableExists(database: SupportSQLiteDatabase, name: String): Boolean =
        database.query("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(name)).use {
            it.moveToFirst()
        }

    private companion object {
        const val DATABASE_NAME = "chat_legacy_chain_migration_test.db"
        const val TRANSITION_PREFS_NAME = "chat_legacy_chain_transition_test"
    }
}
