package com.example.bamachat.service

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

class NotificationService(private val app: Application) {
    companion object {
        const val NOTIFICATION_ID_BASE = 4242
        const val CHANNEL_ID = "bamachat_ai_response"
    }

    fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "KI-Antworten", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Benachrichtigungen bei neuen KI-Antworten"
        }
        val manager = app.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    fun show(title: String, text: String, enabled: Boolean) {
        if (!enabled) return
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        if (!NotificationManagerCompat.from(app).areNotificationsEnabled()) return
        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID_BASE + (System.currentTimeMillis() % 10000).toInt(),
            NotificationCompat.Builder(app, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(text.take(100))
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()
        )
    }
}
