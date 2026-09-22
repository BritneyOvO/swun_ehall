package cn.edu.swun.swun_ehall.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import cn.edu.swun.swun_ehall.data.session.Session
import cn.edu.swun.swun_ehall.ui.clock.ClockScreen
import cn.edu.swun.swun_ehall.ui.credits.CreditsScreen
import cn.edu.swun.swun_ehall.ui.exams.ExamsScreen
import cn.edu.swun.swun_ehall.ui.ktkq.KtkqScreen
import cn.edu.swun.swun_ehall.ui.ktkq.KtkqSignScreen
import cn.edu.swun.swun_ehall.ui.login.LoginScreen
import cn.edu.swun.swun_ehall.ui.mine.AvatarCropScreen
import cn.edu.swun.swun_ehall.ui.selection.SelectionScreen
import cn.edu.swun.swun_ehall.ui.accounts.AccountsScreen
import cn.edu.swun.swun_ehall.ui.accounts.AddAccountScreen
import cn.edu.swun.swun_ehall.ui.settings.AboutScreen
import cn.edu.swun.swun_ehall.ui.settings.LocateTestScreen
import cn.edu.swun.swun_ehall.ui.settings.ProfileScreen
import cn.edu.swun.swun_ehall.ui.settings.ThemeScreen
import cn.edu.swun.swun_ehall.ui.shell.MainShell
import cn.edu.swun.swun_ehall.ui.theme.SwunTheme
import cn.edu.swun.swun_ehall.ui.theme.ThemeSettings
import cn.edu.swun.swun_ehall.ui.ykt.YktScreen

@Composable
fun SwunApp(
    playSplash: Boolean = true,
    session: Session = viewModel(),
    theme: ThemeSettings = viewModel(),
) {
    SwunTheme(theme) {
        val nav = rememberNavController()
        LaunchedEffect(session.loggedIn) {
            val dest = if (session.loggedIn) "shell" else "login"
            val current = nav.currentDestination?.route ?: return@LaunchedEffect
            if (current == dest) return@LaunchedEffect
            if (session.loggedIn) {
                nav.navigate("shell") {
                    popUpTo("login") { inclusive = true }
                    launchSingleTop = true
                }
            } else if (current != "login") {
                nav.navigate("login") { popUpTo(0) { inclusive = true } }
            }
        }
        val entry by nav.currentBackStackEntryAsState()
        val canPop = nav.previousBackStackEntry != null && entry?.destination?.route != "login"
        if (!theme.predictiveBack && canPop) {
            BackHandler { nav.popBackStack() }
        }
        var splash by remember { mutableStateOf(playSplash) }
        Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val s = theme.pageScale
                    scaleX = s
                    scaleY = s
                },
        ) {
            NavHost(
                navController = nav,
                startDestination = if (session.loggedIn) "shell" else "login",
                enterTransition = {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Start,
                        animationSpec = tween(280),
                    ) + fadeIn(tween(220))
                },
                exitTransition = {
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Start,
                        animationSpec = tween(280),
                    ) + fadeOut(tween(180))
                },
                popEnterTransition = {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.End,
                        animationSpec = tween(280),
                    ) + fadeIn(tween(220))
                },
                popExitTransition = {
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.End,
                        animationSpec = tween(280),
                    ) + fadeOut(tween(180))
                },
            ) {
                composable("login") { LoginScreen(session = session) }
                composable("shell") { MainShell(session = session, nav = nav, theme = theme) }
                composable("ktkq") { KtkqScreen(session, nav) }
                composable(
                    "ktkqSign/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.StringType }),
                ) { entry ->
                    KtkqSignScreen(session, nav, entry.arguments?.getString("id").orEmpty())
                }
                composable("ykt") { YktScreen(session, nav) }
                composable("clock") { ClockScreen(session, nav) }
                composable("credits") { CreditsScreen(session, nav) }
                composable("exams") { ExamsScreen(session, nav) }
                composable("selection") { SelectionScreen(session, nav) }
                composable("accounts") { AccountsScreen(session, nav) }
                composable("accounts/add") { AddAccountScreen(session, nav) }
                composable("theme") { ThemeScreen(theme, nav) }
                composable("profile") { ProfileScreen(session, nav) }
                composable("about") { AboutScreen(session, nav) }
                composable("locateTest") { LocateTestScreen(session, nav) }
            }
        }
        if (splash) {
            SplashOverlay { splash = false }
        }
        if (session.pendingCropPath != null) {
            AvatarCropScreen(session) { session.finishAvatarCrop() }
        }
        }
    }
}

fun NavHostController.go(route: String) {
    navigate(route)
}
