package cn.edu.swun.swun_ehall

import android.app.Application
import com.tencent.bugly.crashreport.CrashReport

class SwunApplication : Application() {
    override fun onCreate() {
        super.onCreate()
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
}
