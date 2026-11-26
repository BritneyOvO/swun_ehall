package cn.edu.swun.swun_ehall.ui.calendar

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import cn.edu.swun.swun_ehall.data.model.CalendarEvent
import cn.edu.swun.swun_ehall.data.model.SchoolCalendar
import cn.edu.swun.swun_ehall.data.session.Session
import cn.edu.swun.swun_ehall.ui.common.BackNav
import cn.edu.swun.swun_ehall.ui.common.FeatureLoadingPage
import cn.edu.swun.swun_ehall.ui.common.RefreshNav
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun CalendarScreen(session: Session, nav: NavHostController) {
    var loading by remember { mutableStateOf(!session.demoMode && session.schoolCalendar == null) }
    val scope = rememberCoroutineScope()
    suspend fun refresh() {
        if (session.demoMode) return
        loading = session.schoolCalendar == null
        try {
            session.refreshCalendar()
        } finally {
            loading = false
        }
    }
    LaunchedEffect(session.loggedIn, session.demoMode) {
        if (!session.demoMode && session.schoolCalendar == null) refresh()
    }
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "校历",
                navigationIcon = { BackNav { nav.popBackStack() } },
                actions = { RefreshNav { scope.launch { refresh() } } },
            )
        },
    ) { padding ->
        if (loading) {
            FeatureLoadingPage(padding)
            return@Scaffold
        }
        val calendar = session.schoolCalendar
        if (calendar == null) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(session.calendarError ?: "没有获取到校历", textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                TextButton("重试", { scope.launch { refresh() } })
            }
            return@Scaffold
        }
        CalendarContent(calendar, Modifier.padding(padding))
    }
}

@Composable
private fun CalendarContent(calendar: SchoolCalendar, modifier: Modifier = Modifier) {
    val today = LocalDate.now()
    val classDays = remember(calendar.classDays) { calendar.classDays.toHashSet() }
    val months = remember(calendar.startDate, calendar.endDate) {
        monthsBetween(calendar.startDate, calendar.endDate)
    }
    val shownMonth = remember(months, today) {
        YearMonth.from(today).takeIf { it in months } ?: months.firstOrNull()
    }
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        item {
            Card(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Column(Modifier.padding(18.dp)) {
                    Text(
                        calendar.termTitle.ifBlank { "本学期校历" },
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp,
                    )
                    Text(
                        listOfNotNull(calendar.startDate?.let(::dateText), calendar.endDate?.let(::dateText))
                            .joinToString(" — ").ifBlank { "日期待学校发布" },
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(top = 5.dp),
                    )
                    calendar.weekOf(today)?.let {
                        Text("今天是第 $it 教学周", color = MiuixTheme.colorScheme.primary, modifier = Modifier.padding(top = 7.dp))
                    }
                }
            }
        }
        shownMonth?.let { month ->
            item {
                MonthCard(month, classDays, today)
            }
        }
        if (calendar.events.isNotEmpty()) {
            item {
                Text("校历事件", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 18.dp, start = 4.dp, bottom = 7.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        calendar.events.forEach { event -> EventRow(event) }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun EventRow(event: CalendarEvent) {
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(event.title, fontWeight = FontWeight.SemiBold)
            val range = listOfNotNull(event.startDate?.let(::dateText), event.endDate?.let(::dateText))
                .distinct().joinToString(" — ")
            if (range.isNotEmpty()) Text(range, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun MonthCard(month: YearMonth, classDays: Set<LocalDate>, today: LocalDate) {
    val leading = month.atDay(1).dayOfWeek.value - 1
    val cells = remember(month) {
        val values = MutableList<LocalDate?>(leading) { null }
        (1..month.lengthOfMonth()).forEach { values += month.atDay(it) }
        while (values.size % 7 != 0) values += null
        values.chunked(7)
    }
    Text("${month.year}年${month.monthValue}月", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 18.dp, start = 4.dp, bottom = 7.dp))
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp)) {
            Row(Modifier.fillMaxWidth()) {
                listOf("一", "二", "三", "四", "五", "六", "日").forEach {
                    Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 12.sp)
                }
            }
            cells.forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { date -> DayCell(date, date != null && date in classDays, date == today) }
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.DayCell(date: LocalDate?, classDay: Boolean, today: Boolean) {
    val primary = MiuixTheme.colorScheme.primary
    Box(
        Modifier.weight(1f).aspectRatio(1f).padding(3.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (date != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(if (today) primary else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${date.dayOfMonth}",
                        color = if (today) Color.White else MiuixTheme.colorScheme.onBackground,
                        fontSize = 12.sp,
                        fontWeight = if (today) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
                Text(
                    if (classDay) "" else "休",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 8.sp,
                )
            }
        }
    }
}

private fun monthsBetween(start: LocalDate?, end: LocalDate?): List<YearMonth> {
    if (start == null || end == null || end.isBefore(start)) return emptyList()
    val first = YearMonth.from(start)
    val last = YearMonth.from(end)
    return generateSequence(first) { it.plusMonths(1) }.takeWhile { it <= last }.take(12).toList()
}

private fun dateText(date: LocalDate): String = "${date.year}年${date.monthValue}月${date.dayOfMonth}日"
