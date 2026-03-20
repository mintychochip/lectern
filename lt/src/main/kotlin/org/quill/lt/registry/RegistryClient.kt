package org.quill.lt.registry

import org.quill.lt.model.Semver
import org.quill.lt.model.SemverRange
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * Represents a package version available in the registry.
 */
data class RegistryPackageVersion(
    val version: String,
    val url: String,
    val dependencies: Map<String, String>
)

/**
 * Represents a package's versions in the registry.
 */
data class RegistryPackage(
    val name: String,
    val versions: Map<String, RegistryPackageVersion>
)

/**
 * Fetches package metadata from a Quill registry.
 */
class RegistryClient(
    private val registryUrl: String
) {
    private val httpClient = HttpClient.newHttpClient()

    /**
     * Fetch the full registry index (list of all packages and versions).
     */
    fun fetchIndex(): Map<String, RegistryPackage> {
        val url = "$registryUrl/index.json"
        val request = HttpRequest.newBuilder(URI(url)).build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) {
            throw RuntimeException("Failed to fetch registry index: ${response.statusCode()}")
        }

        val json = response.body()
        return parseIndexJson(json)
    }

    /**
     * Find the best matching version for a package given a semver range.
     * Returns null if no version satisfies the range.
     */
    fun findBestMatch(pkgName: String, range: SemverRange): RegistryPackageVersion? {
        val index = fetchIndex()
        val pkg = index[pkgName] ?: return null

        val candidate = pkg.versions.keys
            .mapNotNull { version ->
                try {
                    Semver.parse(version) to version
                } catch (e: Exception) {
                    null
                }
            }
            .filter { (semver, _) -> range.matches(semver) }
            .maxByOrNull { it.first }
            ?.second

        return candidate?.let { pkg.versions[it] }
    }

    @PublishedApi
    internal fun parseIndexJson(json: String): Map<String, RegistryPackage> {
        val result = mutableMapOf<String, RegistryPackage>()
        parseJsonObject(json)?.forEach { (pkgName, value) ->
            val versions = mutableMapOf<String, RegistryPackageVersion>()
            (value as? Map<*, *>)?.forEach { (ver, verData) ->
                val verStr = ver.toString()
                val verMap = (verData as? Map<*, *>) ?: return@forEach
                versions[verStr] = RegistryPackageVersion(
                    version = verStr,
                    url = verMap["url"]?.toString() ?: "",
                    dependencies = (verMap["dependencies"] as? Map<*, *>)?.mapKeys { it.key.toString() }?.mapValues { it.value?.toString() ?: "" }
                        ?: emptyMap()
                )
            }
            result[pkgName] = RegistryPackage(pkgName, versions)
        }
        return result
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseJsonObject(json: String): Map<String, Any>? {
        val map = mutableMapOf<String, Any>()
        var i = skipWhitespace(json, 0)
        if (json[i] != '{') return null
        i++
        while (i < json.length) {
            i = skipWhitespace(json, i)
            if (json[i] == '}') break
            val key = parseString(json, i)
            i = key.second
            i = skipWhitespace(json, i)
            if (json[i] != ':') return null
            i++
            val value = parseValue(json, i)
            map[key.first] = value.first
            i = skipWhitespace(json, value.second)
            if (json[i] == ',') i++
        }
        return (map["packages"] as? Map<String, Any>) ?: map
    }

    private fun parseValue(json: String, i: Int): Pair<Any, Int> {
        val c = json[i]
        return when {
            c == '{' -> {
                var depth = 1
                var j = i + 1
                while (j < json.length && depth > 0) {
                    if (json[j] == '{') depth++
                    else if (json[j] == '}') depth--
                    j++
                }
                val obj = mutableMapOf<String, Any>()
                var k = i + 1
                while (k < j - 1) {
                    k = skipWhitespace(json, k)
                    if (json[k] == '}') break
                    val key = parseString(json, k)
                    k = skipWhitespace(json, key.second)
                    k++ // skip :
                    val value = parseValue(json, k)
                    obj[key.first] = value.first
                    k = skipWhitespace(json, value.second)
                    if (json[k] == ',') k++
                }
                obj to j
            }
            c == '"' -> {
                val s = parseString(json, i)
                s.first as Any to s.second
            }
            else -> throw IllegalArgumentException("Unexpected JSON char at $i: $c")
        }
    }

    private fun parseString(json: String, i: Int): Pair<String, Int> {
        var j = i + 1
        while (j < json.length && json[j] != '"') {
            if (json[j] == '\\') j++ // skip escape
            j++
        }
        val str = json.substring(i + 1, j)
        return str.replace("\\\"", "\"").replace("\\\\", "\\") to j + 1
    }

    private fun skipWhitespace(json: String, i: Int): Int {
        var j = i
        while (j < json.length && json[j] in " \t\n\r") j++
        return j
    }
}