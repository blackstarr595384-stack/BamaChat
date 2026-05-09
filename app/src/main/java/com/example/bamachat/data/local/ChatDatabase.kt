package com.example.bamachat.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ChatMessageEntity::class,
        ConversationEntity::class,
        PersonaMemoryEntity::class,
        PersonaFeedbackEntity::class,
        PersonaPromptVersionEntity::class,
        UserMemoryFactEntity::class,
        KnowledgeChunkEntity::class,
        KnowledgeEdgeEntity::class,
        PersonaTrainingExampleEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var INSTANCE: ChatDatabase? = null

        fun getDatabase(context: Context): ChatDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ChatDatabase::class.java,
                    "chat_database"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
