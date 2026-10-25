package cn.edu.swun.swun_ehall.data.update

import cn.edu.swun.swun_ehall.data.model.AppRelease
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

    @Test
    fun reissuedReleasePreservesBuildCodeForUpdateChecks() {
        val raw = org.json.JSONObject(
            """
            {"tag_name":"v1.1.0","assets":[
              {"name":"swun_ehall-1.1.0-12-arm64-debug.apk","browser_download_url":"https://example/debug.apk"},
              {"name":"swun_ehall-1.1.0-12-arm64-release.apk","browser_download_url":"https://example/release.apk"}
            ]}
            """.trimIndent(),
        )
        val release = UpdateClient.parse(raw)!!
        assertEquals("1.1.0", release.version)
        assertEquals(12, release.versionCode)
        assertTrue(release.versionCode > 11)
        assertEquals("https://example/release.apk", release.apkUrl)
        assertEquals("swun_ehall-1.1.0-12-arm64-release.apk", release.apkName)
    }

    @Test
    fun sameVersionUpdateUsesBuildCode() {
        val release = AppRelease(Versions.APP, "", null, "", false, Versions.CODE + 1)
        assertTrue(Versions.isNewer(release))
        assertFalse(Versions.isNewer(release.copy(versionCode = Versions.CODE)))
        assertFalse(Versions.isNewer(release.copy(versionCode = Versions.CODE - 1)))
    }
}
