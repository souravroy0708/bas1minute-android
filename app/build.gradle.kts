plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Release builds are signed with YOUR upload key (GitHub secrets); Google Play re-signs them with the app
// signing key (Play App Signing). Without the secrets the build falls back to the debug key (not for Play).
val releaseKeystore: String? = System.getenv("B1M_KEYSTORE_PATH")
// Every Play release needs a higher versionCode: the GitHub build number. Bump APP_VERSION for big releases.
val buildNumber: Int = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
val appVersion = "2.0"

// Push notifications need app/google-services.json (from Firebase). Without it the app builds without pushes.
if (file("google-services.json").exists()) apply(plugin = "com.google.gms.google-services")

android {
    namespace = "com.bas1minute.sync"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.bas1minute.sync"
        minSdk = 26          // Health Connect needs Android 8.0+
        targetSdk = 36
        versionCode = buildNumber
        versionName = "$appVersion.$buildNumber"
        // Your live site. Members can change it in the app (for local testing).
        buildConfigField("String", "DEFAULT_SERVER", "\"https://bas1minute.pythonanywhere.com\"")
        // Shown on the in-app privacy screen. Change to your real support address.
        buildConfigField("String", "SUPPORT_EMAIL", "\"tosourav6@gmail.com\"")
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
            // Shrink, optimise and obfuscate. Upload app/build/outputs/mapping/release/mapping.txt to Play
            // (Play Console does this automatically for bundles) so crash reports stay readable.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        // Keep release checks on, but always write a readable report (uploaded by GitHub Actions on failure).
        htmlReport = true
        xmlReport = false
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    // Only the languages the app speaks: drops ~80 translations Play services and AndroidX bring along.
    androidResources {
        localeFilters += listOf("en", "hi")
    }
    packaging {
        resources {
            excludes += listOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/*.kotlin_module", "DebugProbesKt.bin",
                "kotlin-tooling-metadata.json", "/META-INF/*.version")
        }
    }
    bundle {
        // Phones download only their own screen density and language from Play
        density { enableSplit = true }
        language { enableSplit = true }
        abi { enableSplit = true }
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
    implementation(libs.compose.material.icons.extended)   // R8 strips the icons we don't use
    implementation(libs.compose.animation)                 // InfiniteTransition.animateFloat (TourScreen)
    implementation(libs.splashscreen)
    implementation(libs.activity.compose)
    implementation(libs.fragment.ktx)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.work.runtime.ktx)
    implementation(libs.health.connect)
    implementation(libs.play.services.fitness)
    implementation(libs.coroutines.play.services)
    // GPS walk / run
    implementation(libs.play.services.location)
    implementation(libs.osmdroid)
    // Google Play in-app updates (forced "Update required" and "Update available")
    implementation(libs.play.app.update)
    testImplementation(libs.junit)
}
