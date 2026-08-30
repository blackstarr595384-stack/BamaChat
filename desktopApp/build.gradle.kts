import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.compose)
}

fun toStoreMsixVersion(desktopVersion: String): String {
    val components = desktopVersion.split('.')
    require(components.size == 3) {
        "Desktop packageVersion muss für MSIX exakt drei numerische Komponenten besitzen."
    }
    val numbers = components.mapIndexed { index, component ->
        require(component.matches(Regex("[0-9]+"))) {
            "Desktop packageVersion-Komponente ${index + 1} ist nicht numerisch."
        }
        component.toIntOrNull()?.also { number ->
            require(number in 0..65_535) {
                "Desktop packageVersion-Komponente ${index + 1} liegt außerhalb 0..65535."
            }
        } ?: error("Desktop packageVersion-Komponente ${index + 1} ist ungültig.")
    }
    require(numbers.first() > 0) {
        "Die erste MSIX-Versionskomponente muss größer als 0 sein."
    }
    return numbers.joinToString(".") + ".0"
}

val desktopPackageName = "BamaChatDesktop"
val desktopPackageVersion = "1.0.1"
val storeMsixVersion = toStoreMsixVersion(desktopPackageVersion)
val storeMsixOutput = layout.buildDirectory.file(
    "compose/binaries/main/msix/BamaFlow_${storeMsixVersion}_x64.msix"
)

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":sharedCore"))
    implementation(compose.desktop.currentOs)
    implementation(compose.materialIconsExtended)
    implementation(libs.google.gson)
    implementation(libs.jna.platform)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)
    testImplementation(kotlin("test"))
}

compose.desktop {
    application {
        mainClass = "com.example.bamachat.desktop.DesktopMainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = desktopPackageName
            packageVersion = desktopPackageVersion
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

tasks.register<Exec>("packageStoreMsix") {
    group = "distribution"
    description = "Builds and verifies the unsigned Microsoft Store MSIX package."
    dependsOn("createDistributable")

    val appImageDirectory = layout.buildDirectory.dir(
        "compose/binaries/main/app/$desktopPackageName"
    )
    val packagingScript = layout.projectDirectory.file("scripts/package-store-msix.ps1")
    val manifestTemplate = layout.projectDirectory.file(
        "src/main/msix/AppxManifest.xml.template"
    )
    val storeContract = layout.projectDirectory.file("src/main/msix/store-package.json")
    val sourceIcon = layout.projectDirectory.file("src/main/resources/bamachat.ico")

    inputs.dir(appImageDirectory)
    inputs.file(packagingScript)
    inputs.file(manifestTemplate)
    inputs.file(storeContract)
    inputs.file(sourceIcon)
    inputs.property("desktopPackageVersion", desktopPackageVersion)
    inputs.property("storeMsixVersion", storeMsixVersion)
    outputs.file(storeMsixOutput)

    doFirst {
        require(System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
            "packageStoreMsix kann ausschließlich unter Windows ausgeführt werden."
        }
        val executable = appImageDirectory.get().file("$desktopPackageName.exe").asFile
        require(executable.isFile) {
            "Compose-App-Image fehlt oder enthält nicht $desktopPackageName.exe."
        }
    }

    commandLine(
        "powershell.exe",
        "-NoProfile",
        "-NonInteractive",
        "-ExecutionPolicy",
        "Bypass",
        "-File",
        packagingScript.asFile.absolutePath,
        "-AppImageDirectory",
        appImageDirectory.get().asFile.absolutePath,
        "-ManifestTemplate",
        manifestTemplate.asFile.absolutePath,
        "-StoreContract",
        storeContract.asFile.absolutePath,
        "-SourceIcon",
        sourceIcon.asFile.absolutePath,
        "-BuildDirectory",
        layout.buildDirectory.get().asFile.absolutePath,
        "-DesktopVersion",
        desktopPackageVersion,
        "-OutputMsix",
        storeMsixOutput.get().asFile.absolutePath
    )
}
