package cn.edu.swun.swun_ehall.ui.ykt

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
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
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun WaterScreen(session: Session, nav: NavHostController) {
    var account by remember { mutableStateOf<YktRechargeAccount?>(null) }
    var amount by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<YktPaymentResult?>(null) }
    var loading by remember { mutableStateOf(true) }
    var submitting by remember { mutableStateOf(false) }
    var confirmAmount by remember { mutableStateOf<Double?>(null) }
    val scope = rememberCoroutineScope()
    val notices = rememberFeatureNotices(session.studentId, session.demoMode)
    FeatureNoticeHost(notices)

    LaunchedEffect(session.studentId, session.demoMode) {
        loading = true
        try {
            account = session.waterRechargeAccount()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            notices.error(e.message ?: "热水账户加载失败")
        } finally {
            loading = false
        }
    }

    Scaffold(
        topBar = { SmallTopAppBar(title = "淋浴 / 热水充值", navigationIcon = { BackNav { nav.popBackStack() } }) },
    ) { padding ->
        if (loading && account == null) {
            FeatureLoadingPage(padding)
            return@Scaffold
        }
        FeatureColumn(padding) {
            val cs = MiuixTheme.colorScheme
            Card(modifier = Modifier.padding(top = 14.dp).fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("从一卡通余额转入热水账户", fontWeight = FontWeight.SemiBold)
                    Text(
                        account?.balanceYuan?.let { "当前一卡通余额 ¥ ${"%.2f".format(it)}" } ?: "当前一卡通余额 —",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    Text(
                        "提交后会立即扣除一卡通余额，单次最高 20 元。",
                        color = cs.onSurfaceVariantSummary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 5.dp),
                    )
                }
            }
            FeatureSection("充值金额")
            QuickAmounts(listOf(5, 10, 20), amount) { amount = it.toString() }
            Spacer(Modifier.height(8.dp))
            TextField(
                value = amount,
                onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
                label = "充值金额（最高 20 元）",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            FeatureMutedPanel(
                title = "扣款方式",
                summary = "从一卡通余额立即扣除，确认后无法在本应用撤销。",
                modifier = Modifier.padding(top = 14.dp),
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    val money = amount.toDoubleOrNull()
                    when {
                        money == null || money <= 0 -> notices.error("请输入有效的充值金额")
                        money > 20 -> notices.error("热水充值金额不能超过 20 元")
                        account?.balanceYuan != null && money > account!!.balanceYuan!! -> notices.error("一卡通余额不足")
                        else -> confirmAmount = money
                    }
                },
                enabled = !submitting,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) { Text(if (submitting) "充值中…" else "确认充值") }
            PaymentResultCard(result) { }
            FeatureBottomSpace()
        }
    }

    confirmAmount?.let { money ->
        WindowDialog(
            show = true,
            title = "确认热水充值",
            summary = "将从一卡通余额立即扣除 ¥ ${"%.2f".format(money)}。确认后无法在本应用撤销。",
            onDismissRequest = { confirmAmount = null },
        ) {
            Row(Modifier.fillMaxWidth()) {
                TextButton("取消", { confirmAmount = null }, Modifier.weight(1f))
                Spacer(Modifier.padding(4.dp))
                TextButton(
                    text = "确认扣款",
                    onClick = {
                        confirmAmount = null
                        scope.launch {
                            submitting = true
                            try {
                                result = session.rechargeWater(money)
                                when (val got = result) {
                                    is YktPaymentResult.Success -> {
                                        notices.message(got.message)
                                        account = session.waterRechargeAccount()
                                    }
                                    is YktPaymentResult.Failure -> notices.error(got.message)
                                    else -> Unit
                                }
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                notices.error(e.message ?: "热水充值失败")
                            } finally {
                                submitting = false
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}
