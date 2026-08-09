package com.example.bamachat.di

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import com.example.bamachat.data.cloud.ChatCloudSyncGateway
import com.example.bamachat.data.cloud.ChatSyncPolicy
import com.example.bamachat.data.github.AndroidGitHubReadOnlyRepositoryGateway
import com.example.bamachat.data.github.DisabledAgentDraftPrGateway
import com.example.bamachat.data.local.ChatDatabase
import com.example.bamachat.data.local.ChatDao
import com.example.bamachat.data.repository.ChatRepository
import com.example.bamachat.data.provider.ProviderSecretStorage
import com.example.bamachat.data.provider.ProviderSecretStore
import com.example.bamachat.data.provider.local.ProviderDao
import com.example.bamachat.data.provider.local.ProviderStore
import com.example.bamachat.data.provider.local.RoomProviderStore
import com.example.bamachat.service.ChatEngine
import com.example.bamachat.service.ConversationService
import com.example.bamachat.service.AndroidGitHubProposalAnalyzer
import com.example.bamachat.service.GitHubProposalAnalyzer
import com.example.bamachat.service.KnowledgeService
import com.example.bamachat.service.MediaService
import com.example.bamachat.service.NotificationService
import com.example.bamachat.shared.core.github.GitHubReadOnlyRepositoryGateway
import com.example.bamachat.shared.core.github.AgentDraftPrGateway
import com.example.bamachat.shared.core.github.RepositoryContextBuilder
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
    fun provideProviderDao(db: ChatDatabase): ProviderDao = db.providerDao()

    @Provides
    @Singleton
    fun provideProviderStore(dao: ProviderDao): ProviderStore = RoomProviderStore(dao)

    @Provides
    @Singleton
    fun provideProviderSecretStorage(store: ProviderSecretStore): ProviderSecretStorage = store

    @Provides
    @Singleton
    fun provideChatRepository(dao: ChatDao): ChatRepository {
        return ChatRepository(dao)
    }

    @Provides
    @Singleton
    fun provideChatCloudSyncGateway(): ChatCloudSyncGateway {
        return ChatCloudSyncGateway()
    }

    @Provides
    @Singleton
    fun provideChatSyncPolicy(prefs: SharedPreferences): ChatSyncPolicy {
        return ChatSyncPolicy(prefs)
    }

    @Provides
    @Singleton
    fun provideApiManager(app: Application): ApiManager {
        return ApiManager(app)
    }

    @Provides
    @Singleton
    fun provideGitHubReadOnlyRepositoryGateway(): GitHubReadOnlyRepositoryGateway {
        return AndroidGitHubReadOnlyRepositoryGateway()
    }

    @Provides
    @Singleton
    fun provideAgentDraftPrGateway(): AgentDraftPrGateway {
        return DisabledAgentDraftPrGateway()
    }

    @Provides
    @Singleton
    fun provideRepositoryContextBuilder(): RepositoryContextBuilder {
        return RepositoryContextBuilder()
    }

    @Provides
    fun provideGitHubProposalAnalyzer(apiManager: ApiManager): GitHubProposalAnalyzer {
        return AndroidGitHubProposalAnalyzer(apiManager)
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
        app: Application
    ): KnowledgeService {
        return KnowledgeService(repo, app)
    }

    @Provides
    @Singleton
    fun provideMediaService(
        app: Application,
        apiManager: ApiManager,
        knowledgeService: KnowledgeService
    ): MediaService {
        return MediaService(app, apiManager, knowledgeService)
    }

    @Provides
    @Singleton
    fun provideChatEngine(
        apiManager: ApiManager,
        app: Application
    ): ChatEngine {
        return ChatEngine(apiManager, app)
    }

    @Provides
    @Singleton
    fun provideNotificationService(app: Application): NotificationService {
        return NotificationService(app)
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
