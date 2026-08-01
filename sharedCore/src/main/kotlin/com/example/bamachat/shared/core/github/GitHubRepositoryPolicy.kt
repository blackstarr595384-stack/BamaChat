package com.example.bamachat.shared.core.github

import java.util.Locale
import java.nio.charset.StandardCharsets

object GitHubRepositoryPolicy {
    const val OWNER = "blackstarr595384-stack"
    const val REPOSITORY = "BamaChat"
    const val DEFAULT_REF = "phase-7.5b-stable"
    const val RELEASED_BRANCH = "phase-7.5b-shared-provider-selection-core"

    val repository = GitHubRepositoryRef(OWNER, REPOSITORY)
    val allowedRefs: Set<String> = setOf(DEFAULT_REF, RELEASED_BRANCH)

    fun isAllowed(repository: GitHubRepositoryRef): Boolean {
        return repository.owner == OWNER && repository.name == REPOSITORY
    }

    fun isAllowedRef(ref: String): Boolean {
        return ref == ref.trim() && ref in allowedRefs
    }
}

enum class GitHubPathRejection {
    EMPTY,
    ABSOLUTE,
    TRAVERSAL,
    ENCODED_PATH,
    BACKSLASH,
    NULL_BYTE,
    CONTROL_CHARACTER,
    UNSAFE_UNICODE_FORMAT,
    UNTRUSTED_BOUNDARY_MARKER,
    PATH_TOO_LONG,
    INVALID_SEGMENT,
    SENSITIVE_PATH,
    ARTIFACT_PATH,
    UNSUPPORTED_TYPE
}

sealed interface GitHubPathValidation {
    data object Allowed : GitHubPathValidation
    data class Rejected(val reason: GitHubPathRejection) : GitHubPathValidation
}

object GitHubPathPolicy {
    internal const val MAX_PATH_UTF8_BYTES = 4_096

    private val allowedExtensions = setOf(
        "kt",
        "kts",
        "java",
        "md",
        "xml",
        "json",
        "toml",
        "yml",
        "yaml",
        "txt",
        "rules",
        "ps1",
        "js",
        "ts"
    )

    private val blockedExtensions = setOf(
        "apk",
        "aab",
        "exe",
        "msi",
        "zip",
        "jar",
        "class",
        "so",
        "dll",
        "dylib",
        "png",
        "jpg",
        "jpeg",
        "gif",
        "webp",
        "svg",
        "ico",
        "mp3",
        "wav",
        "ogg",
        "mp4",
        "mkv",
        "mov",
        "avi",
        "db",
        "sqlite",
        "sqlite3",
        "jks",
        "keystore",
        "p12",
        "pfx",
        "pem",
        "key",
        "crt",
        "der"
    )

    private val blockedDirectories = setOf(
        ".git",
        ".gradle",
        ".idea",
        ".ssh",
        ".gnupg",
        ".aws",
        ".azure",
        ".kube",
        "build",
        "out",
        "dist",
        "node_modules",
        "captures",
        "reports"
    )

    private val blockedExactNames = setOf(
        ".env",
        "local.properties",
        "keystore.properties",
        "google-services.json",
        "id_rsa",
        "id_ed25519"
    )

    private val blockedPrefixes = listOf(
        ".env.",
        "service-account",
        "firebase-admin",
        "credentials",
        "secrets",
        "token"
    )

    fun validate(path: String): GitHubPathValidation {
        if (path.toByteArray(StandardCharsets.UTF_8).size > MAX_PATH_UTF8_BYTES) {
            return GitHubPathValidation.Rejected(GitHubPathRejection.PATH_TOO_LONG)
        }
        if ('\u0000' in path) {
            return GitHubPathValidation.Rejected(GitHubPathRejection.NULL_BYTE)
        }
        if (path.codePoints().anyMatch(::isControlCharacter)) {
            return GitHubPathValidation.Rejected(GitHubPathRejection.CONTROL_CHARACTER)
        }
        if (path.codePoints().anyMatch { Character.getType(it) == Character.FORMAT.toInt() }) {
            return GitHubPathValidation.Rejected(GitHubPathRejection.UNSAFE_UNICODE_FORMAT)
        }
        if (UNTRUSTED_BOUNDARY_MARKER.containsMatchIn(path)) {
            return GitHubPathValidation.Rejected(GitHubPathRejection.UNTRUSTED_BOUNDARY_MARKER)
        }
        if (path.isBlank() || path != path.trim()) {
            return GitHubPathValidation.Rejected(GitHubPathRejection.EMPTY)
        }
        if (path.startsWith('/') || DRIVE_PREFIX.matches(path) || URI_PREFIX.containsMatchIn(path)) {
            return GitHubPathValidation.Rejected(GitHubPathRejection.ABSOLUTE)
        }
        if ('\\' in path) {
            return GitHubPathValidation.Rejected(GitHubPathRejection.BACKSLASH)
        }
        if ('%' in path) {
            return GitHubPathValidation.Rejected(GitHubPathRejection.ENCODED_PATH)
        }
        val segments = path.split('/')
        if (segments.any { it.isBlank() || it == "." }) {
            return GitHubPathValidation.Rejected(GitHubPathRejection.INVALID_SEGMENT)
        }
        if (segments.any { it == ".." }) {
            return GitHubPathValidation.Rejected(GitHubPathRejection.TRAVERSAL)
        }
        val normalizedSegments = segments.map { it.lowercase(Locale.ROOT) }
        if (normalizedSegments.any { it in blockedDirectories }) {
            return GitHubPathValidation.Rejected(GitHubPathRejection.ARTIFACT_PATH)
        }
        val fileName = normalizedSegments.last()
        if (fileName in blockedExactNames || blockedPrefixes.any(fileName::startsWith)) {
            return GitHubPathValidation.Rejected(GitHubPathRejection.SENSITIVE_PATH)
        }
        val extension = fileName.substringAfterLast('.', missingDelimiterValue = "")
        if (extension in blockedExtensions) {
            return GitHubPathValidation.Rejected(GitHubPathRejection.ARTIFACT_PATH)
        }
        if (extension !in allowedExtensions) {
            return GitHubPathValidation.Rejected(GitHubPathRejection.UNSUPPORTED_TYPE)
        }
        return GitHubPathValidation.Allowed
    }

    fun isAllowed(path: String): Boolean = validate(path) == GitHubPathValidation.Allowed

    fun normalizeAllowedPaths(paths: Iterable<String>): List<String> {
        return paths.asSequence()
            .filter(::isAllowed)
            .distinct()
            .sorted()
            .take(GitHubReadLimits.MAX_FILES)
            .toList()
    }

    private val DRIVE_PREFIX = Regex("^[A-Za-z]:/")
    private val URI_PREFIX = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")
    private val UNTRUSTED_BOUNDARY_MARKER = Regex(
        pattern = "(?:BEGIN|END)\\s+UNTRUSTED\\s+(?:REPOSITORY\\s+CONTENT|MODEL\\s+OUTPUT)",
        option = RegexOption.IGNORE_CASE
    )

    private fun isControlCharacter(codePoint: Int): Boolean {
        return codePoint in 0x01..0x1F ||
            codePoint in 0x7F..0x9F ||
            codePoint == 0x2028 ||
            codePoint == 0x2029
    }
}
