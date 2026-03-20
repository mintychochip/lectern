package org.quill.lt.model

data class PackageManifest(
    val name: String,
    val version: String,
    val entry: String,
    val dependencies: Map<String, String> = emptyMap()
)
