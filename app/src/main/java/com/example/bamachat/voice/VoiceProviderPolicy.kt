package com.example.bamachat.voice

import java.net.URI
import java.util.Locale

object VoiceProviderPolicy {
    fun requiresOnDeviceInput(mode: VoiceMode, requestedProvider: VoiceInputProvider): Boolean =
        mode == VoiceMode.LOCAL || requestedProvider == VoiceInputProvider.ANDROID

    fun resolveLocalOutputProvider(
        requestedProvider: VoiceOutputProvider,
        piperEndpoint: String
    ): VoiceOutputProvider = if (
        requestedProvider == VoiceOutputProvider.PIPER && isPrivateNetworkEndpoint(piperEndpoint)
    ) {
        VoiceOutputProvider.PIPER
    } else {
        VoiceOutputProvider.ANDROID
    }

    fun isPrivateNetworkEndpoint(rawEndpoint: String): Boolean {
        val host = runCatching { URI(rawEndpoint.trim()).host }
            .getOrNull()
            ?.trim('[', ']')
            ?.lowercase(Locale.ROOT)
            ?: return false
        if (host == "localhost" || host.endsWith(".local")) return true
        if (host == "::1" || host.startsWith("fe80:") || host.startsWith("fc") || host.startsWith("fd")) return true
        val octets = host.split('.').mapNotNull(String::toIntOrNull)
        if (octets.size != 4 || octets.any { it !in 0..255 }) return false
        return octets[0] == 10 ||
            octets[0] == 127 ||
            (octets[0] == 169 && octets[1] == 254) ||
            (octets[0] == 172 && octets[1] in 16..31) ||
            (octets[0] == 192 && octets[1] == 168)
    }
}
