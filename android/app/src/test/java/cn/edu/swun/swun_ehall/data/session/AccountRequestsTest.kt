package cn.edu.swun.swun_ehall.data.session

import cn.edu.swun.swun_ehall.data.http.RequestCancellation
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class AccountRequestsTest {
    @Test fun concurrentRefreshesShareOneRequest() = runBlocking {
        withTimeout(5_000) {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val requests = AccountRequests(scope)
            try {
                val started = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                val count = AtomicInteger()
                val first = async {
                    requests.run("schedule") {
                        count.incrementAndGet()
                        started.complete(Unit)
                        release.await()
                        "courses"
                    }
                }
                started.await()
                val second = async(start = CoroutineStart.UNDISPATCHED) {
                    requests.run("schedule") { count.incrementAndGet(); "duplicate" }
                }
                release.complete(Unit)
                assertEquals("courses", first.await())
                assertEquals("courses", second.await())
                assertEquals(1, count.get())
            } finally { requests.close(); scope.cancel() }
        }
    }

    @Test fun accountSwitchRejectsLateWritesAndDrainsOldWork() = runBlocking {
        withTimeout(5_000) {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val requests = AccountRequests(scope)
            try {
                val started = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                val wroteOldData = AtomicBoolean()
                val old = async {
                    requests.run("old") {
                        started.complete(Unit)
                        withContext(NonCancellable) {
                            release.await()
                            requests.commit { wroteOldData.set(true) }
                        }
                    }
                }
                started.await()
                val next = requests.begin()
                val changed = AtomicBoolean()
                val transition = async(start = CoroutineStart.UNDISPATCHED) {
                    requests.transition(next) { changed.set(true) }
                }
                assertFalse(transition.isCompleted)
                assertFalse(changed.get())
                release.complete(Unit)
                try { old.await(); fail("Old work should be cancelled") } catch (_: CancellationException) {}
                transition.await()
                assertFalse(wroteOldData.get())
                assertTrue(changed.get())
                assertEquals("new-account", requests.run("old") { "new-account" })
            } finally { requests.close(); scope.cancel() }
        }
    }

    @Test fun switchWakesBlockingWaitAndCancelsItsTransport() = runBlocking {
        withTimeout(5_000) {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val requests = AccountRequests(scope)
            try {
                val started = CompletableDeferred<Unit>()
                val transportCancelled = AtomicBoolean()
                val old = async {
                    requests.run("network") {
                        withContext(Dispatchers.IO) {
                            RequestCancellation.current()!!.onCancel { transportCancelled.set(true) }.use {
                                started.complete(Unit)
                                RequestCancellation.await(CountDownLatch(1), 30_000)
                            }
                        }
                    }
                }
                started.await()
                val next = requests.begin()
                requests.transition(next) {}
                try { old.await(); fail("Expected cancellation") } catch (_: CancellationException) {}
                assertTrue(transportCancelled.get())
            } finally { requests.close(); scope.cancel() }
        }
    }

    @Test fun staleTransitionCannotClearNewAccount() = runBlocking {
        withTimeout(5_000) {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val requests = AccountRequests(scope)
            try {
                val stale = requests.begin()
                val latest = requests.begin()
                requests.transition(latest) {}
                try {
                    requests.transition(stale) { fail("Stale cleanup ran") }
                    fail("Expected cancellation")
                } catch (_: CancellationException) {}
                assertTrue(requests.isCurrent(latest))
            } finally { requests.close(); scope.cancel() }
        }
    }
}
