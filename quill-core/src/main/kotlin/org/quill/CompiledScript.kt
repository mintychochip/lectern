package org.quill

import org.quill.lang.Chunk
import org.quill.lang.VM

/**
 * A compiled Quill script, ready for execution.
 * Scripts are identified by name (typically the filename).
 */
class CompiledScript(
    val name: String,
    private val chunk: Chunk
) {
    /** Maximum instructions before timeout (default 10M). Set by ScriptManager. */
    var instructionLimit: Long = 10_000_000L

    /**
     * Execute this script with the given context.
     * Runtime errors (infinite loop timeout, undefined variable) are thrown as exceptions.
     * The host should wrap this in a try-catch and format errors for the user.
     */
    fun execute(context: QuillContext) {
        val vm = VM(context)
        vm.instructionLimit = instructionLimit
        vm.execute(chunk)
    }
}
