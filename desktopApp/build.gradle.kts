import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":sharedCore"))
    implementation(compose.desktop.currentOs)
    implementation(compose.materialIconsExtended)
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
}

compose.desktop {
    application {
        mainClass = "com.example.bamachat.desktop.DesktopMainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "BamaChatDesktop"
            packageVersion = "0.1.4"
            modules(
                "java.net.http",
                "jdk.httpserver",
                "jdk.crypto.ec",
                "jdk.unsupported",
                "java.naming"
            )
            windows {
                menu = true
                shortcut = true
                dirChooser = true
                perUserInstall = true
                menuGroup = "BamaChat"
                upgradeUuid = "9f45b82e-98a3-4b03-b95f-3ec8b1dbe8ff"
            }
        }
    }
}
