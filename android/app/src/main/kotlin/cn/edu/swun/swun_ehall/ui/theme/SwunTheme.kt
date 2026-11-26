package cn.edu.swun.swun_ehall.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowInsetsControllerCompat
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

@Composable
fun SwunTheme(settings: ThemeSettings, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (settings.mode) {
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
        ThemeMode.System -> systemDark
    }
    val accent = settings.resolvedAccentColor()
    val lightColors = remember(accent) { swunLightColorScheme(accent) }
    val darkColors = remember(accent) { swunDarkColorScheme(accent) }
    val controller = ThemeController(
        colorSchemeMode = settings.schemeMode,
        lightColors = lightColors,
        darkColors = darkColors,
        keyColor = null,
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

private fun swunLightColorScheme(accent: Color): Colors {
    val accentSoft = lerp(Color.White, accent, 0.1f)
    val onAccent = if (accent.luminance() > 0.179f) Color.Black else Color.White
    return lightColorScheme(
        primary = accent,
        onPrimary = onAccent,
        primaryVariant = accent,
        onPrimaryVariant = onAccent,
        disabledPrimary = lerp(Color(0xFFE4E4E7), accent, 0.12f),
        disabledPrimaryButton = lerp(Color(0xFFE4E4E7), accent, 0.12f),
        disabledPrimarySlider = lerp(Color(0xFFD8D8DC), accent, 0.16f),
        primaryContainer = accent,
        onPrimaryContainer = onAccent,
        tertiaryContainer = accentSoft,
        onTertiaryContainer = accent,
        tertiaryContainerVariant = accentSoft,
        background = Color(0xFFF7F7F8),
        surface = Color(0xFFF7F7F8),
        surfaceVariant = Color.White,
        secondaryContainer = Color(0xFFEFEFF1),
        secondaryContainerVariant = Color(0xFFEFEFF1),
        surfaceContainer = Color.White,
        surfaceContainerHigh = Color(0xFFEFEFF1),
        surfaceContainerHighest = Color(0xFFE8E8EB),
        outline = Color(0xFFD8D8DC),
        dividerLine = Color(0xFFE5E5E8),
        sliderKeyPointForeground = accent,
    )
}

private fun swunDarkColorScheme(accent: Color): Colors {
    val accentSoft = lerp(Color(0xFF1A1B1E), accent, 0.22f)
    val accentText = lerp(accent, Color.White, 0.5f)
    val onAccent = if (accent.luminance() > 0.179f) Color.Black else Color.White
    return darkColorScheme(
        primary = accentText,
        onPrimary = Color.Black,
        primaryVariant = accent,
        onPrimaryVariant = onAccent,
        disabledPrimary = lerp(Color(0xFF34353A), accent, 0.2f),
        disabledPrimaryButton = lerp(Color(0xFF34353A), accent, 0.2f),
        disabledPrimarySlider = lerp(Color(0xFF3E3F45), accent, 0.22f),
        primaryContainer = accent,
        onPrimaryContainer = onAccent,
        tertiaryContainer = accentSoft,
        onTertiaryContainer = accentText,
        tertiaryContainerVariant = accentSoft,
        background = Color(0xFF101113),
        surface = Color(0xFF101113),
        surfaceVariant = Color(0xFF1A1B1E),
        secondaryContainer = Color(0xFF242529),
        secondaryContainerVariant = Color(0xFF242529),
        surfaceContainer = Color(0xFF1A1B1E),
        surfaceContainerHigh = Color(0xFF242529),
        surfaceContainerHighest = Color(0xFF2B2C31),
        outline = Color(0xFF3A3B41),
        dividerLine = Color(0xFF303137),
        sliderKeyPointForeground = accentText,
    )
}
