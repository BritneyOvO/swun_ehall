package cn.edu.swun.swun_ehall.ui.ykt

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import cn.edu.swun.swun_ehall.data.session.Session
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun YktScreen(session: Session, nav: NavHostController) {
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "一卡通",
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        top.yukonga.miuix.kmp.basic.Icon(MiuixIcons.Back, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("余额", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    Text(
                        session.yktBalance?.let { "¥ ${"%.2f".format(it)}" } ?: "—",
                        style = MiuixTheme.textStyles.title1,
                    )
                    Text("付款码（预览）", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    Text("SWUN-${session.studentId.ifEmpty { "DEMO" }}")
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("今日余额使用明细", style = MiuixTheme.textStyles.title3)
            Spacer(Modifier.height(8.dp))
            session.yktBills.forEach { b ->
                Card(modifier = Modifier.padding(bottom = 8.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Text(b.title)
                        Text(
                            "${b.time} · ${b.amountYuan} 元",
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
        }
    }
}
