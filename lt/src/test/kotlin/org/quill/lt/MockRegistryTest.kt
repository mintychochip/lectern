package org.quill.lt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.quill.lt.model.Semver
import org.quill.lt.model.SemverRange
import org.quill.lt.registry.RegistryClient
import org.quill.lt.registry.RegistryPackage
import org.quill.lt.registry.RegistryPackageVersion

class MockRegistryTest {
    @Test
    fun `parseIndexJson handles empty registry`() {
        val client = RegistryClient("http://localhost:8080")
        val index = client.parseIndexJson("{}")
        assertTrue(index.isEmpty())
    }

    @Test
    fun `parseIndexJson parses single package with versions`() {
        val client = RegistryClient("http://localhost:8080")
        val json = """{"mylib":{"1.0.0":{"url":"https://example.com/mylib.tar.gz","dependencies":{}}}}"""
        val index = client.parseIndexJson(json)
        assertEquals(1, index.size)
        val pkg = index["mylib"]
        assertNotNull(pkg)
        assertEquals("mylib", pkg.name)
        assertEquals(1, pkg.versions.size)
        val ver = pkg.versions["1.0.0"]
        assertNotNull(ver)
        assertEquals("1.0.0", ver.version)
        assertEquals("https://example.com/mylib.tar.gz", ver.url)
        assertTrue(ver.dependencies.isEmpty())
    }

    @Test
    fun `parseIndexJson parses package with dependencies`() {
        val client = RegistryClient("http://localhost:8080")
        val json = """{"express":{"4.17.1":{"url":"https://example.com/express.tar.gz","dependencies":{"body-parser":"1.19.0","cookie":"0.4.0"}}}}"""
        val index = client.parseIndexJson(json)
        val express = index["express"]
        assertNotNull(express)
        val ver = express.versions["4.17.1"]
        assertNotNull(ver)
        assertEquals(2, ver.dependencies.size)
        assertEquals("1.19.0", ver.dependencies["body-parser"])
        assertEquals("0.4.0", ver.dependencies["cookie"])
    }

    @Test
    fun `parseIndexJson parses multiple packages`() {
        val client = RegistryClient("http://localhost:8080")
        val json = """{"foo":{"1.0.0":{"url":"https://example.com/foo.tar.gz","dependencies":{}}},"bar":{"2.3.4":{"url":"https://example.com/bar.tar.gz","dependencies":{"foo":"1.0.0"}}}}"""
        val index = client.parseIndexJson(json)
        assertEquals(2, index.size)
        assertNotNull(index["foo"])
        assertNotNull(index["bar"])
    }

    @Test
    fun `SemverRange caret matches within same major version`() {
        val range = SemverRange("^1.2.3")
        assertTrue(range.matches(Semver(1, 2, 3)))
        // Note: patch must be >= base.patch (3) even when minor > base.minor
        assertTrue(range.matches(Semver(1, 5, 5)))
        assertTrue(range.matches(Semver(1, 9, 9)))
        // minor must be >= base.minor (2), so 1.0.0 doesn't match
        assertFalse(range.matches(Semver(1, 0, 0)))
        assertFalse(range.matches(Semver(2, 0, 0)))
        assertFalse(range.matches(Semver(0, 9, 9)))
    }

    @Test
    fun `SemverRange tilde matches within minor range`() {
        val range = SemverRange("~1.2.3")
        assertTrue(range.matches(Semver(1, 2, 3)))
        assertTrue(range.matches(Semver(1, 2, 9)))
        assertTrue(range.matches(Semver(1, 3, 0)))
        assertFalse(range.matches(Semver(1, 4, 0)))
        assertFalse(range.matches(Semver(2, 0, 0)))
    }

    @Test
    fun `SemverRange exact matches exact version only`() {
        val range = SemverRange("1.2.3")
        assertTrue(range.matches(Semver(1, 2, 3)))
        assertFalse(range.matches(Semver(1, 2, 4)))
        assertFalse(range.matches(Semver(1, 3, 0)))
        assertFalse(range.matches(Semver(2, 0, 0)))
    }

    @Test
    fun `SemverRange wildcard major matches any version`() {
        val range = SemverRange("*")
        assertTrue(range.matches(Semver(0, 0, 0)))
        assertTrue(range.matches(Semver(1, 2, 3)))
        assertTrue(range.matches(Semver(99, 99, 99)))
    }

    @Test
    fun `SemverRange wildcard minor matches within major`() {
        val range = SemverRange("2.*")
        assertTrue(range.matches(Semver(2, 0, 0)))
        assertTrue(range.matches(Semver(2, 99, 99)))
        assertFalse(range.matches(Semver(3, 0, 0)))
        assertFalse(range.matches(Semver(1, 99, 99)))
    }

    @Test
    fun `RegistryPackageVersion stores all fields correctly`() {
        val ver = RegistryPackageVersion(
            version = "1.0.0",
            url = "https://example.com/pkg.tar.gz",
            dependencies = mapOf("dep1" to "1.0.0", "dep2" to "2.0.0")
        )
        assertEquals("1.0.0", ver.version)
        assertEquals("https://example.com/pkg.tar.gz", ver.url)
        assertEquals(2, ver.dependencies.size)
        assertEquals("1.0.0", ver.dependencies["dep1"])
        assertEquals("2.0.0", ver.dependencies["dep2"])
    }

    @Test
    fun `RegistryPackage stores name and versions`() {
        val pkg = RegistryPackage(
            name = "test-pkg",
            versions = mapOf(
                "1.0.0" to RegistryPackageVersion("1.0.0", "url1", emptyMap()),
                "2.0.0" to RegistryPackageVersion("2.0.0", "url2", emptyMap())
            )
        )
        assertEquals("test-pkg", pkg.name)
        assertEquals(2, pkg.versions.size)
        assertNotNull(pkg.versions["1.0.0"])
        assertNotNull(pkg.versions["2.0.0"])
    }
}
