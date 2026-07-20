package com.example.bamachat.data.provider.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(tableName = "providers")
data class ProviderEntity(
    @androidx.room.PrimaryKey val providerId: String,
    val displayName: String,
    val connectionType: String,
    val baseUrl: String,
    val authenticationType: String,
    val defaultModelId: String?,
    val streaming: Boolean,
    val modelDiscovery: Boolean,
    val tools: Boolean,
    val vision: Boolean,
    val timeoutMs: Long,
    val enabled: Boolean,
    val builtIn: Boolean,
    val localHttpConfirmed: Boolean,
    val hasSecret: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "provider_models",
    primaryKeys = ["providerId", "modelId"],
    foreignKeys = [
        ForeignKey(
            entity = ProviderEntity::class,
            parentColumns = ["providerId"],
            childColumns = ["providerId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("providerId")]
)
data class ProviderModelEntity(
    val providerId: String,
    val modelId: String,
    val displayName: String,
    val source: String,
    val enabled: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)
