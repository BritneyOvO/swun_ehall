package cn.edu.swun.swun_ehall.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import cn.edu.swun.swun_ehall.data.session.Session
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun HomeScreen(session: Session, nav: NavHostController) {
    val weekdayName = listOf("", "一", "二", "三", "四", "五", "六", "日")
        .getOrElse(java.util.Calendar.getInstance().let {
            val d = it.get(java.util.Calendar.DAY_OF_WEEK)
            if (d == java.util.Calendar.SUNDAY) 7 else d - 1
        }) { "" }
    val today = session.todayLessons()
    val services = listOf(
        "一卡通" to "ykt",
        "课堂考勤" to "ktkq",
        "公寓打卡" to "clock",
        "选课" to "ktkq",
    )
    Scaffold(topBar = { SmallTopAppBar(title = "首页") }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text("你好，${session.displayName}", style = MiuixTheme.textStyles.title2)
            Text("星期$weekdayName", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            Spacer(Modifier.height(16.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.height(180.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                userScrollEnabled = false,
            ) {
                items(services.size) { i ->
                    val (label, route) = services[i]
                    Card(onClick = { nav.navigate(route) }) {
                        Column(Modifier.padding(14.dp)) {
                            Text(label, style = MiuixTheme.textStyles.body1)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("今日课程", style = MiuixTheme.textStyles.title3)
            Spacer(Modifier.height(8.dp))
            if (today.isEmpty()) {
                Card { Text("今天没有课", modifier = Modifier.padding(16.dp)) }
            } else {
                today.forEach { lesson ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        onClick = {
                            val act = session.ktkq.firstOrNull { it.classroom == lesson.room }
                            if (act != null) nav.navigate("ktkqSign/${act.activityId}")
                            else nav.navigate("ktkq")
                        },
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(lesson.name)
                            Text(
                                "${lesson.room} · ${lesson.teacher} · 第${lesson.start}节",
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                }
            }
        }
    }
}
