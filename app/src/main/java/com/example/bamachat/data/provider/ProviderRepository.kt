package com.example.bamachat.data.provider

import com.example.bamachat.data.provider.local.ProviderEntity
import com.example.bamachat.data.provider.local.ProviderModelEntity
import com.example.bamachat.data.provider.local.ProviderStore
import java.util.concurrent.CancellationException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class ProviderRepositoryError {
    INVALID_DEFINITION,
    PROVIDER_ALREADY_EXISTS,
    PROVIDER_NOT_FOUND,
    BUILT_IN_REQUIRED,
    CUSTOM_REQUIRED,
    BUILT_IN_DELETE_FORBIDDEN,
    ID_IMMUTABLE,
    MODEL_NOT_FOUND,
    MODEL_DISABLED,
    MODEL_PROVIDER_MISMATCH,
    DEFAULT_MODEL_DELETE_FORBIDDEN,
    STORAGE_FAILURE,
    CLEANUP_REQUIRED
}

class ProviderRepositoryException(
    val error: ProviderRepositoryError,
    message: String
) : IllegalStateException(message)

@Singleton
class ProviderRepository @Inject constructor(
    private val store: ProviderStore,
    private val secretStorage: ProviderSecretStorage
) {
    @Volatile
    private var builtInsSeeded = false
    private val seedMutex = Mutex()

    fun observeProviders(): Flow<List<ProviderDefinition>> = flow {
        ensureBuiltInsSeeded()
        emitAll(store.observeProviders().map { entities -> entities.map(::toDefinitionWithSecretState) })
    }

    fun observeEnabledProviders(): Flow<List<ProviderDefinition>> = flow {
        ensureBuiltInsSeeded()
        emitAll(store.observeEnabledProviders().map { entities -> entities.map(::toDefinitionWithSecretState) })
    }

    suspend fun getProvider(id: ProviderId): ProviderDefinition? = storageCall {
        ensureBuiltInsSeeded()
        store.getProvider(id.value)?.let(::toDefinitionWithSecretState)
    }

    suspend fun getModels(providerId: ProviderId): List<ProviderModelDefinition> = storageCall {
        ensureBuiltInsSeeded()
        ensureProviderExists(providerId)
        store.getModels(providerId.value).map(ProviderModelEntity::toDefinition)
    }

    suspend fun createCustomProvider(definition: ProviderDefinition) = storageCall {
        ensureBuiltInsSeeded()
        ensureCustom(definition)
        if (definition.defaultModelId != null) {
            throw ProviderRepositoryException(
                ProviderRepositoryError.MODEL_NOT_FOUND,
                "Ein Standardmodell kann erst nach dem Hinzufügen des Modells gewählt werden."
            )
        }
        if (store.getProvider(definition.id.value) != null) {
            throw ProviderRepositoryException(
                ProviderRepositoryError.PROVIDER_ALREADY_EXISTS,
                "Ein Anbieter mit dieser technischen ID ist bereits vorhanden."
            )
        }
        val now = System.currentTimeMillis()
        store.insertProvider(
            normalizeAndValidate(definition).copy(
                hasSecret = secretState(definition.id),
                createdAt = now,
                updatedAt = now
            ).toEntity()
        )
    }

    suspend fun updateCustomProvider(definition: ProviderDefinition) = storageCall {
        ensureBuiltInsSeeded()
        ensureCustom(definition)
        val existing = store.getProvider(definition.id.value)
            ?: throw ProviderRepositoryException(ProviderRepositoryError.PROVIDER_NOT_FOUND, "Der Anbieter wurde nicht gefunden.")
        if (existing.providerId != definition.id.value) {
            throw ProviderRepositoryException(ProviderRepositoryError.ID_IMMUTABLE, "Die technische Anbieter-ID kann nicht geändert werden.")
        }
        if (existing.builtIn || definition.builtIn) {
            throw ProviderRepositoryException(ProviderRepositoryError.CUSTOM_REQUIRED, "Nur benutzerdefinierte Anbieter können hier bearbeitet werden.")
        }
        if (definition.defaultModelId != existing.defaultModelId) {
            throw ProviderRepositoryException(
                ProviderRepositoryError.ID_IMMUTABLE,
                "Das Standardmodell muss über die Modellverwaltung geändert werden."
            )
        }
        val updated = normalizeAndValidate(definition).copy(
            createdAt = existing.createdAt,
            updatedAt = System.currentTimeMillis(),
            hasSecret = secretState(definition.id)
        )
        if (store.updateProvider(updated.toEntity()) != 1) {
            throw ProviderRepositoryException(ProviderRepositoryError.STORAGE_FAILURE, "Der Anbieter konnte nicht gespeichert werden.")
        }
    }

    suspend fun setEnabled(providerId: ProviderId, enabled: Boolean) = storageCall {
        ensureBuiltInsSeeded()
        ensureProviderExists(providerId)
        if (store.setEnabled(providerId.value, enabled, System.currentTimeMillis()) != 1) {
            throw ProviderRepositoryException(ProviderRepositoryError.STORAGE_FAILURE, "Der Anbieterstatus konnte nicht gespeichert werden.")
        }
    }

    suspend fun setDefaultModel(providerId: ProviderId, modelId: String) = storageCall {
        ensureBuiltInsSeeded()
        ensureProviderExists(providerId)
        val cleanModelId = modelId.trim()
        val model = store.getModel(providerId.value, cleanModelId)
            ?: throw ProviderRepositoryException(ProviderRepositoryError.MODEL_NOT_FOUND, "Das ausgewählte Modell wurde nicht gefunden.")
        if (!model.enabled) {
            throw ProviderRepositoryException(ProviderRepositoryError.MODEL_DISABLED, "Ein deaktiviertes Modell kann nicht als Standard verwendet werden.")
        }
        if (store.setDefaultModel(providerId.value, cleanModelId, System.currentTimeMillis()) != 1) {
            throw ProviderRepositoryException(ProviderRepositoryError.STORAGE_FAILURE, "Das Standardmodell konnte nicht gespeichert werden.")
        }
    }

    suspend fun clearDefaultModel(providerId: ProviderId) = storageCall {
        ensureBuiltInsSeeded()
        ensureProviderExists(providerId)
        if (store.setDefaultModel(providerId.value, null, System.currentTimeMillis()) != 1) {
            throw ProviderRepositoryException(ProviderRepositoryError.STORAGE_FAILURE, "Das Standardmodell konnte nicht entfernt werden.")
        }
    }

    suspend fun replaceModels(providerId: ProviderId, models: List<ProviderModelDefinition>) = storageCall {
        ensureBuiltInsSeeded()
        ensureProviderExists(providerId)
        validateModels(providerId, models)
        store.replaceModels(providerId.value, models.distinctBy { it.modelId }.map(ProviderModelDefinition::toEntity))
    }

    suspend fun addManualModel(providerId: ProviderId, model: ProviderModelDefinition) = storageCall {
        ensureBuiltInsSeeded()
        ensureProviderExists(providerId)
        if (model.providerId != providerId) {
            throw ProviderRepositoryException(ProviderRepositoryError.MODEL_PROVIDER_MISMATCH, "Das Modell gehört nicht zu diesem Anbieter.")
        }
        val manualModel = model.copy(source = ProviderModelSource.MANUAL)
        store.insertModel(manualModel.toEntity())
    }

    suspend fun deleteModel(providerId: ProviderId, modelId: String) = storageCall {
        ensureBuiltInsSeeded()
        val provider = ensureProviderExists(providerId)
        val cleanModelId = modelId.trim()
        if (provider.defaultModelId == cleanModelId) {
            throw ProviderRepositoryException(
                ProviderRepositoryError.DEFAULT_MODEL_DELETE_FORBIDDEN,
                "Das Standardmodell muss zuerst geändert werden."
            )
        }
        if (store.deleteModel(providerId.value, cleanModelId) != 1) {
            throw ProviderRepositoryException(ProviderRepositoryError.MODEL_NOT_FOUND, "Das Modell wurde nicht gefunden.")
        }
    }

    suspend fun deleteCustomProvider(providerId: ProviderId) {
        ensureBuiltInsSeeded()
        val provider = storageCall { ensureProviderExists(providerId) }
        if (provider.builtIn || !providerId.isCustom) {
            throw ProviderRepositoryException(
                ProviderRepositoryError.BUILT_IN_DELETE_FORBIDDEN,
                "Integrierte Anbieter können nicht gelöscht werden."
            )
        }
        try {
            secretStorage.remove(providerId)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            throw ProviderRepositoryException(
                ProviderRepositoryError.CLEANUP_REQUIRED,
                "Der Anbieter-Schlüssel konnte nicht sicher gelöscht werden. Der Anbieter bleibt erhalten."
            )
        }
        try {
            if (store.deleteProvider(providerId.value) != 1) {
                throw ProviderRepositoryException(ProviderRepositoryError.PROVIDER_NOT_FOUND, "Der Anbieter wurde nicht gefunden.")
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: ProviderRepositoryException) {
            throw ProviderRepositoryException(
                ProviderRepositoryError.CLEANUP_REQUIRED,
                "Der Schlüssel wurde entfernt, aber die lokalen Anbieterdaten müssen erneut bereinigt werden."
            )
        } catch (_: Exception) {
            throw ProviderRepositoryException(
                ProviderRepositoryError.CLEANUP_REQUIRED,
                "Der Schlüssel wurde entfernt, aber die lokalen Anbieterdaten müssen erneut bereinigt werden."
            )
        }
    }

    suspend fun saveCustomSecret(providerId: ProviderId, secret: String) {
        ensureBuiltInsSeeded()
        val provider = storageCall { ensureProviderExists(providerId) }
        if (provider.builtIn || !providerId.isCustom) {
            throw ProviderRepositoryException(ProviderRepositoryError.CUSTOM_REQUIRED, "Nur benutzerdefinierte Anbieter verwenden diesen Schlüsselspeicher.")
        }
        try {
            secretStorage.put(providerId, secret)
            storageCall {
                if (store.setHasSecret(providerId.value, true, System.currentTimeMillis()) != 1) {
                    throw ProviderRepositoryException(ProviderRepositoryError.STORAGE_FAILURE, "Der Schlüsselstatus konnte nicht gespeichert werden.")
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: ProviderRepositoryException) {
            throw error
        } catch (_: Exception) {
            throw ProviderRepositoryException(ProviderRepositoryError.STORAGE_FAILURE, "Der Anbieter-Schlüssel konnte nicht sicher gespeichert werden.")
        }
    }

    suspend fun removeCustomSecret(providerId: ProviderId) {
        ensureBuiltInsSeeded()
        val provider = storageCall { ensureProviderExists(providerId) }
        if (provider.builtIn || !providerId.isCustom) {
            throw ProviderRepositoryException(ProviderRepositoryError.CUSTOM_REQUIRED, "Nur benutzerdefinierte Anbieter verwenden diesen Schlüsselspeicher.")
        }
        try {
            secretStorage.remove(providerId)
            storageCall {
                if (store.setHasSecret(providerId.value, false, System.currentTimeMillis()) != 1) {
                    throw ProviderRepositoryException(ProviderRepositoryError.STORAGE_FAILURE, "Der Schlüsselstatus konnte nicht gespeichert werden.")
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: ProviderRepositoryException) {
            throw error
        } catch (_: Exception) {
            throw ProviderRepositoryException(ProviderRepositoryError.STORAGE_FAILURE, "Der Anbieter-Schlüssel konnte nicht sicher gelöscht werden.")
        }
    }

    suspend fun seedBuiltInProviders() = storageCall {
        seedMutex.withLock {
            seedBuiltInsNow()
            builtInsSeeded = true
        }
    }

    private suspend fun ensureBuiltInsSeeded() {
        if (builtInsSeeded) return
        seedMutex.withLock {
            if (!builtInsSeeded) {
                seedBuiltInsNow()
                builtInsSeeded = true
            }
        }
    }

    private suspend fun seedBuiltInsNow() {
        val definitions = BuiltInProviderCatalog.entries.map { it.definition.toEntity() }
        val models = BuiltInProviderCatalog.entries.flatMap { entry -> entry.models.map(ProviderModelDefinition::toEntity) }
        store.seedBuiltIns(definitions, models)
    }

    private suspend fun ensureProviderExists(providerId: ProviderId): ProviderEntity =
        store.getProvider(providerId.value)
            ?: throw ProviderRepositoryException(ProviderRepositoryError.PROVIDER_NOT_FOUND, "Der Anbieter wurde nicht gefunden.")

    private fun ensureCustom(definition: ProviderDefinition) {
        if (!definition.id.isCustom || definition.builtIn) {
            throw ProviderRepositoryException(ProviderRepositoryError.CUSTOM_REQUIRED, "Eine gültige benutzerdefinierte Anbieter-ID ist erforderlich.")
        }
    }

    private fun normalizeAndValidate(definition: ProviderDefinition): ProviderDefinition {
        return when (val result = ProviderUrlPolicy.validate(definition.baseUrl, definition.localHttpConfirmed)) {
            is ProviderUrlValidationResult.Valid -> {
                if (definition.authenticationType == ProviderAuthenticationType.NONE_LOCAL_ONLY && !result.localTarget) {
                    throw ProviderRepositoryException(
                        ProviderRepositoryError.INVALID_DEFINITION,
                        "Anbieter ohne Authentifizierung sind ausschließlich für lokale Ziele erlaubt."
                    )
                }
                if (definition.connectionType == ProviderConnectionType.OLLAMA_LOCAL && !result.localTarget) {
                    throw ProviderRepositoryException(
                        ProviderRepositoryError.INVALID_DEFINITION,
                        "Lokale Ollama-Verbindungen benötigen ein lokales Ziel."
                    )
                }
                definition.copy(baseUrl = result.normalizedUrl)
            }
            is ProviderUrlValidationResult.RequiresLocalHttpConfirmation -> throw ProviderRepositoryException(
                ProviderRepositoryError.INVALID_DEFINITION,
                result.message
            )
            is ProviderUrlValidationResult.Invalid -> throw ProviderRepositoryException(
                ProviderRepositoryError.INVALID_DEFINITION,
                result.message
            )
        }
    }

    private fun validateModels(providerId: ProviderId, models: List<ProviderModelDefinition>) {
        if (models.any { it.providerId != providerId }) {
            throw ProviderRepositoryException(ProviderRepositoryError.MODEL_PROVIDER_MISMATCH, "Mindestens ein Modell gehört zu einem anderen Anbieter.")
        }
        if (models.map { it.modelId }.distinct().size != models.size) {
            throw ProviderRepositoryException(ProviderRepositoryError.INVALID_DEFINITION, "Modell-IDs müssen je Anbieter eindeutig sein.")
        }
    }

    private fun toDefinitionWithSecretState(entity: ProviderEntity): ProviderDefinition =
        entity.toDefinition().copy(hasSecret = secretState(ProviderId(entity.providerId)))

    private fun secretState(providerId: ProviderId): Boolean =
        if (providerId.isCustom) secretStorage.contains(providerId) else false

    private suspend fun <T> storageCall(block: suspend () -> T): T = try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (error: ProviderRepositoryException) {
        throw error
    } catch (_: Exception) {
        throw ProviderRepositoryException(ProviderRepositoryError.STORAGE_FAILURE, "Die Anbieterdaten konnten nicht sicher verarbeitet werden.")
    }
}

internal fun ProviderDefinition.toEntity(): ProviderEntity = ProviderEntity(
    providerId = id.value,
    displayName = displayName,
    connectionType = connectionType.name,
    baseUrl = baseUrl,
    authenticationType = authenticationType.name,
    defaultModelId = defaultModelId,
    streaming = capabilities.streaming,
    modelDiscovery = capabilities.modelDiscovery,
    tools = capabilities.tools,
    vision = capabilities.vision,
    timeoutMs = timeoutMs,
    enabled = enabled,
    builtIn = builtIn,
    localHttpConfirmed = localHttpConfirmed,
    hasSecret = hasSecret,
    createdAt = createdAt,
    updatedAt = updatedAt
)

internal fun ProviderEntity.toDefinition(): ProviderDefinition = ProviderDefinition.create(
    id = ProviderId(providerId),
    displayName = displayName,
    connectionType = ProviderConnectionType.valueOf(connectionType),
    baseUrl = baseUrl,
    authenticationType = ProviderAuthenticationType.valueOf(authenticationType),
    defaultModelId = defaultModelId,
    capabilities = ProviderCapabilities(streaming, modelDiscovery, tools, vision),
    timeoutMs = timeoutMs,
    enabled = enabled,
    builtIn = builtIn,
    localHttpConfirmed = localHttpConfirmed,
    hasSecret = hasSecret,
    createdAt = createdAt,
    updatedAt = updatedAt
)

internal fun ProviderModelDefinition.toEntity(): ProviderModelEntity = ProviderModelEntity(
    providerId = providerId.value,
    modelId = modelId,
    displayName = displayName,
    source = source.name,
    enabled = enabled,
    createdAt = createdAt,
    updatedAt = updatedAt
)

internal fun ProviderModelEntity.toDefinition(): ProviderModelDefinition = ProviderModelDefinition.create(
    providerId = ProviderId(providerId),
    modelId = modelId,
    displayName = displayName,
    source = ProviderModelSource.valueOf(source),
    enabled = enabled,
    createdAt = createdAt,
    updatedAt = updatedAt
)
