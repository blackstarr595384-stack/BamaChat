package com.example.bamachat.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DB,
            10,
            true,
            ChatDatabase.MIGRATION_9_10
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
            close()
        }

        var database = openDatabase()
        val claim = kotlinx.coroutines.runBlocking {
            RoomLegacyScopeClaimer(database).claim("fixture-account")
        }
        assertEquals(1, claim.claimedConversations)
        assertEquals(1, claim.claimedMessages)
        kotlinx.coroutines.runBlocking {
            val dao = database.chatDao()
            assertEquals(0, dao.countConversationsForScope(ChatOwnerScope.LEGACY_UNCLASSIFIED))
            assertEquals(0, dao.countMessagesForScope(ChatOwnerScope.LEGACY_UNCLASSIFIED))
            assertEquals(1, dao.countConversationsForScope(ChatOwnerScope.account("fixture-account")))
            assertEquals(1, dao.countMessagesForScope(ChatOwnerScope.account("fixture-account")))
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
            assertEquals(1, ftsCount(database, "guestfixture"))
            assertEquals(1, ftsCount(database, "accountfixture"))
            RoomGuestScopeChatCleaner(dao).clear(guestScope)
            assertEquals(0, dao.countConversationsForScope(guestScope))
            assertEquals(1, dao.countConversationsForScope(ChatOwnerScope.account("fixture-account")))
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
    ).addMigrations(ChatDatabase.MIGRATION_9_10).build()

    private companion object {
        const val TEST_DB = "chat-ownership-migration-instrumented"
    }
}
