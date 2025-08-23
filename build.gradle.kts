

buildscript {
    repositories {
        mavenCentral()
    }
}

plugins {
    java
    idea
    application
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.shadow)
    alias(libs.plugins.versions)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
}

group = "com.rustyrazorblade"

java {
    sourceCompatibility = JavaVersion.VERSION_17
}

application {
    applicationName = "easy-cass-lab"
    mainClass.set("com.rustyrazorblade.easycasslab.MainKt")
    applicationDefaultJvmArgs =
        listOf(
            "-Deasycasslab.ami.name=rustyrazorblade/images/easy-cass-lab-cassandra-amd64-$version",
            "-Deasycasslab.ami.owner=081145431955",
            "-Deasycasslab.version=$version",
        )
}

tasks.named<CreateStartScripts>("startScripts") {
    doLast {
        // Update the Unix / Mac / Linux start script
        val replacement = "\$1 \nDEFAULT_JVM_OPTS=\"\\\$DEFAULT_JVM_OPTS -Deasycasslab.apphome=\\\$APP_HOME\""
        val regex = "^(DEFAULT_JVM_OPTS=.*)".toRegex(RegexOption.MULTILINE)
        val body = unixScript.readText()
        val newBody = regex.replace(body, replacement)
        unixScript.writeText(newBody)

        // This needs to be updated for windows
    }
}

// In this section you declare where to find the dependencies of your project
allprojects {
    repositories {
        mavenCentral()
    }
}

// In this section you declare the dependencies for your production and test code
dependencies {
    // Logging
    implementation(libs.bundles.logging)

    // Kotlin
    implementation(libs.kotlin.stdlib)

    // CLI and UI
    implementation(libs.jcommander)
    implementation(libs.guava)
    implementation(libs.jline)
    implementation(libs.mordant)

    // AWS SDK
    implementation(awssdk.services.s3) // Add dependency on the AWS SDK for Kotlin's S3 client.
    implementation(libs.bundles.awssdk)

    // Utilities
    implementation(libs.reflections)
    implementation(libs.commons.io)

    // Docker
    implementation(libs.bundles.docker)

    // Project dependencies
    implementation(project(":core"))

    // Jackson
    implementation(libs.bundles.jackson)

    // SSH
    implementation(libs.bundles.sshd)

    // Ktor
    implementation(libs.bundles.ktor)

    // Koin Dependency Injection
    implementation(platform(libs.koin.bom))
    implementation(libs.bundles.koin)

    // Testing
    testImplementation(libs.bundles.testing)
    testImplementation(libs.bundles.koin.test)
}

kotlin {
    jvmToolchain(17)
}

sourceSets {
    val main by getting {
        java.srcDirs("src/main/kotlin")
        resources.srcDirs("build/aws")
    }
    val test by getting {
        java.srcDirs("src/test/kotlin")
    }

    val integrationTest by creating {
        java {
            compileClasspath += sourceSets["main"].output + sourceSets["test"].output
            runtimeClasspath += sourceSets["main"].output + sourceSets["test"].output
            srcDir("src/integration-test/kotlin")
        }
    }
}

// The integrationTest source set creates these configurations automatically
// We just need to make them extend from the test configurations
configurations["integrationTestImplementation"].extendsFrom(configurations["testImplementation"])
configurations["integrationTestRuntimeOnly"].extendsFrom(configurations["testRuntimeOnly"])

tasks.register<Test>("integrationTest") {
    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath
    outputs.upToDateWhen { false }
    description = "Runs the full end to end tests. Will create a cluster in AWS. Errors might require manual cluster tear down."
    group = "Verification"
}

tasks.test {
    useJUnitPlatform()
//    reports {
//        junitXml.isEnabled = true
//        html.isEnabled = true
//    }
    testLogging.showStandardStreams = true
}

tasks.register("buildAll") {
    group = "Publish"
//    dependsOn("buildDeb")
//    dependsOn("buildRpm")
    dependsOn(tasks.named("distTar"))
}

distributions {
    main {
        // Include the "packer" directory in the distribution
        contents {
            from("packer") {
                into("packer")
            }
        }
    }
}

tasks.distTar {
    compression = Compression.GZIP
    archiveExtension.set("tar.gz")
}

tasks.assemble {
    mustRunAfter(tasks.clean)
}

// Kover code coverage configuration
kover {
    reports {
        filters {
            excludes {
                // Exclude test classes
                classes(
                    "*Test",
                    "*Test\$*",
                    "*.test.*",
                    "*Mock*",
                    // Generated classes
                    "*\$\$*",
                    "*_Factory",
                    "*_Impl",
                    "*.BuildConfig",
                )

                // Exclude test packages
                packages(
                    "*.test",
                    "*.mock",
                )
            }
        }

        // Configure verification rules
        // Current coverage: Line 34.08%, Branch 16.54%
        // TODO: Gradually increase these thresholds as more tests are added
        verify {
            rule("Minimal line coverage") {
                disabled = false

                bound {
                    minValue = 40 // Start at 30%, gradually increase
                    coverageUnits = kotlinx.kover.gradle.plugin.dsl.CoverageUnit.LINE
                    aggregationForGroup = kotlinx.kover.gradle.plugin.dsl.AggregationType.COVERED_PERCENTAGE
                }
            }

            rule("Minimal branch coverage") {
                disabled = false

                bound {
                    minValue = 15 // Start at 15%, gradually increase
                    coverageUnits = kotlinx.kover.gradle.plugin.dsl.CoverageUnit.BRANCH
                    aggregationForGroup = kotlinx.kover.gradle.plugin.dsl.AggregationType.COVERED_PERCENTAGE
                }
            }
        }
    }
}
