package cn.edu.swun.swun_ehall.ui.common

import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CompletableDeferred
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
internal fun rememberFeatureNotices(vararg keys: Any?): FeatureNotices {
    val notices = remember(*keys) { FeatureNotices() }
    DisposableEffect(notices) { onDispose { notices.close() } }
    return notices
}

/** Present events only while this destination is visible; dialogs never overlap. */
@Composable
internal fun FeatureNoticeHost(notices: FeatureNotices) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var dialog by remember(notices) { mutableStateOf<FeatureNotice.Dialog?>(null) }
    var dismissed by remember(notices) { mutableStateOf<CompletableDeferred<Unit>?>(null) }
    LaunchedEffect(notices, owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            notices.resume()
            var toast: Toast? = null
            try {
                notices.events.collect { event ->
                    toast?.cancel()
                    when (event) {
                        is FeatureNotice.Toast -> {
                            toast = Toast.makeText(context, event.message, Toast.LENGTH_SHORT)
                            toast?.show()
                        }
                        is FeatureNotice.Dialog -> {
                            val completion = CompletableDeferred<Unit>()
                            dismissed = completion
                            dialog = event
                            try { completion.await() } finally { dialog = null; dismissed = null }
                        }
                    }
                }
            } finally {
                toast?.cancel()
                notices.pause()
            }
        }
    }
    dialog?.let { notice ->
        WindowDialog(
            show = true,
            title = notice.title,
            onDismissRequest = { dismissed?.complete(Unit) },
        ) {
            Text(
                notice.message,
                modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
            )
            TextButton(
                text = "知道了",
                onClick = { dismissed?.complete(Unit) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}
