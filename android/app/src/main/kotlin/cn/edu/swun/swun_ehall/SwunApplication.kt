package cn.edu.swun.swun_ehall

import android.app.Application

class SwunApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        BuglyPlugin.init(this)
    }
}
