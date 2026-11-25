package com.fcj.telnumbercheck.factory

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {
    @Test
    fun `compares numeric version segments and ignores trailing zeroes`() {
        assertTrue(AppVersion.isNewer("0.3.10", "0.3.9"))
        assertTrue(AppVersion.isNewer("1.0", "0.99.999"))
        assertFalse(AppVersion.isNewer("0.3.1.0", "0.3.1"))
        assertFalse(AppVersion.isNewer("0.3.0", "0.3.1"))
    }

    @Test
    fun `treats a stable release as newer than its prerelease`() {
        assertTrue(AppVersion.isNewer("1.0.0", "1.0.0-rc.2"))
        assertTrue(AppVersion.isNewer("1.0.0-rc.10", "1.0.0-rc.2"))
        assertFalse(AppVersion.isNewer("1.0.0-rc.2", "1.0.0"))
        assertFalse(AppVersion.isNewer("v1.0.0+build.2", "1.0.0+build.1"))
    }

    @Test
    fun `unrecognized or empty versions never force an update`() {
        assertFalse(AppVersion.isNewer("", "0.3.1"))
        assertFalse(AppVersion.isNewer("latest", "0.3.1"))
        assertFalse(AppVersion.isNewer("1.0", "unknown"))
    }
}
