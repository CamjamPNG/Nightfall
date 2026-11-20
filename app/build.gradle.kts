// SPDX-License-Identifier: MIT
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Robolectric picks its Android sandbox from `sdk` in the `robolectric.properties` on the test
// classpath, and has no system-property override for it — so the level is a build input here.
// Temurin 17, which the release job and the F-Droid recipe build on, cannot start the API 36 sandbox
// ("Android SDK 36 requires Java 21"), so 35 stays the default for every ordinary build and CI's
// Android 16 job selects the 36 file with `-PnightfallRobolectricSdk=36`. The tests are the same
// either way; only the platform runtime beneath them changes.
val robolectricSdk = providers.gradleProperty("nightfallRobolectricSdk").getOrElse("35")

android {
    namespace = "dev.nightfall"
    compileSdk = 36
    defaultConfig {
        applicationId = "io.github.camjampng.nightfall"
        minSdk = 26
        targetSdk = 36
        versionCode = 41
        versionName = "2.4"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    // The seven field recordings ship as Opus containers, 10,089,142 bytes against 77,449,472 as raw
    // PCM. `src/pcm/assets` is not an asset source any more: the capture is kept as the reference the
    // host measurement tests decode, so the build carries exactly one recording kind and a stale
    // second copy cannot ship beside it.
    sourceSets.getByName("main").assets.srcDir("src/opus/assets")
    // Two distribution channels. `github` ships the in-app updater and the only network
    // permission in the product; `fdroid` ships no updater, no network permission and no
    // network code, which is what F-Droid's inclusion policy requires of self-updating apps.
    flavorDimensions += "channel"
    productFlavors {
        create("github") { dimension = "channel" }
        create("fdroid") { dimension = "channel" }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
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
    lint {
        abortOnError = true
        // Copy typed into a Compose call is invisible to a translator and to strings.xml, and no test
        // can see it: making it an error means the next hardcoded string fails CI instead of shipping.
        error += "HardcodedText"
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        // DataStore is a per-process singleton: one JVM per test class keeps scenarios isolated.
        unitTests.all { it.forkEvery = 1 }
    }
    // The sandbox level itself, selected by -PnightfallRobolectricSdk as described above.
    sourceSets.getByName("test").resources.setSrcDirs(
        listOf("src/test/resources", "src/robolectricApi$robolectricSdk/resources"),
    )
}

// The flavor split turns lintDebug/lintRelease into per-channel tasks; keep the aggregate names
// that the documented build gate and the release workflow use, covering both channels.
// Compose draws copy from Kotlin string literals, which lint's HardcodedText cannot see: that check
// reads View XML and Java, not `Text("…")`. This is the guard for the Compose half, and it hangs off
// the same lint tasks the gate and the release workflow already call, so no workflow changed.
val verifyNoHardcodedCopy = tasks.register("verifyNoHardcodedCopy") {
    val sources = fileTree("src/main/java") { include("**/*.kt") }
    inputs.files(sources)
    val markers =
        listOf(
            "Text(\"",
            "contentDescription = \"",
            "stateDescription = \"",
            "showSnackbar(\"",
            "-> \"",
        )
    doLast {
        val offenders =
            sources.flatMap { file ->
                val lines = file.readLines()
                lines
                    .withIndex()
                    .filter { (index, line) ->
                        val allowed =
                            line.contains("hardcoded-copy-allowed") ||
                                (index > 0 && lines[index - 1].contains("hardcoded-copy-allowed"))
                        // An empty literal draws nothing, so it is not copy.
                        !allowed &&
                            !line.contains("R.string") &&
                            !line.contains("-> \"\"") &&
                            markers.any { line.contains(it) }
                    }
                    .map { (index, line) -> "${file.path}:${index + 1}: ${line.trim()}" }
            }
        if (offenders.isNotEmpty())
            throw GradleException(
                "Copy belongs in res/values/strings.xml, not in a Compose call:\n" +
                    offenders.joinToString("\n")
            )
    }
}

tasks.matching { it.name == "lintDebug" || it.name == "lintRelease" }.configureEach {
    dependsOn(verifyNoHardcodedCopy)
}

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
