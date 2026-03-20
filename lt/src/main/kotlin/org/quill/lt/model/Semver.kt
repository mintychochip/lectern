package org.quill.lt.model

data class Semver(
    val major: Int,
    val minor: Int,
    val patch: Int
) : Comparable<Semver> {

    override fun compareTo(other: Semver): Int {
        return when {
            major != other.major -> major - other.major
            minor != other.minor -> minor - other.minor
            else -> patch - other.patch
        }
    }

    override fun toString(): String = "$major.$minor.$patch"
}

class SemverRange(val range: String) {
    private val prefix: Char? = if (range.startsWith("^") || range.startsWith("~")) range[0] else null
    private val versionStr: String = if (prefix != null) range.substring(1) else range
    private val isWildcardMajor: Boolean = versionStr == "*"
    private val isWildcardMinor: Boolean = versionStr.endsWith(".*")

    private val baseVersion: Semver? = if (!isWildcardMajor && !isWildcardMinor) {
        parseVersion(versionStr)
    } else null

    private fun parseVersion(v: String): Semver {
        val parts = v.split(".").map { it.toInt() }
        return Semver(parts.getOrElse(0) { 0 }, parts.getOrElse(1) { 0 }, parts.getOrElse(2) { 0 })
    }

    fun matches(version: Semver): Boolean {
        return when {
            isWildcardMajor -> true
            isWildcardMinor -> {
                val major = versionStr.removeSuffix(".*").toInt()
                version.major == major
            }
            prefix == '^' -> {
                val base = baseVersion!!
                version.major == base.major &&
                    version.minor >= base.minor &&
                    version.patch >= base.patch
            }
            prefix == '~' -> {
                val base = baseVersion!!
                version.major == base.major &&
                    version.minor >= base.minor &&
                    version.minor <= base.minor + 1
            }
            else -> {
                val base = baseVersion!!
                version == base
            }
        }
    }

    override fun toString(): String = range
}
