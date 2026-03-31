import org.gradle.api.plugins.ApplicationPlugin
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.Sync
import org.gradle.jvm.tasks.Jar

plugins {
    kotlin("jvm") version "2.1.10"
    application
}

group = "dev.xhtmlinlinecheck"
version = "0.1.0-SNAPSHOT"
description = "Static verifier for JSF Facelets XHTML include-inlining refactors"

val faceletsVerifyMainClass = "dev.xhtmlinlinecheck.cli.MainKt"
val faceletsVerifyApplicationName = "facelets-verify"
val faceletsVerifyInstallDir = layout.buildDirectory.dir(faceletsVerifyApplicationName)

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.github.ajalt.clikt:clikt:4.4.0")
    implementation(platform("com.fasterxml.jackson:jackson-bom:2.17.2"))
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.woodstox:woodstox-core:6.6.2")

    testImplementation(kotlin("test"))
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.junit.jupiter:junit-jupiter-params")
    testImplementation("org.assertj:assertj-core:3.26.3")
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass = faceletsVerifyMainClass
    applicationName = faceletsVerifyApplicationName
}

distributions {
    main {
        distributionBaseName = faceletsVerifyApplicationName
    }
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = faceletsVerifyMainClass
    }
}

tasks.register<Jar>("fatJar") {
    group = "build"
    description = "Builds an executable jar that bundles runtime dependencies."
    archiveClassifier = "all"
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = faceletsVerifyMainClass
    }
    from(sourceSets.named("main").get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().map { dependency ->
            if (dependency.isDirectory) dependency else zipTree(dependency)
        }
    })
}

tasks.named<Sync>("installDist") {
    into(faceletsVerifyInstallDir)
}

tasks.distZip {
    archiveBaseName = faceletsVerifyApplicationName
}

tasks.distTar {
    archiveBaseName = faceletsVerifyApplicationName
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
}

val sourceSets = the<SourceSetContainer>()

tasks.register<JavaExec>("runFaceletsVerify") {
    group = ApplicationPlugin.APPLICATION_GROUP
    description = "Runs the facelets-verify CLI entrypoint."
    classpath = sourceSets.named("main").get().runtimeClasspath
    mainClass = faceletsVerifyMainClass
}
