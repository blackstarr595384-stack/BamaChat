package com.example.bamachat

import android.app.Application
import com.google.firebase.Firebase
import com.google.firebase.initialize
import com.example.bamachat.service.ServiceLocator
import com.example.bamachat.data.cloud.AuthenticatedUidProvider
import com.example.bamachat.data.local.ChatSessionScopeStore
import dagger.hilt.android.HiltAndroidApp
import com.example.bamachat.util.AppTelemetry
import com.example.bamachat.util.LegalPolicy
import javax.inject.Inject

@HiltAndroidApp
class BamaChatApplication : Application() {
    @Inject lateinit var authenticatedUidProvider: AuthenticatedUidProvider
    @Inject lateinit var chatSessionScopeStore: ChatSessionScopeStore

    override fun onCreate() {
        super.onCreate()

        authenticatedUidProvider.currentUid()?.let { uid ->
            chatSessionScopeStore.prepareAccountTransition()
            chatSessionScopeStore.beginAuthenticatedTransition(uid)
        }

        ServiceLocator.init(this)

        try {
            Firebase.initialize(this)
            val settings = getSharedPreferences("settings", MODE_PRIVATE)
            val legalAccepted =
                settings.getInt(LegalPolicy.KEY_ACK_VERSION, 0) >= LegalPolicy.CURRENT_ACK_VERSION
            AppTelemetry.initialize(this, collectionEnabled = legalAccepted)
            if (legalAccepted) {
                AppTelemetry.logEvent("app_open")
            }
        } catch (e: Exception) {
            android.util.Log.e("BamaChatApplication", "Firebase init failed", e)
        }
    }
}
