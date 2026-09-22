package cn.edu.swun.swun_ehall.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import cn.edu.swun.swun_ehall.data.session.Session
import cn.edu.swun.swun_ehall.data.update.Versions
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Alarm
import top.yukonga.miuix.kmp.icon.extended.BankCards
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Location
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Tasks
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun HomeScreen(session: Session, nav: NavHostController) {
    val weekdayName = listOf("", "一", "二", "三", "四", "五", "六", "日")
        .getOrElse(java.util.Calendar.getInstance().let {
            val d = it.get(java.util.Calendar.DAY_OF_WEEK)
            if (d == java.util.Calendar.SUNDAY) 7 else d - 1
        }) { "" }
    val today = session.todayLessons()
    LaunchedEffect(session.loggedIn, session.checkUpdateOnLaunch, session.demoMode) {
        session.checkLatestIfEnabled()
    }
    val update = session.latestRelease?.takeIf { Versions.isNewer(it) }
    val services = listOf(
        Triple("一卡通", "ykt", MiuixIcons.BankCards),
        Triple("学分", "credits", MiuixIcons.Tasks),
        Triple("考试", "exams", MiuixIcons.Notes),
        Triple("课堂考勤", "ktkq", MiuixIcons.Location),
        Triple("公寓打卡", "clock", MiuixIcons.Alarm),
        Triple("选课", "selection", MiuixIcons.Edit),
    )
    Scaffold(topBar = { SmallTopAppBar(title = "首页") }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text("民大助手", color = MiuixTheme.colorScheme.primary)
                    Text("你好，${session.displayName}", style = MiuixTheme.textStyles.title2, modifier = Modifier.padding(top = 8.dp))
                    Text(
                        "星期$weekdayName · 第${session.curWeek}周" + if (session.demoMode) " · 预览" else "",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            if (update != null) {
                Spacer(Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { nav.navigate("about") },
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("发现新版本 ${update.version}", color = MiuixTheme.colorScheme.primary)
                        Text(
                            "点按查看说明并下载安装",
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                services.chunked(3).forEach { row ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        row.forEach { (label, route, icon) ->
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f),
                                onClick = { nav.navigate(route) },
                            ) {
                                Column(
                                    Modifier.fillMaxSize().padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Icon(
                                        icon,
                                        contentDescription = label,
                                        tint = MiuixTheme.colorScheme.primary,
                                        modifier = Modifier.height(32.dp).fillMaxWidth(0.45f),
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(label, textAlign = TextAlign.Center, fontSize = 13.sp)
                                }
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MiuixTheme.colorScheme.primary),
                        )
                        Spacer(Modifier.size(8.dp))
                        Text("今日课程", style = MiuixTheme.textStyles.title3)
                    }
                    if (today.isEmpty()) {
                        Text(
                            "今天没有课",
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    } else {
                        today.forEach { lesson ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        nav.navigate(session.prepareKtkqSign(lesson = lesson, week = session.curWeek))
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            ) {
                                Text(lesson.name)
                                Text(
                                    "${lesson.room} · ${lesson.teacher} · 第${lesson.start}-${lesson.end}节",
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
