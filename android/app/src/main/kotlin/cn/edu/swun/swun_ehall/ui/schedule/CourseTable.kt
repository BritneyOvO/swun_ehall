@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package cn.edu.swun.swun_ehall.ui.schedule

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.edu.swun.swun_ehall.data.model.Lesson
import cn.edu.swun.swun_ehall.data.model.kPeriodTimes
import cn.edu.swun.swun_ehall.data.session.SlotChoiceStore
import java.time.LocalDate
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val LessonPalette = listOf(
    Color(0xFF9B1B30),
    Color(0xFF3D6B5E),
    Color(0xFF4A6080),
    Color(0xFF6A4E6E),
    Color(0xFF8A5A3A),
    Color(0xFF4E6B3E),
    Color(0xFF5A6278),
    Color(0xFF6D4C41),
)

private val DayNames = listOf("", "周一", "周二", "周三", "周四", "周五", "周六", "周日")
private val PeriodH = 64.dp
private val TimeW = 36.dp
private val HeadH = 46.dp

fun lessonColor(name: String): Color {
    var h = 0
    for (c in name) h = (h * 31 + c.code) and 0x7fffffff
    return LessonPalette[h % LessonPalette.size]
}

fun mondayOfSchoolWeek(week: Int, curWeek: Int): LocalDate {
    val n = LocalDate.now()
    val thisMonday = n.minusDays((n.dayOfWeek.value - 1).toLong())
    return thisMonday.plusDays(7L * (week - curWeek))
}

@Composable
private fun RoomLabel(room: String, color: Color, maxLines: Int) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val maxW = constraints.maxWidth.coerceAtLeast(1)
        var size = 10f
        var layout = measurer.measure(
            text = room,
            style = TextStyle(fontSize = size.sp, lineHeight = (size + 2f).sp, fontWeight = FontWeight.Medium),
            overflow = TextOverflow.Clip,
            softWrap = true,
            maxLines = maxLines,
            constraints = Constraints(maxWidth = maxW),
        )
        while (layout.hasVisualOverflow && size > 7.5f) {
            size -= 0.5f
            layout = measurer.measure(
                text = room,
                style = TextStyle(fontSize = size.sp, lineHeight = (size + 2f).sp, fontWeight = FontWeight.Medium),
                overflow = TextOverflow.Clip,
                softWrap = true,
                maxLines = maxLines,
                constraints = Constraints(maxWidth = maxW),
            )
        }
        Text(
            room,
            color = color,
            fontSize = size.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = (size + 2f).sp,
            maxLines = maxLines,
            overflow = TextOverflow.Clip,
            softWrap = true,
        )
    }
}

fun maxPeriodOf(lessons: List<Lesson>): Int {
    var n = kPeriodTimes.size.coerceAtLeast(10)
    for (l in lessons) if (l.end > n) n = l.end
    return n.coerceIn(1, 16)
}

@Composable
fun CourseTable(
    lessons: List<Lesson>,
    week: Int,
    curWeek: Int,
    choices: SlotChoiceStore,
    onLessonTap: (Lesson) -> Unit,
    onConflictTap: (List<Lesson>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val periods = remember(lessons) { maxPeriodOf(lessons) }
    val pickRev = choices.generation
    val monday = remember(week, curWeek) { mondayOfSchoolWeek(week, curWeek) }
    val today = LocalDate.now()
    val ink = MiuixTheme.colorScheme.onBackground
    val line = ink.copy(alpha = 0.14f)
    val muted = MiuixTheme.colorScheme.onSurfaceVariantSummary
    val primary = MiuixTheme.colorScheme.primary
    Column(modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(HeadH),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(TimeW))
            (1..7).forEach { d ->
                val date = monday.plusDays((d - 1).toLong())
                val active = date == today
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        DayNames[d],
                        fontSize = 12.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (active) primary else muted,
                    )
                    Text(
                        "${date.monthValue}/${date.dayOfMonth}",
                        fontSize = 10.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (active) primary else muted,
                    )
                }
            }
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(PeriodH * periods),
            ) {
                Column(Modifier.width(TimeW).fillMaxHeight()) {
                    val byIndex = kPeriodTimes.associate { it.first to it.second }
                    (1..periods).forEach { p ->
                        val t = byIndex[p]
                        Column(
                            Modifier
                                .width(TimeW)
                                .height(PeriodH)
                                .padding(end = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("$p", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = ink)
                            if (t != null) {
                                Text(t.first, fontSize = 9.sp, color = muted)
                                Text(t.second, fontSize = 9.sp, color = muted)
                            }
                        }
                    }
                }
                BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                    val colW = maxWidth / 7
                    Canvas(Modifier.fillMaxSize()) {
                        val cw = size.width / 7f
                        val ph = size.height / periods
                        val vp = line
                        for (d in 1..7) {
                            val x = d * cw
                            drawLine(vp, Offset(x, 0f), Offset(x, size.height), 1f)
                        }
                        for (p in 0..periods) {
                            val y = p * ph
                            drawLine(vp, Offset(0f, y), Offset(size.width, y), 1f)
                        }
                    }
                    (1..7).forEach { day ->
                        val groups = remember(day, lessons, pickRev) {
                            choices.groups(lessons.filter { it.weekday == day })
                        }
                        groups.forEach { group ->
                            val chosen = choices.chosen(week, group)
                            val conflict = group.size > 1
                            val l = chosen
                            val start = l?.start ?: group.minOf { it.start }
                            val span = l?.span ?: (group.maxOf { it.end } - start + 1)
                            Box(
                                Modifier
                                    .offset(
                                        x = colW * (day - 1) + 2.dp,
                                        y = PeriodH * (start - 1) + 2.dp,
                                    )
                                    .width(colW - 4.dp)
                                    .height(PeriodH * span - 4.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .then(
                                        if (l != null) Modifier.background(lessonColor(l.name))
                                        else Modifier
                                            .background(muted.copy(alpha = 0.18f))
                                            .border(1.dp, primary.copy(alpha = 0.45f), RoundedCornerShape(8.dp)),
                                    )
                                    .combinedClickable(
                                        onClick = {
                                            if (l == null) onConflictTap(group) else onLessonTap(l)
                                        },
                                        onLongClick = { if (conflict) onConflictTap(group) },
                                    )
                                    .padding(start = 4.dp, top = 4.dp, end = 3.dp, bottom = 3.dp),
                            ) {
                                if (l == null) {
                                    Column {
                                        Text("点选课程", color = primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Text("${group.size} 门课重叠", color = muted, fontSize = 10.sp)
                                    }
                                } else {
                                    Column(Modifier.fillMaxSize()) {
                                        Text(
                                            if (conflict) "▾ ${l.name}" else l.name,
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = if (span >= 3) 3 else if (span >= 2) 2 else 1,
                                            overflow = TextOverflow.Ellipsis,
                                            lineHeight = 13.sp,
                                            modifier = Modifier.weight(1f, fill = false),
                                        )
                                        if (l.teacher.isNotEmpty()) {
                                            Text(
                                                l.teacher,
                                                color = Color.White.copy(alpha = 0.7f),
                                                fontSize = 9.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        if (l.room.isNotEmpty()) {
                                            RoomLabel(
                                                room = l.room,
                                                color = Color.White.copy(alpha = 0.92f),
                                                maxLines = if (span >= 2) 3 else 2,
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
