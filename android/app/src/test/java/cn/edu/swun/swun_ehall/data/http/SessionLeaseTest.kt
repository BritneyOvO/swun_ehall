package cn.edu.swun.swun_ehall.data.http

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

class SessionLeaseTest {
    @Test fun concurrentRequestsWaitForOneLogin() {
        val lease = SessionLease()
        val pool = Executors.newFixedThreadPool(2)
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val logins = AtomicInteger()
        try {
            val first = pool.submit {
                lease.ensure(probe = { false }, login = {
                    logins.incrementAndGet()
                    started.countDown()
                    check(release.await(3, TimeUnit.SECONDS))
                    true
                })
            }
            assertTrue(started.await(3, TimeUnit.SECONDS))
            val second = pool.submit {
                lease.ensure(probe = { false }, login = { logins.incrementAndGet(); true })
            }
            try {
                second.get(100, TimeUnit.MILLISECONDS)
                fail("Concurrent request returned before login finished")
            } catch (_: TimeoutException) {}
            release.countDown()
            first.get(3, TimeUnit.SECONDS)
            second.get(3, TimeUnit.SECONDS)
            assertEquals(1, logins.get())
        } finally { release.countDown(); pool.shutdownNow() }
    }

    @Test fun warmSessionSkipsRepeatedProbesAndExpires() {
        var now = 0L
        val lease = SessionLease(ttlMillis = 60_000, clock = { now })
        var probes = 0
        repeat(3) { lease.ensure(probe = { probes++; true }, login = { fail("Unexpected login"); false }) }
        assertEquals(1, probes)
        now = 60_001
        lease.ensure(probe = { probes++; true }, login = { false })
        assertEquals(2, probes)
        lease.reset()
        lease.ensure(probe = { probes++; true }, login = { false })
        assertEquals(3, probes)
    }

    @Test fun rejectedSessionRenewsEvenInsideCacheLifetime() {
        val lease = SessionLease()
        var logins = 0
        lease.confirm()
        lease.ensure(force = true, probe = { fail("Do not probe a rejected session"); false }, login = { logins++; true })
        assertEquals(1, logins)
    }

    @Test fun failedLoginDoesNotBecomeCachedSuccess() {
        val lease = SessionLease()
        var logins = 0
        repeat(2) { lease.ensure(probe = { false }, login = { logins++; false }) }
        assertEquals(2, logins)
    }
    @Test fun concurrentRequestsShareSlowLoginFailure() {
        val lease = SessionLease()
        val pool = Executors.newFixedThreadPool(2)
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val failure = IllegalStateException("offline")
        val logins = AtomicInteger()
        try {
            val first = pool.submit {
                lease.ensure(probe = { false }, login = {
                    logins.incrementAndGet()
                    started.countDown()
                    check(release.await(3, TimeUnit.SECONDS))
                    throw failure
                })
            }
            assertTrue(started.await(3, TimeUnit.SECONDS))
            val second = pool.submit {
                lease.ensure(probe = { false }, login = { logins.incrementAndGet(); throw failure })
            }
            try {
                second.get(100, TimeUnit.MILLISECONDS)
                fail("Concurrent request did not wait")
            } catch (_: TimeoutException) {}
            release.countDown()
            for (future in listOf(first, second)) {
                try { future.get(3, TimeUnit.SECONDS); fail("Expected login failure") }
                catch (e: java.util.concurrent.ExecutionException) { assertSame(failure, e.cause) }
            }
            assertEquals(1, logins.get())
        } finally { release.countDown(); pool.shutdownNow() }
    }
}
