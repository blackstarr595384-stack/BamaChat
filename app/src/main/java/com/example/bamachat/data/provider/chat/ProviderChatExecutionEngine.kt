package com.example.bamachat.data.provider.chat

import com.example.bamachat.data.provider.ProviderAuthenticationType
import com.example.bamachat.data.provider.ProviderConnectionType
import com.example.bamachat.data.provider.ProviderSecretStorage
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

@Singleton
class ProviderChatExecutionEngine @Inject constructor(
    private val resolver: ActiveChatProviderResolver,
    private val secretStorage: ProviderSecretStorage,
    private val openAiAdapter: OpenAiCompatibleChatAdapter,
    private val ollamaAdapter: OllamaLocalChatAdapter
) {
    suspend fun execute(request: ProviderChatRequest, onChunk: suspend (ProviderChatChunk) -> Unit): ProviderChatResult {
        val resolved = resolver.resolve(request.selection)
        if (resolved !is ActiveChatProviderResolution.ResolvedCustomProvider) {
            throw ProviderChatException(ProviderChatError.INVALID_SELECTION, message = "Custom provider selection invalid")
        }
        val secret = if (resolved.definition.authenticationType == ProviderAuthenticationType.BEARER) {
            secretStorage.get(resolved.definition.id)?.takeIf { it.isNotBlank() }
                ?: throw ProviderChatException(ProviderChatError.SECRET_MISSING, message = "Provider secret unavailable")
        } else null
        val adapter = when (resolved.definition.connectionType) {
            ProviderConnectionType.OPENAI_COMPATIBLE -> openAiAdapter
            ProviderConnectionType.OLLAMA_LOCAL -> ollamaAdapter
        }
        return try {
            adapter.execute(
                provider = resolved.definition,
                normalizedBaseUrl = resolved.normalizedBaseUrl,
                modelId = resolved.model.modelId,
                secret = secret,
                messages = request.messages,
                onChunk = onChunk
            )
        } catch (error: CancellationException) {
            throw error
        }
    }
}
