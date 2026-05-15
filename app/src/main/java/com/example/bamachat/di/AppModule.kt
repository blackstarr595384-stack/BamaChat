package com.example.bamachat.di

import android.content.Context
import android.content.SharedPreferences
import com.example.bamachat.data.local.ChatDatabase
import com.example.bamachat.data.local.ChatDao
import com.example.bamachat.data.repository.ChatRepository
import com.example.bamachat.service.ChatEngine
import com.example.bamachat.service.ConversationService
import com.example.bamachat.service.KnowledgeService
import com.example.bamachat.service.MediaService
import com.example.bamachat.service.NotificationService
import com.example.bamachat.ui.viewmodel.ApiManager
import com.example.bamachat.util.McpServerManager
import com.example.bamachat.util.McpWorkflowManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences {
        return context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    }

    @Provides
    @Singleton
    fun provideChatDatabase(@ApplicationContext context: Context): ChatDatabase {
        return ChatDatabase.getDatabase(context)
    }

    @Provides
    @Singleton
    fun provideChatDao(db: ChatDatabase): ChatDao {
        return db.chatDao()
    }

    @Provides
    @Singleton
    fun provideChatRepository(dao: ChatDao): ChatRepository {
        return ChatRepository(dao)
    }

    @Provides
    @Singleton
    fun provideApiManager(@ApplicationContext context: Context): ApiManager {
        return ApiManager(context)
    }

    @Provides
    @Singleton
    fun provideConversationService(
        repo: ChatRepository,
        prefs: SharedPreferences
    ): ConversationService {
        return ConversationService(repo, prefs)
    }

    @Provides
    @Singleton
    fun provideKnowledgeService(
        repo: ChatRepository,
        @ApplicationContext context: Context
    ): KnowledgeService {
        return KnowledgeService(repo, context)
    }

    @Provides
    @Singleton
    fun provideMediaService(
        @ApplicationContext context: Context,
        apiManager: ApiManager,
        knowledgeService: KnowledgeService
    ): MediaService {
        return MediaService(context, apiManager, knowledgeService)
    }

    @Provides
    @Singleton
    fun provideChatEngine(
        apiManager: ApiManager,
        @ApplicationContext context: Context
    ): ChatEngine {
        return ChatEngine(apiManager, context)
    }

    @Provides
    @Singleton
    fun provideNotificationService(@ApplicationContext context: Context): NotificationService {
        return NotificationService(context)
    }

    @Provides
    @Singleton
    fun provideMcpServerManager(@ApplicationContext context: Context): McpServerManager {
        return McpServerManager(context)
    }

    @Provides
    @Singleton
    fun provideMcpWorkflowManager(serverManager: McpServerManager): McpWorkflowManager {
        return McpWorkflowManager(serverManager)
    }
}
