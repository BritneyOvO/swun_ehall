package cn.edu.swun.swun_ehall.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import cn.edu.swun.swun_ehall.data.session.Session
import cn.edu.swun.swun_ehall.ui.grades.GradesScreen
import cn.edu.swun.swun_ehall.ui.home.HomeScreen
import cn.edu.swun.swun_ehall.ui.mine.MineScreen
import cn.edu.swun.swun_ehall.ui.schedule.ScheduleScreen
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.File
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.VerticalSplit

@Composable
fun MainShell(session: Session, nav: NavHostController) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    val items = listOf(
        NavigationItem("首页", MiuixIcons.Home),
        NavigationItem("课表", MiuixIcons.VerticalSplit),
        NavigationItem("成绩", MiuixIcons.File),
        NavigationItem("我的", MiuixIcons.Contacts),
    )
    Scaffold(
        bottomBar = {
            NavigationBar {
                items.forEachIndexed { i, item ->
                    NavigationBarItem(
                        selected = index == i,
                        onClick = { index = i },
                        icon = item.icon,
                        label = item.label,
                    )
                }
            }
        },
    ) {
        Box(Modifier.fillMaxSize()) {
            when (index) {
                0 -> HomeScreen(session, nav)
                1 -> ScheduleScreen(session)
                2 -> GradesScreen(session)
                else -> MineScreen(session, nav)
            }
        }
    }
}
