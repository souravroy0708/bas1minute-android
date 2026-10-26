plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Pilot builds on GitHub Actions are signed with YOUR key (from repository secrets), so every new
// APK installs as an update over the previous one. Without the secrets it falls back to the debug key.
val releaseKeystore: String? = System.getenv("B1M_KEYSTORE_PATH")
// Each GitHub build gets a higher version number, which Android needs to accept an update.
val buildNumber: Int = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "com.bas1minute.sync"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.bas1minute.sync"
        minSdk = 26          // Health Connect needs Android 8.0+
        targetSdk = 36
        versionCode = buildNumber
        versionName = "0.3.$buildNumber"
        // Your live site. Members can change it in the app (for local testing).
        buildConfigField("String", "DEFAULT_SERVER", "\"https://bas1minute.pythonanywhere.com\"")
    }

    signingConfigs {
        create("release") {
            if (releaseKeystore != null) {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("B1M_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("B1M_KEY_ALIAS")
                keyPassword = System.getenv("B1M_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            manifestPlaceholders["cleartext"] = "true"   // allows http:// to a laptop on your Wi-Fi
        }
        release {
            manifestPlaceholders["cleartext"] = "false"   // HTTPS only
            signingConfig = if (releaseKeystore != null) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
            // Kept off during the pilot so crash reports stay readable. Turn on before the Play Store.
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.work.runtime.ktx)
    implementation(libs.health.connect)
    implementation(libs.play.services.fitness)
    implementation(libs.coroutines.play.services)
}
