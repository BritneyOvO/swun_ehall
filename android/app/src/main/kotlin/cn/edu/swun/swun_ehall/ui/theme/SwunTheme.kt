package cn.edu.swun.swun_ehall.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowInsetsControllerCompat
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@Composable
fun SwunTheme(settings: ThemeSettings, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (settings.mode) {
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
        ThemeMode.System -> systemDark
    }
    val controller = ThemeController(
        colorSchemeMode = settings.schemeMode,
        keyColor = settings.resolvedKeyColor(),
        isDark = dark,
        paletteStyle = settings.paletteStyle,
        colorSpec = settings.colorSpec,
    )
    val context = LocalContext.current
    LaunchedEffect(dark) {
        val activity = context as? Activity ?: return@LaunchedEffect
        WindowInsetsControllerCompat(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
    MiuixTheme(controller = controller, content = content)
}
