package org.quill.lt.model

data class Lockfile(
    val packages: Map<String, LockfileEntry>
)

data class LockfileEntry(
    val version: String,
    val resolutionSource: String
)
