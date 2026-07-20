package com.example.bamachat.data.provider

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.example.bamachat.data.local.ChatDatabase
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
class ProviderDatabaseMigrationTest {
    private val context = RuntimeEnvironment.getApplication()

    @Before
    fun removeOldTestDatabase() {
        context.deleteDatabase(DATABASE_NAME)
    }

    @After
    fun cleanUpTestDatabase() {
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun migrationEightToNinePreservesChatDataAndCreatesSecretFreeProviderTables() {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(DATABASE_NAME)
                .callback(object : SupportSQLiteOpenHelper.Callback(8) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE `conversations` (" +
                                "`id` TEXT NOT NULL, `title` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                                "`updatedAt` INTEGER NOT NULL, `personaName` TEXT NOT NULL DEFAULT 'ASSISTANT', " +
                                "PRIMARY KEY(`id`))"
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )

        val database = helper.writableDatabase
        database.execSQL(
            "INSERT INTO conversations(id, title, createdAt, updatedAt, personaName) VALUES (?, ?, ?, ?, ?)",
            arrayOf<Any?>("conversation-test", "Lokaler Test", 1L, 2L, "Bama")
        )

        ChatDatabase.MIGRATION_8_9.migrate(database)

        assertEquals(1L, scalarLong(database, "SELECT COUNT(*) FROM conversations"))
        assertTrue(tableExists(database, "providers"))
        assertTrue(tableExists(database, "provider_models"))
        val allColumns = columns(database, "providers") + columns(database, "provider_models")
        assertFalse(allColumns.any {
            it.equals("secret", ignoreCase = true) ||
                it.equals("secretValue", ignoreCase = true) ||
                it.equals("apiKey", ignoreCase = true)
        })
        assertTrue(allColumns.contains("hasSecret"))
        helper.close()
    }

    private fun tableExists(database: SupportSQLiteDatabase, table: String): Boolean =
        database.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(table)).use { it.moveToFirst() }

    private fun columns(database: SupportSQLiteDatabase, table: String): List<String> =
        database.query("PRAGMA table_info(`$table`)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(nameIndex))
            }
        }

    private fun scalarLong(database: SupportSQLiteDatabase, query: String): Long =
        database.query(query).use { cursor ->
            cursor.moveToFirst()
            cursor.getLong(0)
        }

    private companion object {
        const val DATABASE_NAME = "provider_migration_test.db"
    }
}
