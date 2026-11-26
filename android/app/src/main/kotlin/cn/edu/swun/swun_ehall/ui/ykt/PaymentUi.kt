package cn.edu.swun.swun_ehall.ui.ykt

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.edu.swun.swun_ehall.data.model.YktPayMethod
import cn.edu.swun.swun_ehall.data.model.YktPayOrder
import cn.edu.swun.swun_ehall.data.model.YktPaymentResult
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun PayMethodPicker(selected: YktPayMethod, onSelected: (YktPayMethod) -> Unit) {
    val cs = MiuixTheme.colorScheme
    Text("支付方式", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
    YktPayMethod.entries.forEach { method ->
        Card(
            modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
            onClick = { onSelected(method) },
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(method.label, fontWeight = FontWeight.SemiBold)
                    Text(method.description, color = cs.onSurfaceVariantSummary, fontSize = 12.sp)
                }
                Text(if (selected == method) "✓" else "", color = cs.primary, fontSize = 18.sp)
            }
        }
    }
}

@Composable
internal fun QuickAmounts(values: List<Int>, onPick: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        values.forEach { value ->
            TextButton(
                text = "¥$value",
                onClick = { onPick(value) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
internal fun PaymentResultCard(result: YktPaymentResult?, onOpenOrder: (YktPayOrder) -> Unit) {
    if (result == null) return
    val cs = MiuixTheme.colorScheme
    Card(modifier = Modifier.padding(top = 14.dp).fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            when (result) {
                is YktPaymentResult.Success -> {
                    Text("操作结果", fontWeight = FontWeight.SemiBold)
                    Text(result.message, color = cs.onSurfaceVariantSummary, modifier = Modifier.padding(top = 5.dp))
                }
                is YktPaymentResult.Failure -> {
                    Text("充值未完成", fontWeight = FontWeight.SemiBold)
                    Text(result.message, color = cs.onSurfaceVariantSummary, modifier = Modifier.padding(top = 5.dp))
                }
                is YktPaymentResult.OrderCreated -> {
                    Text("官方订单已创建", fontWeight = FontWeight.SemiBold)
                    Text(
                        "付款尚未完成。请前往建行收银台完成支付，最终结果以一卡通余额和账单为准。",
                        color = cs.onSurfaceVariantSummary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 5.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { onOpenOrder(result.order) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) { Text("前往建行收银台") }
                }
            }
        }
    }
}

internal fun openCcbOrder(context: Context, order: YktPayOrder): Result<Unit> = runCatching {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(order.ccbUrl()))
    context.startActivity(intent)
}
