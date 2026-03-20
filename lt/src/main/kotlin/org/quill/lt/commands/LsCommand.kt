package org.quill.lt.commands

import org.quill.lt.util.TomlParser
import java.nio.file.Files
import java.nio.file.Path

/**
 * lt ls — list installed packages.
 */
class LsCommand(private val projectDir: Path) {

    fun run() {
        val packagesDir = projectDir.resolve("packages")
        if (!Files.exists(packagesDir)) {
            println("No packages installed.")
            return
        }

        val entries = Files.list(packagesDir)
            .filter { it.toFile().isDirectory }
            .map { dir ->
                val quillToml = dir.resolve("quill.toml")
                if (Files.exists(quillToml)) {
                    try {
                        val manifest = TomlParser.read(quillToml)
                        "${manifest.name} v${manifest.version}"
                    } catch (e: Exception) {
                        "${dir.fileName} (invalid quill.toml)"
                    }
                } else {
                    "${dir.fileName} (no manifest)"
                }
            }
            .toList()
            .sorted()

        if (entries.isEmpty()) {
            println("No packages installed.")
        } else {
            println("Installed packages (${entries.size}):")
            entries.forEach { println("  $it") }
        }
    }
}
