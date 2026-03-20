package org.quill.lt.commands

import org.quill.lt.model.PackageManifest
import org.quill.lt.util.TomlParser
import java.nio.file.Files
import java.nio.file.Path

/**
 * lt init — create quill.toml in an existing project directory.
 */
class InitCommand(private val projectDir: Path) {

    fun run(name: String?, version: String = "0.1.0", main: String = "main") {
        val quillToml = projectDir.resolve("quill.toml")

        if (Files.exists(quillToml)) {
            println("quill.toml already exists. Use `lt add` or `lt remove` to modify it.")
            return
        }

        val resolvedName = name ?: projectDir.fileName.toString().lowercase()
        val manifest = PackageManifest(
            name = resolvedName,
            version = version,
            entry = main,
            dependencies = emptyMap()
        )

        TomlParser.write(manifest, quillToml)
        println("Created quill.toml: $resolvedName v$version")
    }
}
