package com.example.bamachat.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ChatMessageEntity::class, ConversationEntity::class,
        PersonaMemoryEntity::class, PersonaFeedbackEntity::class,
        PersonaPromptVersionEntity::class, UserMemoryFactEntity::class,
        KnowledgeChunkEntity::class, KnowledgeEdgeEntity::class,
        PersonaTrainingExampleEntity::class, ChatMessageFtsEntity::class
    ],
    version = 8,
    exportSchema = false
)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile private var INSTANCE: ChatDatabase? = null

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE VIRTUAL TABLE IF NOT EXISTS `chat_messages_fts` USING FTS4(" +
                        "`message_id` TEXT, `conversation_id` TEXT, " +
                        "`text` TEXT, `is_user` INTEGER, " +
                        "`timestamp` INTEGER, notindexed=`message_id`, notindexed=`conversation_id`, notindexed=`is_user`, notindexed=`timestamp`)"
                )
                db.execSQL(
                    "INSERT INTO `chat_messages_fts` (`message_id`, `conversation_id`, `text`, `is_user`, `timestamp`) " +
                        "SELECT `id`, `conversationId`, `text`, `isUser`, `timestamp` FROM `chat_messages`"
                )
            }
        }

        fun getDatabase(context: Context): ChatDatabase {
            return INSTANCE ?: synchronized(this) {
                var instance = INSTANCE
                if (instance == null) {
                    instance = Room.databaseBuilder(
                        context.applicationContext,
                        ChatDatabase::class.java,
                        "chat_database"
                    )
                        .addMigrations(MIGRATION_7_8)
                        .fallbackToDestructiveMigration(true)
                        .build()
                    INSTANCE = instance
                }
                instance
            }
        }
    }
}
