package cn.edu.swun.swun_ehall.data.http

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.webkit.WebView
import java.lang.ref.WeakReference

object WebViewHost {
    @Volatile private var activityRef: WeakReference<Activity>? = null

    fun bind(activity: Activity) {
        activityRef = WeakReference(activity)
    }

    fun unbind(activity: Activity) {
        if (activityRef?.get() === activity) activityRef = null
    }

    fun activity(): Activity? = activityRef?.get()

    fun context(fallback: Context): Context = activity() ?: fallback

    fun attach(view: WebView) {
        val act = activity() ?: return
        if (view.parent != null) return
        val root = act.findViewById<ViewGroup>(android.R.id.content) ?: return
        view.layoutParams = FrameLayout.LayoutParams(2, 2)
        view.alpha = 0f
        view.visibility = View.INVISIBLE
        root.addView(view)
    }
}
