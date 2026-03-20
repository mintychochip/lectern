package org.quill.lt.util

import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPInputStream

/**
 * File system utilities for the package manager.
 */
object FileUtils {

    /**
     * Extract a gzip-compressed tar (.tar.gz) to a destination directory.
     * Uses Apache Commons Compress for proper tar parsing.
     */
    fun extractTarGz(inputStream: InputStream, destDir: Path) {
        Files.createDirectories(destDir)
        GZIPInputStream(inputStream).use { gzip ->
            val tar = org.apache.commons.compress.archivers.tar.TarArchiveInputStream(gzip)
            tar.use {
                var entry = tar.nextTarEntry
                while (entry != null) {
                    val target = destDir.resolve(entry.name)
                    if (entry.isDirectory) {
                        Files.createDirectories(target)
                    } else {
                        Files.createDirectories(target.parent)
                        Files.copy(tar, target)
                    }
                    entry = tar.nextTarEntry
                }
            }
        }
    }

    /**
     * Download a file from a URL and save to destination.
     */
    fun downloadFile(url: String, dest: Path): Path {
        val connection = java.net.URL(url).openConnection()
        connection.connect()
        Files.createDirectories(dest.parent)
        connection.getInputStream().use { input ->
            Files.copy(input, dest)
        }
        return dest
    }

    /**
     * Delete a directory and all its contents recursively.
     */
    fun deleteDirectory(dir: Path) {
        if (!Files.exists(dir)) return
        Files.walk(dir)
            .sorted(java.util.Comparator.reverseOrder())
            .forEach { Files.deleteIfExists(it) }
    }

    /**
     * Ensure a directory exists (create if missing).
     */
    fun ensureDir(dir: Path) {
        Files.createDirectories(dir)
    }
}