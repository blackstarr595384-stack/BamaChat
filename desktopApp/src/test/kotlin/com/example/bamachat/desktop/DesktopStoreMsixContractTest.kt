package com.example.bamachat.desktop

import com.google.gson.JsonParser
import java.nio.file.Files
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DesktopStoreMsixContractTest {
    @Test
    fun storeContractAndManifestUseExactPartnerCenterIdentity() {
        val projectDirectory = desktopProjectDirectory()
        val contract = JsonParser.parseString(
            projectDirectory.resolve("src/main/msix/store-package.json").readText()
        ).asJsonObject

        assertEquals(IDENTITY_NAME, contract["identityName"].asString)
        assertEquals(PUBLISHER, contract["publisher"].asString)
        assertEquals(PUBLISHER_DISPLAY_NAME, contract["publisherDisplayName"].asString)
        assertEquals("9P61V47KR1Z8", contract["storeId"].asString)
        assertEquals("x64", contract["architecture"].asString)
        assertEquals("BamaChatDesktop.exe", contract["executable"].asString)

        val replacements = mapOf(
            "{{IDENTITY_NAME}}" to contract["identityName"].asString,
            "{{PUBLISHER}}" to contract["publisher"].asString,
            "{{MSIX_VERSION}}" to "1.0.1.0",
            "{{ARCHITECTURE}}" to contract["architecture"].asString,
            "{{DISPLAY_NAME}}" to contract["displayName"].asString,
            "{{PUBLISHER_DISPLAY_NAME}}" to contract["publisherDisplayName"].asString,
            "{{TARGET_DEVICE_FAMILY}}" to contract["targetDeviceFamily"].asString,
            "{{MINIMUM_WINDOWS_VERSION}}" to contract["minimumWindowsVersion"].asString,
            "{{MAXIMUM_WINDOWS_VERSION_TESTED}}" to contract["maximumWindowsVersionTested"].asString,
            "{{APPLICATION_ID}}" to contract["applicationId"].asString,
            "{{EXECUTABLE}}" to contract["executable"].asString
        )
        val manifestText = replacements.entries.fold(
            projectDirectory.resolve("src/main/msix/AppxManifest.xml.template").readText()
        ) { text, replacement -> text.replace(replacement.key, replacement.value) }
        assertFalse(manifestText.contains("{{"))
        assertFalse(manifestText.contains(contract["storeId"].asString))

        val document = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }.newDocumentBuilder().parse(manifestText.byteInputStream())
        val identity = document.getElementsByTagNameNS(FOUNDATION_NAMESPACE, "Identity").item(0)
        assertNotNull(identity)
        assertEquals(IDENTITY_NAME, identity.attributes.getNamedItem("Name").nodeValue)
        assertEquals(PUBLISHER, identity.attributes.getNamedItem("Publisher").nodeValue)
        assertEquals("1.0.1.0", identity.attributes.getNamedItem("Version").nodeValue)
        assertEquals("x64", identity.attributes.getNamedItem("ProcessorArchitecture").nodeValue)
        assertTrue(identity.attributes.getNamedItem("Version").nodeValue.matches(MSIX_VERSION_PATTERN))

        val publisherDisplayName = document.getElementsByTagNameNS(
            FOUNDATION_NAMESPACE,
            "PublisherDisplayName"
        ).item(0)
        assertEquals(PUBLISHER_DISPLAY_NAME, publisherDisplayName.textContent)
        val application = document.getElementsByTagNameNS(FOUNDATION_NAMESPACE, "Application").item(0)
        assertEquals("BamaFlow", application.attributes.getNamedItem("Id").nodeValue)
        assertEquals("BamaChatDesktop.exe", application.attributes.getNamedItem("Executable").nodeValue)
        assertEquals(
            "packagedClassicApp",
            application.attributes.getNamedItemNS(UAP10_NAMESPACE, "RuntimeBehavior").nodeValue
        )
        assertEquals(
            "mediumIL",
            application.attributes.getNamedItemNS(UAP10_NAMESPACE, "TrustLevel").nodeValue
        )
        val target = document.getElementsByTagNameNS(FOUNDATION_NAMESPACE, "TargetDeviceFamily").item(0)
        assertEquals("Windows.Desktop", target.attributes.getNamedItem("Name").nodeValue)

        val visualElements = document.getElementsByTagNameNS(UAP_NAMESPACE, "VisualElements").item(0)
        assertEquals("Assets\\Square44x44Logo.png", visualElements.attributes.getNamedItem("Square44x44Logo").nodeValue)
        assertEquals("Assets\\Square150x150Logo.png", visualElements.attributes.getNamedItem("Square150x150Logo").nodeValue)
        val logo = document.getElementsByTagNameNS(FOUNDATION_NAMESPACE, "Logo").item(0)
        assertEquals("Assets\\StoreLogo.png", logo.textContent)

        val capabilities = document.getElementsByTagNameNS(RESTRICTED_NAMESPACE, "Capability")
        assertEquals(1, capabilities.length)
        assertEquals("runFullTrust", capabilities.item(0).attributes.getNamedItem("Name").nodeValue)
    }

    @Test
    fun packagingScriptChecksAssetsAndRejectsSensitiveLocalFiles() {
        val projectDirectory = desktopProjectDirectory()
        val script = projectDirectory.resolve("scripts/package-store-msix.ps1").readText()

        listOf("44", "150", "50").forEach { size ->
            assertTrue(script.contains("Assert-ImageDimensions") && script.contains("$size $size"))
        }
        listOf(
            "settings.properties",
            "local.properties",
            "apikeys.properties",
            "session_salt.bin",
            ".env",
            ".pfx",
            ".cer",
            ".log",
            ".msi"
        ).forEach { forbiddenName -> assertTrue(script.contains(forbiddenName)) }
        assertTrue(script.contains("Assert-SafePackageContent"))
        assertTrue(script.contains("& \$makeAppx pack"))
        assertTrue(script.contains("& \$makeAppx unpack"))
    }

    private fun desktopProjectDirectory(): Path {
        val workingDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
        return sequenceOf(workingDirectory, workingDirectory.resolve("desktopApp"))
            .firstOrNull { Files.isRegularFile(it.resolve("src/main/msix/store-package.json")) }
            ?: error("desktopApp-Projektverzeichnis konnte nicht ermittelt werden.")
    }

    private companion object {
        const val IDENTITY_NAME = "MamadouDianBald.BamaFlow"
        const val PUBLISHER = "CN=2279D882-BC23-4831-AA4E-D384F8EFCD9A"
        const val PUBLISHER_DISPLAY_NAME = "Mamadou Dian Baldé"
        const val FOUNDATION_NAMESPACE = "http://schemas.microsoft.com/appx/manifest/foundation/windows10"
        const val UAP_NAMESPACE = "http://schemas.microsoft.com/appx/manifest/uap/windows10"
        const val UAP10_NAMESPACE = "http://schemas.microsoft.com/appx/manifest/uap/windows10/10"
        const val RESTRICTED_NAMESPACE =
            "http://schemas.microsoft.com/appx/manifest/foundation/windows10/restrictedcapabilities"
        val MSIX_VERSION_PATTERN = Regex("^[0-9]+\\.[0-9]+\\.[0-9]+\\.0$")
    }
}
