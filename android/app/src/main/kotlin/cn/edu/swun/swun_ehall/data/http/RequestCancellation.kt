package cn.edu.swun.swun_ehall.data.http

import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.asContextElement

/** Connects blocking HTTP and WebView waits to their account's lifetime. */
internal class RequestCancellation {
    private val lock = Any()
    private var cancelled = false
    private val callbacks = mutableSetOf<() -> Unit>()

    fun checkActive() = synchronized(lock) {
        if (cancelled) throw CancellationException("账号会话已切换")
    }

    fun onCancel(callback: () -> Unit): AutoCloseable {
        val invoke = synchronized(lock) {
            if (cancelled) true else { callbacks.add(callback); false }
        }
        if (invoke) callback()
        return AutoCloseable { synchronized(lock) { callbacks.remove(callback) } }
    }

    fun cancel() {
        val pending = synchronized(lock) {
            cancelled = true
            callbacks.toList().also { callbacks.clear() }
        }
        pending.forEach { it() }
    }

    fun context() = local.asContextElement(this)

    companion object {
        private val local = ThreadLocal<RequestCancellation?>()
        fun current(): RequestCancellation? = local.get()
        fun check() { current()?.checkActive() }

        fun await(latch: CountDownLatch, timeoutMs: Long): Boolean {
            val owner = current()
            owner?.checkActive()
            val wakeup = owner?.onCancel { latch.countDown() }
            try {
                val completed = latch.await(timeoutMs, TimeUnit.MILLISECONDS)
                owner?.checkActive()
                return completed
            } finally {
                wakeup?.close()
            }
        }
    }
}
