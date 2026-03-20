plugins {
    kotlin("jvm") version "2.2.21"
    application
}

group = "org.quill"
version = "1.0-SNAPSHOT"

application {
    mainClass.set("org.quill.MainKt")
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    testImplementation(kotlin("test"))
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    implementation("org.xerial:sqlite-jdbc:3.45.1.0")
    implementation("org.yaml:snakeyaml:2.2")
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}