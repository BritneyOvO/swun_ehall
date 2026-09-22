package cn.edu.swun.swun_ehall.data.push

import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cn.edu.swun.swun_ehall.BuildConfig
import com.heytap.msp.push.HeytapPushManager
import com.heytap.msp.push.callback.ICallBackResultService
import com.hihonor.push.sdk.HonorPushCallback
import com.hihonor.push.sdk.HonorPushClient
import com.huawei.hms.aaid.HmsInstanceId
import com.meizu.cloud.pushsdk.PushManager
import com.vivo.push.PushClient
import com.xiaomi.mipush.sdk.MiPushClient

object VendorPush {
    private const val PREF = "swun_push"
    var channel by mutableStateOf("")
    var status by mutableStateOf("未初始化")
    var token by mutableStateOf("")

    fun start(app: Application) {
        val kind = channelOf()
        channel = kind
        val prefs = app.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        token = prefs.getString("token", "").orEmpty()
        try {
            when (kind) {
                "小米" -> startXiaomi(app)
                "华为" -> startHuawei(app)
                "荣耀" -> startHonor(app)
                "OPPO" -> startOppo(app)
                "vivo" -> startVivo(app)
                "魅族" -> startMeizu(app)
                else -> status = "当前机型没有厂商通道"
            }
        } catch (e: Exception) {
            status = "$kind · 初始化失败"
            Log.e("swun", "push $kind", e)
        }
        Log.i("swun", "push channel=$kind status=$status")
    }

    fun saveToken(context: Context, kind: String, value: String) {
        if (value.isBlank()) return
        token = value
        channel = kind
        status = "$kind · 已注册"
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString("token", value)
            .putString("channel", kind)
            .apply()
        Log.i("swun", "push token $kind ${value.take(12)}…")
    }

    fun channelOf(): String {
        val m = Build.MANUFACTURER.lowercase()
        val b = Build.BRAND.lowercase()
        return when {
            m.contains("xiaomi") || b.contains("xiaomi") || b.contains("redmi") -> "小米"
            m.contains("honor") || b.contains("honor") -> "荣耀"
            m.contains("huawei") || b.contains("huawei") -> "华为"
            m.contains("oppo") || b.contains("oppo") || b.contains("realme") || b.contains("oneplus") || m.contains("oneplus") -> "OPPO"
            m.contains("vivo") || b.contains("vivo") || b.contains("iqoo") -> "vivo"
            m.contains("meizu") || b.contains("meizu") -> "魅族"
            else -> "其他"
        }
    }

    private fun startXiaomi(app: Application) {
        val id = BuildConfig.MI_APP_ID.trim()
        val key = BuildConfig.MI_APP_KEY.trim()
        if (id.isEmpty() || key.isEmpty()) {
            status = "小米 · 未配置 AppId/AppKey"
            return
        }
        MiPushClient.registerPush(app, id, key)
        val existing = MiPushClient.getRegId(app)
        if (!existing.isNullOrBlank()) saveToken(app, "小米", existing)
        else status = "小米 · 注册中"
    }

    private fun startHuawei(app: Application) {
        val id = BuildConfig.HUAWEI_APP_ID.trim()
        if (id.isEmpty()) {
            status = "华为 · 未配置 AppId"
            return
        }
        status = "华为 · 注册中"
        Thread {
            try {
                val got = HmsInstanceId.getInstance(app).getToken(id, "HCM")
                if (!got.isNullOrBlank()) saveToken(app, "华为", got)
            } catch (e: Exception) {
                status = "华为 · 取 token 失败"
                Log.e("swun", "hms token", e)
            }
        }.start()
    }

    private fun startHonor(app: Application) {
        val id = BuildConfig.HONOR_APP_ID.trim()
        if (id.isEmpty()) {
            status = "荣耀 · 未配置 AppId"
            return
        }
        if (!HonorPushClient.getInstance().checkSupportHonorPush(app)) {
            status = "荣耀 · 系统不支持"
            return
        }
        HonorPushClient.getInstance().init(app, false)
        status = "荣耀 · 注册中"
        HonorPushClient.getInstance().getPushToken(object : HonorPushCallback<String> {
            override fun onSuccess(token: String?) {
                if (!token.isNullOrBlank()) saveToken(app, "荣耀", token)
            }

            override fun onFailure(code: Int, msg: String?) {
                status = "荣耀 · 注册失败 $code"
                Log.e("swun", "honor $code $msg")
            }
        })
    }

    private fun startOppo(app: Application) {
        val key = BuildConfig.OPPO_APP_KEY.trim()
        val secret = BuildConfig.OPPO_APP_SECRET.trim()
        if (key.isEmpty() || secret.isEmpty()) {
            status = "OPPO · 未配置 AppKey/AppSecret"
            return
        }
        HeytapPushManager.init(app, BuildConfig.DEBUG)
        if (!HeytapPushManager.isSupportPush(app)) {
            status = "OPPO · 系统不支持"
            return
        }
        status = "OPPO · 注册中"
        HeytapPushManager.register(app, key, secret, object : ICallBackResultService {
            override fun onRegister(code: Int, registerId: String?) {
                if (code == 0 && !registerId.isNullOrBlank()) saveToken(app, "OPPO", registerId)
                else status = "OPPO · 注册失败 $code"
            }

            override fun onUnRegister(code: Int) {}
            override fun onSetPushTime(code: Int, time: String?) {}
            override fun onGetPushStatus(code: Int, statusCode: Int) {}
            override fun onGetNotificationStatus(code: Int, statusCode: Int) {}
            override fun onError(code: Int, msg: String?) {
                status = "OPPO · $msg"
                Log.e("swun", "oppo $code $msg")
            }
        })
    }

    private fun startVivo(app: Application) {
        val id = BuildConfig.VIVO_APP_ID.trim()
        val key = BuildConfig.VIVO_APP_KEY.trim()
        if (id.isEmpty() || key.isEmpty()) {
            status = "vivo · 未配置 AppId/AppKey"
            return
        }
        if (!PushClient.getInstance(app).isSupport) {
            status = "vivo · 系统不支持"
            return
        }
        PushClient.getInstance(app).initialize()
        status = "vivo · 注册中"
        PushClient.getInstance(app).turnOnPush { state ->
            if (state == 0) {
                val reg = PushClient.getInstance(app).regId
                if (!reg.isNullOrBlank()) saveToken(app, "vivo", reg)
            } else {
                status = "vivo · 开启失败 $state"
            }
        }
    }

    private fun startMeizu(app: Application) {
        val id = BuildConfig.MEIZU_APP_ID.trim()
        val key = BuildConfig.MEIZU_APP_KEY.trim()
        if (id.isEmpty() || key.isEmpty()) {
            status = "魅族 · 未配置 AppId/AppKey"
            return
        }
        if (!PushManager.isBrandMeizu()) {
            status = "魅族 · 系统不支持"
            return
        }
        PushManager.register(app, id, key)
        val existing = PushManager.getPushId(app)
        if (!existing.isNullOrBlank()) saveToken(app, "魅族", existing)
        else status = "魅族 · 注册中"
    }
}
