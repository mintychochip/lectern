package org.quill.lt.commands

import org.quill.lt.model.PackageManifest
import org.quill.lt.model.SemverRange
import org.quill.lt.registry.RegistryClient
import org.quill.lt.util.FileUtils
import org.quill.lt.util.TomlParser
import java.nio.file.Files
import java.nio.file.Path

/**
 * lt add <pkg>[@version] — install a package to packages/.
 */
class AddCommand(
    private val projectDir: Path,
    private val registryUrl: String = System.getenv("QUILL_REGISTRY") ?: "https://packages.quill-lang.org"
) {

    fun run(pkgSpec: String) {
        val (pkgName, version) = parseSpec(pkgSpec)
        val range = if (version != null) {
            SemverRange(version)
        } else {
            SemverRange(">=0.0.0") // any version
        }

        val quillToml = projectDir.resolve("quill.toml")
        val manifest = if (Files.exists(quillToml)) {
            TomlParser.read(quillToml)
        } else {
            val autoName = projectDir.fileName.toString()
            PackageManifest(autoName, "0.1.0", "main", emptyMap())
        }

        if (pkgName in manifest.dependencies) {
            println("$pkgName is already in dependencies.")
            return
        }

        val client = RegistryClient(registryUrl)
        val pkgVersion = client.findBestMatch(pkgName, range)
        if (pkgVersion == null) {
            println("No version of $pkgName satisfies $version")
            if (version == null) println("(use `lt add $pkgName@^1.0.0` to specify a version)")
            return
        }

        // Download and extract to packages/
        val packagesDir = projectDir.resolve("packages")
        val pkgDir = packagesDir.resolve(pkgName.replace("/", "-"))

        if (Files.exists(pkgDir)) {
            println("$pkgName is already installed.")
            return
        }

        println("Installing $pkgName v${pkgVersion.version}...")

        val tempFile = projectDir.resolve(".quill-cache/.tmp-${System.currentTimeMillis()}.tar.gz")
        FileUtils.ensureDir(tempFile.parent)
        FileUtils.downloadFile(pkgVersion.url, tempFile)
        FileUtils.extractTarGz(tempFile.toUri().toURL().openStream(), pkgDir)
        Files.deleteIfExists(tempFile)

        // Update quill.toml
        val updatedManifest = manifest.copy(
            dependencies = manifest.dependencies + (pkgName to (version ?: "^${pkgVersion.version}"))
        )
        TomlParser.write(updatedManifest, quillToml)

        println("Installed $pkgName v${pkgVersion.version} → packages/$pkgName")
    }

    private fun parseSpec(spec: String): Pair<String, String?> {
        val at = spec.lastIndexOf('@')
        return if (at >= 0) {
            spec.substring(0, at) to spec.substring(at + 1)
        } else {
            spec to null
        }
    }
}
