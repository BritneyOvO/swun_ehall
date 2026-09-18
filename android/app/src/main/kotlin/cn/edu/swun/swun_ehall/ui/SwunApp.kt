package cn.edu.swun.swun_ehall.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import cn.edu.swun.swun_ehall.data.session.Session
import cn.edu.swun.swun_ehall.ui.clock.ClockScreen
import cn.edu.swun.swun_ehall.ui.ktkq.KtkqScreen
import cn.edu.swun.swun_ehall.ui.ktkq.KtkqSignScreen
import cn.edu.swun.swun_ehall.ui.login.LoginScreen
import cn.edu.swun.swun_ehall.ui.settings.AboutScreen
import cn.edu.swun.swun_ehall.ui.settings.LocateTestScreen
import cn.edu.swun.swun_ehall.ui.settings.SettingsScreen
import cn.edu.swun.swun_ehall.ui.shell.MainShell
import cn.edu.swun.swun_ehall.ui.theme.SwunTheme
import cn.edu.swun.swun_ehall.ui.ykt.YktScreen

@Composable
fun SwunApp(session: Session = viewModel()) {
    SwunTheme {
        val nav = rememberNavController()
        val start = remember(session.loggedIn) { if (session.loggedIn) "shell" else "login" }
        NavHost(navController = nav, startDestination = start) {
            composable("login") {
                LoginScreen(
                    session = session,
                    onLoggedIn = {
                        nav.navigate("shell") {
                            popUpTo("login") { inclusive = true }
                        }
                    },
                )
            }
            composable("shell") {
                MainShell(session = session, nav = nav)
            }
            composable("ktkq") { KtkqScreen(session, nav) }
            composable(
                "ktkqSign/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                KtkqSignScreen(session, nav, id)
            }
            composable("ykt") { YktScreen(session, nav) }
            composable("clock") { ClockScreen(session, nav) }
            composable("settings") { SettingsScreen(session, nav) }
            composable("about") { AboutScreen(session, nav) }
            composable("locateTest") { LocateTestScreen(session, nav) }
        }
    }
}

fun NavHostController.go(route: String) {
    navigate(route)
}
