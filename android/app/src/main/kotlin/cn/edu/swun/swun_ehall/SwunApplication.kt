package cn.edu.swun.swun_ehall

import android.app.Application
import android.os.Build
import cn.edu.swun.swun_ehall.data.push.VendorPush
import cn.edu.swun.swun_ehall.ui.theme.applyPredictiveBack
import com.tencent.bugly.crashreport.CrashReport

class SwunApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        pickingMedia = getSharedPreferences("swun_session", MODE_PRIVATE).getBoolean(PREF_PICKING_MEDIA, false)
        if (Build.VERSION.SDK_INT >= 34) {
            val enable = getSharedPreferences("swun_theme", MODE_PRIVATE).getBoolean("predictiveBack", false)
            applyPredictiveBack(this, enable)
        }
        VendorPush.start(this)
        val appId = BuildConfig.BUGLY_APP_ID.trim()
        if (appId.isEmpty()) return
        try {
            val strategy = CrashReport.UserStrategy(this).apply {
                appVersion = BuildConfig.VERSION_NAME
                appPackageName = BuildConfig.APPLICATION_ID
            }
            CrashReport.initCrashReport(this, appId, BuildConfig.DEBUG, strategy)
        } catch (_: Exception) {
        }
    }

    companion object {
        const val PREF_PICKING_MEDIA = "picking_media"

        @Volatile
        var pickingMedia: Boolean = false

        fun markPickingMedia(app: Application, picking: Boolean) {
            pickingMedia = picking
            app.getSharedPreferences("swun_session", MODE_PRIVATE)
                .edit()
                .putBoolean(PREF_PICKING_MEDIA, picking)
                .commit()
        }
    }
}
