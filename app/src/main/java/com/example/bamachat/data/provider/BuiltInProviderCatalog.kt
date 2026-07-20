package com.example.bamachat.data.provider

import com.example.bamachat.data.ApiClient

data class BuiltInProviderSeed(
    val definition: ProviderDefinition,
    val models: List<ProviderModelDefinition>
)

object BuiltInProviderCatalog {
    val entries: List<BuiltInProviderSeed> by lazy {
        val now = System.currentTimeMillis()
        listOf(
            seed(
                id = ProviderId(ProviderId.OPENROUTER),
                name = "OpenRouter",
                baseUrl = ApiClient.Provider.OPENROUTER.baseUrl,
                defaultModel = "google/gemma-3-27b-it:free",
                models = ApiClient.OPENROUTER_FREE_MODELS,
                capabilities = ProviderCapabilities(streaming = true, modelDiscovery = false, tools = true, vision = true),
                now = now
            ),
            seed(
                id = ProviderId(ProviderId.OPENCODE),
                name = "OpenCode",
                baseUrl = ApiClient.Provider.OPENCODE.baseUrl,
                defaultModel = ApiClient.OPENCODE_DEFAULT_MODEL,
                models = listOf(ApiClient.OPENCODE_DEFAULT_MODEL),
                capabilities = ProviderCapabilities(streaming = true, modelDiscovery = false, tools = true, vision = false),
                now = now
            ),
            seed(
                id = ProviderId(ProviderId.GROQ),
                name = "Groq",
                baseUrl = ApiClient.Provider.GROQ.baseUrl,
                defaultModel = ApiClient.GROQ_DEFAULT,
                models = ApiClient.GROQ_MODELS,
                capabilities = ProviderCapabilities(streaming = true, modelDiscovery = false, tools = true, vision = false),
                now = now
            ),
            seed(
                id = ProviderId(ProviderId.CEREBRAS),
                name = "Cerebras",
                baseUrl = ApiClient.Provider.CEREBRAS.baseUrl,
                defaultModel = ApiClient.CEREBRAS_DEFAULT,
                models = ApiClient.CEREBRAS_MODELS,
                capabilities = ProviderCapabilities(streaming = true, modelDiscovery = false, tools = false, vision = false),
                now = now
            ),
            seed(
                id = ProviderId(ProviderId.TOGETHER),
                name = "Together",
                baseUrl = ApiClient.Provider.TOGETHER.baseUrl,
                defaultModel = ApiClient.TOGETHER_DEFAULT,
                models = ApiClient.TOGETHER_MODELS,
                capabilities = ProviderCapabilities(streaming = true, modelDiscovery = false, tools = true, vision = false),
                now = now
            ),
            seed(
                id = ProviderId(ProviderId.OLLAMA),
                name = "Ollama",
                baseUrl = "http://localhost:11434/",
                defaultModel = null,
                models = emptyList(),
                capabilities = ProviderCapabilities(streaming = true, modelDiscovery = true, tools = false, vision = false),
                now = now,
                connectionType = ProviderConnectionType.OLLAMA_LOCAL,
                authenticationType = ProviderAuthenticationType.NONE_LOCAL_ONLY,
                localHttpConfirmed = true
            )
        )
    }

    private fun seed(
        id: ProviderId,
        name: String,
        baseUrl: String,
        defaultModel: String?,
        models: List<String>,
        capabilities: ProviderCapabilities,
        now: Long,
        connectionType: ProviderConnectionType = ProviderConnectionType.OPENAI_COMPATIBLE,
        authenticationType: ProviderAuthenticationType = ProviderAuthenticationType.BEARER,
        localHttpConfirmed: Boolean = false
    ): BuiltInProviderSeed = BuiltInProviderSeed(
        definition = ProviderDefinition.create(
            id = id,
            displayName = name,
            connectionType = connectionType,
            baseUrl = baseUrl,
            authenticationType = authenticationType,
            defaultModelId = defaultModel,
            capabilities = capabilities,
            builtIn = true,
            localHttpConfirmed = localHttpConfirmed,
            hasSecret = false,
            createdAt = now,
            updatedAt = now
        ),
        models = models.distinct().map { modelId ->
            ProviderModelDefinition.create(
                providerId = id,
                modelId = modelId,
                displayName = modelId,
                source = ProviderModelSource.BUILT_IN,
                createdAt = now,
                updatedAt = now
            )
        }
    )
}
