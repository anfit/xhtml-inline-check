import org.gradle.api.plugins.ApplicationPlugin
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.Sync

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
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
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
}

val sourceSets = the<SourceSetContainer>()

tasks.register<JavaExec>("runFaceletsVerify") {
    group = ApplicationPlugin.APPLICATION_GROUP
    description = "Runs the facelets-verify CLI entrypoint."
    classpath = sourceSets.named("main").get().runtimeClasspath
    mainClass = faceletsVerifyMainClass
}
