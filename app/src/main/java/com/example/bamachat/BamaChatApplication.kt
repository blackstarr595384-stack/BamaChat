package com.example.bamachat

import android.app.Application
import android.content.pm.ApplicationInfo
import com.example.bamachat.util.AppTelemetry
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class BamaChatApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
        val isDebuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        AppTelemetry.init(
            context = this,
            enableCrashlytics = !isDebuggable
        )
    }
}
