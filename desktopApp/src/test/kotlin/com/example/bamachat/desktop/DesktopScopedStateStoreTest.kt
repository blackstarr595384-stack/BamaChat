package com.example.bamachat.desktop

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class DesktopScopedStateStoreTest {
    @Test
    fun smokeOverrideIsolatesDataAndSettingsUnderTemporaryRoot() =
        withTemporaryDataDirectory { dataDirectory ->
            val environment = mapOf(
                DesktopDataDirectoryResolver.OVERRIDE_ENVIRONMENT_VARIABLE to dataDirectory.toString(),
                "LOCALAPPDATA" to dataDirectory.resolve("unused-local-app-data").toString()
            )

            assertEquals(
                dataDirectory.toAbsolutePath().normalize(),
                DesktopDataDirectoryResolver.resolve(
                    environment = environment,
                    userHome = dataDirectory.resolve("unused-home").toString(),
                    osName = "Windows 11"
                )
            )
            assertEquals(
                dataDirectory.resolve("settings").toAbsolutePath().normalize(),
                DesktopDataDirectoryResolver.resolveSettingsDirectory(
                    environment = environment,
                    userHome = dataDirectory.resolve("unused-home").toString()
                )
            )
        }

    @Test
    fun settingsDefaultWithoutOverrideRemainsUnderUserHome() =
        withTemporaryDataDirectory { temporaryRoot ->
            val userHome = temporaryRoot.resolve("production-home").toAbsolutePath().normalize()

            assertEquals(
                userHome.resolve(".bamachat-desktop"),
                DesktopDataDirectoryResolver.resolveSettingsDirectory(
                    environment = emptyMap(),
                    userHome = userHome.toString()
                )
            )
        }

    @Test
    fun absoluteSettingsOverrideIsNormalizedAndIndependentFromDataOverride() =
        withTemporaryDataDirectory { temporaryRoot ->
            val settingsDirectory = temporaryRoot.resolve("settings-root")
            val environment = mapOf(
                DesktopDataDirectoryResolver.SETTINGS_OVERRIDE_ENVIRONMENT_VARIABLE to
                    settingsDirectory.resolve("nested").resolve("..").toString(),
                DesktopDataDirectoryResolver.OVERRIDE_ENVIRONMENT_VARIABLE to
                    temporaryRoot.resolve("data-root").toString()
            )

            assertEquals(
                settingsDirectory.toAbsolutePath().normalize(),
                DesktopDataDirectoryResolver.resolveSettingsDirectory(
                    environment = environment,
                    userHome = temporaryRoot.resolve("unused-home").toString()
                )
            )
            assertEquals(
                temporaryRoot.resolve("data-root").toAbsolutePath().normalize(),
                DesktopDataDirectoryResolver.resolve(
                    environment = environment,
                    userHome = temporaryRoot.resolve("unused-home").toString()
                )
            )
        }

    @Test
    fun relativeSettingsOverrideIsRejectedClearly() {
        val failure = assertFailsWith<IllegalArgumentException> {
            DesktopDataDirectoryResolver.resolveSettingsDirectory(
                environment = mapOf(
                    DesktopDataDirectoryResolver.SETTINGS_OVERRIDE_ENVIRONMENT_VARIABLE to
                        "relative/settings"
                ),
                userHome = "C:\\unused-home"
            )
        }

        assertTrue(
            failure.message.orEmpty().contains(
                DesktopDataDirectoryResolver.SETTINGS_OVERRIDE_ENVIRONMENT_VARIABLE
            )
        )
    }

    @Test
    fun settingsOverrideRejectsExistingFile() =
        withTemporaryDataDirectory { temporaryRoot ->
            val regularFile = temporaryRoot.resolve("not-a-directory")
            Files.writeString(regularFile, "fixture")

            val failure = assertFailsWith<IllegalArgumentException> {
                DesktopDataDirectoryResolver.resolveSettingsDirectory(
                    environment = mapOf(
                        DesktopDataDirectoryResolver.SETTINGS_OVERRIDE_ENVIRONMENT_VARIABLE to
                            regularFile.toString()
                    ),
                    userHome = temporaryRoot.resolve("unused-home").toString()
                )
            }

            assertTrue(failure.message.orEmpty().contains("Verzeichnis"))
        }

    @Test
    fun settingsOverrideKeepsProductionDirectoryUntouched() =
        withTemporaryDataDirectory { temporaryRoot ->
            val fakeUserHome = temporaryRoot.resolve("production-home")
            val productionDirectory = fakeUserHome.resolve(".bamachat-desktop")
            val productionSentinel = productionDirectory.resolve("settings.properties")
            Files.createDirectories(productionDirectory)
            Files.writeString(productionSentinel, "unchanged-production-fixture")
            val productionBytes = Files.readAllBytes(productionSentinel)
            val overrideDirectory = temporaryRoot.resolve("isolated-settings")
            val resolved = DesktopDataDirectoryResolver.resolveSettingsDirectory(
                environment = mapOf(
                    DesktopDataDirectoryResolver.SETTINGS_OVERRIDE_ENVIRONMENT_VARIABLE to
                        overrideDirectory.toString()
                ),
                userHome = fakeUserHome.toString()
            )
            val cipher = DesktopCredentialCipher(
                settingsDirectory = resolved,
                platformDetector = DesktopPlatformDetector { false }
            )
            val repository = DesktopSettingsRepository(
                settingsDirectory = resolved,
                credentialCipher = cipher
            )

            repository.save(
                DesktopUserSettings(
                    provider = DesktopProvider.OPENROUTER,
                    openRouterApiKey = "isolated-test-secret",
                    openRouterModel = "fixture-model",
                    firebaseApiKey = "public-fixture-config",
                    firebaseProjectId = "fixture-project",
                    googleOAuthClientId = "fixture-client"
                )
            )

            assertEquals(overrideDirectory.toAbsolutePath().normalize(), resolved)
            assertTrue(Files.isRegularFile(overrideDirectory.resolve("settings.properties")))
            assertTrue(Files.isRegularFile(overrideDirectory.resolve("session_salt.bin")))
            assertTrue(productionBytes.contentEquals(Files.readAllBytes(productionSentinel)))
            assertEquals(1L, Files.list(productionDirectory).use { it.count() })
        }

    @Test
    fun emptyFirstStartReturnsEmptyStateWithoutCreatingAFile() = withTemporaryDataDirectory { dataDirectory ->
        val store = DesktopScopedStateStore(dataDirectory = dataDirectory)
        val ownerScope = DesktopOwnerScope.guest()

        val result = store.load(ownerScope)

        assertEquals(DesktopLocalState.empty(ownerScope), result.state)
        assertFalse(result.recoveryCopyCreated)
        assertFalse(Files.exists(store.stateFile(ownerScope)))
    }

    @Test
    fun unicodeLineBreaksAndMultipleMessagesRoundTrip() = withTemporaryDataDirectory { dataDirectory ->
        val session = DesktopScopedStateSession(
            store = DesktopScopedStateStore(dataDirectory = dataDirectory),
            clock = sequence(1_000L, 2_000L),
            newId = sequence("message-1", "conversation-1", "message-2")
        )

        session.appendMessage(
            DesktopLocalMessageRole.USER,
            "Grüße aus BamaChat 👋\nZweite Zeile"
        )
        session.appendMessage(
            DesktopLocalMessageRole.ASSISTANT,
            "Antwort mit Umlauten: äöüß\nund neuer Zeile"
        )

        val restored = DesktopScopedStateSession(
            store = DesktopScopedStateStore(dataDirectory = dataDirectory)
        ).currentState.activeConversation()?.messages.orEmpty()

        assertEquals(2, restored.size)
        assertEquals("Grüße aus BamaChat 👋\nZweite Zeile", restored[0].text)
        assertEquals("Antwort mit Umlauten: äöüß\nund neuer Zeile", restored[1].text)
        assertEquals("message-1", restored[0].id)
        assertEquals("message-2", restored[1].id)
    }

    @Test
    fun workspaceNotesRoundTrip() = withTemporaryDataDirectory { dataDirectory ->
        val session = DesktopScopedStateSession(
            store = DesktopScopedStateStore(dataDirectory = dataDirectory),
            clock = { 3_000L }
        )

        session.updateWorkspaceNotes("Projekt α\n- TODO: Persistenz prüfen")

        val restored = DesktopScopedStateSession(
            store = DesktopScopedStateStore(dataDirectory = dataDirectory)
        ).currentState
        assertEquals("Projekt α\n- TODO: Persistenz prüfen", restored.workspace.notes)
        assertEquals(3_000L, restored.workspace.updatedAtEpochMs)
    }

    @Test
    fun twoAccountOwnersAreStrictlySeparated() = withTemporaryDataDirectory { dataDirectory ->
        val session = DesktopScopedStateSession(
            store = DesktopScopedStateStore(dataDirectory = dataDirectory),
            initialAuthenticatedUid = "account-a-raw-uid",
            clock = sequence(1_000L, 2_000L),
            newId = sequence("a-message", "a-conversation", "b-message", "b-conversation")
        )
        session.appendMessage(DesktopLocalMessageRole.USER, "Nur Konto A")
        val accountAScope = session.activeOwnerScopeId

        assertIs<DesktopOwnerSwitchResult.Activated>(
            session.switchToAuthenticatedOwner("account-b-raw-uid")
        )
        assertTrue(session.currentState.conversations.isEmpty())
        session.appendMessage(DesktopLocalMessageRole.USER, "Nur Konto B")
        val accountBScope = session.activeOwnerScopeId

        assertNotEquals(accountAScope, accountBScope)
        session.switchToAuthenticatedOwner("account-a-raw-uid")
        assertEquals("Nur Konto A", session.currentState.activeConversation()?.messages?.single()?.text)
        session.switchToAuthenticatedOwner("account-b-raw-uid")
        assertEquals("Nur Konto B", session.currentState.activeConversation()?.messages?.single()?.text)
    }

    @Test
    fun guestAndAccountContentsNeverMix() = withTemporaryDataDirectory { dataDirectory ->
        val session = DesktopScopedStateSession(
            store = DesktopScopedStateStore(dataDirectory = dataDirectory),
            clock = sequence(1_000L, 2_000L, 3_000L, 4_000L),
            newId = sequence(
                "guest-message",
                "guest-conversation",
                "account-message",
                "account-conversation"
            )
        )
        session.appendMessage(DesktopLocalMessageRole.USER, "Gastinhalt")
        session.updateWorkspaceNotes("Gastnotiz")

        session.switchToAuthenticatedOwner("authenticated-account-uid")
        assertTrue(session.currentState.conversations.isEmpty())
        assertEquals("", session.currentState.workspace.notes)
        session.appendMessage(DesktopLocalMessageRole.USER, "Kontoinhalt")
        session.updateWorkspaceNotes("Kontonotiz")

        session.switchToGuest()
        assertEquals("Gastinhalt", session.currentState.activeConversation()?.messages?.single()?.text)
        assertEquals("Gastnotiz", session.currentState.workspace.notes)
        session.switchToAuthenticatedOwner("authenticated-account-uid")
        assertEquals("Kontoinhalt", session.currentState.activeConversation()?.messages?.single()?.text)
        assertEquals("Kontonotiz", session.currentState.workspace.notes)
    }

    @Test
    fun failedOwnerSwitchLeavesScopeAndStateUnchanged() = withTemporaryDataDirectory { dataDirectory ->
        val session = DesktopScopedStateSession(
            store = DesktopScopedStateStore(dataDirectory = dataDirectory),
            clock = { 1_000L },
            newId = sequence("message", "conversation")
        )
        session.appendMessage(DesktopLocalMessageRole.USER, "Bleibt sichtbar")
        val scopeBefore = session.activeOwnerScopeId
        val stateBefore = session.currentState

        val result = session.switchToAuthenticatedOwner("   ")

        assertIs<DesktopOwnerSwitchResult.Rejected>(result)
        assertEquals(scopeBefore, session.activeOwnerScopeId)
        assertEquals(stateBefore, session.currentState)
    }

    @Test
    fun corruptFileIsQuarantinedOnceAndValidStateCanBeStoredAfterward() =
        withTemporaryDataDirectory { dataDirectory ->
            val store = DesktopScopedStateStore(
                dataDirectory = dataDirectory,
                clock = { 4_000L },
                recoveryId = { "recovery-id" }
            )
            val ownerScope = DesktopOwnerScope.guest()
            val stateFile = store.stateFile(ownerScope)
            val corruptBytes = "{not-valid-json".toByteArray(Charsets.UTF_8)
            Files.createDirectories(dataDirectory)
            Files.write(stateFile, corruptBytes)

            val firstLoad = store.load(ownerScope)

            assertEquals(DesktopLocalState.empty(ownerScope), firstLoad.state)
            assertTrue(firstLoad.recoveryCopyCreated)
            assertFalse(Files.exists(stateFile))
            val firstRecoveryFiles = recoveryFiles(dataDirectory, stateFile)
            assertEquals(1, firstRecoveryFiles.size)
            val recoveryFile = firstRecoveryFiles.single()
            assertContentEquals(corruptBytes, Files.readAllBytes(recoveryFile))

            val secondLoad = store.load(ownerScope)

            assertEquals(DesktopLocalState.empty(ownerScope), secondLoad.state)
            assertFalse(secondLoad.recoveryCopyCreated)
            assertEquals(firstRecoveryFiles, recoveryFiles(dataDirectory, stateFile))
            assertContentEquals(corruptBytes, Files.readAllBytes(recoveryFile))

            val validState = DesktopLocalState.empty(ownerScope).copy(
                workspace = DesktopLocalWorkspace("Neuer gültiger Zustand", 5_000L)
            )
            store.save(ownerScope, validState)

            val validLoad = store.load(ownerScope)

            assertEquals(validState, validLoad.state)
            assertFalse(validLoad.recoveryCopyCreated)
            assertTrue(Files.isRegularFile(stateFile))
            assertEquals(firstRecoveryFiles, recoveryFiles(dataDirectory, stateFile))
            assertContentEquals(corruptBytes, Files.readAllBytes(recoveryFile))
        }

    @Test
    fun corruptFileRecoveryUsesDeterministicSuffixWithoutOverwritingCollision() =
        withTemporaryDataDirectory { dataDirectory ->
            val store = DesktopScopedStateStore(
                dataDirectory = dataDirectory,
                clock = { 6_000L },
                recoveryId = { "collision/id" }
            )
            val ownerScope = DesktopOwnerScope.guest()
            val stateFile = store.stateFile(ownerScope)
            val corruptBytes = "{still-not-json".toByteArray(Charsets.UTF_8)
            val existingRecovery = dataDirectory.resolve(
                "${stateFile.fileName}.recovery-6000-collision-id.json"
            )
            val existingBytes = "existing-recovery".toByteArray(Charsets.UTF_8)
            Files.createDirectories(dataDirectory)
            Files.write(existingRecovery, existingBytes)
            Files.write(stateFile, corruptBytes)

            val result = store.load(ownerScope)

            val suffixedRecovery = dataDirectory.resolve(
                "${stateFile.fileName}.recovery-6000-collision-id-1.json"
            )
            assertEquals(DesktopLocalState.empty(ownerScope), result.state)
            assertTrue(result.recoveryCopyCreated)
            assertFalse(Files.exists(stateFile))
            assertContentEquals(existingBytes, Files.readAllBytes(existingRecovery))
            assertContentEquals(corruptBytes, Files.readAllBytes(suffixedRecovery))
            assertEquals(2, recoveryFiles(dataDirectory, stateFile).size)
        }

    @Test
    fun failedAtomicReplacePreservesLastValidState() = withTemporaryDataDirectory { dataDirectory ->
        val ownerScope = DesktopOwnerScope.guest()
        val workingStore = DesktopScopedStateStore(dataDirectory = dataDirectory)
        val validState = DesktopLocalState.empty(ownerScope).copy(
            workspace = DesktopLocalWorkspace("Letzter gültiger Stand", 1_000L)
        )
        workingStore.save(ownerScope, validState)
        val failingStore = DesktopScopedStateStore(
            dataDirectory = dataDirectory,
            atomicWriter = DesktopAtomicStateWriter { _, _ ->
                throw IOException("Simulierter Fehler vor Replace")
            }
        )
        val replacement = validState.copy(
            workspace = DesktopLocalWorkspace("Darf nicht übernehmen", 2_000L)
        )

        assertFailsWith<IOException> {
            failingStore.save(ownerScope, replacement)
        }

        assertEquals(validState, workingStore.load(ownerScope).state)
        val temporaryFiles = Files.list(dataDirectory).use { paths ->
            paths.filter { it.fileName.toString().endsWith(".tmp") }.count()
        }
        assertEquals(0L, temporaryFiles)
    }

    @Test
    fun stateFileContainsNoSettingsSecretsOrRawAccountIdentity() = withTemporaryDataDirectory { dataDirectory ->
        val settings = DesktopUserSettings(
            openRouterApiKey = "test-api-key-never-persist",
            firebaseApiKey = "test-firebase-key-never-persist",
            firebaseProjectId = "test-project-id-never-persist",
            googleOAuthClientId = "test-client-id-never-persist",
            googleOAuthClientSecret = "test-client-secret-never-persist",
            authEmail = "owner@example.invalid",
            authUid = "raw-firebase-uid-never-persist",
            authIdToken = "test-id-token-never-persist",
            authRefreshToken = "test-refresh-token-never-persist"
        )
        val store = DesktopScopedStateStore(dataDirectory = dataDirectory)
        val session = DesktopScopedStateSession(
            store = store,
            initialAuthenticatedUid = settings.authUid,
            clock = { 1_000L },
            newId = sequence("message", "conversation")
        )
        session.appendMessage(DesktopLocalMessageRole.USER, "Unkritischer Chattext")
        session.updateWorkspaceNotes("Unkritische Workspace-Notiz")
        val ownerScope = requireNotNull(
            DesktopOwnerScope.accountFromAuthenticatedUid(settings.authUid)
        )
        val storedText = Files.readString(store.stateFile(ownerScope), Charsets.UTF_8)
        val storedFileName = store.stateFile(ownerScope).fileName.toString()

        listOf(
            settings.openRouterApiKey,
            settings.firebaseApiKey,
            settings.firebaseProjectId,
            settings.googleOAuthClientId,
            settings.googleOAuthClientSecret,
            settings.authEmail,
            settings.authUid,
            settings.authIdToken,
            settings.authRefreshToken
        ).forEach { forbiddenValue ->
            assertFalse(storedText.contains(forbiddenValue))
            assertFalse(storedFileName.contains(forbiddenValue))
        }
        assertFalse(storedText.contains("authUid"))
        assertFalse(storedText.contains("authEmail"))
        assertTrue(storedText.contains("account-"))
    }

    private fun <T> withTemporaryDataDirectory(block: (Path) -> T): T {
        val dataDirectory = Files.createTempDirectory("bamachat-desktop-state-test-")
        return try {
            block(dataDirectory)
        } finally {
            dataDirectory.toFile().deleteRecursively()
        }
    }

    private fun recoveryFiles(dataDirectory: Path, stateFile: Path): List<Path> {
        return Files.list(dataDirectory).use { paths ->
            paths.filter { path ->
                path.fileName.toString().startsWith("${stateFile.fileName}.recovery-")
            }.sorted().toList()
        }
    }

    private fun <T> sequence(vararg values: T): () -> T {
        val iterator = values.iterator()
        return { iterator.next() }
    }
}
