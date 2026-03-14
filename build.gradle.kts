plugins {
    kotlin("jvm") version "2.1.10"
    application
}

group = "dev.xhtmlinlinecheck"
version = "0.1.0-SNAPSHOT"

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
    mainClass = "dev.xhtmlinlinecheck.cli.MainKt"
}

tasks.test {
    useJUnitPlatform()
}
