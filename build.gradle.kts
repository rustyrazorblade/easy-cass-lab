

extra.apply {
    set("log4j_api_version", "1.2.0")
    set("log4j_core_version", "2.20.0")
    set("slf4j_version", "2.11.2")
    set("jackson_dataformat_version", "2.15.2")
    set("jackson_kotlin_version", "2.9.+")
    set("jupiter_version", "5.5.2")
    set("assertj_version", "3.11.1")
    set("jcommander_version", "1.82")
}

buildscript {
    extra.apply {
        set("kotlin_version", "1.9.20")
        set("docker_compose_version", "0.9.4")
    }

    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${rootProject.extra["kotlin_version"]}")
        classpath("com.avast.gradle:gradle-docker-compose-plugin:${rootProject.extra["docker_compose_version"]}")
    }
}

plugins {
    java
    idea
    application
    id("com.bmuschko.docker-remote-api") version "9.4.0"
    id("org.jetbrains.kotlin.jvm") version "1.9.20"
    id("com.github.johnrengelman.shadow")  version "8.1.1"
}

group = "com.rustyrazorblade"

java {
    sourceCompatibility = JavaVersion.VERSION_11
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

// In this section you declare where to find the dependencies of your project
allprojects {
    repositories {
        mavenCentral()
    }
}

// In this section you declare the dependencies for your production and test code
dependencies {

    implementation("org.apache.logging.log4j:log4j-api-kotlin:${project.extra["log4j_api_version"]}")
    implementation("org.apache.logging.log4j:log4j-core:${project.extra["log4j_core_version"]}")
    implementation("org.apache.logging.log4j:log4j-slf4j18-impl:${project.extra["slf4j_version"]}")

    implementation("org.jetbrains.kotlin:kotlin-stdlib:${project.extra["kotlin_version"]}")

    implementation("com.beust:jcommander:${project.extra["jcommander_version"]}")
    implementation("com.google.guava:guava:30.0-jre")

    // for finding resources
    // https://mvnrepository.com/artifact/org.reflections/reflections
    implementation("org.reflections:reflections:0.9.11")

    // https://mvnrepository.com/artifact/commons-io/commons-io
    implementation("commons-io:commons-io:2.7")

    // https://mvnrepository.com/artifact/com.github.docker-java/docker-java
    implementation("com.github.docker-java:docker-java:3.4.0")

    // https://mvnrepository.com/artifact/com.github.docker-java/docker-java-transport-httpclient5
    implementation("com.github.docker-java:docker-java-transport-httpclient5:3.3.4")

    implementation(project(":core"))

    implementation("software.amazon.awssdk:ec2:2.20.145")

    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:${project.extra["jackson_dataformat_version"]}")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:${project.extra["jackson_kotlin_version"]}")

    // https://mvnrepository.com/artifact/org.apache.sshd/sshd-core
    implementation("org.apache.sshd:sshd-core:2.12.1")
    implementation("org.apache.sshd:sshd-scp:2.12.1")

    implementation("org.jline:jline:3.25.0")

    testImplementation("org.junit.jupiter:junit-jupiter-engine:${project.extra["jupiter_version"]}")

    // https://mvnrepository.com/artifact/org.assertj/assertj-core
    testImplementation("org.assertj:assertj-core:${project.extra["assertj_version"]}")

    implementation("com.github.ajalt:mordant:1.2.1")

    testImplementation("io.mockk:mockk:1.9.3")
}


kotlin {
    jvmToolchain(11)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xallow-result-return-type")
    }
}

tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>("compileTestKotlin") {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xallow-result-return-type")
    }
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

tasks.register<Exec>("packer") {
    group = "Build"
    workingDir = file("packer")
    commandLine("packer", "build", "base.pkr.hcl")
    commandLine("packer", "build", "cassandra.pkr.hcl")
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

