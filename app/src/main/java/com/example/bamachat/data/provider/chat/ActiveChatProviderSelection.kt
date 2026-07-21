package com.example.bamachat.data.provider.chat

import com.example.bamachat.data.provider.ProviderId

sealed interface ActiveChatProviderSelection {
    data object Legacy : ActiveChatProviderSelection

    data class Custom(
        val providerId: ProviderId,
        val modelId: String
    ) : ActiveChatProviderSelection {
        init {
            require(providerId.isCustom) { "Nur benutzerdefinierte Anbieter sind zulässig." }
            require(modelId.isNotBlank() && modelId == modelId.trim()) { "Die Modell-ID ist ungültig." }
        }

        override fun toString(): String = "Custom(provider=$providerId, model=redacted)"
    }
}
