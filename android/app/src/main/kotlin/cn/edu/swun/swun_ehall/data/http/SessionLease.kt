package cn.edu.swun.swun_ehall.data.http

/** Coalesces concurrent authentication and avoids a probe before every API call. */
internal class SessionLease(
    private val ttlMillis: Long = 60_000,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private val lock = Any()
    private var verifiedAt: Long? = null
    private var lastFailure: Exception? = null
    @Volatile private var revision = 0L

    fun ensure(force: Boolean = false, probe: () -> Boolean, login: () -> Boolean) {
        val observed = revision
        synchronized(lock) {
            RequestCancellation.check()
            if (revision != observed) {
                lastFailure?.let { throw it }
                return
            }
            val fresh = verifiedAt?.let { clock() - it in 0 until ttlMillis } == true
            if (fresh && !force) return
            verifiedAt = null
            try {
                if ((!force && probe()) || login()) confirm() else {
                    lastFailure = null
                    revision++
                }
            } catch (e: Exception) {
                lastFailure = e
                revision++
                throw e
            }
        }
    }

    fun confirm() = synchronized(lock) {
        RequestCancellation.check()
        verifiedAt = clock()
        lastFailure = null
        revision++
    }

    fun reset() = synchronized(lock) { verifiedAt = null; lastFailure = null; revision++ }
}
