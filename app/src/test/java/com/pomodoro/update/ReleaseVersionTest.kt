package com.pomodoro.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseVersionTest {
    @Test
    fun comparesNumericVersionsAndPrereleasesRatherThanStrings() {
        val ordered = listOf("v1.1.1-alpha", "v1.1.1-alpha.2", "v1.1.1-alpha.10", "v1.1.1-beta", "v1.1.1", "v1.1.2-alpha", "v1.10.0-alpha")
        ordered.zipWithNext().forEach { (a, b) ->
            assertTrue("$a must precede $b", ReleaseVersion.parse(a)!! < ReleaseVersion.parse(b)!!)
        }
        assertEquals(ReleaseVersion.parse("1.0.0-alpha"), ReleaseVersion.parse("V1.0-Alpha"))
        assertEquals(ReleaseVersion.parse("1.1.1-alpha"), ReleaseVersion.parse("v1.1.1-alpha+build.7"))
        assertNull(ReleaseVersion.parse("not-a-version"))
        assertNull(ReleaseVersion.parse("1.1.1-alpha..2"))
    }
}
