package cn.edu.swun.swun_ehall.data.push

import android.content.Context
import com.hihonor.push.sdk.HonorMessageService
import com.hihonor.push.sdk.HonorPushDataMsg
import com.huawei.hms.push.HmsMessageService
import com.huawei.hms.push.RemoteMessage
import com.meizu.cloud.pushsdk.MzPushMessageReceiver
import com.meizu.cloud.pushsdk.handler.MzPushMessage
import com.meizu.cloud.pushsdk.platform.message.RegisterStatus
import com.vivo.push.model.UPSNotificationMessage
import com.vivo.push.model.UnvarnishedMessage
import com.vivo.push.sdk.OpenClientPushMessageReceiver
import com.xiaomi.mipush.sdk.MiPushClient
import com.xiaomi.mipush.sdk.MiPushCommandMessage
import com.xiaomi.mipush.sdk.MiPushMessage
import com.xiaomi.mipush.sdk.PushMessageReceiver

class MiPushReceiver : PushMessageReceiver() {
    override fun onReceiveRegisterResult(context: Context, message: MiPushCommandMessage) {
        if (message.command == MiPushClient.COMMAND_REGISTER && message.resultCode == 0L) {
            val id = message.commandArguments?.firstOrNull().orEmpty()
            VendorPush.saveToken(context, "小米", id)
        }
    }

    override fun onReceivePassThroughMessage(context: Context, message: MiPushMessage) {
        PushNotifier.show(context, message.title.orEmpty(), message.content.orEmpty())
    }
}

class HwPushService : HmsMessageService() {
    override fun onNewToken(token: String?) {
        if (!token.isNullOrBlank()) VendorPush.saveToken(this, "华为", token)
    }

    override fun onMessageReceived(message: RemoteMessage?) {
        val n = message?.notification
        val title = n?.title.orEmpty()
        val body = n?.body ?: message?.data.orEmpty()
        PushNotifier.show(this, title, body)
    }
}

class HonorPushService : HonorMessageService() {
    override fun onNewToken(token: String?) {
        if (!token.isNullOrBlank()) VendorPush.saveToken(this, "荣耀", token)
    }

    override fun onMessageReceived(msg: HonorPushDataMsg?) {
        PushNotifier.show(this, "民大助手", msg?.data.orEmpty())
    }
}

class VivoPushReceiver : OpenClientPushMessageReceiver() {
    override fun onReceiveRegId(context: Context, regId: String?) {
        if (!regId.isNullOrBlank()) VendorPush.saveToken(context, "vivo", regId)
    }

    override fun onTransmissionMessage(context: Context, message: UnvarnishedMessage?) {
        PushNotifier.show(context, "民大助手", message?.message.orEmpty())
    }

    override fun onNotificationMessageClicked(context: Context, message: UPSNotificationMessage?) {
        PushNotifier.show(context, message?.title.orEmpty(), message?.content.orEmpty())
    }
}

class MeizuPushReceiver : MzPushMessageReceiver() {
    override fun onRegisterStatus(context: Context, status: RegisterStatus?) {
        val id = status?.pushId.orEmpty()
        if (id.isNotBlank()) VendorPush.saveToken(context, "魅族", id)
    }

    override fun onMessage(context: Context, message: String?, platformExtra: String?) {
        PushNotifier.show(context, "民大助手", message.orEmpty())
    }

    override fun onNotificationArrived(context: Context, message: MzPushMessage?) {
        PushNotifier.show(context, message?.title.orEmpty(), message?.content.orEmpty())
    }
}
