package cn.edu.swun.swun_ehall.ui.clock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import cn.edu.swun.swun_ehall.data.session.Session
import cn.edu.swun.swun_ehall.ui.common.BackNav
import cn.edu.swun.swun_ehall.ui.common.FeatureBottomSpace
import cn.edu.swun.swun_ehall.ui.common.FeatureColumn
import cn.edu.swun.swun_ehall.ui.common.FeatureNoticeHost
import cn.edu.swun.swun_ehall.ui.common.rememberFeatureNotices
import cn.edu.swun.swun_ehall.ui.common.FeatureLoadingPage
import cn.edu.swun.swun_ehall.ui.common.FeatureMutedPanel
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(44.dp).clip(CircleShape).background(cs.primary.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("今", color = cs.primary, fontWeight = FontWeight.SemiBold)
                        }
                        Column(Modifier.padding(start = 12.dp)) {
                            Text("今日尚未打卡", style = MiuixTheme.textStyles.title3)
                            Text("请在学校规定时间内完成", color = cs.onSurfaceVariantSummary, fontSize = 12.sp)
                        }
                    }
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
            FeatureSection("打卡记录")
            Card(modifier = Modifier.fillMaxWidth()) {
                if (session.clockRecords.isEmpty()) {
                    Text(if (session.clockError == null) "暂无打卡记录" else "记录暂未加载，点击右上角重试", modifier = Modifier.padding(16.dp), color = cs.onSurfaceVariantSummary)
                } else {
                    session.clockRecords.forEachIndexed { i, r ->
                        if (i > 0) HorizontalDivider()
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(r.time)
                                Text("${r.address} · 位置校验", color = cs.onSurfaceVariantSummary)
                            }
                            Text(
                                r.status,
                                color = if (r.status.contains("正常") || r.status.contains("成功")) {
                                    androidx.compose.ui.graphics.Color(0xFF347A59)
                                } else {
                                    cs.primary
                                },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
            FeatureMutedPanel(
                title = "定位说明",
                summary = "打卡前会请求当前位置，仅用于本次校内范围校验。",
                modifier = Modifier.padding(top = 14.dp),
            )
            FeatureBottomSpace()
        }
    }
}
