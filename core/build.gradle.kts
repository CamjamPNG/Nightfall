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
}

tasks.withType<Test>().configureEach {
    useJUnit {
        // The eight-hour night render takes about two minutes on its own. It always runs in CI, which
        // is where the build gate lives; a local run can skip it with -PwithoutNight.
        if (providers.gradleProperty("withoutNight").isPresent)
            excludeCategories("dev.nightfall.core.Night")
    }
}
