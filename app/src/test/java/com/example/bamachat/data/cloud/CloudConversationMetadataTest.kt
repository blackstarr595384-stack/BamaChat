package com.example.bamachat.data.cloud

import com.example.bamachat.data.local.ChatMessageEntity
import com.example.bamachat.data.local.ChatOwnerScope
import com.example.bamachat.data.local.ConversationEntity
import com.example.bamachat.data.model.ConversationPersonaMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudConversationMetadataTest {
    @Test
    fun cloudUploadRequiresMatchingAccountScope() {
        val account = conversation(personaName = "Account", ownerScope = ChatOwnerScope.account("uid-a"))
        val guest = conversation(personaName = "Gast", ownerScope = ChatOwnerScope.guest("guest-a"))
        val legacy = conversation(personaName = "Legacy", ownerScope = ChatOwnerScope.LEGACY_UNCLASSIFIED)
        val accountMessage = message(ChatOwnerScope.account("uid-a"))
        val guestMessage = message(ChatOwnerScope.guest("guest-a"))

        assertTrue(canUploadConversation("uid-a", account))
        assertFalse(canUploadConversation("uid-b", account))
        assertFalse(canUploadConversation("uid-a", guest))
        assertFalse(canUploadConversation("uid-a", legacy))
        assertTrue(canUploadMessage("uid-a", accountMessage))
        assertFalse(canUploadMessage("uid-a", guestMessage))
    }

    @Test
    fun assistantMessageRoleDoesNotBecomeConversationPersonaName() {
        val personaName = ConversationPersonaMetadata.resolve("ASSISTANT", "Entwickler")

        assertEquals("Entwickler", personaName)
    }

    @Test
    fun userMessageRoleDoesNotBecomeConversationPersonaName() {
        val personaName = ConversationPersonaMetadata.resolve("USER", "Lehrer")

        assertEquals("Lehrer", personaName)
    }

    @Test
    fun normalConversationKeepsSelectedPersonaWithoutWorkspace() {
        val cloudConversation = conversation(personaName = "Entwickler")
            .toCloudConversation(workspaceName = null)

        assertEquals("Entwickler", cloudConversation.personaName)
        assertNull(cloudConversation.workspaceName)
    }

    @Test
    fun workspaceConversationKeepsPersonaAndWorkspaceSeparate() {
        val cloudConversation = conversation(personaName = "Lehrer")
            .toCloudConversation(workspaceName = "Produktplanung")

        assertEquals("Lehrer", cloudConversation.personaName)
        assertEquals("Produktplanung", cloudConversation.workspaceName)
    }

    @Test
    fun metadataUpdatePreservesStoredDisplayPersona() {
        val personaName = ConversationPersonaMetadata.resolve("Reflexions-Begleiter", "Entwickler")

        assertEquals("Reflexions-Begleiter", personaName)
    }

    @Test
    fun blankPersonaUsesExistingApplicationDefaultLabel() {
        val personaName = ConversationPersonaMetadata.resolve("", " ")

        assertEquals(ConversationPersonaMetadata.DEFAULT_PERSONA_DISPLAY_NAME, personaName)
        assertEquals("Assistent", personaName)
    }

    @Test
    fun cloudConversationReusesExistingConversationId() {
        val cloudConversation = conversation(id = "existing-conversation-id", personaName = "Koch")
            .toCloudConversation()

        assertEquals("existing-conversation-id", cloudConversation.id)
    }

    @Test
    fun assistantMessageRoleRemainsAssistant() {
        val cloudMessage = ChatMessageEntity(
            id = "message-id",
            conversationId = "conversation-id",
            text = "Antwort",
            isUser = false,
            timestamp = 2L,
            ownerScope = "account:test"
        ).toCloudMessage()

        assertEquals("ASSISTANT", cloudMessage.role)
    }

    private fun conversation(
        id: String = "conversation-id",
        personaName: String,
        ownerScope: String = "account:test"
    ) = ConversationEntity(
        id = id,
        title = "Titel",
        createdAt = 1L,
        updatedAt = 2L,
        personaName = personaName,
        ownerScope = ownerScope
    )

    private fun message(ownerScope: String) = ChatMessageEntity(
        id = "message-id",
        conversationId = "conversation-id",
        text = "Text",
        isUser = true,
        timestamp = 1L,
        ownerScope = ownerScope
    )
}
