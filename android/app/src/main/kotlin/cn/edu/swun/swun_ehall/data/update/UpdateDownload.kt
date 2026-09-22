package cn.edu.swun.swun_ehall.data.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import cn.edu.swun.swun_ehall.MainActivity
import cn.edu.swun.swun_ehall.R
import cn.edu.swun.swun_ehall.data.model.AppRelease
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object UpdateDownload {
    enum class Status { Idle, Running, Done, Failed }

    var status by mutableStateOf(Status.Idle)
    var progress by mutableFloatStateOf(0f)
    var error by mutableStateOf<String?>(null)
    var file by mutableStateOf<File?>(null)
    var version by mutableStateOf("")

    const val ACTION_DOWNLOAD = "cn.edu.swun.swun_ehall.action.DOWNLOAD_UPDATE"
    const val ACTION_CANCEL = "cn.edu.swun.swun_ehall.action.CANCEL_UPDATE"
    const val ACTION_INSTALL = "cn.edu.swun.swun_ehall.action.INSTALL_UPDATE"
    const val EXTRA_URL = "url"
    const val EXTRA_VERSION = "version"
    const val EXTRA_NOTES = "notes"
    const val EXTRA_PATH = "path"
    const val CHANNEL_ID = "update_download"
    const val NOTIFY_PROGRESS = 42
    const val NOTIFY_DONE = 43

    fun start(context: Context, rel: AppRelease) {
        if (status == Status.Running) return
        UpdateClient.cached(context, rel)?.let { hit ->
            status = Status.Done
            progress = 1f
            file = hit
            version = rel.version
            error = null
            return
        }
        val url = rel.apkUrl?.trim().orEmpty()
        if (url.isEmpty()) {
            status = Status.Failed
            error = "这个版本没有 Android 安装包"
            return
        }
        status = Status.Running
        progress = 0f
        error = null
        version = rel.version
        file = null
        val i = Intent(context, UpdateDownloadService::class.java).apply {
            action = ACTION_DOWNLOAD
            putExtra(EXTRA_URL, url)
            putExtra(EXTRA_VERSION, rel.version)
        }
        ContextCompat.startForegroundService(context, i)
    }

    fun cancel(context: Context) {
        context.startService(Intent(context, UpdateDownloadService::class.java).setAction(ACTION_CANCEL))
    }

    fun installIntent(context: Context, apk: File): Intent {
        return Intent(context, MainActivity::class.java).apply {
            action = ACTION_INSTALL
            putExtra(EXTRA_PATH, apk.absolutePath)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
    }
}

class UpdateDownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private lateinit var nm: NotificationManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(
                    UpdateDownload.CHANNEL_ID,
                    "应用更新",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { setShowBadge(false) },
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            UpdateDownload.ACTION_DOWNLOAD -> {
                val url = intent.getStringExtra(UpdateDownload.EXTRA_URL) ?: return START_NOT_STICKY
                val version = intent.getStringExtra(UpdateDownload.EXTRA_VERSION).orEmpty()
                val n = progressNotification(0, version)
                if (Build.VERSION.SDK_INT >= 34) {
                    startForeground(UpdateDownload.NOTIFY_PROGRESS, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                } else {
                    startForeground(UpdateDownload.NOTIFY_PROGRESS, n)
                }
                startDownload(url, version)
            }
            UpdateDownload.ACTION_CANCEL -> {
                job?.cancel()
                UpdateDownload.status = UpdateDownload.Status.Failed
                UpdateDownload.error = "已取消"
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startDownload(url: String, version: String) {
        job?.cancel()
        job = scope.launch {
            try {
                val rel = AppRelease(version, "", url, "", false)
                var lastPct = -1
                val file = UpdateClient.download(this@UpdateDownloadService, rel) { p ->
                    UpdateDownload.progress = p
                    val pct = (p * 100).toInt().coerceIn(0, 100)
                    if (pct - lastPct >= 2 || pct == 100) {
                        nm.notify(UpdateDownload.NOTIFY_PROGRESS, progressNotification(pct, version))
                        lastPct = pct
                    }
                }
                UpdateDownload.file = file
                UpdateDownload.progress = 1f
                UpdateDownload.status = UpdateDownload.Status.Done
                nm.cancel(UpdateDownload.NOTIFY_PROGRESS)
                nm.notify(UpdateDownload.NOTIFY_DONE, doneNotification(file, version))
            } catch (e: Exception) {
                if (UpdateDownload.status != UpdateDownload.Status.Failed) {
                    UpdateDownload.status = UpdateDownload.Status.Failed
                    UpdateDownload.error = e.message ?: "下载失败"
                    nm.cancel(UpdateDownload.NOTIFY_PROGRESS)
                    nm.notify(UpdateDownload.NOTIFY_DONE, failNotification(e.message ?: "下载失败"))
                }
            } finally {
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf()
            }
        }
    }

    private fun progressNotification(percent: Int, version: String) =
        NotificationCompat.Builder(this, UpdateDownload.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("正在下载 $version")
            .setContentText("$percent%")
            .setColor(getColor(R.color.splash_crimson))
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)
            .setProgress(100, percent, percent == 0)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "取消",
                PendingIntent.getService(
                    this,
                    1,
                    Intent(this, UpdateDownloadService::class.java).setAction(UpdateDownload.ACTION_CANCEL),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .setContentIntent(openApp())
            .build()

    private fun doneNotification(file: File, version: String): android.app.Notification {
        val install = PendingIntent.getActivity(
            this,
            2,
            UpdateDownload.installIntent(this, file),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, UpdateDownload.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("下载完成")
            .setContentText("点按安装 $version")
            .setColor(getColor(R.color.splash_crimson))
            .setAutoCancel(true)
            .setContentIntent(install)
            .addAction(android.R.drawable.ic_input_add, "安装", install)
            .build()
    }

    private fun failNotification(msg: String) =
        NotificationCompat.Builder(this, UpdateDownload.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("下载失败")
            .setContentText(msg)
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .build()

    private fun openApp(): PendingIntent =
        PendingIntent.getActivity(
            this,
            3,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
