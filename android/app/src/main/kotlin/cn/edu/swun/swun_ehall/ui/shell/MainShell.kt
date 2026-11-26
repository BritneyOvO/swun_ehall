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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import cn.edu.swun.swun_ehall.data.session.Session
import cn.edu.swun.swun_ehall.ui.grades.GradesScreen
import cn.edu.swun.swun_ehall.ui.home.HomeScreen
import cn.edu.swun.swun_ehall.ui.mine.MineScreen
import cn.edu.swun.swun_ehall.ui.schedule.ScheduleScreen
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

@Composable
fun MainShell(session: Session, nav: NavHostController) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    val items = listOf(
        MainDestination("首页", MiuixIcons.Home),
        MainDestination("课表", MiuixIcons.VerticalSplit),
        MainDestination("成绩", MiuixIcons.File),
        MainDestination("我的", MiuixIcons.Contacts),
    )

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (index) {
                0 -> HomeScreen(session, nav, onOpenSchedule = { index = 1 })
                1 -> ScheduleScreen(session, nav)
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
