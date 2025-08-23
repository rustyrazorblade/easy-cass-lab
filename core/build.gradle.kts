plugins {
    idea
    java
    kotlin("jvm")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // Logging
    implementation(libs.bundles.logging)

    // Kotlin
    implementation(libs.kotlin.stdlib)

    // Jackson
    implementation(libs.bundles.jackson)
}
