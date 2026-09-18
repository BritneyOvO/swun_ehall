package cn.edu.swun.swun_ehall.ui.schedule

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.edu.swun.swun_ehall.data.session.Session
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ScheduleScreen(session: Session) {
    val names = listOf("", "周一", "周二", "周三", "周四", "周五", "周六", "周日")
    Scaffold(topBar = { SmallTopAppBar(title = "课表") }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            if (session.schedule.isEmpty()) {
                Card { Text("暂无课表", modifier = Modifier.padding(16.dp)) }
            }
            session.schedule.groupBy { it.weekday }.toSortedMap().forEach { (day, list) ->
                Text(names.getOrElse(day) { "周$day" }, style = MiuixTheme.textStyles.title3)
                list.sortedBy { it.start }.forEach { lesson ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(lesson.name)
                            Text(
                                "${lesson.room} · ${lesson.teacher} · 第${lesson.start}-${lesson.start + lesson.span - 1}节",
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                }
            }
        }
    }
}
