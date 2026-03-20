plugins {
    kotlin("jvm") version "2.2.21"
    application
}

group = "org.quill"
version = "0.1.0"

kotlin {
    jvmToolchain(21)
}

application {
    mainClass = "org.quill.lt.MainKt"
}

repositories {
    mavenCentral()
}

dependencies {
    // Quills's standard library
    implementation(project(":"))

    // TOML parsing
    implementation("com.moandjiezana.toml:toml4j:0.7.2")

    // Tar.gz extraction
    implementation("org.apache.commons:commons-compress:1.26.0")

    testImplementation(kotlin("test"))
}
