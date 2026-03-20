plugins {
    kotlin("jvm")
}

group = "org.quill"
version = rootProject.version

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
