package com.example.bamachat

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AppScreenshotCaptureTest {

    companion object {
        private const val PACKAGE_NAME = "com.example.bamachat"
        private const val TIMEOUT = 15_000L
        private const val SCREENSHOT_DIR = "/sdcard/Download/bamachat-screenshots"
    }

    private lateinit var device: UiDevice

    @Before
    fun setup() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.executeShellCommand("rm -rf $SCREENSHOT_DIR && mkdir -p $SCREENSHOT_DIR")
    }

    @Test
    fun captureCoreScreens() {
        launchApp()

        val guestButton = waitForAnyText(TIMEOUT, "Als Gast starten", "Als Gast fortfahren")
            ?: waitForAnyText(TIMEOUT, "Zum BamaHub")
        assertNotNull("Gast-/Hub-Button nicht gefunden", guestButton)
        guestButton?.click()

        val hubTitle = device.wait(Until.findObject(By.textContains("BamaChat")), TIMEOUT)
        assertNotNull("Home Hub nicht erkannt", hubTitle)
        settle()
        captureScreenshot("01_home_hub")

        clickBottomTab("Chat", "chat")
        assertNotNull("Chat-Screen nicht erkannt", device.wait(Until.findObject(By.textContains("BamaChat")), TIMEOUT))
        settle()
        captureScreenshot("02_chat")

        clickBottomTab("Profil", "profile")
        assertNotNull("Profil-Screen nicht erkannt", device.wait(Until.findObject(By.textContains("Profil")), TIMEOUT))
        settle()
        captureScreenshot("03_profile")

        clickBottomTab("Einstell", "settings")
        assertNotNull(
            "Settings-Screen nicht erkannt",
            device.wait(Until.findObject(By.textContains("Einstellungen")), TIMEOUT)
        )
        settle()
        captureScreenshot("04_settings")
    }

    private fun clickBottomTab(vararg texts: String) {
        val tab = waitForAnyText(TIMEOUT, *texts)
        assertNotNull("Bottom-Tab nicht gefunden: ${texts.joinToString()}", tab)
        tab?.click()
    }

    private fun launchApp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val launchIntent = context.packageManager.getLaunchIntentForPackage(PACKAGE_NAME)
            ?: throw IllegalStateException("Launch Intent für $PACKAGE_NAME nicht gefunden")
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(launchIntent)
        device.wait(Until.hasObject(By.pkg(PACKAGE_NAME).depth(0)), TIMEOUT)
    }

    private fun captureScreenshot(fileName: String) {
        val output = File("$SCREENSHOT_DIR/$fileName.png")
        val success = device.takeScreenshot(output)
        assertTrue("Screenshot fehlgeschlagen: ${output.absolutePath}", success)
    }

    private fun settle() {
        device.waitForIdle()
        SystemClock.sleep(700)
    }

    private fun waitForAnyText(timeoutMs: Long, vararg values: String): UiObject2? {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            values.forEach { value ->
                device.findObject(By.text(value))?.let { return it }
                device.findObject(By.textContains(value))?.let { return it }
            }
            device.waitForIdle()
            SystemClock.sleep(250)
        }
        return null
    }
}

