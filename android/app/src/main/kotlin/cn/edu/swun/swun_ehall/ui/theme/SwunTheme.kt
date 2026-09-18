package cn.edu.swun.swun_ehall.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

private val Crimson = Color(0xFF9B1B30)

@Composable
fun SwunTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = if (dark) {
        darkColorScheme(primary = Color(0xFFC45C63))
    } else {
        lightColorScheme(primary = Crimson)
    }
    MiuixTheme(colors = colors, content = content)
}
