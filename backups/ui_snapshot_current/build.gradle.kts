plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.example.bamachat"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.bamachat"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
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
    val mlKitTranslateVersion = "17.0.3"
    val mlKitLanguageIdVersion = "17.0.6"
    val mlKitSmartReplyVersion = "17.0.4"

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    
    implementation(platform("androidx.compose:compose-bom:2026.04.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.9.7")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.fragment:fragment-ktx:1.8.9")
    
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
    // ML Kit Smart Reply
    implementation("com.google.mlkit:smart-reply:$mlKitSmartReplyVersion") {
        // Prevent duplicate classes with language-id-common from gms beta artifact.
        exclude(group = "com.google.android.gms", module = "play-services-mlkit-language-id")
    }

    // Jsoup for Link Previews
    implementation("org.jsoup:jsoup:1.18.3")

    // Location Services
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Google Play Billing (Abo/Paywall Vorbereitung)
    implementation("com.android.billingclient:billing-ktx:8.3.0")

    testImplementation("junit:junit:4.13.2")
}

tasks.register("stabilityCheck") {
    group = "verification"
    description = "Runs core smoke checks for app stability."
    dependsOn("assembleDebug", "testDebugUnitTest", "lintDebug")
}
