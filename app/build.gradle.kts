import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.macrotracker"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.macrotracker"
        minSdk = 26
        targetSdk = 35
        versionCode = 78
        versionName = "1.1.78"

        // Read API keys from local.properties
        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localProperties.load(localPropertiesFile.inputStream())
        }
        val geminiKey = localProperties.getProperty("GEMINI_API_KEY", "")
        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiKey\"")
        val openAiKey = localProperties.getProperty("OPENAI_API_KEY", "")
        buildConfigField("String", "OPENAI_API_KEY", "\"$openAiKey\"")
        val openRouterKey = localProperties.getProperty("OPENROUTER_API_KEY", "")
        buildConfigField("String", "OPENROUTER_API_KEY", "\"$openRouterKey\"")
        val anthropicKey = localProperties.getProperty("ANTHROPIC_API_KEY", "")
        buildConfigField("String", "ANTHROPIC_API_KEY", "\"$anthropicKey\"")
        val youtubeKey = localProperties.getProperty("YOUTUBE_API_KEY", "")
        buildConfigField("String", "YOUTUBE_API_KEY", "\"$youtubeKey\"")
        val twitchClientId = localProperties.getProperty("TWITCH_CLIENT_ID", "")
        buildConfigField("String", "TWITCH_CLIENT_ID", "\"$twitchClientId\"")
        val twitchClientSecret = localProperties.getProperty("TWITCH_CLIENT_SECRET", "")
        buildConfigField("String", "TWITCH_CLIENT_SECRET", "\"$twitchClientSecret\"")
        val githubClientId = localProperties.getProperty("GITHUB_CLIENT_ID", "")
        buildConfigField("String", "GITHUB_CLIENT_ID", "\"$githubClientId\"")
        val githubToken = localProperties.getProperty("GITHUB_TOKEN", "")
        buildConfigField("String", "GITHUB_TOKEN", "\"$githubToken\"")
    }

    signingConfigs {
        // Shared tester keystore so GitHub Releases updates install over prior tester builds
        // (same cert + higher versionCode = Android update, not reinstall).
        create("tester") {
            storeFile = file("tester.jks")
            storePassword = "dailydash"
            keyAlias = "dailydash"
            keyPassword = "dailydash"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("tester")
        }
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("tester")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            optIn.add("kotlin.RequiresOptIn")
            // Hilt qualifiers on constructor properties (`@ApplicationContext private val …`) target the param and field alike.
            freeCompilerArgs.add("-Xannotation-default-target=param-property")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            // JSch ships as a multi-release jar; Android only ever runs the Java 8 baseline.
            excludes += "META-INF/versions/**"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/*.kotlin_module"
        }
    }
}


dependencies {
    // Compose BOM
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.animation)
    debugImplementation(libs.compose.ui.tooling)

    // AndroidX
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.core.ktx)
    implementation(libs.browser)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Networking (OkHttp used directly for Gemini API calls)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // F1 Data - Ktor for modern, type-safe networking
    implementation("io.ktor:ktor-client-core:2.3.12")
    implementation("io.ktor:ktor-client-okhttp:2.3.12")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.12")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.12")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.0")

    // CameraX
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.concurrent.futures)
    // Real Guava needed so CameraControl.enableTorch()'s ListenableFuture is on the classpath
    // (listenablefuture:9999 stub alone is not enough for Kotlin to resolve the return type).
    implementation("com.google.guava:guava:33.3.1-android")

    // Health Connect
    implementation(libs.health.connect)

    // Image loading
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)

    // Location
    implementation(libs.play.services.location)

    // Google Identity (YouTube OAuth via AuthorizationClient)
    implementation(libs.play.services.auth)
    implementation(libs.coroutines.play.services)

    // Glance (App Widgets)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    
    // Installed icon library for full-app usage (Weather, UI, etc.)

    // Frosted-glass blur for the floating pill nav
    implementation(libs.haze)
    implementation(libs.haze.materials)

    // Hold-and-drag reorder for Home/Health panels + pencil editor
    implementation(libs.reorderable)

    // WorkManager (for widget periodic updates)
    implementation(libs.work.runtime)

    // SSH for the server monitor (maintained JSch fork — modern KEX/ciphers, ed25519, no BouncyCastle)
    implementation(libs.jsch)

    testImplementation("junit:junit:4.13.2")
    // Android's org.json is a stub on the JVM; the real one lets the bridge and dashboard parsers be tested.
    testImplementation("org.json:json:20240303")
}