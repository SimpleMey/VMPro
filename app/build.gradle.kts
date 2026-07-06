import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Release signing — loaded from keystore.properties (gitignored, never committed).
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) FileInputStream(f).use { load(it) }
}

// PostHog analytics config — read from local.properties (gitignored) so it stays out of the
// public repo. Set `posthog.key=phc_...` (and optionally `posthog.host=`) there; a blank key
// disables analytics gracefully.
private val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) FileInputStream(f).use { load(it) }
}
val posthogKey: String = localProps.getProperty("posthog.key", "")
val posthogHost: String = localProps.getProperty("posthog.host", "https://us.i.posthog.com")

android {
    namespace = "com.vmpro.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.vmpro.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 44
        versionName = "4.3.1"
        vectorDrawables { useSupportLibrary = true }

        buildConfigField("String", "POSTHOG_KEY", "\"$posthogKey\"")
        buildConfigField("String", "POSTHOG_HOST", "\"$posthogHost\"")
    }

    signingConfigs {
        create("release") {
            if (keystoreProps.isNotEmpty()) {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig =
                if (keystoreProps.isNotEmpty()) signingConfigs.getByName("release") else null
        }
    }

    // Name the built APK "vmpro-<versionName>.apk" (e.g. vmpro-4.0.apk) instead of app-release.apk.
    applicationVariants.all {
        val variant = this
        outputs.all {
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName =
                "vmpro-${variant.versionName}.apk"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    // 2.8.7 fixes the R8 "LocalLifecycleOwner not present" crash present in 2.8.0–2.8.2.
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Background blur ("frosted glass") for the bottom bar — 0.7.3 targets Compose 1.6 /
    // Kotlin 1.9; blurs on Android 12+, degrades to the tint on older devices.
    implementation("dev.chrisbanes.haze:haze:0.7.3")

    // Product analytics (open-source, self-hostable; generous free tier).
    // Pinned to 3.35.0 — the last release built with Kotlin 1.8/1.9 metadata that our
    // Kotlin 1.9.24 toolchain can read (3.40+ require Kotlin 2.1).
    implementation("com.posthog:posthog-android:3.35.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
