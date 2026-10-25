package cn.edu.swun.swun_ehall.data.http

import org.junit.Assert.*
import org.junit.Test

class RsJsBridgeTest {
    @Test fun traceRedactsCredentialValuesButKeepsSigningSamples() {
        val bridge = RsJsBridge()
        val credential = "sensitive-credential-".repeat(30)
        bridge.beginTrace()
        bridge.keep("enable_3AAFGrVH3FUN", "true")
        bridge.keep("3AAFGrVH3FUNP", credential)
        val trace = bridge.trace()
        assertTrue(trace.contains("3AAFGrVH3FUNP:${credential.length}"))
        assertFalse(trace.contains(credential))
        val sample = bridge.samples().single()
        assertEquals(credential, sample.value)
        assertEquals("enable_3AAFGrVH3FUN", sample.enableName)
        assertEquals("true", sample.enable)
    }

    @Test fun challengeReloadDiscardsSamplesAndReadinessFromPreviousPage() {
        val bridge = RsJsBridge()
        bridge.beginTrace()
        bridge.keep("3AAFGrVH3FUNP", "old".repeat(150))
        bridge.ready("replace")
        bridge.beginTrace()
        assertTrue(bridge.samples().isEmpty())
        assertEquals("", bridge.trace())
        assertEquals(0, bridge.lastPLen)
        assertEquals("", bridge.readyKind)
        bridge.keep("3AAFGrVH3FUNP", "new".repeat(150))
        assertEquals("new".repeat(150), bridge.samples().single().value)
    }
}
