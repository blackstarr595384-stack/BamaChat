package com.example.bamachat.data.provider.chat

/** The old provider flag has no model binding. Never interpret it as permission for cloud routing. */
object LegacyOllamaGuard {
    const val MESSAGE = "Die bisherige Ollama-Einstellung ist noch nicht mit einem Modell verbunden. " +
        "Richte Ollama unter KI und Modelle → Anbieter verwalten als eigenen Anbieter ein und wähle " +
        "anschließend Anbieter und Modell für den Chat. Es wurde kein anderer Anbieter verwendet."

    fun blocksStandardChat(provider: String?): Boolean = provider == "Ollama"

    fun blocksSelection(provider: String?, selection: ActiveChatProviderSelection): Boolean =
        selection == ActiveChatProviderSelection.Legacy && blocksStandardChat(provider)
}
