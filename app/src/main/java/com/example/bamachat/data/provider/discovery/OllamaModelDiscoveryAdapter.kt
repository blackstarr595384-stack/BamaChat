package com.example.bamachat.data.provider.discovery

import com.example.bamachat.data.provider.ProviderDefinition
import com.example.bamachat.data.provider.chat.ProviderEndpointBuilder
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.Request

@Singleton
class OllamaModelDiscoveryAdapter @Inject constructor() : ProviderModelDiscoveryAdapter {
    override suspend fun discover(
        provider: ProviderDefinition,
        normalizedBaseUrl: String,
        secret: String?
    ): ProviderDiscoveryResult {
        val request = Request.Builder()
            .url(ProviderEndpointBuilder.ollamaTags(normalizedBaseUrl))
            .header("Accept", "application/json")
            .get()
            .build()
        val root = ProviderDiscoveryHttp.get(request, provider.timeoutMs)
        val modelsNode = root.takeIf { it.isJsonObject }?.asJsonObject?.get("models")
        if (modelsNode == null || !modelsNode.isJsonArray) {
            throw ProviderDiscoveryException(ProviderDiscoveryError.UNEXPECTED_FORMAT, "Discovery response format unsupported")
        }
        val ids = modelsNode.asJsonArray.asSequence().mapNotNull { item ->
            val objectNode = item.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            sequenceOf("name", "model")
                .mapNotNull { key -> objectNode.get(key)?.takeIf { it.isJsonPrimitive }?.asString }
                .firstOrNull { it.isNotBlank() }
        }
        val (models, truncated) = ProviderDiscoveryHttp.normalizeModels(ids)
        return ProviderDiscoveryResult(provider.id, models, truncated)
    }
}
