package com.example.bamachat

import android.app.Application
import com.google.firebase.Firebase
import com.google.firebase.initialize
import com.example.bamachat.service.ServiceLocator
import dagger.hilt.android.HiltAndroidApp
import com.example.bamachat.util.AppTelemetry
import com.example.bamachat.util.LegalPolicy

@HiltAndroidApp
class BamaChatApplication : Application() {
    override fun onCreate() {
        super.onCreate()

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
