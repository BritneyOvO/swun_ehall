package cn.edu.swun.swun_ehall.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import cn.edu.swun.swun_ehall.data.model.Lesson
import cn.edu.swun.swun_ehall.data.model.kPeriodTimes
import cn.edu.swun.swun_ehall.data.model.minutesNow
import cn.edu.swun.swun_ehall.data.session.Session
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
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

private data class QuickAction(val label: String, val route: String, val icon: ImageVector)
private data class ServiceGroup(
    val title: String,
    val icon: ImageVector,
    val items: List<Pair<String, String>>,
)

@Composable
fun HomeScreen(
    session: Session,
    nav: NavHostController,
    onOpenSchedule: () -> Unit,
) {
    val weekdayName = listOf("", "一", "二", "三", "四", "五", "六", "日")
        .getOrElse(java.util.Calendar.getInstance().let {
            val day = it.get(java.util.Calendar.DAY_OF_WEEK)
            if (day == java.util.Calendar.SUNDAY) 7 else day - 1
        }) { "" }
    var nowMinutes by remember { mutableIntStateOf(minutesNow()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30_000)
            nowMinutes = minutesNow()
        }
    }
    LaunchedEffect(session.loggedIn, session.checkUpdateOnLaunch, session.demoMode) {
        session.checkLatestIfEnabled()
    }

    val today = session.todayLessons()
    val remaining = today.filter { !it.ended(nowMinutes) }
    val next = remaining.firstOrNull()
    val quickActions = listOf(
        QuickAction("一卡通", "ykt", MiuixIcons.BankCards),
        QuickAction("课堂考勤", "ktkq", MiuixIcons.Location),
        QuickAction("考试", "exams", MiuixIcons.Notes),
        QuickAction("请假销假", "leave", MiuixIcons.Edit),
    )
    val groups = listOf(
        ServiceGroup("教务服务", MiuixIcons.Tasks, listOf("学分" to "credits", "选课" to "selection", "校历" to "calendar")),
        ServiceGroup(
            "生活缴费",
            MiuixIcons.BankCards,
            listOf("一卡通充值" to "ykt/recharge", "宿舍电费" to "electric", "热水充值" to "water"),
        ),
        ServiceGroup("校园生活", MiuixIcons.Alarm, listOf("公寓打卡" to "clock", "校车" to "bus")),
    )
    val cs = MiuixTheme.colorScheme
    val displayName = session.displayName.ifBlank { "同学" }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "民大助手",
                actions = {
                    Box(
                        Modifier
                            .padding(end = 12.dp)
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(cs.primary.copy(alpha = 0.1f))
                            .clickable { nav.navigate("profile") },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(displayName.take(1), color = cs.primary, fontWeight = FontWeight.SemiBold)
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
                .padding(horizontal = 16.dp),
        ) {
            Text(
                "周" + weekdayName + " · 第 " + session.curWeek + " 周" + if (session.demoMode) " · 预览" else "",
                color = cs.onSurfaceVariantSummary,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 12.dp),
            )
            NextLessonCard(next, onOpenSchedule)
            HomeSectionTitle("今日课程", if (today.isEmpty()) null else today.size.toString() + " 节")
            TodayLessons(
                lessons = today,
                next = next,
                emptyText = when {
                    session.scheduleError != null && session.schedule.isEmpty() -> "课表暂未加载，请到课表页重试"
                    today.isEmpty() -> "今天没有课"
                    else -> "今天的课程已结束"
                },
                onLesson = { nav.navigate(session.prepareKtkqSign(lesson = it, week = session.curWeek)) },
            )
            HomeSectionTitle("常用功能")
            QuickActions(quickActions) { nav.navigate(it) }
            HomeSectionTitle("校园服务")
            ServiceGroups(groups) { nav.navigate(it) }
            session.exams.firstOrNull()?.let { exam ->
                HomeSectionTitle("待办")
                Card(modifier = Modifier.fillMaxWidth(), onClick = { nav.navigate("exams") }) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(exam.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                listOf(exam.time, exam.place).filter { it.isNotBlank() }.joinToString(" · "),
                                color = cs.onSurfaceVariantSummary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 3.dp),
                            )
                        }
                        Text("查看", color = cs.primary, fontSize = 12.sp)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun NextLessonCard(lesson: Lesson?, onSchedule: () -> Unit) {
    val cs = MiuixTheme.colorScheme
    Card(modifier = Modifier.fillMaxWidth(), onClick = onSchedule) {
        Row(Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .padding(vertical = 12.dp)
                    .width(4.dp)
                    .height(112.dp)
                    .clip(RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp))
                    .background(cs.primary),
            )
            Column(Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (lesson == null) "今日安排" else "下一节课",
                        color = cs.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.weight(1f))
                    lesson?.let {
                        Text(periodRange(it), color = cs.primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Text(
                    lesson?.name ?: "当前没有待上课程",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    lesson?.let {
                        listOf(periodRange(it), it.room, it.teacher).filter { value -> value.isNotBlank() }.joinToString(" · ")
                    } ?: "点击查看本周课表",
                    color = cs.onSurfaceVariantSummary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun HomeSectionTitle(title: String, trailing: String? = null) {
    Row(
        Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp, start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MiuixTheme.textStyles.title3, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        trailing?.let { Text(it, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 12.sp) }
    }
}

@Composable
private fun TodayLessons(
    lessons: List<Lesson>,
    next: Lesson?,
    emptyText: String,
    onLesson: (Lesson) -> Unit,
) {
    val cs = MiuixTheme.colorScheme
    Card(modifier = Modifier.fillMaxWidth()) {
        if (lessons.isEmpty()) {
            Text(emptyText, color = cs.onSurfaceVariantSummary, modifier = Modifier.padding(16.dp))
        } else {
            lessons.forEachIndexed { index, lesson ->
                if (index > 0) HorizontalDivider(modifier = Modifier.padding(start = 78.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onLesson(lesson) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.width(62.dp)) {
                        Text(
                            kPeriodTimes.firstOrNull { it.first == lesson.start }?.second?.first.orEmpty(),
                            color = if (lesson == next) cs.primary else cs.onBackground,
                            fontWeight = if (lesson == next) FontWeight.SemiBold else FontWeight.Normal,
                            fontSize = 13.sp,
                        )
                        Text(
                            kPeriodTimes.firstOrNull { it.first == lesson.end }?.second?.second.orEmpty(),
                            color = cs.onSurfaceVariantSummary,
                            fontSize = 10.sp,
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(lesson.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            listOf(lesson.room, lesson.teacher).filter { it.isNotBlank() }.joinToString(" · "),
                            color = cs.onSurfaceVariantSummary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                    if (lesson == next) {
                        Text("下一节", color = cs.primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActions(actions: List<QuickAction>, onRoute: (String) -> Unit) {
    val cs = MiuixTheme.colorScheme
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            actions.forEachIndexed { index, action ->
                Column(
                    Modifier
                        .weight(1f)
                        .clickable { onRoute(action.route) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(action.icon, contentDescription = action.label, tint = cs.primary, modifier = Modifier.size(24.dp))
                    Text(action.label, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                }
                if (index < actions.lastIndex) {
                    Box(Modifier.width(1.dp).height(48.dp).background(cs.outline))
                }
            }
        }
    }
}

@Composable
private fun ServiceGroups(groups: List<ServiceGroup>, onRoute: (String) -> Unit) {
    val cs = MiuixTheme.colorScheme
    Card(modifier = Modifier.fillMaxWidth()) {
        groups.forEachIndexed { index, group ->
            if (index > 0) HorizontalDivider(modifier = Modifier.padding(start = 52.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(group.icon, contentDescription = group.title, tint = cs.primary, modifier = Modifier.size(22.dp))
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(group.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Row(
                        Modifier.fillMaxWidth().padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        group.items.forEachIndexed { itemIndex, item ->
                            if (itemIndex > 0) {
                                Text(" · ", color = cs.onSurfaceVariantSummary, fontSize = 12.sp)
                            }
                            Text(
                                item.first,
                                color = cs.onSurfaceVariantSummary,
                                fontSize = 12.sp,
                                modifier = Modifier.clickable { onRoute(item.second) }.padding(vertical = 4.dp),
                            )
                        }
                    }
                }
                Text("›", color = cs.onSurfaceVariantSummary, fontSize = 18.sp)
            }
        }
    }
}

private fun periodRange(lesson: Lesson): String {
    val start = kPeriodTimes.firstOrNull { it.first == lesson.start }?.second?.first
    val end = kPeriodTimes.firstOrNull { it.first == lesson.end }?.second?.second
    return listOfNotNull(start, end).joinToString("—")
}
