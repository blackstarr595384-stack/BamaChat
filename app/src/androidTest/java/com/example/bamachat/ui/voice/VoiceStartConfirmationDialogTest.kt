package com.example.bamachat.ui.voice

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bamachat.ui.component.voice.VoiceStartConfirmationDialog
import com.example.bamachat.ui.component.voice.VoiceRuntimeDisclosure
import com.example.bamachat.ui.theme.BamaChatTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class VoiceStartConfirmationDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun debugDialogIsExplicitAndStartsOnlyAfterConfirmation() {
        val startCount = AtomicInteger(0)
        val dismissCount = AtomicInteger(0)
        setDialog(
            presentation = VoiceRuntimePresentation.resolve(),
            onConfirm = { startCount.incrementAndGet() },
            onDismiss = { dismissCount.incrementAndGet() }
        )

        composeRule.onNodeWithText("Debug-Simulation starten?").assertIsDisplayed()
        composeRule.onNodeWithText("keine echte OpenAI-Verbindung", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("kein echtes Mikrofon", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Direct Live verwendet", substring = true).assertDoesNotExist()
        assertEquals(0, startCount.get())
        assertEquals(0, dismissCount.get())

        composeRule.onNodeWithTag("voice_start_confirm").performClick()

        assertEquals(1, startCount.get())
        assertEquals(0, dismissCount.get())
    }

    @Test
    fun directLiveDialogUsesShortConsentAndCancelDoesNotStart() {
        val startCount = AtomicInteger(0)
        val dismissCount = AtomicInteger(0)
        setDialog(
            presentation = DirectLiveRuntimePresentation.model,
            onConfirm = { startCount.incrementAndGet() },
            onDismiss = { dismissCount.incrementAndGet() }
        )

        composeRule.onNodeWithText("Direct Live starten?").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Direct Live verwendet dein Mikrofon und überträgt Sprache an OpenAI."
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Besonders schnelle Unterhaltung").assertIsDisplayed()
        composeRule.onNodeWithText("Cloud-Audio und mögliche Zusatzkosten").assertIsDisplayed()
        composeRule.onNodeWithText("WebRTC", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Credential", substring = true).assertDoesNotExist()
        assertEquals(0, startCount.get())

        composeRule.onNodeWithTag("voice_start_cancel").performClick()

        assertEquals(0, startCount.get())
        assertEquals(1, dismissCount.get())
    }

    @Test
    fun normalDebugVoiceDisclosureShowsSimulationBoundaries() {
        composeRule.setContent {
            BamaChatTheme(darkTheme = true, dynamicColor = false) {
                VoiceRuntimeDisclosure(
                    VoiceRuntimePresentation.resolve()
                )
            }
        }

        composeRule.onNodeWithTag("voice_runtime_badge").assertIsDisplayed()
        composeRule.onNodeWithText("Debug-Simulation").assertIsDisplayed()
        composeRule.onNodeWithText("Keine echte OpenAI-Verbindung").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Das Mikrofon wird in dieser Simulation nicht verwendet."
        ).assertIsDisplayed()
    }

    private fun setDialog(
        presentation: VoiceRuntimePresentationModel,
        onConfirm: () -> Unit,
        onDismiss: () -> Unit
    ) {
        composeRule.setContent {
            BamaChatTheme(darkTheme = true, dynamicColor = false) {
                VoiceStartConfirmationDialog(
                    presentation = presentation,
                    onConfirm = onConfirm,
                    onDismiss = onDismiss
                )
            }
        }
        composeRule.waitForIdle()
    }
}
