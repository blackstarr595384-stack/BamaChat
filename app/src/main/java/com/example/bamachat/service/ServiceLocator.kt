package com.example.bamachat.service

import android.app.Application
import android.content.Context
import com.example.bamachat.data.local.ChatDatabase
import com.example.bamachat.data.local.ChatSessionScopeStore
import com.example.bamachat.data.repository.ChatRepository
import com.example.bamachat.ui.viewmodel.ApiManager

object ServiceLocator {
    private var initialized = false
    private lateinit var app: Application

    lateinit var conversationService: ConversationService
        private set
    lateinit var knowledgeService: KnowledgeService
        private set
    lateinit var mediaService: MediaService
        private set
    lateinit var chatEngine: ChatEngine
        private set
    lateinit var apiManager: ApiManager
        private set
    lateinit var notificationService: NotificationService
        private set

    private var _chatRepository: ChatRepository? = null
    private val chatRepository: ChatRepository
        get() {
            if (_chatRepository == null) {
                val db = ChatDatabase.getDatabase(app)
                _chatRepository = ChatRepository(db.chatDao())
            }
            return _chatRepository ?: throw IllegalStateException("ChatRepository not initialized")
        }

    fun init(application: Application) {
        if (initialized) return
        app = application
        val prefs = application.getSharedPreferences("settings", Context.MODE_PRIVATE)

        apiManager = ApiManager(application)
        conversationService = ConversationService(chatRepository, prefs, ChatSessionScopeStore(prefs))
        knowledgeService = KnowledgeService(chatRepository, application)
        mediaService = MediaService(application, apiManager, knowledgeService)
        chatEngine = ChatEngine(apiManager, application)
        notificationService = NotificationService(application)

        initialized = true
    }

    fun reset() {
        initialized = false
        _chatRepository = null
    }
}
