package com.example.bamachat.data.local

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.bamachat.data.provider.local.ProviderDao
import com.example.bamachat.data.provider.local.ProviderEntity
import com.example.bamachat.data.provider.local.ProviderModelEntity
import com.example.bamachat.data.provider.local.ProviderRoomSchema

@Database(
    entities = [
        ChatMessageEntity::class, ConversationEntity::class,
        PersonaMemoryEntity::class, PersonaFeedbackEntity::class,
        PersonaPromptVersionEntity::class, UserMemoryFactEntity::class,
        KnowledgeChunkEntity::class, KnowledgeEdgeEntity::class,
        PersonaTrainingExampleEntity::class, ChatMessageFtsEntity::class,
        ProviderEntity::class, ProviderModelEntity::class
    ],
    version = 11,
    exportSchema = true
)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun providerDao(): ProviderDao

    companion object {
        @Volatile private var INSTANCE: ChatDatabase? = null

        private fun createLegacyConversationsTable(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `conversations` (" +
                    "`id` TEXT NOT NULL, `title` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                    "`updatedAt` INTEGER NOT NULL, `personaName` TEXT NOT NULL DEFAULT 'ASSISTANT', " +
                    "PRIMARY KEY(`id`))"
            )
            if (!columnExists(db, "conversations", "personaName")) {
                db.execSQL(
                    "ALTER TABLE `conversations` ADD COLUMN `personaName` TEXT NOT NULL DEFAULT 'ASSISTANT'"
                )
            }
        }

        private fun migrateVersionOneMessages(db: SupportSQLiteDatabase) {
            if (!tableExists(db, "chat_messages") || columnExists(db, "chat_messages", "conversationId")) return
            createLegacyConversationsTable(db)
            db.execSQL("ALTER TABLE `chat_messages` RENAME TO `chat_messages_v1`")
            db.execSQL(
                "CREATE TABLE `chat_messages` (" +
                    "`id` TEXT NOT NULL, `conversationId` TEXT NOT NULL, `text` TEXT NOT NULL, " +
                    "`isUser` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, `imageUrl` TEXT, " +
                    "`sourcesJson` TEXT, `webFetchedAtIso` TEXT, PRIMARY KEY(`id`), " +
                    "FOREIGN KEY(`conversationId`) REFERENCES `conversations`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE)"
            )
            db.execSQL(
                "INSERT OR IGNORE INTO `conversations` (`id`, `title`, `createdAt`, `updatedAt`, `personaName`) " +
                    "SELECT 'legacy-import-v1', 'Importierter Verlauf', MIN(`timestamp`), MAX(`timestamp`), 'ASSISTANT' " +
                    "FROM `chat_messages_v1` HAVING COUNT(*) > 0"
            )
            db.execSQL(
                "INSERT INTO `chat_messages` (`id`, `conversationId`, `text`, `isUser`, `timestamp`) " +
                    "SELECT `id`, 'legacy-import-v1', `text`, `isUser`, `timestamp` FROM `chat_messages_v1`"
            )
            db.execSQL("DROP TABLE `chat_messages_v1`")
        }

        private fun createLegacyTables(db: SupportSQLiteDatabase) {
            createLegacyConversationsTable(db)
            migrateVersionOneMessages(db)
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `chat_messages` (" +
                    "`id` TEXT NOT NULL, `conversationId` TEXT NOT NULL, `text` TEXT NOT NULL, " +
                    "`isUser` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, `imageUrl` TEXT, " +
                    "`sourcesJson` TEXT, `webFetchedAtIso` TEXT, PRIMARY KEY(`id`), " +
                    "FOREIGN KEY(`conversationId`) REFERENCES `conversations`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE)"
            )
            if (!columnExists(db, "chat_messages", "imageUrl")) {
                db.execSQL("ALTER TABLE `chat_messages` ADD COLUMN `imageUrl` TEXT")
            }
            if (!columnExists(db, "chat_messages", "sourcesJson")) {
                db.execSQL("ALTER TABLE `chat_messages` ADD COLUMN `sourcesJson` TEXT")
            }
            if (!columnExists(db, "chat_messages", "webFetchedAtIso")) {
                db.execSQL("ALTER TABLE `chat_messages` ADD COLUMN `webFetchedAtIso` TEXT")
            }
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_chat_messages_conversationId` " +
                    "ON `chat_messages` (`conversationId`)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `persona_memory` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT, `personaName` TEXT NOT NULL, " +
                    "`memoryText` TEXT NOT NULL, `sourceMessageId` TEXT, `createdAt` INTEGER NOT NULL, " +
                    "`updatedAt` INTEGER NOT NULL)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_persona_memory_personaName` " +
                    "ON `persona_memory` (`personaName`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_persona_memory_updatedAt` " +
                    "ON `persona_memory` (`updatedAt`)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `persona_feedback` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT, `personaName` TEXT NOT NULL, " +
                    "`messageId` TEXT NOT NULL, `helpful` INTEGER NOT NULL, `note` TEXT, " +
                    "`createdAt` INTEGER NOT NULL)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_persona_feedback_personaName` " +
                    "ON `persona_feedback` (`personaName`)"
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_persona_feedback_messageId` " +
                    "ON `persona_feedback` (`messageId`)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `persona_prompt_versions` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT, `personaName` TEXT NOT NULL, " +
                    "`promptText` TEXT NOT NULL, `source` TEXT NOT NULL DEFAULT 'manual_edit', " +
                    "`createdAt` INTEGER NOT NULL, `isRollbackPoint` INTEGER NOT NULL DEFAULT 0)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_persona_prompt_versions_personaName` " +
                    "ON `persona_prompt_versions` (`personaName`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_persona_prompt_versions_createdAt` " +
                    "ON `persona_prompt_versions` (`createdAt`)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `user_memory_facts` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT, `personaName` TEXT NOT NULL, " +
                    "`factText` TEXT NOT NULL, `confidence` REAL NOT NULL DEFAULT 0.6, " +
                    "`sourceMessageId` TEXT, `createdAt` INTEGER NOT NULL, " +
                    "`updatedAt` INTEGER NOT NULL)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_user_memory_facts_personaName` " +
                    "ON `user_memory_facts` (`personaName`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_user_memory_facts_updatedAt` " +
                    "ON `user_memory_facts` (`updatedAt`)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `knowledge_chunks` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT, `sourceTitle` TEXT NOT NULL, " +
                    "`content` TEXT NOT NULL, `keywords` TEXT NOT NULL, " +
                    "`sourceType` TEXT NOT NULL DEFAULT 'text', `createdAt` INTEGER NOT NULL)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_knowledge_chunks_sourceTitle` " +
                    "ON `knowledge_chunks` (`sourceTitle`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_knowledge_chunks_createdAt` " +
                    "ON `knowledge_chunks` (`createdAt`)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `knowledge_edges` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT, `fromConcept` TEXT NOT NULL, " +
                    "`relation` TEXT NOT NULL, `toConcept` TEXT NOT NULL, " +
                    "`weight` REAL NOT NULL DEFAULT 1.0, `updatedAt` INTEGER NOT NULL)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_knowledge_edges_fromConcept` " +
                    "ON `knowledge_edges` (`fromConcept`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_knowledge_edges_toConcept` " +
                    "ON `knowledge_edges` (`toConcept`)"
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_knowledge_edges_fromConcept_relation_toConcept` " +
                    "ON `knowledge_edges` (`fromConcept`, `relation`, `toConcept`)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `persona_training_examples` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT, `personaName` TEXT NOT NULL, " +
                    "`userInput` TEXT NOT NULL, `idealResponse` TEXT NOT NULL, " +
                    "`source` TEXT NOT NULL DEFAULT 'manual', `createdAt` INTEGER NOT NULL, " +
                    "`updatedAt` INTEGER NOT NULL, `enabled` INTEGER NOT NULL DEFAULT 1)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_persona_training_examples_personaName` " +
                    "ON `persona_training_examples` (`personaName`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_persona_training_examples_updatedAt` " +
                    "ON `persona_training_examples` (`updatedAt`)"
            )
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                createLegacyTables(db)
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                createLegacyTables(db)
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                createLegacyTables(db)
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                createLegacyTables(db)
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                createLegacyTables(db)
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                createLegacyTables(db)
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                createLegacyTables(db)
                db.execSQL(
                    "CREATE VIRTUAL TABLE IF NOT EXISTS `chat_messages_fts` USING FTS4(" +
                        "`message_id` TEXT NOT NULL, `conversation_id` TEXT NOT NULL, " +
                        "`text` TEXT NOT NULL, `is_user` INTEGER NOT NULL, " +
                        "`timestamp` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "INSERT INTO `chat_messages_fts` (`message_id`, `conversation_id`, `text`, `is_user`, `timestamp`) " +
                        "SELECT `id`, `conversationId`, `text`, `isUser`, `timestamp` FROM `chat_messages`"
                )
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ProviderRoomSchema.createTables(db)
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!columnExists(db, "conversations", "ownerScope")) {
                    db.execSQL(
                        "ALTER TABLE `conversations` ADD COLUMN `ownerScope` TEXT NOT NULL " +
                            "DEFAULT '${ChatOwnerScope.LEGACY_UNCLASSIFIED}'"
                    )
                }
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_conversations_ownerScope` " +
                        "ON `conversations` (`ownerScope`)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_conversations_id_ownerScope` " +
                        "ON `conversations` (`id`, `ownerScope`)"
                )
                val messageScopeExpression = if (columnExists(db, "chat_messages", "ownerScope")) {
                    "`ownerScope`"
                } else {
                    "'${ChatOwnerScope.LEGACY_UNCLASSIFIED}'"
                }
                db.execSQL(
                    "CREATE TABLE `chat_messages_scoped` (" +
                        "`id` TEXT NOT NULL, `conversationId` TEXT NOT NULL, `text` TEXT NOT NULL, " +
                        "`isUser` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, `imageUrl` TEXT, " +
                        "`sourcesJson` TEXT, `webFetchedAtIso` TEXT, " +
                        "`ownerScope` TEXT NOT NULL DEFAULT '${ChatOwnerScope.LEGACY_UNCLASSIFIED}', " +
                        "PRIMARY KEY(`id`), FOREIGN KEY(`conversationId`, `ownerScope`) " +
                        "REFERENCES `conversations`(`id`, `ownerScope`) ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL(
                    "INSERT INTO `chat_messages_scoped` " +
                        "(`id`, `conversationId`, `text`, `isUser`, `timestamp`, `imageUrl`, `sourcesJson`, `webFetchedAtIso`, `ownerScope`) " +
                        "SELECT `id`, `conversationId`, `text`, `isUser`, `timestamp`, `imageUrl`, `sourcesJson`, `webFetchedAtIso`, " +
                        "$messageScopeExpression FROM `chat_messages`"
                )
                db.execSQL("DROP TABLE `chat_messages`")
                db.execSQL("ALTER TABLE `chat_messages_scoped` RENAME TO `chat_messages`")
                db.execSQL(
                    "CREATE INDEX `index_chat_messages_conversationId` ON `chat_messages` (`conversationId`)"
                )
                db.execSQL(
                    "CREATE INDEX `index_chat_messages_ownerScope` ON `chat_messages` (`ownerScope`)"
                )
                db.execSQL(
                    "CREATE INDEX `index_chat_messages_conversationId_ownerScope` " +
                        "ON `chat_messages` (`conversationId`, `ownerScope`)"
                )
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!columnExists(db, "knowledge_chunks", "ownerScope")) {
                    db.execSQL(
                        "ALTER TABLE `knowledge_chunks` ADD COLUMN `ownerScope` TEXT NOT NULL " +
                            "DEFAULT '${ChatOwnerScope.LEGACY_UNCLASSIFIED}'"
                    )
                }
                if (!columnExists(db, "knowledge_edges", "ownerScope")) {
                    db.execSQL(
                        "ALTER TABLE `knowledge_edges` ADD COLUMN `ownerScope` TEXT NOT NULL " +
                            "DEFAULT '${ChatOwnerScope.LEGACY_UNCLASSIFIED}'"
                    )
                }
                db.execSQL("DROP INDEX IF EXISTS `index_knowledge_chunks_sourceTitle`")
                db.execSQL("DROP INDEX IF EXISTS `index_knowledge_chunks_createdAt`")
                db.execSQL("DROP INDEX IF EXISTS `index_knowledge_edges_fromConcept`")
                db.execSQL("DROP INDEX IF EXISTS `index_knowledge_edges_toConcept`")
                db.execSQL("DROP INDEX IF EXISTS `index_knowledge_edges_fromConcept_relation_toConcept`")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_knowledge_chunks_ownerScope` " +
                        "ON `knowledge_chunks` (`ownerScope`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_knowledge_chunks_ownerScope_sourceTitle` " +
                        "ON `knowledge_chunks` (`ownerScope`, `sourceTitle`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_knowledge_chunks_ownerScope_createdAt` " +
                        "ON `knowledge_chunks` (`ownerScope`, `createdAt`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_knowledge_edges_ownerScope` " +
                        "ON `knowledge_edges` (`ownerScope`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_knowledge_edges_ownerScope_fromConcept` " +
                        "ON `knowledge_edges` (`ownerScope`, `fromConcept`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_knowledge_edges_ownerScope_toConcept` " +
                        "ON `knowledge_edges` (`ownerScope`, `toConcept`)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "`index_knowledge_edges_ownerScope_fromConcept_relation_toConcept` " +
                        "ON `knowledge_edges` (`ownerScope`, `fromConcept`, `relation`, `toConcept`)"
                )
            }
        }

        private fun columnExists(
            db: SupportSQLiteDatabase,
            table: String,
            column: String
        ): Boolean = db.query("PRAGMA table_info(`$table`)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            var found = false
            while (cursor.moveToNext() && !found) {
                found = cursor.getString(nameIndex) == column
            }
            found
        }

        private fun tableExists(db: SupportSQLiteDatabase, table: String): Boolean =
            db.query(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ? LIMIT 1",
                arrayOf(table)
            ).use { cursor -> cursor.moveToFirst() }

        fun getDatabase(context: Context): ChatDatabase {
            return INSTANCE ?: synchronized(this) {
                var instance = INSTANCE
                if (instance == null) {
                    instance = Room.databaseBuilder(
                        context.applicationContext,
                        ChatDatabase::class.java,
                        "chat_database"
                    )
                        .addMigrations(
                            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,
                            MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
                            MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11
                        )
                        .addCallback(object : RoomDatabase.Callback() {
                            override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                                super.onDestructiveMigration(db)
                                Log.w("ChatDatabase", "Destructive migration occurred - all local data was reset")
                            }
                        })
                        .build()
                    INSTANCE = instance
                }
                instance
            }
        }
    }
}
