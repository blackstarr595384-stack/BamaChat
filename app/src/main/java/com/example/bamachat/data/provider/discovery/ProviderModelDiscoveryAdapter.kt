package com.example.bamachat.data.provider.discovery

import com.example.bamachat.data.provider.ProviderDefinition

interface ProviderModelDiscoveryAdapter {
    suspend fun discover(
        provider: ProviderDefinition,
        normalizedBaseUrl: String,
        secret: String?
    ): ProviderDiscoveryResult
}
