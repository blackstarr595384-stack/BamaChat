package com.example.bamachat.data.provider

data class ProviderCapabilities(
    val streaming: Boolean,
    val modelDiscovery: Boolean,
    val tools: Boolean,
    val vision: Boolean
)
