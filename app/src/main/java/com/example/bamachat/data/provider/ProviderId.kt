package com.example.bamachat.data.provider

import java.util.UUID

enum class ProviderIdValidationError {
    EMPTY,
    UNKNOWN_BUILT_IN,
    INVALID_CUSTOM_UUID,
    UNSUPPORTED_FORMAT
}

class ProviderIdValidationException(
    val error: ProviderIdValidationError,
    message: String
) : IllegalArgumentException(message)

@JvmInline
value class ProviderId(val value: String) {
    init {
        validate(value)?.let { throw it }
    }

    val isBuiltIn: Boolean
        get() = value.startsWith(BUILT_IN_PREFIX)

    val isCustom: Boolean
        get() = value.startsWith(CUSTOM_PREFIX)

    override fun toString(): String = when {
        isBuiltIn -> "ProviderId($value)"
        isCustom -> "ProviderId(custom:…${value.takeLast(4)})"
        else -> "ProviderId(invalid)"
    }

    companion object {
        const val OPENROUTER = "builtin:openrouter"
        const val OPENCODE = "builtin:opencode"
        const val GROQ = "builtin:groq"
        const val CEREBRAS = "builtin:cerebras"
        const val TOGETHER = "builtin:together"
        const val OLLAMA = "builtin:ollama"

        private const val BUILT_IN_PREFIX = "builtin:"
        private const val CUSTOM_PREFIX = "custom:"

        val builtInValues: Set<String> = setOf(
            OPENROUTER,
            OPENCODE,
            GROQ,
            CEREBRAS,
            TOGETHER,
            OLLAMA
        )

        fun newCustom(uuid: UUID = UUID.randomUUID()): ProviderId = ProviderId("$CUSTOM_PREFIX$uuid")

        private fun validate(value: String): ProviderIdValidationException? {
            if (value.isBlank()) {
                return ProviderIdValidationException(
                    ProviderIdValidationError.EMPTY,
                    "Die Anbieter-ID darf nicht leer sein."
                )
            }
            if (value.startsWith(BUILT_IN_PREFIX)) {
                return if (value in builtInValues) null else ProviderIdValidationException(
                    ProviderIdValidationError.UNKNOWN_BUILT_IN,
                    "Die integrierte Anbieter-ID ist nicht zulässig."
                )
            }
            if (value.startsWith(CUSTOM_PREFIX)) {
                val rawUuid = value.removePrefix(CUSTOM_PREFIX)
                val uuid = runCatching { UUID.fromString(rawUuid) }.getOrNull()
                return if (uuid != null && uuid.toString() == rawUuid) null else ProviderIdValidationException(
                    ProviderIdValidationError.INVALID_CUSTOM_UUID,
                    "Die benutzerdefinierte Anbieter-ID ist ungültig."
                )
            }
            return ProviderIdValidationException(
                ProviderIdValidationError.UNSUPPORTED_FORMAT,
                "Das Format der Anbieter-ID wird nicht unterstützt."
            )
        }
    }
}
