package com.example.bamachat.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bamachat.ui.settings.VoiceModeFamily
import com.example.bamachat.ui.theme.BamaChatTheme
import com.example.bamachat.voice.RealtimeTurnTaking
import com.example.bamachat.voice.RealtimeVoice
import com.example.bamachat.voice.VoiceInputProvider
import com.example.bamachat.voice.VoiceMode
import com.example.bamachat.voice.VoiceOutputProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class SettingsUxScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun openingOverviewAndVoiceSettingsDoesNotInvokeActions() {
        val actionCount = AtomicInteger(0)
        composeRule.setContent {
            BamaChatTheme(darkTheme = true, dynamicColor = false) {
                VoiceAudioSettingsContent(
                    state = voiceState(mode = VoiceMode.AUTOMATIC),
                    callbacks = countingCallbacks(actionCount)
                )
            }
        }

        composeRule.waitForIdle()

        assertEquals(0, actionCount.get())
        composeRule.onNodeWithText("Automatische Auswahl").fetchSemanticsNode()
    }

    @Test
    fun smartVoiceShowsOnlySmartOptions() {
        setVoiceContent(VoiceMode.UNIVERSAL)

        scrollVoiceListTo("MIKROFON UND ERKENNUNG")
        scrollVoiceListTo("ANTWORTSTIMME")
        scrollVoiceListTo("ElevenLabs")
        assertTextAbsent("REALTIME-STIMME")
        assertTextAbsent("LOKALE ANTWORTSTIMME")
    }

    @Test
    fun directLiveShowsExperimentAndOnlyRealtimeOptions() {
        setVoiceContent(VoiceMode.LIVE)

        composeRule.onNodeWithText("Experimentell").fetchSemanticsNode()
        scrollVoiceListTo("REALTIME-STIMME")
        scrollVoiceListTo("GESPRÄCHSFLUSS")
        scrollVoiceListTo("Unterbrechung erlauben")
        assertTextAbsent("ElevenLabs")
        assertTextAbsent("Piper")
        assertTextAbsent("Antworten automatisch vorlesen")
    }

    @Test
    fun localModeShowsOnlyLocalAudioOptions() {
        setVoiceContent(VoiceMode.LOCAL)

        scrollVoiceListTo("Android-Spracherkennung")
        scrollVoiceListTo("LOKALE ANTWORTSTIMME")
        scrollVoiceListTo("Piper")
        assertTextAbsent("REALTIME-STIMME")
        assertTextAbsent("ElevenLabs")
    }

    @Test
    fun voicePreviewStartsOnlyAfterExplicitTap() {
        val previewCount = AtomicInteger(0)
        composeRule.setContent {
            BamaChatTheme(darkTheme = true, dynamicColor = false) {
                VoiceAudioSettingsContent(
                    state = voiceState(mode = VoiceMode.UNIVERSAL),
                    callbacks = countingCallbacks(AtomicInteger(0), previewCount)
                )
            }
        }

        composeRule.waitForIdle()
        assertEquals(0, previewCount.get())

        scrollVoiceListTo("Stimme testen")
        composeRule.onNodeWithText("Stimme testen").performClick()

        assertEquals(1, previewCount.get())
    }

    @Test
    fun overviewVoiceRowUsesSingleNavigationAction() {
        val voiceNavigationCount = AtomicInteger(0)
        val otherActionCount = AtomicInteger(0)
        composeRule.setContent {
            BamaChatTheme(darkTheme = true, dynamicColor = false) {
                SettingsOverviewContent(
                    tier = "Free",
                    provider = "OpenRouter",
                    workspace = "Standard",
                    syncStatus = "Nur lokal",
                    voiceModeSummary = "Smart Voice · Standard-Chat",
                    onBack = { otherActionCount.incrementAndGet() },
                    onOpenAccount = { otherActionCount.incrementAndGet() },
                    onOpenWorkspaces = { otherActionCount.incrementAndGet() },
                    onOpenGeneral = { otherActionCount.incrementAndGet() },
                    onOpenAiModels = { otherActionCount.incrementAndGet() },
                    onOpenVoiceAudio = { voiceNavigationCount.incrementAndGet() },
                    onOpenPrivacyData = { otherActionCount.incrementAndGet() },
                    onOpenAdvanced = { otherActionCount.incrementAndGet() }
                )
            }
        }

        composeRule.onNodeWithTag("settings_overview_list")
            .performScrollToNode(hasTestTag("settings_category_voice"))
        composeRule.onNodeWithTag("settings_category_voice").performClick()

        assertEquals(1, voiceNavigationCount.get())
        assertEquals(0, otherActionCount.get())
    }

    @Test
    fun voiceModeCardsFitAt320DpWithLargeFont() = assertVoiceCardsAtWidth(320, 2f)

    @Test
    fun voiceModeCardsFitAt360Dp() = assertVoiceCardsAtWidth(360, 1.3f)

    @Test
    fun voiceModeCardsFitAt411Dp() = assertVoiceCardsAtWidth(411, 1.5f)

    @Test
    fun voiceModeCardsFitAt600Dp() = assertVoiceCardsAtWidth(600, 1.5f)

    private fun assertVoiceCardsAtWidth(widthDp: Int, fontScale: Float) {
        composeRule.setContent {
            val currentDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(currentDensity.density, fontScale)
            ) {
                BamaChatTheme(darkTheme = true, dynamicColor = false) {
                    Box(
                        modifier = Modifier
                            .width(widthDp.dp)
                            .fillMaxHeight()
                    ) {
                        VoiceAudioSettingsContent(
                            state = voiceState(mode = VoiceMode.UNIVERSAL),
                            callbacks = countingCallbacks(AtomicInteger(0))
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithText("Smart Voice").fetchSemanticsNode()
        composeRule.onNode(
            hasContentDescription("Smart Voice", substring = true)
        ).assertHeightIsAtLeast(48.dp)
        scrollVoiceListTo("Direct Live")
        scrollVoiceListTo("Lokal")
    }

    private fun setVoiceContent(mode: VoiceMode) {
        composeRule.setContent {
            BamaChatTheme(darkTheme = true, dynamicColor = false) {
                VoiceAudioSettingsContent(
                    state = voiceState(mode = mode),
                    callbacks = countingCallbacks(AtomicInteger(0))
                )
            }
        }
    }

    private fun scrollVoiceListTo(text: String) {
        composeRule.onNodeWithTag("voice_audio_settings_list")
            .performScrollToNode(hasText(text))
        composeRule.onNodeWithText(text).fetchSemanticsNode()
    }

    private fun assertTextAbsent(text: String) {
        assertTrue(
            "Unerwarteter Text sichtbar: $text",
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()
        )
    }

    private fun voiceState(mode: VoiceMode): VoiceAudioSettingsUiState = VoiceAudioSettingsUiState(
        mode = mode,
        inputProvider = VoiceInputProvider.AUTOMATIC,
        outputProvider = VoiceOutputProvider.AUTOMATIC,
        autoPlayback = false,
        handsFree = false,
        pushToTalk = false,
        interruptionEnabled = true,
        providerFallbackEnabled = true,
        silenceTimeoutMs = 1_200L,
        speed = 1f,
        pitch = 1f,
        realtimeVoice = RealtimeVoice.MARIN,
        realtimeTurnTaking = RealtimeTurnTaking.SEMANTIC,
        realtimeAvailable = true,
        liveSessionActive = false,
        previewPlaying = false,
        piperConfigured = false
    )

    private fun countingCallbacks(
        actionCount: AtomicInteger,
        previewCount: AtomicInteger = actionCount
    ): VoiceAudioSettingsCallbacks = VoiceAudioSettingsCallbacks(
        onBack = { actionCount.incrementAndGet() },
        onSelectModeFamily = { _: VoiceModeFamily -> actionCount.incrementAndGet() },
        onSelectInputProvider = { actionCount.incrementAndGet() },
        onSelectOutputProvider = { actionCount.incrementAndGet() },
        onAutoPlaybackChange = { actionCount.incrementAndGet() },
        onHandsFreeChange = { actionCount.incrementAndGet() },
        onPushToTalkChange = { actionCount.incrementAndGet() },
        onInterruptionChange = { actionCount.incrementAndGet() },
        onProviderFallbackChange = { actionCount.incrementAndGet() },
        onSilenceTimeoutChange = { actionCount.incrementAndGet() },
        onSpeedChange = { actionCount.incrementAndGet() },
        onPitchChange = { actionCount.incrementAndGet() },
        onSelectRealtimeVoice = { actionCount.incrementAndGet() },
        onSelectRealtimeTurnTaking = { actionCount.incrementAndGet() },
        onPreviewVoice = { previewCount.incrementAndGet() },
        onStopPreview = { actionCount.incrementAndGet() },
        onOpenLegacyVoiceSettings = { actionCount.incrementAndGet() }
    )
}
