package com.example.bamachat.ui.screen

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.example.bamachat.data.provider.ProviderAuthenticationType
import com.example.bamachat.data.provider.ProviderCapabilities
import com.example.bamachat.data.provider.ProviderConnectionType
import com.example.bamachat.data.provider.ProviderDefinition
import com.example.bamachat.data.provider.ProviderId
import com.example.bamachat.ui.theme.BamaChatTheme
import com.example.bamachat.ui.viewmodel.ProviderListItemUi
import org.junit.Rule
import org.junit.Test

class ProviderManagementScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun builtInCardHasNoDeleteOrDuplicateAction() {
        setCard(definition(ProviderId(ProviderId.OPENROUTER), builtIn = true))

        composeRule.onNodeWithTag("provider_card_0").assertExists()
        composeRule.onNodeWithTag("provider_delete").assertDoesNotExist()
        composeRule.onNodeWithTag("provider_duplicate").assertDoesNotExist()
    }

    @Test
    fun customCardOffersDeleteAndDuplicateWithoutShowingSecret() {
        val provider = definition(ProviderId.newCustom(), builtIn = false).copy(hasSecret = true)
        composeRule.setContent {
            BamaChatTheme(darkTheme = true, dynamicColor = false) {
                ProviderCard(
                    item = ProviderListItemUi(provider, 2),
                    index = 0,
                    onOpen = {},
                    onEnabledChange = {},
                    onDuplicate = {},
                    onDelete = {}
                )
            }
        }

        composeRule.onNodeWithTag("provider_delete").assertExists()
        composeRule.onNodeWithTag("provider_duplicate").assertExists()
    }

    private fun setCard(provider: ProviderDefinition) {
        composeRule.setContent {
            BamaChatTheme(darkTheme = true, dynamicColor = false) {
                ProviderCard(ProviderListItemUi(provider, 1), 0, {}, {})
            }
        }
    }

    private fun definition(id: ProviderId, builtIn: Boolean) = ProviderDefinition.create(
        id = id,
        displayName = if (builtIn) "OpenRouter" else "Sehr langer eigener Anbietername für responsive Darstellung",
        connectionType = ProviderConnectionType.OPENAI_COMPATIBLE,
        baseUrl = "https://provider.example/v1/",
        authenticationType = ProviderAuthenticationType.BEARER,
        capabilities = ProviderCapabilities(true, false, false, false),
        builtIn = builtIn,
        hasSecret = false
    )
}
