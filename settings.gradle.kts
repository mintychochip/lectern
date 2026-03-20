plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "quill"

include("quill-core")
include("quill-paper")
include("quill-intellij")
include("quill-vscode")