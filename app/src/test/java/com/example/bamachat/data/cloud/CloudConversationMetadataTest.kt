package com.example.bamachat.data.cloud

import com.example.bamachat.data.local.ChatMessageEntity
import com.example.bamachat.data.local.ConversationEntity
import com.example.bamachat.data.model.ConversationPersonaMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CloudConversationMetadataTest {
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
            timestamp = 2L
        ).toCloudMessage()

        assertEquals("ASSISTANT", cloudMessage.role)
    }

    private fun conversation(
        id: String = "conversation-id",
        personaName: String
    ) = ConversationEntity(
        id = id,
        title = "Titel",
        createdAt = 1L,
        updatedAt = 2L,
        personaName = personaName
    )
}
