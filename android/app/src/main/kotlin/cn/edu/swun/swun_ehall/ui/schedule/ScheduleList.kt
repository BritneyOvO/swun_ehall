package cn.edu.swun.swun_ehall.ui.schedule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.edu.swun.swun_ehall.data.model.Lesson
import java.time.LocalDate
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ScheduleList(
    lessons: List<Lesson>,
    week: Int,
    curWeek: Int,
    week1Monday: LocalDate?,
    onLessonTap: (Lesson) -> Unit,
) {
    val sorted = remember(lessons) { lessons.sortedWith(compareBy({ it.weekday }, { it.start }, { it.name })) }
    if (sorted.isEmpty()) {
        Text(
            "本周没有课",
            modifier = Modifier.fillMaxSize().padding(top = 72.dp),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
        )
        return
    }
    val monday = remember(week, curWeek, week1Monday) { mondayOfSchoolWeek(week, curWeek, week1Monday) }
    val days = remember(sorted) { sorted.groupBy { it.weekday }.toSortedMap().toList() }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        items(days, key = { it.first }) { (weekday, dayLessons) ->
            val date = monday.plusDays((weekday - 1).toLong())
            Text(
                "${dayName(weekday)}  ${date.monthValue}月${date.dayOfMonth}日",
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 14.dp, bottom = 7.dp, start = 4.dp),
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                dayLessons.forEachIndexed { index, lesson ->
                    if (index > 0) HorizontalDivider()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onLessonTap(lesson) }
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(lesson.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            val detail = listOf(lesson.teacher, lesson.room)
                                .filter { it.isNotBlank() }
                                .joinToString(" · ")
                            if (detail.isNotEmpty()) {
                                Text(
                                    detail,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 3.dp),
                                )
                            }
                        }
                        Spacer(Modifier.padding(horizontal = 6.dp))
                        Text(
                            "第${lesson.start}-${lesson.end}节",
                            color = MiuixTheme.colorScheme.primary,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.padding(bottom = 16.dp)) }
    }
}

private fun dayName(day: Int): String =
    listOf("", "周一", "周二", "周三", "周四", "周五", "周六", "周日").getOrElse(day) { "" }
