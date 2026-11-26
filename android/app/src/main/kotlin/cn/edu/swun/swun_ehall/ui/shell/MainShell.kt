package cn.edu.swun.swun_ehall.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import cn.edu.swun.swun_ehall.data.model.Lesson
import cn.edu.swun.swun_ehall.data.session.Session
import cn.edu.swun.swun_ehall.ui.grades.GradesScreen
import cn.edu.swun.swun_ehall.ui.home.HomeScreen
import cn.edu.swun.swun_ehall.ui.mine.MineScreen
import cn.edu.swun.swun_ehall.ui.schedule.LessonDetailsSheet
import cn.edu.swun.swun_ehall.ui.schedule.ScheduleScreen
import cn.edu.swun.swun_ehall.ui.schedule.mondayOfSchoolWeek
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.File
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.VerticalSplit
import top.yukonga.miuix.kmp.theme.MiuixTheme

private data class MainDestination(
    val label: String,
    val icon: ImageVector,
)

private data class LessonSelection(val lesson: Lesson, val week: Int)

@Composable
fun MainShell(session: Session, nav: NavHostController) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    var selection by remember { mutableStateOf<LessonSelection?>(null) }
    fun showLesson(lesson: Lesson, week: Int) {
        selection = LessonSelection(lesson, week)
    }

    val items = listOf(
        MainDestination("首页", MiuixIcons.Home),
        MainDestination("课表", MiuixIcons.VerticalSplit),
        MainDestination("成绩", MiuixIcons.File),
        MainDestination("我的", MiuixIcons.Contacts),
    )

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (index) {
                0 -> HomeScreen(session, nav, onLessonTap = { showLesson(it, session.curWeek) })
                1 -> ScheduleScreen(session, onLessonTap = ::showLesson)
                2 -> GradesScreen(session)
                else -> MineScreen(session, nav)
            }
        }
        MainBottomBar(
            items = items,
            selected = index,
            onSelected = { index = it },
        )
    }
    selection?.let { (lesson, week) ->
        val monday = mondayOfSchoolWeek(week, session.curWeek, session.week1Monday)
        LessonDetailsSheet(
            lesson = lesson,
            week = week,
            date = monday.plusDays((lesson.weekday - 1).toLong()),
            onDismiss = { selection = null },
        )
    }
}

@Composable
private fun MainBottomBar(
    items: List<MainDestination>,
    selected: Int,
    onSelected: (Int) -> Unit,
) {
    val cs = MiuixTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .background(cs.surface),
    ) {
        HorizontalDivider()
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(64.dp)
                .selectableGroup(),
        ) {
            items.forEachIndexed { index, item ->
                val active = index == selected
                val tone = if (active) cs.primary else cs.onSurfaceVariantSummary
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(selected = active, role = Role.Tab) { onSelected(index) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .height(3.dp)
                            .fillMaxWidth(0.22f)
                            .background(if (active) cs.primary else cs.surface),
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                item.icon,
                                contentDescription = null,
                                tint = tone,
                                modifier = Modifier.size(23.dp),
                            )
                            Text(
                                item.label,
                                color = tone,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
