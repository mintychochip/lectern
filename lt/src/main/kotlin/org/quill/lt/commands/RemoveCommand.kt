package org.quill.lt.commands

import org.quill.lt.util.FileUtils
import org.quill.lt.util.TomlParser
import java.nio.file.Files
import java.nio.file.Path

/**
 * lt remove <pkg> — uninstall a package.
 */
class RemoveCommand(private val projectDir: Path) {

    fun run(pkgName: String) {
        val quillToml = projectDir.resolve("quill.toml")
        if (!Files.exists(quillToml)) {
            println("No quill.toml found.")
            return
        }

        val manifest = TomlParser.read(quillToml)
        if (pkgName !in manifest.dependencies) {
            println("$pkgName is not in dependencies.")
            return
        }

        // Remove from packages/
        val pkgDir = projectDir.resolve("packages").resolve(pkgName.replace("/", "-"))
        if (Files.exists(pkgDir)) {
            FileUtils.deleteDirectory(pkgDir)
            println("Removed packages/$pkgName")
        }

        // Update quill.toml
        val updatedManifest = manifest.copy(
            dependencies = manifest.dependencies - pkgName
        )
        TomlParser.write(updatedManifest, quillToml)
        println("Removed $pkgName from dependencies.")
    }
}
