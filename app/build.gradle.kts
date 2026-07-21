import java.util.Properties

plugins {
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.20"
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services") apply false

    id("io.gitlab.arturbosch.detekt") version "1.23.6"
    id("com.google.dagger.hilt.android")
}

val googleServicesFiles = listOf(
    "google-services.json",
    "src/main/google-services.json",
    "src/debug/google-services.json",
    "src/release/google-services.json"
).map { layout.projectDirectory.file(it).asFile }
val hasGoogleServicesJson = googleServicesFiles.any { it.exists() }

fun String.asBuildConfigString(): String =
    "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

val bamaVoiceRealtimeSessionUrl = providers.gradleProperty("bamaVoiceRealtimeSessionUrl")
    .orElse(providers.environmentVariable("BAMA_VOICE_REALTIME_SESSION_URL"))
    .orElse("")
    .get()
val bamaVoiceRealtimeSessionEndUrl = providers.gradleProperty("bamaVoiceRealtimeSessionEndUrl")
    .orElse(providers.environmentVariable("BAMA_VOICE_REALTIME_SESSION_END_URL"))
    .orElse("")
    .get()

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
    compileSdk = 35  // Android 15 (SDK 35) – required for targetSdk 35 + Galaxy S25 Ultra compat

    defaultConfig {
        applicationId = "de.bamachat.app"
        minSdk = 33
        targetSdk = 35
        versionCode = 3
        versionName = "1.0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    bundle {
        language {
            enableSplit = false
        }
        density {
            enableSplit = true
        }
        abi {
            enableSplit = true
        }
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
        debug {
            buildConfigField("String", "BAMA_VOICE_REALTIME_SESSION_URL", "\"\"")
            buildConfigField("String", "BAMA_VOICE_REALTIME_SESSION_END_URL", "\"\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            buildConfigField(
                "String",
                "BAMA_VOICE_REALTIME_SESSION_URL",
                bamaVoiceRealtimeSessionUrl.asBuildConfigString()
            )
            buildConfigField(
                "String",
                "BAMA_VOICE_REALTIME_SESSION_END_URL",
                bamaVoiceRealtimeSessionEndUrl.asBuildConfigString()
            )
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
        buildConfig = true
    }
    ksp {
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
    // Play In-App Review
    implementation("com.google.android.play:review-ktx:2.0.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    val mlKitTranslateVersion = "17.0.3"
    val mlKitLanguageIdVersion = "17.0.6"
    val mlKitSmartReplyVersion = "17.0.4"
    val bouncyCastleVersion = "1.84"
    val credentialsVersion = "1.2.0-rc01"

    implementation(project(":sharedCore"))
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    implementation(platform("androidx.compose:compose-bom:2024.10.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    
    // Lottie Animations
    implementation("com.airbnb.android:lottie-compose:6.4.1")
    
    // Material Design Icons (Icons für UI)
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.fragment:fragment-ktx:1.8.9")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    
    // Room Database
    val roomVersion = "2.8.4"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")
    
    // Markdown support
    implementation("com.github.jeziellago:compose-markdown:0.7.1")
    implementation("io.coil-kt:coil-compose:2.7.0")
    
    // Retrofit + OkHttp
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    // Gemini AI
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")

    // ML Kit Translation & Language ID
    implementation("com.google.mlkit:translate:$mlKitTranslateVersion")
    implementation("com.google.mlkit:language-id:$mlKitLanguageIdVersion")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    // ML Kit Smart Reply
    implementation("com.google.mlkit:smart-reply:$mlKitSmartReplyVersion") {
        // Prevent duplicate classes with language-id-common from gms beta artifact.
        exclude(group = "com.google.android.gms", module = "play-services-mlkit-language-id")
    }

    // Jsoup for Link Previews
    implementation("org.jsoup:jsoup:1.18.3")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    implementation("org.bouncycastle:bcprov-jdk15to18:$bouncyCastleVersion")
    implementation("org.bouncycastle:bcpkix-jdk15to18:$bouncyCastleVersion")
    implementation("org.bouncycastle:bcutil-jdk15to18:$bouncyCastleVersion")

    // Location Services
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.google.android.gms:play-services-auth:21.3.0")
    implementation("androidx.credentials:credentials:$credentialsVersion")
    implementation("androidx.credentials:credentials-play-services-auth:$credentialsVersion")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.0")

    // Google Play Billing (Abo/Paywall Vorbereitung)
    implementation("com.android.billingclient:billing-ktx:8.3.0")

    // Hilt DI
    val hiltVersion = "2.54"
    implementation("com.google.dagger:hilt-android:$hiltVersion")
    ksp("com.google.dagger:hilt-compiler:$hiltVersion")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Firebase (Auth / Profile / Storage)
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.firebase:firebase-analytics")
    implementation(libs.webrtc.android)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("androidx.room:room-testing:$roomVersion")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.10.00"))
    androidTestImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.6")
}

tasks.register("stabilityCheck") {
    group = "verification"
    description = "Runs core smoke checks for app stability."
    dependsOn("assembleDebug", "testDebugUnitTest", "lintDebug")
}

// Fix Room/KSP kotlinx-serialization compatibility
kotlin {
    sourceSets {
        debug {
            kotlin.srcDir("build/generated/ksp/debug/kotlin")
        }
    }
}


