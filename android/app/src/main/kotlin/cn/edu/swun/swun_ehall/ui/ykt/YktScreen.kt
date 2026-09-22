package cn.edu.swun.swun_ehall.ui.ykt

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import cn.edu.swun.swun_ehall.data.qr.Qr
import cn.edu.swun.swun_ehall.data.session.Session
import cn.edu.swun.swun_ehall.ui.common.BackNav
import cn.edu.swun.swun_ehall.ui.common.FeatureBottomSpace
import cn.edu.swun.swun_ehall.ui.common.FeatureColumn
import cn.edu.swun.swun_ehall.ui.common.RefreshNav
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun YktScreen(session: Session, nav: NavHostController) {
    var loading by remember { mutableStateOf(!session.demoMode && session.yktQr == null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(session.loggedIn, session.demoMode) {
        loading = true
        session.refreshYkt()
        loading = false
    }
    val qr = session.yktQr
    val bmp = remember(qr) { qr?.takeIf { it.isNotBlank() }?.let { Qr.bitmap(it, 512) } }
    val cs = MiuixTheme.colorScheme
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "一卡通",
                navigationIcon = { BackNav { nav.popBackStack() } },
                actions = {
                    RefreshNav {
                        scope.launch {
                            loading = true
                            session.refreshYkt()
                            loading = false
                        }
                    }
                },
            )
        },
    ) { padding ->
        FeatureColumn(padding) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, start = 4.dp, end = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text("余额", color = cs.onSurfaceVariantSummary, fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                Text(
                    session.yktBalance?.let { "¥ ${"%.2f".format(it)}" } ?: if (loading) "—" else "—",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Card(modifier = Modifier.padding(top = 12.dp).fillMaxWidth()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, top = 22.dp, end = 20.dp, bottom = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("付款码", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Spacer(Modifier.height(16.dp))
                    when {
                        bmp != null -> {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "付款码",
                                modifier = Modifier
                                    .size(240.dp)
                                    .background(Color.White),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                qr.orEmpty(),
                                color = cs.onSurfaceVariantSummary,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("请向收款设备出示", color = cs.onSurfaceVariantSummary, fontSize = 13.sp)
                        }
                        loading -> Text(
                            "正在获取付款码…",
                            color = cs.onSurfaceVariantSummary,
                            modifier = Modifier.padding(vertical = 48.dp),
                        )
                        else -> Text(
                            session.yktError ?: "暂无二维码",
                            color = if (session.yktError != null) cs.error else cs.onSurfaceVariantSummary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(32.dp),
                        )
                    }
                }
            }
            Card(modifier = Modifier.padding(top = 12.dp).fillMaxWidth()) {
                Column(Modifier.padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 8.dp)) {
                    Text("今日余额使用明细", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    when {
                        loading && session.yktBills.isEmpty() -> Text(
                            "正在读取明细…",
                            color = cs.onSurfaceVariantSummary,
                            modifier = Modifier.padding(vertical = 20.dp).fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                        session.yktBills.isEmpty() -> Text(
                            "暂无明细",
                            color = cs.onSurfaceVariantSummary,
                            modifier = Modifier.padding(vertical = 20.dp).fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                        else -> session.yktBills.forEachIndexed { i, b ->
                            if (i > 0) HorizontalDivider()
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                                    Text(b.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                    val sub = buildList {
                                        if (b.time.isNotEmpty()) add(b.time)
                                        b.balanceYuan?.let { add("余额 ¥ ${"%.2f".format(it)}") }
                                    }.joinToString("  ")
                                    if (sub.isNotEmpty()) {
                                        Text(sub, color = cs.onSurfaceVariantSummary, fontSize = 12.sp)
                                    }
                                }
                                val spend = b.amountYuan < 0
                                val sign = when {
                                    b.amountYuan > 0 -> "+"
                                    spend -> "-"
                                    else -> ""
                                }
                                Text(
                                    "$sign¥ ${"%.2f".format(kotlin.math.abs(b.amountYuan))}",
                                    color = if (spend) cs.primary else Color(0xFF2E9E5B),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                )
                            }
                        }
                    }
                }
            }
            Text(
                "温馨提示：二维码异常时请切换校园网后点右上角刷新。付款码请勿截图长时间外传。",
                color = cs.onSurfaceVariantSummary,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
            )
            FeatureBottomSpace()
        }
    }
}
