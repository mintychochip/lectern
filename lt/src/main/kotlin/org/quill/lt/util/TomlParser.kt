package org.quill.lt.util

import com.moandjiezana.toml.Toml
import org.quill.lt.model.PackageManifest
import java.nio.file.Path

/**
 * Reads and writes quill.toml manifest files.
 */
object TomlParser {

    /**
     * Read a quill.toml file and return a PackageManifest.
     */
    fun read(path: Path): PackageManifest {
        val toml = Toml().read(path.toFile())

        val pkg = toml.getTable("package")
            ?: throw IllegalArgumentException("quill.toml is missing [package] section")

        val name = pkg.getString("name")
            ?: throw IllegalArgumentException("quill.toml missing package.name")

        return PackageManifest(
            name = name,
            version = pkg.getString("version") ?: "0.0.0",
            entry = pkg.getString("entry") ?: "main",
            dependencies = pkg.getTable("dependencies")?.entrySet()
                ?.associate { it.key to (it.value as? String ?: "") }
                ?: emptyMap()
        )
    }

    /**
     * Write a PackageManifest to a quill.toml file.
     */
    fun write(manifest: PackageManifest, path: Path) {
        val content = buildString {
            appendLine("[package]")
            appendLine("name = \"${manifest.name}\"")
            appendLine("version = \"${manifest.version}\"")
            appendLine("entry = \"${manifest.entry}\"")
            if (manifest.dependencies.isNotEmpty()) {
                appendLine()
                appendLine("[dependencies]")
                for ((depName, depVer) in manifest.dependencies) {
                    appendLine("$depName = \"$depVer\"")
                }
            }
        }
        path.parent?.toFile()?.mkdirs()
        path.toFile().writeText(content)
    }
}