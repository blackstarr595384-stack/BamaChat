package com.example.bamachat.data.provider.local

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

interface ProviderStore {
    fun observeProviders(): Flow<List<ProviderEntity>>
    fun observeEnabledProviders(): Flow<List<ProviderEntity>>
    suspend fun getProvider(providerId: String): ProviderEntity?
    suspend fun insertProvider(provider: ProviderEntity)
    suspend fun updateProvider(provider: ProviderEntity): Int
    suspend fun setEnabled(providerId: String, enabled: Boolean, updatedAt: Long): Int
    suspend fun setDefaultModel(providerId: String, modelId: String?, updatedAt: Long): Int
    suspend fun setHasSecret(providerId: String, hasSecret: Boolean, updatedAt: Long): Int
    suspend fun deleteProvider(providerId: String): Int
    suspend fun getModels(providerId: String): List<ProviderModelEntity>
    suspend fun getModel(providerId: String, modelId: String): ProviderModelEntity?
    suspend fun replaceModels(providerId: String, models: List<ProviderModelEntity>)
    suspend fun insertModel(model: ProviderModelEntity)
    suspend fun deleteModel(providerId: String, modelId: String): Int
    suspend fun seedBuiltIns(providers: List<ProviderEntity>, models: List<ProviderModelEntity>)
}

class RoomProviderStore @Inject constructor(
    private val dao: ProviderDao
) : ProviderStore {
    override fun observeProviders(): Flow<List<ProviderEntity>> = dao.observeProviders()
    override fun observeEnabledProviders(): Flow<List<ProviderEntity>> = dao.observeEnabledProviders()
    override suspend fun getProvider(providerId: String): ProviderEntity? = dao.getProvider(providerId)
    override suspend fun insertProvider(provider: ProviderEntity) = dao.insertProvider(provider)
    override suspend fun updateProvider(provider: ProviderEntity): Int = dao.updateProvider(provider)
    override suspend fun setEnabled(providerId: String, enabled: Boolean, updatedAt: Long): Int =
        dao.setEnabled(providerId, enabled, updatedAt)
    override suspend fun setDefaultModel(providerId: String, modelId: String?, updatedAt: Long): Int =
        dao.setDefaultModel(providerId, modelId, updatedAt)
    override suspend fun setHasSecret(providerId: String, hasSecret: Boolean, updatedAt: Long): Int =
        dao.setHasSecret(providerId, hasSecret, updatedAt)
    override suspend fun deleteProvider(providerId: String): Int = dao.deleteProvider(providerId)
    override suspend fun getModels(providerId: String): List<ProviderModelEntity> = dao.getModels(providerId)
    override suspend fun getModel(providerId: String, modelId: String): ProviderModelEntity? =
        dao.getModel(providerId, modelId)
    override suspend fun replaceModels(providerId: String, models: List<ProviderModelEntity>) =
        dao.replaceModels(providerId, models)
    override suspend fun insertModel(model: ProviderModelEntity) = dao.insertModel(model)
    override suspend fun deleteModel(providerId: String, modelId: String): Int = dao.deleteModel(providerId, modelId)
    override suspend fun seedBuiltIns(providers: List<ProviderEntity>, models: List<ProviderModelEntity>) =
        dao.seedBuiltIns(providers, models)
}
