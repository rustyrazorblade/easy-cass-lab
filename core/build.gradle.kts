plugins {
    idea
    java
    kotlin("jvm")
}

dependencies {
    implementation("org.apache.logging.log4j:log4j-api-kotlin:${rootProject.extra["log4j_api_version"]}")
    implementation("org.apache.logging.log4j:log4j-core:${rootProject.extra["log4j_core_version"]}")
    implementation("org.apache.logging.log4j:log4j-slf4j18-impl:${rootProject.extra["slf4j_version"]}")

    // https://mvnrepository.com/artifact/org.jetbrains.kotlin/kotlin-stdlib
    implementation("org.jetbrains.kotlin:kotlin-stdlib:${rootProject.extra["kotlin_version"]}")

    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:${rootProject.extra["jackson_dataformat_version"]}")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:${rootProject.extra["jackson_kotlin_version"]}")
}
