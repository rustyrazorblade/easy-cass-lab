

extra.apply {
    set("logback_version", "1.5.18")
    set("jackson_dataformat_version", "2.15.2")
    set("jackson_kotlin_version", "2.9.+")
    set("jupiter_version", "5.5.2")
    set("assertj_version", "3.11.1")
    set("jcommander_version", "1.82")
    set("kotlin_version", "2.1.21")
    set("ktor_version", "3.1.3")
}

buildscript {
    repositories {
        mavenCentral()
    }
}

plugins {
    java
    idea
    application
    id("org.jetbrains.kotlin.jvm") version "2.1.21"
    id("com.github.johnrengelman.shadow")  version "8.1.1"
    id("com.github.ben-manes.versions") version "0.52.0"

}

group = "com.rustyrazorblade"

java {
    sourceCompatibility = JavaVersion.VERSION_17
}

application {
    applicationName = "easy-cass-lab"
    mainClass.set("com.rustyrazorblade.easycasslab.MainKt")
    applicationDefaultJvmArgs = listOf(
        "-Deasycasslab.ami.name=rustyrazorblade/images/easy-cass-lab-cassandra-amd64-$version",
        "-Deasycasslab.ami.owner=081145431955",
        "-Deasycasslab.version=$version"

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

    implementation("ch.qos.logback:logback-classic:${project.extra["logback_version"]}")
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.7")

    implementation("org.jetbrains.kotlin:kotlin-stdlib:${project.extra["kotlin_version"]}")

    implementation("com.beust:jcommander:${project.extra["jcommander_version"]}")
    implementation("com.google.guava:guava:33.4.8-jre")

    // for finding resources
    // https://mvnrepository.com/artifact/org.reflections/reflections
    implementation("org.reflections:reflections:0.10.2")

    // https://mvnrepository.com/artifact/commons-io/commons-io
    implementation("commons-io:commons-io:2.7")

    // https://mvnrepository.com/artifact/com.github.docker-java/docker-java
    implementation("com.github.docker-java:docker-java:3.5.1")

    // https://mvnrepository.com/artifact/com.github.docker-java/docker-java-transport-httpclient5
    implementation("com.github.docker-java:docker-java-transport-httpclient5:3.5.1")

    implementation(project(":core"))

    implementation("software.amazon.awssdk:ec2:2.20.145")

    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:${project.extra["jackson_dataformat_version"]}")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:${project.extra["jackson_kotlin_version"]}")

    // https://mvnrepository.com/artifact/org.apache.sshd/sshd-core
    implementation("org.apache.sshd:sshd-core:2.12.1")
    implementation("org.apache.sshd:sshd-scp:2.12.1")

    implementation("org.jline:jline:3.25.0")
    implementation("com.github.ajalt:mordant:1.2.1")

    implementation("io.ktor:ktor-server-core:${project.extra["ktor_version"]}")
    implementation("io.ktor:ktor-server-netty:${project.extra["ktor_version"]}")
    implementation("io.ktor:ktor-server-content-negotiation:${project.extra["ktor_version"]}")
    implementation("io.ktor:ktor-serialization-jackson:${project.extra["ktor_version"]}")

    testImplementation("org.junit.jupiter:junit-jupiter-engine:${project.extra["jupiter_version"]}")

    // https://mvnrepository.com/artifact/org.assertj/assertj-core
    testImplementation("org.assertj:assertj-core:${project.extra["assertj_version"]}")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
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

val integrationTestCompile by configurations.creating {
    extendsFrom(configurations["testImplementation"])
}

val integrationTestRuntime by configurations.creating {
    extendsFrom(configurations["testRuntimeOnly"])
}

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

