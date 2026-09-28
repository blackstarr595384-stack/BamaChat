package com.example.bamachat

import android.content.Context
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppScreenshotCaptureTest {

    companion object {
        private const val TIMEOUT = 25_000L
        private const val SCREENSHOT_DEVICE_DIR = "/sdcard/Download/bamachat-screenshots"
    }

    private lateinit var device: UiDevice

    private val composeRule = createEmptyComposeRule()

    @get:Rule
    val testRules: RuleChain = RuleChain.outerRule(composeRule).around(object : TestWatcher() {
        override fun failed(failure: Throwable, description: Description) {
            captureFailureDiagnostics(failure)
        }
    })

    @Before
    fun setup() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val context = ApplicationProvider.getApplicationContext<Context>()
        ensureFirebaseInitialized(context)
        device.executeShellCommand("rm -rf $SCREENSHOT_DEVICE_DIR")
        device.executeShellCommand("mkdir -p $SCREENSHOT_DEVICE_DIR")
        val directoryOutput = device.executeShellCommand("ls -d $SCREENSHOT_DEVICE_DIR")
        assertTrue(
            "Screenshot-Verzeichnis konnte nicht vorbereitet werden: $directoryOutput",
            directoryOutput.lineSequence().any { it.trim() == SCREENSHOT_DEVICE_DIR }
        )
        verifyHierarchyWriter()
    }

    private fun ensureFirebaseInitialized(context: Context) {
        val defaultAppExists = FirebaseApp.getApps(context)
            .any { it.name == FirebaseApp.DEFAULT_APP_NAME }

        if (!defaultAppExists) {
            val options = FirebaseOptions.Builder()
                .setApplicationId("1:000000000000:android:0000000000000000")
                .setProjectId("bamachat-screenshot-test")
                .setApiKey("bamachat-screenshot-test-api-key")
                .build()

            checkNotNull(FirebaseApp.initializeApp(context, options)) {
                "Firebase-Testinitialisierung fehlgeschlagen"
            }
        }
    }

    @Test
    fun captureCoreScreens() {
        launchApp()
        completeInitialFlow()

        val hubTab = waitForExactDescription(TIMEOUT, "Hub")
        assertNotNull("Hub-Navigation nicht gefunden", hubTab)
        assertTrue("Hub-Navigation ist deaktiviert", hubTab?.isEnabled == true)
        hubTab?.click()
        composeRule.waitForIdle()

        assertNotNull("BamaHub nicht erkannt", waitForExactText(TIMEOUT, "BamaHub"))
        assertNotNull(
            "Home-Hub-Beschreibung nicht erkannt",
            waitForExactText(TIMEOUT, "Wähle einen Bereich, um loszulegen")
        )
        assertTextAbsent("AI Workspace OS")
        settle()
        captureScreenshot("01_home_hub")

        composeRule.onAllNodesWithTag("bottom_nav_chat", useUnmergedTree = true)
            .assertCountEquals(1)
        val chatTab = composeRule.onNodeWithTag("bottom_nav_chat", useUnmergedTree = true)
        chatTab.assertIsDisplayed().assertHasClickAction().assertIsEnabled().performClick()
        chatTab.assertIsSelected()
        composeRule.onNodeWithTag("chat_screen", useUnmergedTree = true)
            .assertExists().assertIsDisplayed()

        assertNotNull(
            "Leerer Chat nicht erkannt",
            waitForExactText(TIMEOUT, "Worüber möchtest du sprechen?")
        )
        assertNotNull("Hub-Navigation im Chat nicht gefunden", waitForExactDescription(TIMEOUT, "Hub"))
        assertStartFlowTextsAbsent()
        settle()
        captureScreenshot("02_chat")
        assertRequiredScreenshotsDiffer()
    }

    private fun completeInitialFlow() {
        val startState = waitForStartState(TIMEOUT)
        assertNotNull("Kein bekannter Startzustand erkannt", startState)
        recordOnboardingDiagnostics("initial")

        if (startState == StartState.ONBOARDING) {
            val skipButton = waitForExactText(TIMEOUT, "Überspringen")
            assertNotNull("Onboarding-Button Überspringen nicht gefunden", skipButton)
            assertTrue("Onboarding-Button Überspringen ist deaktiviert", skipButton?.isEnabled == true)
            recordOnboardingDiagnostics("before_skip", skipButton)
            skipButton?.click()
            recordOnboardingDiagnostics("after_skip_before_compose_idle")
            composeRule.waitForIdle()
            recordOnboardingDiagnostics("after_skip_after_compose_idle")
            val onboardingGone = device.wait(Until.gone(By.text("AI Workspace OS")), TIMEOUT)
            recordOnboardingDiagnostics("after_onboarding_wait")
            assertTrue(
                "Onboarding wurde nicht verlassen",
                onboardingGone
            )
            assertNotNull(
                "Rechtsbildschirm nach Onboarding nicht erkannt",
                waitForExactText(TIMEOUT, "Recht & Datenschutz")
            )
        }

        if (device.hasObject(By.text("Recht & Datenschutz"))) {
            acceptLegalDisclaimer()
        }

        val guestButton = waitForExactText(TIMEOUT, "Als Gast starten")
        assertNotNull("Button Als Gast starten nicht gefunden", guestButton)
        assertTrue("Button Als Gast starten ist deaktiviert", guestButton?.isEnabled == true)
        guestButton?.click()
        composeRule.waitForIdle()

        assertNotNull(
            "Leerer Chat nach Gaststart nicht erkannt",
            waitForExactText(TIMEOUT, "Worüber möchtest du sprechen?")
        )
        assertNotNull("Hub-Navigation nach Gaststart nicht gefunden", waitForExactDescription(TIMEOUT, "Hub"))
        assertStartFlowTextsAbsent()
    }

    private fun acceptLegalDisclaimer() {
        val checkboxes = waitForLegalCheckboxes(TIMEOUT)
        assertTrue("Weniger als zwei Checkboxen gefunden", checkboxes.size >= 2)

        repeat(2) {
            val unchecked = waitForLegalCheckboxes(TIMEOUT)
                .take(2)
                .firstOrNull { !it.isChecked }
            unchecked?.click()
            composeRule.waitForIdle()
            device.waitForIdle()
        }

        assertTrue("Rechtliche Checkboxen wurden nicht aktiviert", waitForLegalCheckboxesChecked(TIMEOUT))
        val acceptButton = waitForExactText(TIMEOUT, "Akzeptieren & Fortfahren")
        assertNotNull("Button Akzeptieren & Fortfahren nicht gefunden", acceptButton)
        assertTrue("Button Akzeptieren & Fortfahren ist deaktiviert", acceptButton?.isEnabled == true)
        acceptButton?.click()
        composeRule.waitForIdle()
        assertNotNull(
            "Welcome-Screen nach Rechtsbestätigung nicht erkannt",
            waitForExactText(TIMEOUT, "Als Gast starten")
        )
    }

    private fun launchApp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val launchIntent = TestAppIdentity.mainActivityIntent()
        device.pressHome()
        SystemClock.sleep(400)
        context.startActivity(launchIntent)
        assertTrue("App-Start nicht erkannt", waitForAppVisible(TIMEOUT))
    }

    private fun captureScreenshot(fileName: String) {
        assertTrue(
            "Ungültiger Screenshot-Dateiname: $fileName",
            fileName.matches(Regex("^[a-z0-9_]+$"))
        )
        val outputPath = "$SCREENSHOT_DEVICE_DIR/$fileName.png"
        device.executeShellCommand("screencap -p $outputPath")
        val sizeOutput = device.executeShellCommand("stat -c %s $outputPath")
        val screenshotSize = sizeOutput.lineSequence()
            .map { it.trim() }
            .firstOrNull { line -> line.isNotEmpty() && line.all { character -> character.isDigit() } }
            ?.toLongOrNull()
        assertTrue(
            "Screenshot fehlgeschlagen: $outputPath; stat=$sizeOutput",
            screenshotSize != null && screenshotSize > 0L
        )
    }

    private fun verifyHierarchyWriter() {
        val probePath = "$SCREENSHOT_DEVICE_DIR/writer_probe.txt"
        val probe = "BamaFlow-Diagnose: äöü ✓\n".toByteArray(Charsets.UTF_8)
        try {
            writeShellFile(probePath, probe)
            check(readShellFile(probePath).contentEquals(probe)) {
                "Diagnose-Writer hat den UTF-8-Roundtrip nicht erhalten"
            }
        } finally {
            device.executeShellCommand("rm -f $probePath")
        }
    }

    private fun writeShellFile(outputPath: String, content: ByteArray) {
        val descriptors = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommandRw("tee $outputPath")
        check(descriptors.size == 2) { "Unerwartete Descriptor-Anzahl für Diagnose-Writer" }
        val echoed = ParcelFileDescriptor.AutoCloseInputStream(descriptors[0]).use { stdout ->
            ParcelFileDescriptor.AutoCloseOutputStream(descriptors[1]).use { stdin ->
                stdin.write(content)
                stdin.flush()
            }
            stdout.readBytes()
        }
        check(echoed.contentEquals(content)) { "Diagnose-Writer hat unvollständige Daten bestätigt" }

        val sizeOutput = device.executeShellCommand("stat -c %s $outputPath")
        check(sizeOutput.trim().toLongOrNull() == content.size.toLong()) {
            "Diagnose-Datei wurde nicht vollständig gespeichert"
        }
    }

    private fun readShellFile(inputPath: String): ByteArray {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("cat $inputPath")
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
    }

    private fun recordOnboardingDiagnostics(stage: String, skipNode: UiObject2? = null) {
        runCatching {
            val prefs = InstrumentationRegistry.getInstrumentation().targetContext
                .getSharedPreferences("settings", Context.MODE_PRIVATE)
            Log.i(
                "BamaScreenshotDiag",
                "stage=$stage elapsed=${SystemClock.uptimeMillis()} " +
                    "clock=${composeRule.mainClock.currentTime} autoAdvance=${composeRule.mainClock.autoAdvance} " +
                    "onboardingCompleted=${prefs.getBoolean("onboarding_completed", false)} " +
                    "onboardingVisible=${device.hasObject(By.text("AI Workspace OS"))} " +
                    "legalVisible=${device.hasObject(By.text("Recht & Datenschutz"))} " +
                    "welcomeVisible=${device.hasObject(By.text("Als Gast starten"))}"
            )
            if (skipNode != null) {
                Log.i("BamaScreenshotDiag", "stage=$stage skipMatches=${device.findObjects(By.text("Überspringen")).size}")
                var node: UiObject2? = skipNode
                repeat(3) { depth ->
                    node?.let { current ->
                        Log.i(
                            "BamaScreenshotDiag",
                            "stage=$stage depth=$depth class=${current.className} " +
                                "bounds=${current.visibleBounds} center=${current.visibleCenter} " +
                                "enabled=${current.isEnabled} clickable=${current.isClickable} " +
                                "matchesSkip=${current.text == "Überspringen"}"
                        )
                        node = current.parent
                    }
                }
            }
        }.onFailure { diagnosticFailure ->
            Log.i("BamaScreenshotDiag", "stage=$stage exceptionClass=${diagnosticFailure.javaClass.name}")
        }
    }

    private fun captureFailureDiagnostics(failure: Throwable) {
        if (!::device.isInitialized) return

        recordOnboardingDiagnostics("failure_before_semantics")
        runCatching {
            captureScreenshot("failure")
        }.onFailure { diagnosticFailure ->
            failure.addSuppressed(IllegalStateException(
                "stage=failure_screenshot exceptionClass=${diagnosticFailure.javaClass.name}"
            ))
        }
        var diagnosticStage = "semantics_fetch"
        runCatching {
            val roots = composeRule.onAllNodes(isRoot(), useUnmergedTree = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
            diagnosticStage = "semantics_serialize"
            val hierarchy = buildString {
                appendLine("Unmerged Compose hierarchy; text and content descriptions omitted")
                appendLine("rootCount=${roots.size}")
                roots.forEach { node -> appendDiagnosticNode(node, 0) }
            }
            val outputPath = "$SCREENSHOT_DEVICE_DIR/failure_semantics.txt"
            diagnosticStage = "semantics_write"
            writeShellFile(outputPath, hierarchy.toByteArray(Charsets.UTF_8))
        }.onFailure { diagnosticFailure ->
            val summary = "stage=$diagnosticStage exceptionClass=${diagnosticFailure.javaClass.name}"
            Log.i("BamaScreenshotDiag", summary)
            failure.addSuppressed(IllegalStateException(summary))
        }
        recordOnboardingDiagnostics("failure_after_semantics")
    }

    private fun StringBuilder.appendDiagnosticNode(node: SemanticsNode, depth: Int) {
        val tag = node.config.getOrNull(SemanticsProperties.TestTag)
            ?.takeIf { it in setOf("bottom_nav_chat", "bottom_nav_hub", "chat_screen") }
            ?: "other"
        append("  ".repeat(depth))
        appendLine(
            "node=${node.id} tag=$tag bounds=${node.boundsInRoot} " +
                "selected=${node.config.getOrNull(SemanticsProperties.Selected)} " +
                "enabled=${!node.config.contains(SemanticsProperties.Disabled)} " +
                "hasClick=${node.config.contains(SemanticsActions.OnClick)}"
        )
        node.children.forEach { child -> appendDiagnosticNode(child, depth + 1) }
    }

    private fun assertRequiredScreenshotsDiffer() {
        val homePath = "$SCREENSHOT_DEVICE_DIR/01_home_hub.png"
        val chatPath = "$SCREENSHOT_DEVICE_DIR/02_chat.png"
        val hashOutput = device.executeShellCommand("sha256sum $homePath $chatPath")
        val hashes = hashOutput.lineSequence()
            .map { it.trim().substringBefore(' ') }
            .filter { hash -> hash.length == 64 && hash.all { character -> character.isHexDigit() } }
            .map { it.lowercase() }
            .toList()

        assertTrue(
            "Pflicht-Screenshot-Hashes konnten nicht eindeutig gelesen werden: $hashOutput",
            hashes.size == 2
        )
        assertTrue(
            "Pflicht-Screenshots sind identisch: home=${hashes.getOrNull(0)}, " +
                "chat=${hashes.getOrNull(1)}; sha256sum=$hashOutput",
            hashes.size == 2 && hashes[0] != hashes[1]
        )
    }

    private fun Char.isHexDigit(): Boolean =
        this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    private fun settle() {
        device.waitForIdle()
        SystemClock.sleep(700)
    }

    private fun waitForStartState(timeoutMs: Long): StartState? {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            if (device.hasObject(By.text("AI Workspace OS")) || device.hasObject(By.text("Überspringen"))) {
                return StartState.ONBOARDING
            }
            if (device.hasObject(By.text("Recht & Datenschutz"))) return StartState.LEGAL
            if (device.hasObject(By.text("Als Gast starten"))) return StartState.WELCOME
            device.waitForIdle()
            SystemClock.sleep(250)
        }
        return null
    }

    private fun waitForExactText(timeoutMs: Long, text: String): UiObject2? =
        device.wait(Until.findObject(By.text(text)), timeoutMs)

    private fun waitForExactDescription(timeoutMs: Long, description: String): UiObject2? =
        device.wait(Until.findObject(By.desc(description)), timeoutMs)

    private fun waitForLegalCheckboxes(timeoutMs: Long): List<UiObject2> {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            val classCheckboxes = device.findObjects(By.clazz("android.widget.CheckBox"))
            if (classCheckboxes.size >= 2) return classCheckboxes

            // Some Compose/UI-Automator combinations export checkbox semantics only as checkable nodes.
            val semanticCheckboxes = device.findObjects(By.checkable(true))
            if (semanticCheckboxes.size >= 2) return semanticCheckboxes

            device.waitForIdle()
            SystemClock.sleep(250)
        }
        return emptyList()
    }

    private fun waitForLegalCheckboxesChecked(timeoutMs: Long): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            val checkboxes = waitForLegalCheckboxes(1_000L).take(2)
            if (checkboxes.size == 2 && checkboxes.all { it.isChecked }) return true
            device.waitForIdle()
            SystemClock.sleep(250)
        }
        return false
    }

    private fun assertStartFlowTextsAbsent() {
        assertTextAbsent("AI Workspace OS")
        assertTextAbsent("Recht & Datenschutz")
        assertTextAbsent("Als Gast starten")
    }

    private fun assertTextAbsent(text: String) {
        assertTrue("Unerwarteter Starttext sichtbar: $text", !device.hasObject(By.text(text)))
    }

    private fun waitForAppVisible(timeoutMs: Long): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            allowRuntimeDialogsIfPresent()
            val byPkg = device.hasObject(By.pkg(TestAppIdentity.APPLICATION_ID))
            val byTitle = device.hasObject(By.textContains("BamaFlow")) ||
                device.hasObject(By.textContains("BamaHub")) ||
                device.hasObject(By.textContains("Willkommen"))
            if (byPkg || byTitle) return true
            device.waitForIdle()
            SystemClock.sleep(300)
        }
        return false
    }

    private fun allowRuntimeDialogsIfPresent() {
        listOf(
            "Zulassen",
            "Erlauben",
            "Nur während der Nutzung der App",
            "Beim Verwenden der App",
            "Allow",
            "While using the app",
            "Nur dieses Mal",
            "Only this time"
        ).forEach { label ->
            device.findObject(By.textContains(label))?.click()
        }
    }

    private enum class StartState {
        ONBOARDING,
        LEGAL,
        WELCOME
    }
}
