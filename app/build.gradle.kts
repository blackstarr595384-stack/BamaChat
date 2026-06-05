import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.detekt)
    alias(libs.plugins.hilt.android)
}

val googleServicesFiles = listOf(
    "google-services.json",
    "src/main/google-services.json",
    "src/debug/google-services.json",
    "src/release/google-services.json"
).map { layout.projectDirectory.file(it).asFile }
val hasGoogleServicesJson = googleServicesFiles.any { it.exists() }

if (hasGoogleServicesJson) {
    apply(plugin = "com.google.gms.google-services")
} else {
    logger.warn("google-services.json fehlt - Google Services Plugin wird fuer lokalen Build deaktiviert.")
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
val hasReleaseKeystore = keystorePropertiesFile.exists()

if (hasReleaseKeystore) {
    keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
}

android {
    namespace = "com.example.bamachat"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.bamachat"
        minSdk = 33
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (hasReleaseKeystore) {
                val storeFilePath = keystoreProperties.getProperty("storeFile")?.trim().orEmpty()
                val storePassword = keystoreProperties.getProperty("storePassword")?.trim().orEmpty()
                val keyAlias = keystoreProperties.getProperty("keyAlias")?.trim().orEmpty()
                val keyPassword = keystoreProperties.getProperty("keyPassword")?.trim().orEmpty()

                val missing = buildList {
                    if (storeFilePath.isBlank()) add("storeFile")
                    if (storePassword.isBlank()) add("storePassword")
                    if (keyAlias.isBlank()) add("keyAlias")
                    if (keyPassword.isBlank()) add("keyPassword")
                }
                if (missing.isNotEmpty()) {
                    error("keystore.properties ist unvollständig. Fehlende Keys: ${missing.joinToString()}")
                }

                storeFile = rootProject.file(storeFilePath)
                this.storePassword = storePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    
    kotlin {
        jvmToolchain(11)
    }
    
    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.all {
            it.maxHeapSize = "256m"
            it.jvmArgs("-Xms128m", "-XX:MaxMetaspaceSize=128m")
            it.maxParallelForks = 1
        }
    }

}

dependencies {
    implementation(project(":sharedCore"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    
    // Lottie Animations
    implementation(libs.airbnb.lottie.compose)
    
    // Material Design Icons (Icons für UI)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.security.crypto)
    
    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    
    // Markdown support
    implementation(libs.compose.markdown)
    implementation(libs.coil.compose)
    
    // Retrofit + OkHttp
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging)
    implementation(libs.google.gson)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.play.services)

    // Gemini AI
    implementation(libs.google.generativeai)

    // ML Kit Translation & Language ID
    implementation(libs.mlkit.translate)
    implementation(libs.mlkit.language.id)
    implementation(libs.mlkit.text.recognition)
    // ML Kit Smart Reply
    implementation(libs.mlkit.smart.reply) {
        // Prevent duplicate classes with language-id-common from gms beta artifact.
        exclude(group = "com.google.android.gms", module = "play-services-mlkit-language-id")
    }

    // Jsoup for Link Previews
    implementation(libs.jsoup)
    implementation(libs.pdfbox.android)
    implementation(libs.bouncycastle.bcprov)
    implementation(libs.bouncycastle.bcpkix)
    implementation(libs.bouncycastle.bcutil)

    // Location Services
    implementation(libs.play.services.location)
    implementation(libs.play.services.auth)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    // Google Play Billing (Abo/Paywall Vorbereitung)
    implementation(libs.billing.ktx)
    
    // Google Play In-App Review
    implementation(libs.google.play.review.ktx)

    // WorkManager for Offline Sync
    implementation(libs.androidx.work.runtime.ktx)

    // Hilt DI
    implementation(libs.google.hilt.android)
    ksp(libs.google.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Firebase (Auth / Profile / Storage)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)

    testImplementation(libs.junit4)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.core.ktx)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.uiautomator)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    detektPlugins(libs.detekt.formatting)
}

tasks.register("stabilityCheck") {
    group = "verification"
    description = "Runs core smoke checks for app stability."
    dependsOn("assembleDebug", "testDebugUnitTest", "lintDebug")
}
