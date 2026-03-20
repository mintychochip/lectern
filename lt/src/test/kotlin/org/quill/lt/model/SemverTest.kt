package org.quill.lt.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SemverTest {
    @Test
    fun `Semver stores major minor patch correctly`() {
        val v = Semver(1, 2, 3)
        assertEquals(1, v.major)
        assertEquals(2, v.minor)
        assertEquals(3, v.patch)
    }

    @Test
    fun `Semver toString formats correctly`() {
        assertEquals("1.2.3", Semver(1, 2, 3).toString())
        assertEquals("0.0.0", Semver(0, 0, 0).toString())
        assertEquals("10.20.30", Semver(10, 20, 30).toString())
    }

    @Test
    fun `Semver comparison respects major first`() {
        assertTrue(Semver(2, 0, 0) > Semver(1, 99, 99))
        assertTrue(Semver(1, 0, 0) < Semver(2, 0, 0))
    }

    @Test
    fun `Semver comparison respects minor when major equal`() {
        assertTrue(Semver(1, 2, 0) > Semver(1, 1, 99))
        assertTrue(Semver(1, 1, 0) < Semver(1, 2, 0))
    }

    @Test
    fun `Semver comparison respects patch when major minor equal`() {
        assertTrue(Semver(1, 0, 2) > Semver(1, 0, 1))
        assertTrue(Semver(1, 0, 0) < Semver(1, 0, 1))
    }

    @Test
    fun `Semver equals is true for identical components`() {
        assertEquals(Semver(1, 2, 3), Semver(1, 2, 3))
    }

    @Test
    fun `Semver hashCode is consistent for equal versions`() {
        assertEquals(Semver(1, 2, 3).hashCode(), Semver(1, 2, 3).hashCode())
    }

    @Test
    fun `Caret range matches same major only`() {
        val range = SemverRange("^1.2.3")
        assertTrue(range.matches(Semver(1, 2, 3)))
        assertTrue(range.matches(Semver(1, 9, 9)))
        assertFalse(range.matches(Semver(2, 0, 0)))
        assertFalse(range.matches(Semver(0, 9, 9)))
    }

    @Test
    fun `Tilde range matches minor plus one upper bound`() {
        val range = SemverRange("~1.2.3")
        assertTrue(range.matches(Semver(1, 2, 3)))
        assertTrue(range.matches(Semver(1, 2, 9)))
        assertTrue(range.matches(Semver(1, 3, 0)))  // minor + 1 is upper bound
        assertFalse(range.matches(Semver(1, 4, 0)))  // beyond minor + 1
        assertFalse(range.matches(Semver(2, 0, 0)))
    }

    @Test
    fun `Exact range matches exact version`() {
        val range = SemverRange("1.2.3")
        assertTrue(range.matches(Semver(1, 2, 3)))
        assertFalse(range.matches(Semver(1, 2, 4)))
        assertFalse(range.matches(Semver(1, 3, 0)))
    }

    @Test
    fun `Wildcard major range matches any version`() {
        val range = SemverRange("*")
        assertTrue(range.matches(Semver(0, 0, 0)))
        assertTrue(range.matches(Semver(1, 2, 3)))
        assertTrue(range.matches(Semver(99, 99, 99)))
    }

    @Test
    fun `Wildcard minor range matches within same major`() {
        val range = SemverRange("1.*")
        assertTrue(range.matches(Semver(1, 0, 0)))
        assertTrue(range.matches(Semver(1, 99, 99)))
        assertFalse(range.matches(Semver(2, 0, 0)))
    }

    @Test
    fun `SemverRange toString returns original string`() {
        assertEquals("^1.2.3", SemverRange("^1.2.3").toString())
        assertEquals("~1.2.3", SemverRange("~1.2.3").toString())
        assertEquals("1.2.3", SemverRange("1.2.3").toString())
        assertEquals("*", SemverRange("*").toString())
        assertEquals("1.*", SemverRange("1.*").toString())
    }
}
