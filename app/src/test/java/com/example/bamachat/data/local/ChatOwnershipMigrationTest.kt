package com.example.bamachat.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.bamachat.data.repository.ChatRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class ChatOwnershipMigrationTest {
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
    fun roomMigratesRealVersionNineDatabaseAndLegacyClaimSurvivesReopen() = runBlocking {
        createCompleteVersionNineDatabase()

        val migrated = openVersionElevenDatabase()
        val sqlite = migrated.openHelper.writableDatabase
        assertEquals(0, rowCount(sqlite, "PRAGMA foreign_key_check"))
        assertTrue(indexExists(sqlite, "index_conversations_ownerScope"))
        assertTrue(indexExists(sqlite, "index_conversations_id_ownerScope"))
        assertTrue(indexExists(sqlite, "index_chat_messages_ownerScope"))
        assertTrue(indexExists(sqlite, "index_chat_messages_conversationId_ownerScope"))
        assertEquals(1L, scalarLong(sqlite, "SELECT COUNT(*) FROM conversations"))
        assertEquals(1L, scalarLong(sqlite, "SELECT COUNT(*) FROM chat_messages"))
        assertLegacyRowsAndFts(sqlite)
        migrated.close()

        val reopened = openVersionElevenDatabase()
        assertLegacyRowsAndFts(reopened.openHelper.writableDatabase)
        val claim = RoomLegacyScopeClaimer(reopened).claim("uid-migration")
        val accountScope = ChatOwnerScope.account("uid-migration")
        assertEquals(1, claim.claimedConversations)
        assertEquals(1, claim.claimedMessages)
        assertEquals(0, reopened.chatDao().countConversationsForScope(ChatOwnerScope.LEGACY_UNCLASSIFIED))
        assertEquals(0, reopened.chatDao().countMessagesForScope(ChatOwnerScope.LEGACY_UNCLASSIFIED))
        assertEquals(1, reopened.chatDao().countConversationsForScope(accountScope))
        assertEquals(1, reopened.chatDao().countMessagesForScope(accountScope))
        assertEquals(1, ChatRepository(reopened.chatDao()).searchMessages("legacy", accountScope).size)
        reopened.close()

        val claimedReopen = openVersionElevenDatabase()
        assertEquals(0, claimedReopen.chatDao().countConversationsForScope(ChatOwnerScope.LEGACY_UNCLASSIFIED))
        assertEquals(1, claimedReopen.chatDao().countConversationsForScope(accountScope))
        assertEquals(0, rowCount(claimedReopen.openHelper.writableDatabase, "PRAGMA foreign_key_check"))
        assertEquals(1, ChatRepository(claimedReopen.chatDao()).searchMessages("legacy", accountScope).size)
        claimedReopen.close()
    }

    private fun createCompleteVersionNineDatabase() {
        val current = Room.databaseBuilder(context, ChatDatabase::class.java, DATABASE_NAME)
            .allowMainThreadQueries()
            .build()
        val db = current.openHelper.writableDatabase
        db.execSQL(
            "INSERT INTO conversations(id, title, createdAt, updatedAt, personaName, ownerScope) VALUES (?, ?, ?, ?, ?, ?)",
            arrayOf<Any?>("legacy-conversation", "Legacy", 1L, 2L, "Bama", ChatOwnerScope.LEGACY_UNCLASSIFIED)
        )
        db.execSQL(
            "INSERT INTO chat_messages(id, conversationId, text, isUser, timestamp, ownerScope) VALUES (?, ?, ?, ?, ?, ?)",
            arrayOf<Any?>("legacy-message", "legacy-conversation", "legacy text", 1, 3L, ChatOwnerScope.LEGACY_UNCLASSIFIED)
        )
        db.execSQL(
            "INSERT INTO chat_messages_fts(message_id, conversation_id, text, is_user, timestamp) VALUES (?, ?, ?, ?, ?)",
            arrayOf<Any?>("legacy-message", "legacy-conversation", "legacy text", 1, 3L)
        )

        db.execSQL("PRAGMA foreign_keys = OFF")
        db.execSQL(
            "CREATE TABLE conversations_v9 (id TEXT NOT NULL, title TEXT NOT NULL, createdAt INTEGER NOT NULL, " +
                "updatedAt INTEGER NOT NULL, personaName TEXT NOT NULL DEFAULT 'ASSISTANT', PRIMARY KEY(id))"
        )
        db.execSQL(
            "INSERT INTO conversations_v9(id, title, createdAt, updatedAt, personaName) " +
                "SELECT id, title, createdAt, updatedAt, personaName FROM conversations"
        )
        db.execSQL(
            "CREATE TABLE chat_messages_v9 (id TEXT NOT NULL, conversationId TEXT NOT NULL, text TEXT NOT NULL, " +
                "isUser INTEGER NOT NULL, timestamp INTEGER NOT NULL, imageUrl TEXT, sourcesJson TEXT, " +
                "webFetchedAtIso TEXT, PRIMARY KEY(id), FOREIGN KEY(conversationId) REFERENCES conversations_v9(id) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        db.execSQL(
            "INSERT INTO chat_messages_v9(id, conversationId, text, isUser, timestamp, imageUrl, sourcesJson, webFetchedAtIso) " +
                "SELECT id, conversationId, text, isUser, timestamp, imageUrl, sourcesJson, webFetchedAtIso FROM chat_messages"
        )
        db.execSQL("DROP TABLE chat_messages")
        db.execSQL("DROP TABLE conversations")
        db.execSQL("ALTER TABLE conversations_v9 RENAME TO conversations")
        db.execSQL("ALTER TABLE chat_messages_v9 RENAME TO chat_messages")
        db.execSQL("CREATE INDEX index_chat_messages_conversationId ON chat_messages(conversationId)")
        db.execSQL("PRAGMA user_version = 9")
        current.close()
    }

    private fun openVersionElevenDatabase(): ChatDatabase =
        Room.databaseBuilder(context, ChatDatabase::class.java, DATABASE_NAME)
            .addMigrations(ChatDatabase.MIGRATION_9_10, ChatDatabase.MIGRATION_10_11)
            .allowMainThreadQueries()
            .build()
            .also { it.openHelper.writableDatabase }

    private fun assertLegacyRowsAndFts(database: SupportSQLiteDatabase) {
        assertEquals(
            ChatOwnerScope.LEGACY_UNCLASSIFIED,
            scalarString(database, "SELECT ownerScope FROM conversations WHERE id = 'legacy-conversation'")
        )
        assertEquals(
            ChatOwnerScope.LEGACY_UNCLASSIFIED,
            scalarString(database, "SELECT ownerScope FROM chat_messages WHERE id = 'legacy-message'")
        )
        assertEquals(
            1L,
            scalarLong(database, "SELECT COUNT(*) FROM chat_messages_fts WHERE chat_messages_fts MATCH 'legacy'")
        )
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

    private companion object {
        const val DATABASE_NAME = "chat_ownership_room_migration_test.db"
    }
}
