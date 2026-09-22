package cn.edu.swun.swun_ehall.ui.exams

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import cn.edu.swun.swun_ehall.data.session.Session
import cn.edu.swun.swun_ehall.ui.common.BackNav
import cn.edu.swun.swun_ehall.ui.common.FeatureBottomSpace
import cn.edu.swun.swun_ehall.ui.common.FeatureColumn
import cn.edu.swun.swun_ehall.ui.common.FeatureHint
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ExamsScreen(session: Session, nav: NavHostController) {
    var loading by remember { mutableStateOf(session.exams.isEmpty()) }
    LaunchedEffect(session.loggedIn, session.demoMode) {
        loading = true
        session.refreshExams()
        loading = false
    }
    val cs = MiuixTheme.colorScheme
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "考试",
                navigationIcon = { BackNav { nav.popBackStack() } },
            )
        },
    ) { padding ->
        FeatureColumn(padding) {
            when {
                loading && session.exams.isEmpty() -> FeatureHint("正在拉取考试安排…")
                session.exams.isEmpty() -> FeatureHint("本学期暂无考试安排")
                else -> Card(modifier = Modifier.padding(top = 12.dp).fillMaxWidth()) {
                    session.exams.forEachIndexed { i, e ->
                        if (i > 0) HorizontalDivider()
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text(e.name)
                            if (e.time.isNotEmpty()) Text("时间  ${e.time}", color = cs.onSurfaceVariantSummary)
                            if (e.place.isNotEmpty()) Text("地点  ${e.place}", color = cs.onSurfaceVariantSummary)
                            if (e.seat.isNotEmpty()) Text("座位  ${e.seat}", color = cs.onSurfaceVariantSummary)
                        }
                    }
                }
            }
            FeatureBottomSpace()
        }
    }
}
