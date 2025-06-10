plugins {
    idea
    java
    kotlin("jvm")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("ch.qos.logback:logback-classic:${rootProject.extra["logback_version"]}")
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.7")

    // https://mvnrepository.com/artifact/org.jetbrains.kotlin/kotlin-stdlib
    implementation("org.jetbrains.kotlin:kotlin-stdlib:${rootProject.extra["kotlin_version"]}")

    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:${rootProject.extra["jackson_dataformat_version"]}")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:${rootProject.extra["jackson_kotlin_version"]}")
}
