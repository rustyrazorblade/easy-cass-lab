plugins {
    idea
    java
    kotlin("jvm")
    kotlin("plugin.serialization")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    // Logging
    implementation(libs.bundles.logging)

    // Kotlin
    implementation(libs.kotlin.stdlib)

    // Jackson
    implementation(libs.bundles.jackson)
}
