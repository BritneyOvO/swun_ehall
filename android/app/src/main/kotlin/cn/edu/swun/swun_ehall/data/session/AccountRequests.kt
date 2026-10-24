package cn.edu.swun.swun_ehall.data.session

import cn.edu.swun.swun_ehall.data.http.RequestCancellation
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** All work belongs to one account generation, including callers from UI scopes. */
internal class AccountRequests(private val scope: CoroutineScope) {
    internal class Generation(parent: Job?) {
        val job: CompletableJob = SupervisorJob(parent)
        val cancellation = RequestCancellation()
        val inFlight = mutableMapOf<String, Deferred<*>>()
        var ready = true
    }

    private class Owner(val generation: Generation) : AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<Owner>
    }

    private val lock = Any()
    private val transitions = Mutex()
    private val retired = mutableListOf<Job>()
    private var current = Generation(scope.coroutineContext[Job])

    fun begin(): Generation {
        val (old, next) = synchronized(lock) {
            val old = current
            val next = Generation(scope.coroutineContext[Job]).also { it.ready = false }
            current = next
            retired.add(old.job)
            old to next
        }
        old.cancellation.cancel()
        old.job.cancel()
        return next
    }

    fun isCurrent(generation: Generation): Boolean = synchronized(lock) {
        current === generation && generation.job.isActive
    }

    /** Drain cancelled work before changing shared clients, cookies, or disk files. */
    suspend fun <T> transition(generation: Generation, block: suspend () -> T): T =
        task(generation, null, requireReady = false) {
            transitions.withLock {
                val pending = synchronized(lock) { retired.toList() }
                pending.joinAll()
                synchronized(lock) { retired.removeAll(pending.toSet()); checkCurrent(generation) }
                val result = block()
                synchronized(lock) { checkCurrent(generation); generation.ready = true }
                result
            }
        }.await()

    suspend fun <T> run(key: String? = null, block: suspend () -> T): T {
        val inherited = currentCoroutineContext()[Owner]?.generation
        val generation = synchronized(lock) { inherited ?: current }
        return task(generation, key, requireReady = true, block).await()
    }

    fun launch(key: String? = null, block: suspend () -> Unit) {
        val generation = synchronized(lock) { current }
        task(generation, key, requireReady = true, block)
    }

    fun <T> commit(block: () -> T): T = synchronized(lock) {
        val owner = RequestCancellation.current()
        if (owner != null && owner !== current.cancellation) {
            throw CancellationException("账号会话已切换")
        }
        owner?.checkActive()
        block()
    }

    private fun checkCurrent(generation: Generation) {
        if (current !== generation || !generation.job.isActive) {
            throw CancellationException("账号会话已切换")
        }
    }

    private fun <T> task(
        generation: Generation,
        key: String?,
        requireReady: Boolean,
        block: suspend () -> T,
    ): Deferred<T> {
        val result = synchronized(lock) {
            checkCurrent(generation)
            if (requireReady && !generation.ready) throw CancellationException("账号正在切换")
            @Suppress("UNCHECKED_CAST")
            val existing = key?.let { generation.inFlight[it] as? Deferred<T> }
            if (existing != null && !existing.isCompleted) return@synchronized existing
            val created = scope.async(
                generation.job + Owner(generation) + generation.cancellation.context(),
                start = CoroutineStart.LAZY,
            ) {
                generation.cancellation.checkActive()
                block()
            }
            if (key != null) {
                generation.inFlight[key] = created
                created.invokeOnCompletion {
                    synchronized(lock) {
                        if (generation.inFlight[key] === created) generation.inFlight.remove(key)
                    }
                }
            }
            created
        }
        result.start()
        return result
    }

    fun close() {
        val active = synchronized(lock) { current }
        active.cancellation.cancel()
        active.job.cancel()
    }
}
