package com.example.bamachat.desktop

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal const val DESKTOP_LOCAL_STATE_SCHEMA_VERSION = 1

internal enum class DesktopLocalMessageRole {
    SYSTEM,
    USER,
    ASSISTANT
}

internal data class DesktopLocalMessage(
    val id: String,
    val role: String,
    val text: String,
    val createdAtEpochMs: Long
)

internal data class DesktopLocalConversation(
    val id: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val messages: List<DesktopLocalMessage>
)

internal data class DesktopLocalWorkspace(
    val notes: String,
    val updatedAtEpochMs: Long
)

internal data class DesktopLocalState(
    val schemaVersion: Int,
    val ownerScopeId: String,
    val activeConversationId: String?,
    val conversations: List<DesktopLocalConversation>,
    val workspace: DesktopLocalWorkspace
) {
    fun activeConversation(): DesktopLocalConversation? {
        return conversations.firstOrNull { it.id == activeConversationId }
    }

    companion object {
        fun empty(ownerScope: DesktopOwnerScope): DesktopLocalState = DesktopLocalState(
            schemaVersion = DESKTOP_LOCAL_STATE_SCHEMA_VERSION,
            ownerScopeId = ownerScope.id,
            activeConversationId = null,
            conversations = emptyList(),
            workspace = DesktopLocalWorkspace(
                notes = "",
                updatedAtEpochMs = 0L
            )
        )
    }
}

@JvmInline
internal value class DesktopOwnerScope private constructor(val id: String) {
    companion object {
        private const val GUEST_SCOPE_ID = "guest-local-v1"
        private const val ACCOUNT_HASH_PREFIX = "bamachat-desktop-account-scope-v1\u0000"

        fun guest(): DesktopOwnerScope = DesktopOwnerScope(GUEST_SCOPE_ID)

        fun accountFromAuthenticatedUid(authenticatedUid: String): DesktopOwnerScope? {
            val normalizedUid = authenticatedUid.trim()
            if (normalizedUid.isEmpty()) return null
            val digest = MessageDigest.getInstance("SHA-256")
                .digest((ACCOUNT_HASH_PREFIX + normalizedUid).toByteArray(Charsets.UTF_8))
                .joinToString(separator = "") { byte ->
                    "%02x".format(byte.toInt() and 0xff)
                }
            return DesktopOwnerScope("account-$digest")
        }
    }
}

internal object DesktopDataDirectoryResolver {
    const val OVERRIDE_ENVIRONMENT_VARIABLE = "BAMACHAT_DESKTOP_DATA_DIR"
    const val SETTINGS_OVERRIDE_ENVIRONMENT_VARIABLE = "BAMACHAT_DESKTOP_SETTINGS_DIR"

    fun resolve(
        environment: Map<String, String> = System.getenv(),
        userHome: String = System.getProperty("user.home").orEmpty(),
        osName: String = System.getProperty("os.name").orEmpty()
    ): Path {
        environment[OVERRIDE_ENVIRONMENT_VARIABLE]
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { overridePath ->
                return requireAbsolute(Path.of(overridePath), OVERRIDE_ENVIRONMENT_VARIABLE)
            }

        if (osName.startsWith("Windows", ignoreCase = true)) {
            environment["LOCALAPPDATA"]
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { localAppData ->
                    return requireAbsolute(Path.of(localAppData), "LOCALAPPDATA")
                        .resolve("BamaChat")
                        .resolve("data")
                }
        }

        val homePath = requireAbsolute(Path.of(userHome), "user.home")
        return homePath.resolve(".bamachat-desktop").resolve("data")
    }

    fun resolveSettingsDirectory(
        environment: Map<String, String> = System.getenv(),
        userHome: String = System.getProperty("user.home").orEmpty()
    ): Path {
        environment[SETTINGS_OVERRIDE_ENVIRONMENT_VARIABLE]?.let { overridePath ->
            return requireAbsoluteOverride(
                rawPath = overridePath,
                source = SETTINGS_OVERRIDE_ENVIRONMENT_VARIABLE
            )
        }
        environment[OVERRIDE_ENVIRONMENT_VARIABLE]
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { overridePath ->
                return requireAbsolute(Path.of(overridePath), OVERRIDE_ENVIRONMENT_VARIABLE)
                    .resolve("settings")
            }
        return requireAbsolute(Path.of(userHome), "user.home").resolve(".bamachat-desktop")
    }

    private fun requireAbsoluteOverride(rawPath: String, source: String): Path {
        val trimmed = rawPath.trim()
        require(trimmed.isNotEmpty()) { "$source darf nicht leer sein." }
        val path = try {
            Path.of(trimmed)
        } catch (_: InvalidPathException) {
            throw IllegalArgumentException("$source enthält einen ungültigen Pfad.")
        }
        val normalized = requireAbsolute(path, source)
        require(!Files.exists(normalized) || Files.isDirectory(normalized)) {
            "$source muss auf ein Verzeichnis zeigen."
        }
        return normalized
    }

    private fun requireAbsolute(path: Path, source: String): Path {
        require(path.isAbsolute) { "$source muss auf einen absoluten Pfad zeigen." }
        return path.normalize()
    }
}

internal data class DesktopLocalStateLoadResult(
    val state: DesktopLocalState,
    val recoveryCopyCreated: Boolean
)

internal class DesktopAtomicStateWriter(
    private val beforeReplace: (temporaryFile: Path, targetFile: Path) -> Unit = { _, _ -> }
) {
    @Throws(IOException::class)
    fun write(targetFile: Path, bytes: ByteArray) {
        Files.createDirectories(targetFile.parent)
        val temporaryFile = Files.createTempFile(
            targetFile.parent,
            ".${targetFile.fileName}.",
            ".tmp"
        )
        try {
            FileChannel.open(
                temporaryFile,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING
            ).use { channel ->
                val buffer = ByteBuffer.wrap(bytes)
                while (buffer.hasRemaining()) {
                    channel.write(buffer)
                }
                channel.force(true)
            }
            beforeReplace(temporaryFile, targetFile)
            try {
                Files.move(
                    temporaryFile,
                    targetFile,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    temporaryFile,
                    targetFile,
                    StandardCopyOption.REPLACE_EXISTING
                )
            }
        } finally {
            Files.deleteIfExists(temporaryFile)
        }
    }
}

internal class DesktopScopedStateStore(
    dataDirectory: Path = DesktopDataDirectoryResolver.resolve(),
    private val gson: Gson = GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val recoveryId: () -> String = { UUID.randomUUID().toString() },
    private val atomicWriter: DesktopAtomicStateWriter = DesktopAtomicStateWriter()
) {
    private val dataDirectory = dataDirectory.toAbsolutePath().normalize()
    private val lock = directoryLocks.computeIfAbsent(this.dataDirectory.toString()) { Any() }

    fun load(ownerScope: DesktopOwnerScope): DesktopLocalStateLoadResult = synchronized(lock) {
        val stateFile = stateFile(ownerScope)
        if (!Files.isRegularFile(stateFile)) {
            return@synchronized DesktopLocalStateLoadResult(
                state = DesktopLocalState.empty(ownerScope),
                recoveryCopyCreated = false
            )
        }

        try {
            val json = Files.readString(stateFile, Charsets.UTF_8)
            val loaded = gson.fromJson(json, DesktopLocalState::class.java)
            if (!isCompatibleAndValid(loaded, ownerScope)) {
                throw IllegalStateException("Inkompatibler lokaler Desktop-Zustand.")
            }
            DesktopLocalStateLoadResult(
                state = loaded,
                recoveryCopyCreated = false
            )
        } catch (_: Exception) {
            DesktopLocalStateLoadResult(
                state = DesktopLocalState.empty(ownerScope),
                recoveryCopyCreated = preserveRecoveryCopy(stateFile)
            )
        }
    }

    @Throws(IOException::class)
    fun save(ownerScope: DesktopOwnerScope, state: DesktopLocalState) = synchronized(lock) {
        require(isCompatibleAndValid(state, ownerScope)) {
            "Lokaler Desktop-Zustand verletzt die Persistenzgrenzen."
        }
        val encoded = gson.toJson(state).toByteArray(Charsets.UTF_8)
        atomicWriter.write(stateFile(ownerScope), encoded)
    }

    internal fun stateFile(ownerScope: DesktopOwnerScope): Path {
        return dataDirectory.resolve("desktop-state-${ownerScope.id}.json")
    }

    private fun isCompatibleAndValid(
        state: DesktopLocalState?,
        ownerScope: DesktopOwnerScope
    ): Boolean = runCatching {
        if (state == null) return@runCatching false
        if (state.schemaVersion != DESKTOP_LOCAL_STATE_SCHEMA_VERSION) return@runCatching false
        if (state.ownerScopeId != ownerScope.id) return@runCatching false
        if (state.workspace.updatedAtEpochMs < 0L) return@runCatching false

        val conversationIds = mutableSetOf<String>()
        val messageIds = mutableSetOf<String>()
        state.conversations.forEach { conversation ->
            if (conversation.id.isBlank() || !conversationIds.add(conversation.id)) {
                return@runCatching false
            }
            if (conversation.createdAtEpochMs < 0L ||
                conversation.updatedAtEpochMs < conversation.createdAtEpochMs
            ) {
                return@runCatching false
            }
            conversation.messages.forEach { message ->
                if (message.id.isBlank() || !messageIds.add(message.id)) {
                    return@runCatching false
                }
                if (message.role !in allowedMessageRoles || message.createdAtEpochMs < 0L) {
                    return@runCatching false
                }
            }
        }
        state.activeConversationId == null || state.activeConversationId in conversationIds
    }.getOrDefault(false)

    private fun preserveRecoveryCopy(stateFile: Path): Boolean {
        if (!Files.isRegularFile(stateFile)) return false
        return runCatching {
            Files.createDirectories(dataDirectory)
            val recoveryFile = dataDirectory.resolve(
                "${stateFile.fileName}.recovery-${clock()}-${recoveryId()}.json"
            )
            Files.copy(stateFile, recoveryFile)
            true
        }.getOrDefault(false)
    }

    companion object {
        private val allowedMessageRoles = DesktopLocalMessageRole.entries
            .mapTo(mutableSetOf()) { it.name }
        private val directoryLocks = ConcurrentHashMap<String, Any>()
    }
}
