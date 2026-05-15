package com.example.bamachat

import android.app.Application
import android.content.pm.ApplicationInfo
import com.example.bamachat.service.ServiceLocator
import com.example.bamachat.util.AppTelemetry
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class BamaChatApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ensureFirebaseBootstrap()
        PDFBoxResourceLoader.init(applicationContext)
        val isDebuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        AppTelemetry.init(
            context = this,
            enableCrashlytics = !isDebuggable
        )
        ServiceLocator.init(this)
    }

    private fun ensureFirebaseBootstrap() {
        val hasApp = runCatching { FirebaseApp.getApps(this).isNotEmpty() }.getOrDefault(false)
        if (hasApp) return

        val initializedDefault = runCatching { FirebaseApp.initializeApp(this) }.getOrNull()
        if (initializedDefault != null) return

        runCatching {
            val fallback = FirebaseOptions.Builder()
                .setApplicationId("1:000000000000:android:debuglocal000000")
                .setApiKey("debug-local-api-key")
                .setProjectId("bamachat-local")
                .setGcmSenderId("000000000000")
                .build()
            FirebaseApp.initializeApp(this, fallback)
        }
    }
}
