package com.example.bamachat.data.provider.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProviderDao {
    @Query("SELECT * FROM providers ORDER BY builtIn DESC, displayName COLLATE NOCASE ASC")
    fun observeProviders(): Flow<List<ProviderEntity>>

    @Query("SELECT * FROM providers WHERE enabled = 1 ORDER BY builtIn DESC, displayName COLLATE NOCASE ASC")
    fun observeEnabledProviders(): Flow<List<ProviderEntity>>

    @Query("SELECT * FROM providers WHERE providerId = :providerId LIMIT 1")
    suspend fun getProvider(providerId: String): ProviderEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProvider(provider: ProviderEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertProvidersIfAbsent(providers: List<ProviderEntity>): List<Long>

    @Update
    suspend fun updateProvider(provider: ProviderEntity): Int

    @Query("UPDATE providers SET enabled = :enabled, updatedAt = :updatedAt WHERE providerId = :providerId")
    suspend fun setEnabled(providerId: String, enabled: Boolean, updatedAt: Long): Int

    @Query("UPDATE providers SET defaultModelId = :modelId, updatedAt = :updatedAt WHERE providerId = :providerId")
    suspend fun setDefaultModel(providerId: String, modelId: String?, updatedAt: Long): Int

    @Query("UPDATE providers SET hasSecret = :hasSecret, updatedAt = :updatedAt WHERE providerId = :providerId")
    suspend fun setHasSecret(providerId: String, hasSecret: Boolean, updatedAt: Long): Int

    @Query("DELETE FROM providers WHERE providerId = :providerId")
    suspend fun deleteProvider(providerId: String): Int

    @Query("SELECT * FROM provider_models WHERE providerId = :providerId ORDER BY displayName COLLATE NOCASE ASC")
    suspend fun getModels(providerId: String): List<ProviderModelEntity>

    @Query("SELECT * FROM provider_models WHERE providerId = :providerId AND modelId = :modelId LIMIT 1")
    suspend fun getModel(providerId: String, modelId: String): ProviderModelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertModels(models: List<ProviderModelEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertModelsIfAbsent(models: List<ProviderModelEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertModel(model: ProviderModelEntity)

    @Query("DELETE FROM provider_models WHERE providerId = :providerId")
    suspend fun deleteModels(providerId: String)

    @Query("DELETE FROM provider_models WHERE providerId = :providerId AND modelId = :modelId")
    suspend fun deleteModel(providerId: String, modelId: String): Int

    @Transaction
    suspend fun replaceModels(providerId: String, models: List<ProviderModelEntity>) {
        deleteModels(providerId)
        if (models.isNotEmpty()) upsertModels(models)
        val currentDefault = getProvider(providerId)?.defaultModelId
        if (currentDefault != null && models.none { it.modelId == currentDefault && it.enabled }) {
            setDefaultModel(providerId, null, System.currentTimeMillis())
        }
    }

    @Transaction
    suspend fun seedBuiltIns(providers: List<ProviderEntity>, models: List<ProviderModelEntity>) {
        insertProvidersIfAbsent(providers)
        if (models.isNotEmpty()) insertModelsIfAbsent(models)
    }
}
