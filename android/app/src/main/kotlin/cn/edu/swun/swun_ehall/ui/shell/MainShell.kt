package cn.edu.swun.swun_ehall.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import cn.edu.swun.swun_ehall.data.session.Session
import cn.edu.swun.swun_ehall.ui.grades.GradesScreen
import cn.edu.swun.swun_ehall.ui.home.HomeScreen
import cn.edu.swun.swun_ehall.ui.mine.MineScreen
import cn.edu.swun.swun_ehall.ui.schedule.ScheduleScreen
import cn.edu.swun.swun_ehall.ui.theme.ThemeMode
import cn.edu.swun.swun_ehall.ui.theme.ThemeSettings
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.rememberHazeState
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.File
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.VerticalSplit
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun MainShell(session: Session, nav: NavHostController, theme: ThemeSettings = viewModel()) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    val items = listOf(
        NavigationItem("首页", MiuixIcons.Home),
        NavigationItem("课表", MiuixIcons.VerticalSplit),
        NavigationItem("成绩", MiuixIcons.File),
        NavigationItem("我的", MiuixIcons.Contacts),
    )
    val cs = MiuixTheme.colorScheme
    val dark = when (theme.mode) {
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
        ThemeMode.System -> isSystemInDarkTheme()
    }
    val barColor = if (dark) {
        lerp(cs.background, cs.surfaceContainer, 0.42f)
    } else {
        lerp(cs.surfaceContainer, Color.Black, 0.045f)
    }
    val hazeState = rememberHazeState()
    val useGlass = theme.liquidGlass
    val useFloating = theme.floatingBar || useGlass
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .then(if (useGlass || theme.enableBlur) Modifier.hazeBehind(hazeState) else Modifier),
        ) {
            when (index) {
                0 -> HomeScreen(session, nav)
                1 -> ScheduleScreen(session, nav)
                2 -> GradesScreen(session)
                else -> MineScreen(session, nav)
            }
        }
        Box(Modifier.align(Alignment.BottomCenter)) {
            when {
                useFloating -> FloatingNavigationBar(
                    modifier = if (useGlass) Modifier.liquidGlass(hazeState) else Modifier,
                    color = if (useGlass) Color.Transparent else barColor,
                    showDivider = false,
                    shadowElevation = when {
                        useGlass -> 8.dp
                        dark -> 2.dp
                        else -> 10.dp
                    },
                ) {
                    items.forEachIndexed { i, item ->
                        FloatingNavigationBarItem(
                            selected = index == i,
                            onClick = { index = i },
                            icon = item.icon,
                            label = item.label,
                        )
                    }
                }
                else -> NavigationBar(
                    color = if (theme.enableBlur) barColor.copy(alpha = 0.72f) else barColor,
                    showDivider = true,
                    modifier = if (theme.enableBlur) {
                        Modifier.hazeBlur(
                            input = HazeInput.Sources(hazeState),
                            style = HazeBlurStyle { blurRadius(22.dp) },
                        )
                    } else {
                        Modifier
                    },
                ) {
                    items.forEachIndexed { i, item ->
                        NavigationBarItem(
                            selected = index == i,
                            onClick = { index = i },
                            icon = item.icon,
                            label = item.label,
                        )
                    }
                }
            }
        }
    }
}
