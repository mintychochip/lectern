package org.quill.lt.commands

import org.quill.lt.model.PackageManifest
import org.quill.lt.util.TomlParser
import java.nio.file.Files
import java.nio.file.Path

/**
 * lt new <name> — scaffold a new package.
 */
class NewCommand(private val projectDir: Path) {

    fun run(name: String) {
        val targetDir = projectDir.resolve(name)
        if (Files.exists(targetDir)) {
            println("Directory already exists: $name/")
            return
        }

        Files.createDirectories(targetDir)

        val manifest = PackageManifest(
            name = name,
            version = "0.1.0",
            entry = "mod",
            dependencies = emptyMap()
        )

        val quillToml = targetDir.resolve("quill.toml")
        TomlParser.write(manifest, quillToml)

        val mainFile = targetDir.resolve("mod.quill")
        mainFile.toFile().writeText(
            """
            // $name v0.1.0
            // Entry point: ${manifest.entry}.quill

            """.trimIndent()
        )

        println("Created package: $name/")
        println("  quill.toml")
        println("  mod.quill")
    }
}
