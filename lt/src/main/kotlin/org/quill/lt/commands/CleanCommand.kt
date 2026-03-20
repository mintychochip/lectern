package org.quill.lt.commands

import org.quill.lt.util.FileUtils
import java.nio.file.Files
import java.nio.file.Path

/**
 * lt clean — remove .quill-cache/.
 */
class CleanCommand(private val projectDir: Path) {

    fun run() {
        val cacheDir = projectDir.resolve(".quill-cache")
        if (!Files.exists(cacheDir)) {
            println("Nothing to clean (.quill-cache/ does not exist).")
            return
        }
        FileUtils.deleteDirectory(cacheDir)
        println("Removed .quill-cache/")
    }
}
