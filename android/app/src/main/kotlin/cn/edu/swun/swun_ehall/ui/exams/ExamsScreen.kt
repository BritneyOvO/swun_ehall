package cn.edu.swun.swun_ehall.ui.exams

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import cn.edu.swun.swun_ehall.data.session.Session
import cn.edu.swun.swun_ehall.ui.common.BackNav
import cn.edu.swun.swun_ehall.ui.common.FeatureBottomSpace
import cn.edu.swun.swun_ehall.ui.common.FeatureColumn
import cn.edu.swun.swun_ehall.ui.common.FeatureHint
import cn.edu.swun.swun_ehall.ui.common.FeatureInlineNotice
import cn.edu.swun.swun_ehall.ui.common.FeatureLoadingPage
import cn.edu.swun.swun_ehall.ui.common.FeatureSection
import cn.edu.swun.swun_ehall.ui.common.RefreshNav
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ExamsScreen(session: Session, nav: NavHostController) {
    var loading by remember { mutableStateOf(session.exams.isEmpty()) }
    val scope = rememberCoroutineScope()
    suspend fun refresh() {
        loading = true
        try {
            session.refreshExams()
        } finally {
            loading = false
        }
    }
    LaunchedEffect(session.loggedIn, session.demoMode) {
        refresh()
    }
    val cs = MiuixTheme.colorScheme
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "考试",
                navigationIcon = { BackNav { nav.popBackStack() } },
                actions = { RefreshNav { scope.launch { refresh() } } },
            )
        },
    ) { padding ->
        if (loading) {
            FeatureLoadingPage(padding)
            return@Scaffold
        }
        FeatureColumn(padding) {
            when {
                session.exams.isEmpty() -> FeatureHint(
                    title = "本学期暂无考试安排",
                    summary = "有新安排时会在这里显示",
                    action = { TextButton("重新加载", { scope.launch { refresh() } }) },
                )
                else -> {
                    val nearest = session.exams.first()
                    FeatureInlineNotice(
                        title = "最近考试 · ${nearest.name}",
                        summary = listOf(nearest.time, nearest.place).filter { it.isNotBlank() }.joinToString(" · "),
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    FeatureSection("本学期考试 · ${session.exams.size} 场")
                    Card(modifier = Modifier.fillMaxWidth()) {
                        session.exams.forEachIndexed { i, e ->
                            if (i > 0) HorizontalDivider(modifier = Modifier.padding(start = 19.dp))
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier
                                        .width(3.dp)
                                        .height(72.dp)
                                        .background(if (i == 0) cs.primary else cs.outline),
                                )
                                Column(Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 11.dp)) {
                                    Text(e.name)
                                    if (e.time.isNotEmpty()) Text(e.time, color = cs.onSurfaceVariantSummary)
                                    Row(Modifier.fillMaxWidth()) {
                                        if (e.place.isNotEmpty()) Text(e.place, color = cs.onSurfaceVariantSummary, modifier = Modifier.weight(1f))
                                        if (e.seat.isNotEmpty()) Text("座位 ${e.seat}", color = if (i == 0) cs.primary else cs.onSurfaceVariantSummary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            FeatureBottomSpace()
        }
    }
}
