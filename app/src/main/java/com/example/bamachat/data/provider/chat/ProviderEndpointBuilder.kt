package com.example.bamachat.data.provider.chat

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object ProviderEndpointBuilder {
    fun openAiChatCompletions(normalizedBaseUrl: String): HttpUrl =
        build(normalizedBaseUrl, listOf("chat", "completions"), listOf("chat", "completions"))

    fun ollamaChat(normalizedBaseUrl: String): HttpUrl =
        build(normalizedBaseUrl, listOf("api", "chat"), listOf("api", "chat"))

    fun openAiModels(normalizedBaseUrl: String): HttpUrl =
        build(normalizedBaseUrl, listOf("models"), listOf("models"))

    fun ollamaTags(normalizedBaseUrl: String): HttpUrl =
        build(normalizedBaseUrl, listOf("api", "tags"), listOf("api", "tags"))

    private fun build(baseUrl: String, suffix: List<String>, existingSuffix: List<String>): HttpUrl {
        val base = baseUrl.toHttpUrlOrNull()
            ?: throw ProviderChatException(ProviderChatError.UNSAFE_URL, message = "Ungültige Anbieteradresse.")
        if (base.username.isNotEmpty() || base.password.isNotEmpty() || base.query != null || base.fragment != null) {
            throw ProviderChatException(ProviderChatError.UNSAFE_URL, message = "Unsichere Anbieteradresse.")
        }
        val current = base.pathSegments.filter { it.isNotBlank() }
        if (current.takeLast(existingSuffix.size) == existingSuffix) return base.newBuilder().query(null).fragment(null).build()
        val builder = base.newBuilder().query(null).fragment(null)
        suffix.forEach(builder::addPathSegment)
        return builder.build()
    }
}
