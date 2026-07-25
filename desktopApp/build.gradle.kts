import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.compose)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":sharedCore"))
    implementation(compose.desktop.currentOs)
    implementation(compose.materialIconsExtended)
    implementation(libs.google.gson)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)
    testImplementation(kotlin("test"))
}

compose.desktop {
    application {
        mainClass = "com.example.bamachat.desktop.DesktopMainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "BamaChatDesktop"
            packageVersion = "1.0.1"
            vendor = "M.D Baldé"
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
                iconFile.set(project.file("src/main/resources/bamachat.ico"))
            }
        }
    }
}
