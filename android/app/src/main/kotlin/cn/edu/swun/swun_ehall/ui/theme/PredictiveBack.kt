package cn.edu.swun.swun_ehall.ui.theme

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build

fun applyPredictiveBack(context: Context, enable: Boolean) {
    if (Build.VERSION.SDK_INT < 34) return
    runCatching {
        val method = ApplicationInfo::class.java.getDeclaredMethod(
            "setEnableOnBackInvokedCallback",
            Boolean::class.javaPrimitiveType,
        )
        method.isAccessible = true
        method.invoke(context.applicationInfo, enable)
    }
    val activity = context as? Activity ?: return
    runCatching {
        val method = Activity::class.java.getDeclaredMethod(
            "setEnableOnBackInvokedCallback",
            Boolean::class.javaPrimitiveType,
        )
        method.isAccessible = true
        method.invoke(activity, enable)
    }
}
