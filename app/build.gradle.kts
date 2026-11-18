// SPDX-License-Identifier: MIT
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.nightfall"
    compileSdk = 35
    defaultConfig {
        applicationId = "io.github.camjampng.nightfall"
        minSdk = 26
        targetSdk = 35
        versionCode = 16
        versionName = "1.5.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    androidResources { noCompress += "pcm" }
    // Two distribution channels. `github` ships the in-app updater and the only network
    // permission in the product; `fdroid` ships no updater, no network permission and no
    // network code, which is what F-Droid's inclusion policy requires of self-updating apps.
    flavorDimensions += "channel"
    productFlavors {
        create("github") { dimension = "channel" }
        create("fdroid") { dimension = "channel" }
    }
    buildFeatures { compose = true }
    buildTypes {
        release {
            // R8 shrinks and renames the release dex; resource shrinking then drops the resources no
            // code or manifest entry reaches (bundled audio is in assets, which it leaves alone).
            // Keep rules and their reasons live in proguard-rules.pro.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Keep release bytes independent of Git checkout/export state.
            vcsInfo { include = false }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    lint { abortOnError = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        // DataStore is a per-process singleton: one JVM per test class keeps scenarios isolated.
        unitTests.all { it.forkEvery = 1 }
    }
}

// The flavor split turns lintDebug/lintRelease into per-channel tasks; keep the aggregate names
// that the documented build gate and the release workflow use, covering both channels.
tasks.register("lintDebug") {
    group = "verification"
    description = "Runs lint for both channels' debug variants."
    dependsOn("lintGithubDebug", "lintFdroidDebug")
}

tasks.register("lintRelease") {
    group = "verification"
    description = "Runs lint for both channels' release variants."
    dependsOn("lintGithubRelease", "lintFdroidRelease")
}

dependencies {
    implementation(project(":core"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.preview)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.compose)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.datastore)
    implementation(libs.media3.session)
    implementation(libs.coroutines)
    implementation(libs.serialization)
    debugImplementation(libs.compose.tooling)
    debugImplementation(libs.compose.test.manifest)
    testImplementation(libs.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.androidx.test)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.compose.test)
    testImplementation(libs.robolectric)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.androidx.test)
    androidTestImplementation(libs.androidx.runner)
    androidTestImplementation(libs.compose.test)
}
