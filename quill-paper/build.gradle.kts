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
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    implementation(project(":quill-core"))
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    testImplementation("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    testImplementation("com.github.seeseemelk:MockBukkit-Paper:1.103.0")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
