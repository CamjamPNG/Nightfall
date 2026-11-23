// SPDX-License-Identifier: MIT
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(libs.serialization)
    testImplementation(libs.junit)
    // Test scope only: the conformant decoder that verifies the bundled containers' audio. Nothing
    // it brings in reaches an APK.
    testImplementation(libs.concentus)
}

tasks.withType<Test>().configureEach {
    // The suite reads the real bundled recordings, so a changed, truncated or re-encoded file must
    // re-run it: without these inputs Gradle calls the task up to date and a broken container passes.
    inputs.dir("../app/src/opus/assets/audio")
    inputs.dir("../app/src/pcm/assets/audio")
    // `SampleClipTest` reads the committed listening samples, which are also outside this module.
    inputs.dir("../docs/samples")
    useJUnit {
        // The eight-hour night render takes about two minutes on its own. It always runs in CI, which
        // is where the build gate lives; a local run can skip it with -PwithoutNight.
        if (providers.gradleProperty("withoutNight").isPresent)
            excludeCategories("dev.nightfall.core.Night")
    }
}

/**
 * Renders the listening samples in `docs/samples/` to WAV, for `tools/render_samples.sh` to encode.
 *
 * These are documentation, so the task is not part of `check`: it runs by hand when the engine
 * changes, and `SampleClipTest` is what notices a clip that no longer matches the engine.
 */
tasks.register<JavaExec>("renderSamples") {
    group = "documentation"
    description = "Renders every listening sample to build/samples as 48 kHz stereo WAV."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("dev.nightfall.core.SamplesKt")
    args(layout.buildDirectory.dir("samples").get().asFile.path)
}
