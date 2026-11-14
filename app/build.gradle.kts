import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application") version libs.versions.agp.get()
    kotlin("android") version libs.versions.kotlin.get()
    id("com.google.devtools.ksp") version libs.versions.ksp.get()
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    // NOTE: ktlint/detekt Gradle plugins are not applied in this build at all. Both hook the
    // Android/Kotlin Gradle extensions in ways incompatible with AGP 8.7.3 + Kotlin 2.1.0
    // (`CommonExtension` / `BaseExtension` / `KotlinProjectExtension` not found in CI).
    // Style/lint is covered by Android `lintDebug` + `.editorconfig` (see docs/CI.md).
}

val appProps = Properties()
val localPropsFile = file("local.properties")
if (localPropsFile.exists()) {
    FileInputStream(localPropsFile).use { appProps.load(it) }
}

android {
    namespace = "com.sumi"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sumi"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-m0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    // Debug APK from CI must install in-place over previous installs (dev data survives upgrades).
    // This overrides AGP's built-in debug key, which CI would regenerate every run.
    // Not a release secret — the key is committed intentionally (local-only development identity).
    signingConfigs {
        named("debug") {
            storeFile = rootProject.file("app/debug.keystore")
            storePassword = "android"
            keyAlias = "sumi-debug"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        aidl = false
    }

    // No INTERNET permission. See AndroidManifest.xml. Usage Access uses AppOpsManager, not network.
    packaging { resources { excludes += "/META-INF/{DEPENDENCIES,LICENSE,LICENSE.txt,NOTICE,NOTICE.txt}" } }

    // Room schema export: schemas live under app/schemas/.
    sourceSets { getByName("androidTest").assets.srcDirs("$projectDir/schemas") }

    // Robolectric needs the merged resources and the debug manifest to render Compose
    // screens on the JVM. Without this the UI tests cannot run in CI (no device there).
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(project(":domain"))

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.navigation.compose)
    implementation(libs.hilt.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    implementation(libs.hilt.work)
    ksp(libs.hilt.compiler)
    ksp(libs.hilt.work.compiler)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // DataStore
    implementation(libs.datastore.preferences)

    // Coroutines
    implementation(libs.coroutines.android)

    // WorkManager (M1 safety-net collection; configured in M0 foundation)
    implementation(libs.work.runtime)
    implementation(libs.glance.appwidget)

    // Tests
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
    // JVM UI-test harness: Compose semantics assertions under Robolectric, so the
    // onboarding / permission-missing / Today screens are actually rendered in CI.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.espresso)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.room.testing)
    debugImplementation(libs.compose.ui.tooling)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
