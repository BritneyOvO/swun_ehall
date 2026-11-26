package cn.edu.swun.swun_ehall.ui.schedule

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.edu.swun.swun_ehall.data.model.Lesson
import cn.edu.swun.swun_ehall.data.model.kPeriodTimes
import cn.edu.swun.swun_ehall.data.model.kWeekdayLabels
import cn.edu.swun.swun_ehall.ui.common.FeatureInfoRow
import java.time.LocalDate
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

@Composable
internal fun LessonDetailsSheet(lesson: Lesson, week: Int, date: LocalDate, onDismiss: () -> Unit) {
    val day = kWeekdayLabels[date.dayOfWeek.value]
    val dateText = "第 ${week} 周 · ${day} ${date.monthValue}月${date.dayOfMonth}日"
    val period = if (lesson.start == lesson.end) "第${lesson.start}节" else "第${lesson.start}–${lesson.end}节"
    val start = kPeriodTimes.firstOrNull { it.first == lesson.start }?.second?.first
    val end = kPeriodTimes.firstOrNull { it.first == lesson.end }?.second?.second
    val time = if (start != null && end != null) "${period} · ${start}–${end}" else period

    WindowBottomSheet(
        show = true,
        modifier = Modifier.fillMaxHeight(0.5f),
        title = "课程详情",
        endAction = { TextButton(text = "关闭", onClick = onDismiss) },
        onDismissRequest = onDismiss,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(top = 8.dp, bottom = 16.dp),
        ) {
            Text(
                lesson.name,
                style = MiuixTheme.textStyles.title2,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                FeatureInfoRow("上课日期", dateText)
                HorizontalDivider()
                FeatureInfoRow("上课时间", time)
                HorizontalDivider()
                FeatureInfoRow("教室", lesson.room)
                HorizontalDivider()
                FeatureInfoRow("教师", lesson.teacher)
            }
        }
    }
}
