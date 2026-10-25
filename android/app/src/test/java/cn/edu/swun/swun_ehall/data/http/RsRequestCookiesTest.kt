package cn.edu.swun.swun_ehall.data.http

import org.junit.Assert.*
import org.junit.Test

class RsRequestCookiesTest {
    private val old = "a".repeat(535)
    private val fresh = "b".repeat(535)
    private val stored = mapOf("3AAFGrVH3FUNO" to "server", "enable_3AAFGrVH3FUN" to "true")

    @Test fun newlySignedCookieUsesMatchingServerCookieAndExcludesAuthentication() {
        val out = rsRequestCookies("3AAFGrVH3FUNP=$old", "Authorization=secret; 3AAFGrVH3FUNP=$fresh", stored)!!
        assertEquals(3, out.size)
        assertEquals(fresh, out["3AAFGrVH3FUNP"])
        assertEquals("server", out["3AAFGrVH3FUNO"])
        assertEquals("true", out["enable_3AAFGrVH3FUN"])
        assertFalse(out.containsKey("Authorization"))
    }

    @Test fun unchangedOrIncompleteCookieFallsBackToChallengePage() {
        assertNull(rsRequestCookies("3AAFGrVH3FUNP=$old", "3AAFGrVH3FUNP=$old", stored))
        assertNull(rsRequestCookies("", "3AAFGrVH3FUNP=short", stored))
        assertNull(rsRequestCookies("", "3AAFGrVH3FUNP=$fresh", emptyMap()))
        assertNull(rsRequestCookies("", "3AAFGrVH3FUNP=$fresh", mapOf("FSSBBIl1O" to "wrong")))
    }

    @Test fun cookieFamiliesUseTheirOwnServerAndEnableCookies() {
        for (prefix in listOf("3AAFGrVH3FUN", "FSSBBIl1UgzbN7N80S")) {
            val out = rsRequestCookies("", "${prefix}P=$fresh", mapOf("${prefix}O" to "server"))!!
            assertEquals(mapOf("${prefix}O" to "server", "${prefix}P" to fresh), out)
        }
    }

    @Test fun staleDuplicateAndUnrelatedCookiesAreNotCopied() {
        val out = rsRequestCookies(
            "3AAFGrVH3FUNP=$old",
            "3AAFGrVH3FUNP=$old; token=secret; 3AAFGrVH3FUNP=$fresh; otherP=$fresh",
            stored,
        )!!
        assertEquals(fresh, out["3AAFGrVH3FUNP"])
        assertFalse(out.containsKey("token"))
        assertFalse(out.containsKey("otherP"))
    }
}
