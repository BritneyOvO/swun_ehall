package cn.edu.swun.swun_ehall.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.navigation.NavHostController
import cn.edu.swun.swun_ehall.data.model.Lesson
import cn.edu.swun.swun_ehall.data.session.Session
import cn.edu.swun.swun_ehall.ui.common.FeatureLoadingPage
import kotlinx.coroutines.launch
import cn.edu.swun.swun_ehall.ui.common.RefreshNav
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Undo
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ScheduleScreen(session: Session, nav: NavHostController) {
    var loading by remember { mutableStateOf(!session.demoMode && session.schedule.isEmpty()) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(session.loggedIn, session.demoMode) {
        if (!session.demoMode && session.schedule.isEmpty()) {
            loading = true
            session.refreshSchedule()
            loading = false
        }
    }
    val maxW = remember(session.schedule.size, session.totalWeek) {
        val fromMask = session.schedule.maxOfOrNull { it.weeks.length } ?: 0
        maxOf(session.totalWeek, fromMask, 16)
    }
    val cur = session.curWeek.coerceIn(1, maxW)
    val pager = rememberPagerState(initialPage = cur - 1) { maxW }
    var jumped by remember { mutableStateOf(false) }
    LaunchedEffect(session.schedule.size, cur) {
        if (!jumped && session.schedule.isNotEmpty()) {
            pager.scrollToPage(cur - 1)
            jumped = true
        }
    }
    val week = pager.currentPage + 1
    var picking by remember { mutableStateOf<List<Lesson>?>(null) }
    fun openSign(lesson: Lesson) {
        nav.navigate(session.prepareKtkqSign(lesson = lesson, week = week))
    }
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "课表",
                actions = {
                    RefreshNav {
                        scope.launch {
                            loading = true
                            session.refreshSchedule()
                            loading = false
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (loading) {
            FeatureLoadingPage(padding)
            return@Scaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            WeekBar(
                week = week,
                maxWeek = maxW,
                isCurrent = week == cur,
                onPrev = if (week > 1) {
                    { scope.launch { pager.animateScrollToPage(week - 2) } }
                } else {
                    null
                },
                onNext = if (week < maxW) {
                    { scope.launch { pager.animateScrollToPage(week) } }
                } else {
                    null
                },
                onCurrent = if (week != cur) {
                    { scope.launch { pager.animateScrollToPage(cur - 1) } }
                } else {
                    null
                },
            )
            Box(Modifier.weight(1f).fillMaxSize()) {
            when {
                session.schedule.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("本学期暂无课表")
                    }
                }
                else -> {
                    HorizontalPager(
                        state = pager,
                        modifier = Modifier.fillMaxSize(),
                    ) { page ->
                        val w = page + 1
                        CourseTable(
                            lessons = session.weekLessons(w),
                            week = w,
                            curWeek = cur,
                            choices = session.slotPicks,
                            onLessonTap = { openSign(it) },
                            onConflictTap = { picking = it },
                        )
                    }
                }
            }
            picking?.let { group ->
                val selected = session.slotPicks.chosen(week, group)
                Box(
                    Modifier
                        .fillMaxSize()
                        .zIndex(2f)
                        .background(Color.Black.copy(alpha = 0.35f))
                        .clickable { picking = null },
                    contentAlignment = Alignment.Center,
                ) {
                    Card(
                        modifier = Modifier
                            .padding(28.dp)
                            .fillMaxWidth()
                            .clickable { },
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("该时段有 ${group.size} 门课", style = MiuixTheme.textStyles.title3)
                            Text("选一门后所有周的课表、今日课程和打卡都用这门。长按格子可再改。", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                            Spacer(Modifier.padding(top = 8.dp))
                            group.forEach { l ->
                                val on = selected == l
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp)
                                        .clickable {
                                            session.pickSlot(week, group, l)
                                            picking = null
                                        },
                                ) {
                                    Column(Modifier.padding(12.dp)) {
                                        Text(if (on) "已选 · ${l.name}" else l.name)
                                        Text(
                                            listOf(
                                                l.teacher,
                                                l.room,
                                                "第${l.start}-${l.end}节",
                                            ).filter { it.isNotBlank() }.joinToString(" · "),
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
    }
    }
}

@Composable
private fun WeekBar(
    week: Int,
    maxWeek: Int,
    isCurrent: Boolean,
    onPrev: (() -> Unit)?,
    onNext: (() -> Unit)?,
    onCurrent: (() -> Unit)?,
) {
    val crimson = MiuixTheme.colorScheme.primary
    val muted = MiuixTheme.colorScheme.onSurfaceVariantSummary
    Row(
        Modifier.padding(start = 4.dp, top = 6.dp, end = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onPrev?.invoke() }, enabled = onPrev != null) {
            Text("‹", color = crimson, fontSize = 26.sp, fontWeight = FontWeight.Medium)
        }
        Text("第${week}周", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        IconButton(onClick = { onNext?.invoke() }, enabled = onNext != null) {
            Text("›", color = crimson, fontSize = 26.sp, fontWeight = FontWeight.Medium)
        }
        Text("/ $maxWeek", color = muted, fontSize = 12.sp)
        Spacer(Modifier.weight(1f))
        if (isCurrent) {
            Text("本周", color = muted, fontSize = 13.sp)
        } else {
            IconButton(onClick = { onCurrent?.invoke() }) {
                Icon(MiuixIcons.Undo, contentDescription = "回本周", tint = crimson)
            }
        }
    }
}
