package cn.edu.swun.swun_ehall.ui.common

import android.Manifest
import android.app.Activity
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import cn.edu.swun.swun_ehall.data.session.Session
import cn.edu.swun.swun_ehall.data.update.UpdateDownload
import cn.edu.swun.swun_ehall.data.update.Versions
import cn.edu.swun.swun_ehall.data.update.updateNotesMarkdown
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun UpdatePrompt(session: Session) {
    val rel = session.latestRelease?.takeIf { Versions.isNewer(it) && !it.apkUrl.isNullOrBlank() } ?: return
    if (session.dismissedUpdate == rel.version) return
    val notes = remember(rel.version, rel.notes) { updateNotesMarkdown(rel.notes) }
    val context = LocalContext.current
    val running = UpdateDownload.status == UpdateDownload.Status.Running
    val progress = UpdateDownload.progress
    var started by remember(rel.version) { mutableStateOf(false) }
    LaunchedEffect(UpdateDownload.status) {
        if (started && UpdateDownload.status == UpdateDownload.Status.Failed) {
            Toast.makeText(context, UpdateDownload.error ?: "下载失败", Toast.LENGTH_SHORT).show()
        }
    }
    WindowDialog(
        show = true,
        title = "发现新版本 ${rel.version}",
        summary = if (notes.isBlank()) "可以下载并安装这个版本。" else null,
        onDismissRequest = { session.dismissedUpdate = rel.version },
    ) {
        if (notes.isNotBlank()) {
            MarkdownText(
                notes,
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 12.dp),
            )
        }
        Row(Modifier.fillMaxWidth()) {
            TextButton(
                text = "稍后",
                onClick = { session.dismissedUpdate = rel.version },
                modifier = Modifier.weight(1f),
                enabled = !running,
            )
            Spacer(Modifier.width(12.dp))
            TextButton(
                text = when {
                    running -> "下载中 ${"%.0f".format(progress * 100)}%"
                    started && UpdateDownload.status == UpdateDownload.Status.Done -> "已开始安装"
                    else -> "下载并安装"
                },
                onClick = {
                    if (running) return@TextButton
                    if (Build.VERSION.SDK_INT >= 33) {
                        (context as? Activity)?.let {
                            ActivityCompat.requestPermissions(it, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2)
                        }
                    }
                    started = true
                    UpdateDownload.start(context, rel)
                },
                modifier = Modifier.weight(1f),
                enabled = !running,
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}
