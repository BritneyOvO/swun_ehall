package cn.edu.swun.swun_ehall.ui.clock

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import cn.edu.swun.swun_ehall.data.session.Session
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ClockScreen(session: Session, nav: NavHostController) {
    var msg by remember { mutableStateOf(session.lastPunch) }
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "公寓打卡",
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
                Column(Modifier.padding(14.dp)) {
                    Text("在校打卡", style = MiuixTheme.textStyles.title3)
                    Text("提交坐标会走 GCJ-02 转换（gps/fused 转，高德不再转）")
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { msg = session.dormPunch(useCampusFence = false) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("立即打卡") }
            if (session.developerMode) {
                Spacer(Modifier.height(8.dp))
                TextButton(
                    text = "一键校内打卡",
                    onClick = { msg = session.dormPunch(useCampusFence = true) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            val punchMsg = msg
            if (punchMsg != null) {
                Spacer(Modifier.height(12.dp))
                Text(punchMsg, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
            Spacer(Modifier.height(16.dp))
            Text("最近记录", style = MiuixTheme.textStyles.title3)
            cn.edu.swun.swun_ehall.data.demo.DemoData.clockRecords.forEach { r ->
                Card(modifier = Modifier.padding(top = 8.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Text(r.time)
                        Text("${r.address} · ${r.status}", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    }
                }
            }
        }
    }
}
