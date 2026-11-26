package cn.edu.swun.swun_ehall.ui.ykt

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import cn.edu.swun.swun_ehall.data.model.YktPayMethod
import cn.edu.swun.swun_ehall.data.model.YktPaymentResult
import cn.edu.swun.swun_ehall.data.model.YktRechargeAccount
import cn.edu.swun.swun_ehall.data.session.Session
import cn.edu.swun.swun_ehall.ui.common.BackNav
import cn.edu.swun.swun_ehall.ui.common.FeatureBottomSpace
import cn.edu.swun.swun_ehall.ui.common.FeatureColumn
import cn.edu.swun.swun_ehall.ui.common.FeatureLoadingPage
import cn.edu.swun.swun_ehall.ui.common.FeatureMutedPanel
import cn.edu.swun.swun_ehall.ui.common.FeatureNoticeHost
import cn.edu.swun.swun_ehall.ui.common.FeatureSection
import cn.edu.swun.swun_ehall.ui.common.rememberFeatureNotices
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun RechargeScreen(session: Session, nav: NavHostController) {
    var account by remember { mutableStateOf<YktRechargeAccount?>(null) }
    var amount by remember { mutableStateOf("") }
    var method by remember { mutableStateOf(YktPayMethod.Wechat) }
    var result by remember { mutableStateOf<YktPaymentResult?>(null) }
    var loading by remember { mutableStateOf(true) }
    var submitting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val notices = rememberFeatureNotices(session.studentId, session.demoMode)
    FeatureNoticeHost(notices)

    suspend fun load() {
        loading = true
        try {
            account = session.yktRechargeAccount()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            notices.error(e.message ?: "一卡通充值页加载失败")
        } finally {
            loading = false
        }
    }
    LaunchedEffect(session.studentId, session.demoMode) { load() }

    Scaffold(
        topBar = { SmallTopAppBar(title = "一卡通充值", navigationIcon = { BackNav { nav.popBackStack() } }) },
    ) { padding ->
        if (loading && account == null) {
            FeatureLoadingPage(padding)
            return@Scaffold
        }
        FeatureColumn(padding) {
            val cs = MiuixTheme.colorScheme
            Card(modifier = Modifier.padding(top = 14.dp).fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("一卡通账户", fontWeight = FontWeight.SemiBold)
                    Text(
                        account?.let { "${it.name}  ${it.studentId}" }.orEmpty(),
                        color = cs.onSurfaceVariantSummary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Text(
                        account?.balanceYuan?.let { "余额 ¥ ${"%.2f".format(it)}" } ?: "余额 —",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
            FeatureSection("充值金额")
            QuickAmounts(listOf(5, 10, 20, 50, 100, 200), amount) { amount = it.toString() }
            Spacer(Modifier.height(8.dp))
            TextField(
                value = amount,
                onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
                label = "充值金额（最高 200 元）",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            PayMethodPicker(method, onSelected = { method = it })
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    val money = amount.toDoubleOrNull()
                    if (money == null) {
                        notices.error("请输入有效的充值金额")
                        return@Button
                    }
                    scope.launch {
                        submitting = true
                        try {
                            result = session.createYktRecharge(money, method)
                            if (result is YktPaymentResult.Failure) notices.error((result as YktPaymentResult.Failure).message)
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            notices.error(e.message ?: "订单创建失败")
                        } finally {
                            submitting = false
                        }
                    }
                },
                enabled = !submitting,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) { Text(if (submitting) "正在创建订单…" else "创建官方充值订单") }
            PaymentResultCard(result) { order ->
                openCcbOrder(context, order).onFailure { notices.error("无法打开建行收银台") }
            }
            FeatureMutedPanel(
                title = "充值说明",
                summary = "每天 00:30–23:50 可充值。创建订单不代表付款成功，支付结果以一卡通余额和账单为准。",
                modifier = Modifier.padding(top = 14.dp),
            )
            FeatureBottomSpace()
        }
    }
}
