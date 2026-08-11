package com.example.bamachat.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
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
    fun migrationNineToTenPreservesRowsAsLegacyAndKeepsFtsSearchable() {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(DATABASE_NAME)
                .callback(object : SupportSQLiteOpenHelper.Callback(9) {
                    override fun onCreate(db: SupportSQLiteDatabase) = createVersionNineSchema(db)
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        val database = helper.writableDatabase
        insertLegacyRows(database)

        ChatDatabase.MIGRATION_9_10.migrate(database)

        assertEquals(1L, scalarLong(database, "SELECT COUNT(*) FROM conversations"))
        assertEquals(1L, scalarLong(database, "SELECT COUNT(*) FROM chat_messages"))
        assertEquals(1L, scalarLong(database, "SELECT COUNT(*) FROM chat_messages_fts"))
        assertEquals(
            ChatOwnerScope.LEGACY_UNCLASSIFIED,
            scalarString(database, "SELECT ownerScope FROM conversations WHERE id = 'legacy-conversation'")
        )
        assertEquals(
            ChatOwnerScope.LEGACY_UNCLASSIFIED,
            scalarString(database, "SELECT ownerScope FROM chat_messages WHERE id = 'legacy-message'")
        )
        assertTrue(indexExists(database, "index_conversations_id_ownerScope"))
        assertTrue(indexExists(database, "index_chat_messages_conversationId_ownerScope"))
        assertEquals(1L, scalarLong(database, "SELECT COUNT(*) FROM chat_messages_fts WHERE chat_messages_fts MATCH 'legacy'"))
        assertEquals(
            2,
            rowCount(database, "PRAGMA foreign_key_list(`chat_messages`)")
        )
        helper.close()
    }

    private fun createVersionNineSchema(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE `conversations` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                "`personaName` TEXT NOT NULL DEFAULT 'ASSISTANT', PRIMARY KEY(`id`))"
        )
        db.execSQL(
            "CREATE TABLE `chat_messages` (`id` TEXT NOT NULL, `conversationId` TEXT NOT NULL, " +
                "`text` TEXT NOT NULL, `isUser` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, " +
                "`imageUrl` TEXT, `sourcesJson` TEXT, `webFetchedAtIso` TEXT, PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`conversationId`) REFERENCES `conversations`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        db.execSQL("CREATE INDEX `index_chat_messages_conversationId` ON `chat_messages` (`conversationId`)")
        db.execSQL(
            "CREATE VIRTUAL TABLE `chat_messages_fts` USING FTS4(" +
                "`message_id` TEXT, `conversation_id` TEXT, `text` TEXT, `is_user` INTEGER, `timestamp` INTEGER, " +
                "notindexed=`message_id`, notindexed=`conversation_id`, notindexed=`is_user`, notindexed=`timestamp`)"
        )
    }

    private fun insertLegacyRows(db: SupportSQLiteDatabase) {
        db.execSQL(
            "INSERT INTO conversations(id, title, createdAt, updatedAt, personaName) VALUES (?, ?, ?, ?, ?)",
            arrayOf<Any?>("legacy-conversation", "Legacy", 1L, 2L, "Bama")
        )
        db.execSQL(
            "INSERT INTO chat_messages(id, conversationId, text, isUser, timestamp) VALUES (?, ?, ?, ?, ?)",
            arrayOf<Any?>("legacy-message", "legacy-conversation", "legacy text", 1, 3L)
        )
        db.execSQL(
            "INSERT INTO chat_messages_fts(message_id, conversation_id, text, is_user, timestamp) VALUES (?, ?, ?, ?, ?)",
            arrayOf<Any?>("legacy-message", "legacy-conversation", "legacy text", 1, 3L)
        )
    }

    private fun scalarLong(database: SupportSQLiteDatabase, query: String): Long =
        database.query(query).use { cursor ->
            cursor.moveToFirst()
            cursor.getLong(0)
        }

    private fun rowCount(database: SupportSQLiteDatabase, query: String): Int =
        database.query(query).use { cursor -> cursor.count }

    private fun scalarString(database: SupportSQLiteDatabase, query: String): String =
        database.query(query).use { cursor ->
            cursor.moveToFirst()
            cursor.getString(0)
        }

    private fun indexExists(database: SupportSQLiteDatabase, name: String): Boolean =
        database.query("SELECT 1 FROM sqlite_master WHERE type = 'index' AND name = ?", arrayOf(name)).use {
            it.moveToFirst()
        }

    private companion object {
        const val DATABASE_NAME = "chat_ownership_migration_test.db"
    }
}
