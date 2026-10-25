package cn.edu.swun.swun_ehall.ui.common

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.receiveAsFlow

internal sealed interface FeatureNotice {
    data class Toast(val message: String) : FeatureNotice
    data class Dialog(val title: String, val message: String) : FeatureNotice
}

/** Owned by one screen and account; repeated results are separate events. */
internal class FeatureNotices {
    private val queue = Channel<FeatureNotice>(16, BufferOverflow.DROP_OLDEST)
    private var accepting = true
    val events = queue.receiveAsFlow()

    fun resume() { accepting = true }

    fun pause() {
        accepting = false
        while (queue.tryReceive().isSuccess) { /* Discard results from a hidden screen. */ }
    }

    fun message(message: String) {
        val text = message.trim()
        if (text.isEmpty()) return
        if (text.length > 60 || '\n' in text) details("操作结果", text)
        else if (accepting) queue.trySend(FeatureNotice.Toast(text))
    }

    fun error(message: String, title: String = "操作未完成") = details(title, message)

    fun details(title: String, message: String) {
        val text = message.trim()
        if (accepting && text.isNotEmpty()) queue.trySend(FeatureNotice.Dialog(title, text))
    }

    fun close() { pause(); queue.cancel() }
}
