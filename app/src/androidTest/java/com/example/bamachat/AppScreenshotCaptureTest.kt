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
        private const val TIMEOUT = 25_000L
    }

    private lateinit var device: UiDevice
    private lateinit var screenshotDir: File

    @Before
    fun setup() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val context = ApplicationProvider.getApplicationContext<Context>()
        screenshotDir = File(context.getExternalFilesDir(null), "bamachat-screenshots").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    @Test
    fun captureCoreScreens() {
        launchApp()

        val guestButton = waitForAnyText(4_000L, "Als Gast starten", "Als Gast fortfahren", "Zum BamaHub")
        guestButton?.click()

        assertTrue("App-Start nicht erkannt", waitForAppVisible(TIMEOUT))
        settle()
        captureScreenshot("01_home_hub")

        clickBottomTab("Chat", "chat")
        assertNotNull("Chat-Screen nicht erkannt", device.wait(Until.findObject(By.textContains("BamaChat")), TIMEOUT))
        settle()
        captureScreenshot("02_chat")

        if (clickBottomTabOptional("Profil", "profile")) {
            assertNotNull("Profil-Screen nicht erkannt", device.wait(Until.findObject(By.textContains("Profil")), TIMEOUT))
            settle()
            captureScreenshot("03_profile")
        }

        if (clickBottomTabOptional("Einstell", "settings", "Einstellungen")) {
            assertNotNull(
                "Settings-Screen nicht erkannt",
                device.wait(Until.findObject(By.textContains("Einstellungen")), TIMEOUT)
            )
            settle()
            captureScreenshot("04_settings")
        }
    }

    private fun clickBottomTab(vararg texts: String) {
        val tab = waitForAnyText(TIMEOUT, *texts) ?: waitForAnyDescContains(TIMEOUT, *texts)
        assertNotNull("Bottom-Tab nicht gefunden: ${texts.joinToString()}", tab)
        tab?.click()
    }

    private fun clickBottomTabOptional(vararg texts: String): Boolean {
        val tab = waitForAnyText(4_000L, *texts) ?: waitForAnyDescContains(4_000L, *texts)
        tab?.click()
        return tab != null
    }

    private fun launchApp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val launchIntent = context.packageManager.getLaunchIntentForPackage(PACKAGE_NAME)
            ?: throw IllegalStateException("Launch Intent für $PACKAGE_NAME nicht gefunden")
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        device.pressHome()
        SystemClock.sleep(400)
        context.startActivity(launchIntent)
        waitForAppVisible(TIMEOUT)
    }

    private fun captureScreenshot(fileName: String) {
        val output = File(screenshotDir, "$fileName.png")
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

    private fun waitForAnyDescContains(timeoutMs: Long, vararg values: String): UiObject2? {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            values.forEach { value ->
                device.findObject(By.descContains(value))?.let { return it }
            }
            device.waitForIdle()
            SystemClock.sleep(250)
        }
        return null
    }

    private fun waitForAppVisible(timeoutMs: Long): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            allowRuntimeDialogsIfPresent()
            val byPkg = device.hasObject(By.pkg(PACKAGE_NAME))
            val byTitle = device.hasObject(By.textContains("BamaChat")) ||
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
}
