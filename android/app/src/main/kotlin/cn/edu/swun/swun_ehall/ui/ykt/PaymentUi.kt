package cn.edu.swun.swun_ehall.ui.ykt

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun PayMethodPicker(selected: YktPayMethod, onSelected: (YktPayMethod) -> Unit) {
    Text("支付方式", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
    TabRow(
        tabs = YktPayMethod.entries.map { it.label },
        selectedTabIndex = YktPayMethod.entries.indexOf(selected),
        onTabSelected = { onSelected(YktPayMethod.entries[it]) },
        modifier = Modifier.padding(top = 6.dp),
    )
    Text(
        selected.description,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        fontSize = 12.sp,
        modifier = Modifier.padding(start = 4.dp, top = 5.dp),
    )
}

@Composable
internal fun QuickAmounts(values: List<Int>, selected: String, onPick: (Int) -> Unit) {
    val cs = MiuixTheme.colorScheme
    val selectedAmount = selected.toDoubleOrNull()
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        values.forEach { value ->
            val on = selectedAmount == value.toDouble()
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (on) cs.primary else cs.surface)
                    .border(1.dp, if (on) cs.primary else cs.outline, RoundedCornerShape(9.dp))
                    .clickable { onPick(value) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "¥ $value",
                    color = if (on) cs.onPrimary else cs.onBackground,
                    fontSize = 12.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
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
