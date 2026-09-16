package cn.edu.swun.swun_ehall

import android.app.Application
import android.util.Log
import com.tencent.bugly.crashreport.CrashReport
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

class BuglyPlugin : MethodChannel.MethodCallHandler {
    companion object {
        const val CHANNEL = "cn.edu.swun.swun_ehall/bugly"
        private const val TAG = "SwunBugly"
        @Volatile
        private var enabled = false

        fun init(app: Application) {
            val appId = BuildConfig.BUGLY_APP_ID.trim()
            if (appId.isEmpty()) {
                Log.i(TAG, "no app id, skip")
                return
            }
            try {
                val strategy = CrashReport.UserStrategy(app).apply {
                    appVersion = BuildConfig.VERSION_NAME
                    appPackageName = BuildConfig.APPLICATION_ID
                    setEnableANRCrashMonitor(true)
                    setEnableCatchAnrTrace(true)
                    setEnableRecordAnrMainStack(true)
                }
                CrashReport.initCrashReport(app, appId, BuildConfig.DEBUG, strategy)
                CrashReport.setIsDevelopmentDevice(app, BuildConfig.DEBUG)
                enabled = true
                Log.i(TAG, "init ok debug=${BuildConfig.DEBUG}")
            } catch (e: Exception) {
                Log.w(TAG, "init $e")
            }
        }

        fun registerWith(engine: FlutterEngine) {
            MethodChannel(engine.dartExecutor.binaryMessenger, CHANNEL)
                .setMethodCallHandler(BuglyPlugin())
        }
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "info" -> result.success(
                hashMapOf(
                    "enabled" to enabled,
                    "hasKey" to BuildConfig.BUGLY_APP_ID.trim().isNotEmpty(),
                ),
            )
            "setUserId" -> {
                if (enabled) {
                    CrashReport.setUserId((call.arguments as? String)?.trim().orEmpty())
                }
                result.success(null)
            }
            "postError" -> {
                if (enabled) {
                    val name = call.argument<String>("name")?.ifBlank { "FlutterError" } ?: "FlutterError"
                    val message = call.argument<String>("message").orEmpty()
                    val stack = call.argument<String>("stack").orEmpty()
                    try {
                        CrashReport.postException(8, name, message, stack, null)
                    } catch (e: Exception) {
                        Log.w(TAG, "post $e")
                    }
                }
                result.success(null)
            }
            else -> result.notImplemented()
        }
    }
}
