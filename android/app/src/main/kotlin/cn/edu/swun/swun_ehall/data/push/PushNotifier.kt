package cn.edu.swun.swun_ehall.data.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import cn.edu.swun.swun_ehall.MainActivity

object PushNotifier {
    private const val CHANNEL = "vendor_push"

    fun show(context: Context, title: String, body: String) {
        val text = body.ifBlank { title }
        if (text.isBlank()) return
        val nm = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "校园通知", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title.ifBlank { "民大助手" })
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        nm.notify((System.currentTimeMillis() % 100000).toInt(), n)
    }
}
