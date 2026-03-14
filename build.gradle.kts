plugins {
    kotlin("jvm") version "2.1.10"
    application
}

group = "dev.xhtmlinlinecheck"
version = "0.1.0-SNAPSHOT"
description = "Static verifier for JSF Facelets XHTML include-inlining refactors"

val faceletsVerifyMainClass = "dev.xhtmlinlinecheck.cli.MainKt"
val faceletsVerifyApplicationName = "facelets-verify"

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

tasks.distZip {
    archiveBaseName = faceletsVerifyApplicationName
}

tasks.distTar {
    archiveBaseName = faceletsVerifyApplicationName
}

tasks.test {
    useJUnitPlatform()
}
