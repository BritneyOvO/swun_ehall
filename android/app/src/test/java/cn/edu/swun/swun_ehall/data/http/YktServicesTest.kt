package cn.edu.swun.swun_ehall.data.http

import cn.edu.swun.swun_ehall.data.model.YktPayOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YktServicesTest {
    @Test fun amountValidationAcceptsCentsAndRejectsInvalidValues() {
        requireYuan(0.01, 200.0, "充值金额")
        requireYuan(200.0, 200.0, "充值金额")

        for (amount in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertEquals("请输入有效的充值金额", runCatching { requireYuan(amount, 200.0, "充值金额") }.exceptionOrNull()?.message)
        }
        assertEquals("充值金额不能超过 200 元", runCatching { requireYuan(200.01, 200.0, "充值金额") }.exceptionOrNull()?.message)
        assertEquals("金额最多保留两位小数", runCatching { requireYuan(1.001, 200.0, "充值金额") }.exceptionOrNull()?.message)
    }

    @Test fun ccbUrlKeepsSignedOrderInfoByteForByte() {
        val orderInfo = "MERCHANTID=105&POSID=001&ORDERID=A+B&MAC=ab%2Fcd%3D&TXCODE=520100"
        val order = YktPayOrder(payType = "2", orderInfo = orderInfo)
        assertEquals(
            "https://ibsbjstar.ccb.com.cn/CCBIS/ccbMain?CCB_IBSVersion=V6&$orderInfo&QRCODE=1&CHANNEL=2",
            order.ccbUrl(),
        )
        assertTrue(order.ccbUrl().contains("ORDERID=A+B"))
        assertTrue(order.ccbUrl().contains("MAC=ab%2Fcd%3D"))
    }

    @Test fun parsesRechargeAccountAndElectricDirectory() {
        val account = parseYktRechargeAccount(
            """{"text":"用户编号：20260001\n用户姓名：测试同学\n用户余额：18.70 元"}""",
        )
        assertEquals("20260001", account.studentId)
        assertEquals("测试同学", account.name)
        assertEquals(18.7, account.balanceYuan!!, 0.001)

        val rooms = parseElectricOptions(
            """{"room":[{"elecid":"r1","elecname":"101"},{"elecid":"","elecname":"忽略"}]}""",
        )
        assertEquals("r1", rooms.single().id)
        assertEquals("101", rooms.single().name)
        assertNull(parseYktYuan("无余额"))
    }
}
