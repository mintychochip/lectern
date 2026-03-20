package org.quill

/**
 * Interface provided by the runtime host to scripts.
 * Quill scripts call log() and print() which delegate to this context.
 */
interface QuillContext {
    /** Info-level log output — typically server console */
    fun log(message: String)

    /** Player/user-facing output — routed to command sender */
    fun print(message: String)
}
