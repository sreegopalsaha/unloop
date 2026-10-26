package com.unloop.launcher.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortVideoDetectorTest {

    @Test
    fun testSupportedPackages_containsTargetApps() {
        assertTrue(ShortVideoDetector.SUPPORTED_PACKAGES.contains(ShortVideoDetector.PKG_YOUTUBE))
        assertTrue(ShortVideoDetector.SUPPORTED_PACKAGES.contains(ShortVideoDetector.PKG_INSTAGRAM))
        assertTrue(ShortVideoDetector.SUPPORTED_PACKAGES.contains(ShortVideoDetector.PKG_SNAPCHAT))
        assertTrue(ShortVideoDetector.SUPPORTED_PACKAGES.contains(ShortVideoDetector.PKG_FACEBOOK))

        assertEquals(4, ShortVideoDetector.SUPPORTED_PACKAGES.size)
    }

    @Test
    fun testSupportedPackages_excludesUnrelatedApps() {
        assertFalse(ShortVideoDetector.SUPPORTED_PACKAGES.contains("com.whatsapp"))
        assertFalse(ShortVideoDetector.SUPPORTED_PACKAGES.contains("com.android.chrome"))
        assertFalse(ShortVideoDetector.SUPPORTED_PACKAGES.contains("com.spotify.music"))
    }

    @Test
    fun testNullRoot_returnsFalse() {
        assertFalse(ShortVideoDetector.isShortVideoInterface(null, ShortVideoDetector.PKG_YOUTUBE))
        assertFalse(ShortVideoDetector.isShortVideoInterface(null, ShortVideoDetector.PKG_INSTAGRAM))
        assertFalse(ShortVideoDetector.isShortVideoInterface(null, ShortVideoDetector.PKG_SNAPCHAT))
        assertFalse(ShortVideoDetector.isShortVideoInterface(null, ShortVideoDetector.PKG_FACEBOOK))
    }

    @Test
    fun testUnsupportedPackage_returnsFalse() {
        assertFalse(ShortVideoDetector.isShortVideoInterface(null, "com.random.package"))
    }
}
