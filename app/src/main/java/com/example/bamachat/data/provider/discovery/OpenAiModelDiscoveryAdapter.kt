package com.example.bamachat.data.provider.discovery

import com.example.bamachat.data.provider.ProviderAuthenticationType
import com.example.bamachat.data.provider.ProviderDefinition
import com.example.bamachat.data.provider.chat.ProviderEndpointBuilder
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.Request

@Singleton
class OpenAiModelDiscoveryAdapter @Inject constructor() : ProviderModelDiscoveryAdapter {
    override suspend fun discover(
        provider: ProviderDefinition,
        normalizedBaseUrl: String,
        secret: String?
    ): ProviderDiscoveryResult {
        val request = Request.Builder()
            .url(ProviderEndpointBuilder.openAiModels(normalizedBaseUrl))
            .header("Accept", "application/json")
            .apply {
                if (provider.authenticationType == ProviderAuthenticationType.BEARER) {
                    header("Authorization", "Bearer ${secret.orEmpty()}")
                }
            }
            .get()
            .build()
        val root = ProviderDiscoveryHttp.get(request, provider.timeoutMs)
        val data = root.takeIf { it.isJsonObject }?.asJsonObject?.get("data")
        if (data == null || !data.isJsonArray) {
            throw ProviderDiscoveryException(ProviderDiscoveryError.UNEXPECTED_FORMAT, "Discovery response format unsupported")
        }
        val ids = data.asJsonArray.asSequence().mapNotNull { item ->
            item.takeIf { it.isJsonObject }?.asJsonObject?.get("id")?.takeIf { it.isJsonPrimitive }?.asString
        }
        val (models, truncated) = ProviderDiscoveryHttp.normalizeModels(ids)
        return ProviderDiscoveryResult(provider.id, models, truncated)
    }
}
