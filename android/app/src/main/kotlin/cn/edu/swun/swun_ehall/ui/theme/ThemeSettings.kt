package cn.edu.swun.swun_ehall.ui.theme

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

enum class ThemeMode(val index: Int) {
    System(0),
    Light(1),
    Dark(2),
    ;

    companion object {
        fun fromIndex(i: Int) = entries.getOrElse(i) { System }
    }
}

data class ThemeColorOption(val argb: Int, val label: String)

val ThemeColorOptions = listOf(
    ThemeColorOption(0, "默认"),
    ThemeColorOption(0xFF9B1B30.toInt(), "民大红"),
    ThemeColorOption(0xFFF44336.toInt(), "红"),
    ThemeColorOption(0xFFE91E63.toInt(), "粉"),
    ThemeColorOption(0xFF9C27B0.toInt(), "紫"),
    ThemeColorOption(0xFF673AB7.toInt(), "深紫"),
    ThemeColorOption(0xFF3F51B5.toInt(), "靛蓝"),
    ThemeColorOption(0xFF2196F3.toInt(), "蓝"),
    ThemeColorOption(0xFF00BCD4.toInt(), "青"),
    ThemeColorOption(0xFF009688.toInt(), "蓝绿"),
    ThemeColorOption(0xFF4FAF50.toInt(), "绿"),
    ThemeColorOption(0xFFFFEB3B.toInt(), "黄"),
    ThemeColorOption(0xFFFFC107.toInt(), "琥珀"),
    ThemeColorOption(0xFFFF9800.toInt(), "橙"),
    ThemeColorOption(0xFF795548.toInt(), "棕"),
    ThemeColorOption(0xFF607D8F.toInt(), "蓝灰"),
    ThemeColorOption(0xFFFF9CA8.toInt(), "樱花"),
)

val ThemePaletteLabels = listOf(
    ThemePaletteStyle.TonalSpot to "色调点",
    ThemePaletteStyle.Neutral to "中性",
    ThemePaletteStyle.Vibrant to "鲜艳",
    ThemePaletteStyle.Expressive to "表现力",
    ThemePaletteStyle.Rainbow to "彩虹",
    ThemePaletteStyle.FruitSalad to "果盘",
    ThemePaletteStyle.Monochrome to "单色",
    ThemePaletteStyle.Fidelity to "保真",
    ThemePaletteStyle.Content to "内容",
)

class ThemeSettings(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("swun_theme", 0)

    var mode by mutableStateOf(ThemeMode.fromIndex(prefs.getInt("mode", 0)))
        private set
    var monet by mutableStateOf(prefs.getBoolean("monet", false))
        private set
    var keyColor by mutableIntStateOf(prefs.getInt("keyColor", 0))
        private set
    var paletteStyle by mutableStateOf(
        runCatching { ThemePaletteStyle.valueOf(prefs.getString("palette", "TonalSpot")!!) }
            .getOrDefault(ThemePaletteStyle.TonalSpot),
    )
        private set
    var colorSpec by mutableStateOf(
        runCatching { ThemeColorSpec.valueOf(prefs.getString("spec", "Spec2021")!!) }
            .getOrDefault(ThemeColorSpec.Spec2021),
    )
        private set
    var enableBlur by mutableStateOf(prefs.getBoolean("blur", false))
        private set
    var floatingBar by mutableStateOf(prefs.getBoolean("floatingBar", false))
        private set
    var liquidGlass by mutableStateOf(prefs.getBoolean("liquidGlass", false))
        private set
    var navBadge by mutableStateOf(prefs.getBoolean("navBadge", true))
        private set
    var predictiveBack by mutableStateOf(prefs.getBoolean("predictiveBack", false))
        private set
    var pageScale by mutableFloatStateOf(prefs.getFloat("pageScale", 1f).coerceIn(0.8f, 1.1f))
        private set

    val schemeMode: ColorSchemeMode
        get() = when {
            monet && mode == ThemeMode.System -> ColorSchemeMode.MonetSystem
            monet && mode == ThemeMode.Light -> ColorSchemeMode.MonetLight
            monet && mode == ThemeMode.Dark -> ColorSchemeMode.MonetDark
            mode == ThemeMode.Light -> ColorSchemeMode.Light
            mode == ThemeMode.Dark -> ColorSchemeMode.Dark
            else -> ColorSchemeMode.System
        }

    fun persistMode(index: Int) {
        mode = ThemeMode.fromIndex(index)
        prefs.edit().putInt("mode", mode.index).apply()
    }

    fun persistMonet(v: Boolean) {
        monet = v
        prefs.edit().putBoolean("monet", v).apply()
    }

    fun persistKeyColor(argb: Int) {
        keyColor = argb
        prefs.edit().putInt("keyColor", argb).apply()
    }

    fun persistPaletteStyle(style: ThemePaletteStyle) {
        paletteStyle = style
        prefs.edit().putString("palette", style.name).apply()
    }

    fun persistColorSpec(spec: ThemeColorSpec) {
        colorSpec = spec
        prefs.edit().putString("spec", spec.name).apply()
    }

    fun persistBlur(v: Boolean) {
        enableBlur = v
        prefs.edit().putBoolean("blur", v).apply()
    }

    fun persistFloatingBar(v: Boolean) {
        floatingBar = v
        prefs.edit().putBoolean("floatingBar", v).apply()
        if (!v) persistLiquidGlass(false)
    }

    fun persistLiquidGlass(v: Boolean) {
        liquidGlass = v
        prefs.edit().putBoolean("liquidGlass", v).apply()
        if (v) {
            floatingBar = true
            prefs.edit().putBoolean("floatingBar", true).apply()
        }
    }

    fun persistNavBadge(v: Boolean) {
        navBadge = v
        prefs.edit().putBoolean("navBadge", v).apply()
    }

    fun persistPredictiveBack(v: Boolean) {
        predictiveBack = v
        prefs.edit().putBoolean("predictiveBack", v).apply()
    }

    fun persistPageScale(v: Float) {
        pageScale = v.coerceIn(0.8f, 1.1f)
        prefs.edit().putFloat("pageScale", pageScale).apply()
    }

    fun resolvedKeyColor(): Color? {
        if (keyColor != 0) return Color(keyColor)
        if (!monet) return Color(0xFF9B1B30)
        return null
    }
}
