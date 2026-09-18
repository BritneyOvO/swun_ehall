package cn.edu.swun.swun_ehall.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionsTest {
    @Test
    fun comparesSemverAndStripsPrefix() {
        assertTrue(Versions.isNewer("1.0.6", "1.0.5"))
        assertFalse(Versions.isNewer("1.0.5", "1.0.5"))
        assertFalse(Versions.isNewer("1.0.4", "1.0.5"))
        assertTrue(Versions.isNewer("v1.1.0", "1.0.9"))
        assertEquals(0, Versions.compare("1.0.5+6", "v1.0.5"))
    }
}
