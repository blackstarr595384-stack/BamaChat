package com.example.bamachat.desktop

import com.google.gson.JsonParser
import java.io.File

object DesktopFirebaseConfig {
    private val parsedConfig: ParsedFirebaseConfig by lazy {
        readGoogleServicesConfig()
    }

    fun defaultApiKey(): String = parsedConfig.apiKey.orEmpty()

    fun defaultProjectId(): String = parsedConfig.projectId.orEmpty()

    fun defaultGoogleOAuthClientId(): String = parsedConfig.googleOAuthClientId.orEmpty()

    private fun readGoogleServicesConfig(): ParsedFirebaseConfig {
        val candidates = listOf(
            File("app/google-services.json"),
            File(System.getProperty("user.dir"), "app/google-services.json")
        )
        val file = candidates.firstOrNull { it.exists() } ?: return ParsedFirebaseConfig()
        return runCatching {
            val root = JsonParser.parseString(file.readText()).asJsonObject
            val projectId = root.getAsJsonObject("project_info")
                ?.get("project_id")
                ?.asString
                ?.trim()
                ?.takeIf { it.isNotBlank() }
            val apiKey = root.getAsJsonArray("client")
                ?.firstOrNull()
                ?.asJsonObject
                ?.getAsJsonArray("api_key")
                ?.firstOrNull()
                ?.asJsonObject
                ?.get("current_key")
                ?.asString
                ?.trim()
                ?.takeIf { it.isNotBlank() }
            val googleOAuthClientId = root.getAsJsonArray("client")
                ?.firstOrNull()
                ?.asJsonObject
                ?.getAsJsonArray("oauth_client")
                ?.mapNotNull { it.asJsonObject }
                ?.firstOrNull { candidate ->
                    candidate.get("client_type")?.asInt == 3
                }
                ?.get("client_id")
                ?.asString
                ?.trim()
                ?.takeIf { it.isNotBlank() }
            ParsedFirebaseConfig(
                apiKey = apiKey,
                projectId = projectId,
                googleOAuthClientId = googleOAuthClientId
            )
        }.getOrDefault(ParsedFirebaseConfig())
    }
}

private data class ParsedFirebaseConfig(
    val apiKey: String? = null,
    val projectId: String? = null,
    val googleOAuthClientId: String? = null
)
