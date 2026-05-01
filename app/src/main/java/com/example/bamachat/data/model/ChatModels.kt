package com.example.bamachat.data.model

data class ChatMessage(
    val id: String = "",
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val translatedText: String? = null,
    val linkPreview: LinkPreview? = null,
    val role: String? = null,
    val imageUrl: String? = null
)

data class LinkPreview(
    val title: String?,
    val description: String?,
    val imageUrl: String?,
    val url: String
)

data class OllamaChatRequest(
    val model: String,
    val messages: List<OllamaMessage>,
    val stream: Boolean = true,
    val options: Map<String, Any>? = null
)

data class OllamaMessage(
    val role: String,
    val content: String
)

data class OllamaChatResponse(
    val model: String,
    val message: OllamaMessage,
    val done: Boolean
)

data class OllamaRequest(
    val model: String,
    val prompt: String,
    val stream: Boolean = false
)

data class OllamaResponse(
    val response: String,
    val model: String,
    val done: Boolean
)

data class OllamaTagsResponse(
    val models: List<ModelInfo> = emptyList()
)

data class ModelInfo(
    val name: String,
    val model: String,
    val size: Long = 0,
    val modified_at: String = ""
)
