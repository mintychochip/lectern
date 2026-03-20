package org.quill.lt.commands

import org.quill.lt.model.Lockfile
import org.quill.lt.model.LockfileEntry
import org.quill.lt.model.SemverRange
import org.quill.lt.registry.RegistryClient
import org.quill.lt.util.FileUtils
import org.quill.lt.util.TomlParser
import java.nio.file.Files
import java.nio.file.Path

/**
 * lt install — resolve dependencies and populate packages/.
 */
class InstallCommand(
    private val projectDir: Path,
    private val registryUrl: String = System.getenv("QUILL_REGISTRY") ?: "https://packages.quill-lang.org"
) {

    fun run() {
        val quillToml = projectDir.resolve("quill.toml")
        if (!Files.exists(quillToml)) {
            println("No quill.toml found. Run `lt init` or `lt new` first.")
            return
        }

        val manifest = TomlParser.read(quillToml)
        val client = RegistryClient(registryUrl)

        println("Resolving dependencies for ${manifest.name}...")

        val lockedPkgs = mutableMapOf<String, LockfileEntry>()

        for ((depName, depRangeStr) in manifest.dependencies) {
            val range = SemverRange(depRangeStr)
            val pkgVersion = client.findBestMatch(depName, range)

            if (pkgVersion == null) {
                println("ERROR: No version of $depName satisfies $depRangeStr")
                return
            }

            val pkgDir = projectDir.resolve("packages").resolve(depName.replace("/", "-"))
            if (!Files.exists(pkgDir)) {
                println("Installing $depName v${pkgVersion.version}...")
                val tempFile = projectDir.resolve(".quill-cache/.tmp-${System.currentTimeMillis()}.tar.gz")
                FileUtils.ensureDir(tempFile.parent)
                FileUtils.downloadFile(pkgVersion.url, tempFile)
                FileUtils.extractTarGz(tempFile.toUri().toURL().openStream(), pkgDir)
                Files.deleteIfExists(tempFile)
            }

            lockedPkgs["$depName@${pkgVersion.version}"] = LockfileEntry(
                version = pkgVersion.version,
                resolutionSource = pkgVersion.url
            )
        }

        // Write quill.lock
        val lockfile = Lockfile(packages = lockedPkgs)
        writeLockfile(lockfile, projectDir.resolve("quill.lock"))

        println("Installed ${lockedPkgs.size} package(s).")
        println("Run `lt ls` to see installed packages.")
    }

    private fun writeLockfile(lockfile: Lockfile, path: Path) {
        val content = buildString {
            appendLine("{")
            appendLine("  \"version\": 1,")
            appendLine("  \"registry\": \"$registryUrl\",")
            appendLine("  \"packages\": {")
            val entries = lockfile.packages.entries.toList()
            entries.forEachIndexed { idx, (key, pkg) ->
                val last = idx == entries.size - 1
                appendLine("    \"$key\": {")
                appendLine("      \"version\": \"${pkg.version}\",")
                appendLine("      \"resolutionSource\": \"${pkg.resolutionSource}\"")
                append("    }${if (last) "" else ","}")
                if (!last) appendLine()
            }
            appendLine()
            appendLine("  }")
            appendLine("}")
        }
        path.toFile().writeText(content)
    }
}
