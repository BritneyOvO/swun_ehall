package cn.edu.swun.swun_ehall.ui.clock

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import cn.edu.swun.swun_ehall.data.model.GeoFix
import cn.edu.swun.swun_ehall.data.session.Session
import cn.edu.swun.swun_ehall.ui.common.BackNav
import cn.edu.swun.swun_ehall.ui.common.FeatureBottomSpace
import cn.edu.swun.swun_ehall.ui.common.FeatureColumn
import cn.edu.swun.swun_ehall.ui.common.FeatureNoticeHost
import cn.edu.swun.swun_ehall.ui.common.rememberFeatureNotices
import cn.edu.swun.swun_ehall.ui.common.FeatureLoadingPage
import cn.edu.swun.swun_ehall.ui.common.FeatureSection
import cn.edu.swun.swun_ehall.ui.common.RefreshNav
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ClockScreen(session: Session, nav: NavHostController) {
    val notices = rememberFeatureNotices(session.studentId, session.demoMode)
    FeatureNoticeHost(notices)
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(!session.demoMode) }
    var me by remember { mutableStateOf<GeoFix?>(null) }
    suspend fun refresh() {
        loading = true
        try {
            session.refreshClock()
            session.clockError?.let { notices.error(it, "公寓服务暂不可用") }
        } finally {
            loading = false
        }
    }
    suspend fun punch(useCampusFence: Boolean) {
        if (busy) return
        busy = true
        try {
            notices.message(session.dormPunch(useCampusFence))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            notices.error(e.message ?: "打卡失败", "打卡未完成")
        } finally {
            busy = false
        }
    }
    LaunchedEffect(session.loggedIn, session.demoMode) {
        refresh()
        me = try {
            session.locateCampus()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }
    val cs = MiuixTheme.colorScheme
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "公寓打卡",
                navigationIcon = { BackNav { nav.popBackStack() } },
                actions = {
                    RefreshNav {
                        scope.launch { refresh() }
                    }
                },
            )
        },
    ) { padding ->
        if (loading) {
            FeatureLoadingPage(padding)
            return@Scaffold
        }
        FeatureColumn(padding) {
            Card(modifier = Modifier.padding(top = 12.dp).fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("在校打卡", style = MiuixTheme.textStyles.title3)
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (busy) return@Button
                            scope.launch { punch(useCampusFence = false) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy,
                    ) { Text(if (busy) "打卡中…" else "立即打卡") }
                    if (session.developerMode) {
                        Spacer(Modifier.height(8.dp))
                        TextButton(
                            text = "一键校内打卡",
                            onClick = {
                                scope.launch { punch(useCampusFence = true) }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !busy,
                        )
                    }
                }
            }
            CampusMap(me, session.clockFences.toList(), modifier = Modifier.padding(top = 12.dp))
            FeatureSection("最近记录")
            Card(modifier = Modifier.fillMaxWidth()) {
                if (session.clockRecords.isEmpty()) {
                    Text(if (session.clockError == null) "暂无打卡记录" else "记录暂未加载，点击右上角重试", modifier = Modifier.padding(16.dp), color = cs.onSurfaceVariantSummary)
                } else {
                    session.clockRecords.forEachIndexed { i, r ->
                        if (i > 0) HorizontalDivider()
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text(r.time)
                            Text("${r.address} · ${r.status}", color = cs.onSurfaceVariantSummary)
                        }
                    }
                }
            }
            FeatureBottomSpace()
        }
    }
}
