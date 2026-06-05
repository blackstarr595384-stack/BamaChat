package com.example.bamachat.util

import android.content.Context
import com.example.bamachat.data.model.ChatMessage
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BackupManager {
    
    suspend fun exportChatToJSON(messages: List<ChatMessage>): String {
        val jsonArray = messages.map { msg ->
            mapOf(
                "id" to msg.id,
                "text" to msg.text,
                "isUser" to msg.isUser,
                "timestamp" to msg.timestamp,
                "imageUrl" to msg.imageUrl
            )
        }
        return com.google.gson.Gson().toJson(jsonArray)
    }
    
    suspend fun exportChatToMarkdown(messages: List<ChatMessage>, title: String = "Chat Export"): String {
        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        val sb = StringBuilder()
        
        sb.appendLine("# $title\n")
        sb.appendLine("Exportiert am ${dateFormat.format(Date())}\n")
        sb.appendLine("---\n")
        
        messages.forEach { msg ->
            val role = if (msg.isUser) "**Du**" else "**KI**"
            val time = dateFormat.format(Date(msg.timestamp))
            sb.appendLine("### $role ($time)")
            sb.appendLine()
            if (msg.imageUrl != null) {
                sb.appendLine("![Bild](${msg.imageUrl})")
            }
            sb.appendLine(msg.text)
            sb.appendLine()
        }
        
        return sb.toString()
    }
    
    suspend fun backupToCloud(
        conversationId: String,
        messages: List<ChatMessage>,
        userId: String
    ): Result<String> {
        return try {
            val db = FirebaseFirestore.getInstance()
            val backup = mapOf(
                "conversationId" to conversationId,
                "userId" to userId,
                "messageCount" to messages.size,
                "backupDate" to Date(),
                "messages" to messages.map { msg ->
                    mapOf(
                        "id" to msg.id,
                        "text" to msg.text,
                        "isUser" to msg.isUser,
                        "timestamp" to msg.timestamp
                    )
                }
            )
            
            val docId = db.collection("backups").add(backup).await().id
            Result.success(docId)
        } catch (e: Exception) {
            AppTelemetry.logError("backup_to_cloud_failed", e)
            Result.failure(e)
        }
    }
    
    suspend fun restoreFromCloud(backupId: String): Result<List<ChatMessage>> {
        return try {
            val db = FirebaseFirestore.getInstance()
            val doc = db.collection("backups").document(backupId).get().await()
            
            @Suppress("UNCHECKED_CAST")
            val msgs = doc.get("messages") as? List<Map<String, Any>> ?: emptyList()
            
            val messages = msgs.map { msgMap ->
                ChatMessage(
                    id = msgMap["id"] as String,
                    text = msgMap["text"] as String,
                    isUser = msgMap["isUser"] as Boolean,
                    timestamp = (msgMap["timestamp"] as? Number)?.toLong() ?: 0L
                )
            }
            
            Result.success(messages)
        } catch (e: Exception) {
            AppTelemetry.logError("restore_from_cloud_failed", e)
            Result.failure(e)
        }
    }
}
