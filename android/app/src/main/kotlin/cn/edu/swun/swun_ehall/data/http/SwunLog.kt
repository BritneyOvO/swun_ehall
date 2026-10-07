package cn.edu.swun.swun_ehall.data.http

import android.content.Context
import android.util.Log
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** App-private log at files/swun.log. */
object SwunLog {
    private const val MAX_BYTES = 512 * 1024
    private val lock = Any()
    private val stamp = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")
    @Volatile private var file: File? = null

    fun init(context: Context) {
        file = File(context.applicationContext.filesDir, "swun.log")
    }

    fun took(startedNs: Long, where: String) {
        w("耗时 ${(System.nanoTime() - startedNs) / 1_000_000}ms $where")
    }

    fun w(message: String) {
        Log.w("swun", message)
        val target = file ?: return
        val line = "${LocalDateTime.now().format(stamp)} $message\n"
        synchronized(lock) {
            try {
                if (target.exists() && target.length() > MAX_BYTES) {
                    val previous = File(target.parentFile, "swun.log.1")
                    if (previous.exists()) previous.delete()
                    target.renameTo(previous)
                }
                target.appendText(line)
            } catch (_: Exception) {
            }
        }
    }

    fun api(where: String, status: Int, url: String, body: String) {
        val path = url.substringBefore('?').substringAfter("://").let { raw ->
            val slash = raw.indexOf('/')
            if (slash >= 0) raw.substring(slash) else raw
        }
        val text = body.replace(Regex("\\s+"), " ").take(2000)
        w("$where HTTP $status $path $text")
    }
}
