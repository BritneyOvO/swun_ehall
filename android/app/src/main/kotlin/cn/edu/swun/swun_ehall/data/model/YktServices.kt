package cn.edu.swun.swun_ehall.data.model

enum class YktPayMethod(val code: String, val label: String, val description: String) {
    Wechat("wechat", "微信支付", "进入建行手机收银台后选择微信"),
    QrCode("alipay", "二维码支付", "使用微信、支付宝等扫码支付"),
    CcbPay("CCBpay", "龙支付", "需要中国建设银行 App"),
}

data class YktRechargeAccount(
    val name: String = "",
    val studentId: String = "",
    val balanceYuan: Double? = null,
)

data class YktDirectoryOption(
    val id: String,
    val name: String,
)

data class ElectricSelection(
    val school: YktDirectoryOption,
    val building: YktDirectoryOption,
    val floor: YktDirectoryOption,
    val room: YktDirectoryOption,
) {
    val label: String
        get() = listOf(school.name, building.name, floor.name, room.name)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
}

sealed interface YktPaymentResult {
    data class Success(val message: String) : YktPaymentResult
    data class OrderCreated(val order: YktPayOrder) : YktPaymentResult
    data class Failure(val message: String) : YktPaymentResult
}

data class YktPayOrder(
    val payType: String,
    val orderInfo: String,
) {
    /** orderInfo is already signed by the bank and must remain byte-for-byte unchanged. */
    fun ccbUrl(): String {
        val channel = if (payType == "2") "2" else "1"
        return "https://ibsbjstar.ccb.com.cn/CCBIS/ccbMain?CCB_IBSVersion=V6&" +
            orderInfo + "&QRCODE=1&CHANNEL=$channel"
    }
}
