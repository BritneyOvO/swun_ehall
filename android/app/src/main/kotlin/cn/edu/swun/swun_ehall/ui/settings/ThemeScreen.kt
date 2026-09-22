package cn.edu.swun.swun_ehall.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import android.app.Activity
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import cn.edu.swun.swun_ehall.ui.theme.ThemeColorOptions
import cn.edu.swun.swun_ehall.ui.theme.ThemePaletteLabels
import cn.edu.swun.swun_ehall.ui.theme.ThemeSettings
import cn.edu.swun.swun_ehall.ui.theme.applyPredictiveBack
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec

@Composable
fun ThemeScreen(settings: ThemeSettings, nav: NavHostController) {
    val cs = MiuixTheme.colorScheme
    val context = LocalContext.current
    var scale by remember(settings.pageScale) { mutableFloatStateOf(settings.pageScale) }
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "主题外观",
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        top.yukonga.miuix.kmp.basic.Icon(MiuixIcons.Back, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
        ) {
            ThemePreviewCard(settings)
            Spacer(Modifier.height(16.dp))
            TabRow(
                tabs = listOf("跟随系统", "浅色", "深色"),
                selectedTabIndex = settings.mode.index,
                onTabSelected = { settings.persistMode(it) },
            )
            Card(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth(),
            ) {
                SwitchPreference(
                    title = "莫奈取色",
                    summary = "用壁纸颜色生成主题",
                    checked = settings.monet,
                    onCheckedChange = { settings.persistMonet(it) },
                )
                AnimatedVisibility(visible = settings.monet) {
                    Column {
                        val colorIndex = ThemeColorOptions.indexOfFirst { it.argb == settings.keyColor }.coerceAtLeast(0)
                        OverlayDropdownPreference(
                            title = "主题色",
                            items = ThemeColorOptions.map { it.label },
                            selectedIndex = colorIndex,
                            onSelectedIndexChange = { settings.persistKeyColor(ThemeColorOptions[it].argb) },
                        )
                        AnimatedVisibility(visible = settings.keyColor != 0) {
                            Column {
                                val styles = ThemePaletteLabels
                                OverlayDropdownPreference(
                                    title = "配色风格",
                                    items = styles.map { it.second },
                                    selectedIndex = styles.indexOfFirst { it.first == settings.paletteStyle }.coerceAtLeast(0),
                                    onSelectedIndexChange = { settings.persistPaletteStyle(styles[it].first) },
                                )
                                val specs = listOf(ThemeColorSpec.Spec2021 to "2021", ThemeColorSpec.Spec2025 to "2025")
                                OverlayDropdownPreference(
                                    title = "色板规格",
                                    items = specs.map { it.second },
                                    selectedIndex = specs.indexOfFirst { it.first == settings.colorSpec }.coerceAtLeast(0),
                                    onSelectedIndexChange = { settings.persistColorSpec(specs[it].first) },
                                )
                            }
                        }
                    }
                }
            }
            Card(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth(),
            ) {
                if (Build.VERSION.SDK_INT >= 33) {
                    SwitchPreference(
                        title = "背景模糊",
                        summary = "顶栏和底栏半透明，透出下层内容",
                        checked = settings.enableBlur,
                        onCheckedChange = { settings.persistBlur(it) },
                    )
                }
                SwitchPreference(
                    title = "悬浮底栏",
                    summary = "底栏改为悬浮胶囊",
                    checked = settings.floatingBar,
                    onCheckedChange = { settings.persistFloatingBar(it) },
                )
                AnimatedVisibility(visible = settings.floatingBar) {
                    SwitchPreference(
                        title = "液态玻璃",
                        summary = "悬浮底栏使用半透明玻璃效果",
                        checked = settings.liquidGlass,
                        onCheckedChange = { settings.persistLiquidGlass(it) },
                    )
                }
                SwitchPreference(
                    title = "导航栏角标",
                    summary = "在课表等入口显示数量角标",
                    checked = settings.navBadge,
                    onCheckedChange = { settings.persistNavBadge(it) },
                )
            }
            Card(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth(),
            ) {
                if (Build.VERSION.SDK_INT >= 34) {
                    SwitchPreference(
                        title = "预测性返回",
                        summary = "侧滑返回时预览上一页，切换后会重启界面",
                        checked = settings.predictiveBack,
                        onCheckedChange = {
                            settings.persistPredictiveBack(it)
                            applyPredictiveBack(context, it)
                            (context as? Activity)?.recreate()
                        },
                    )
                }
                SliderPreference(
                    value = scale,
                    onValueChange = { scale = it },
                    title = "界面缩放",
                    summary = "整体界面大小 · ${(scale * 100).toInt()}%",
                    valueRange = 0.8f..1.1f,
                    onValueChangeFinished = { settings.persistPageScale(scale) },
                )
            }
        }
    }
}

@Composable
private fun ThemePreviewCard(settings: ThemeSettings) {
    val cs = MiuixTheme.colorScheme
    val configuration = LocalConfiguration.current
    val ratio = configuration.screenWidthDp.toFloat() / configuration.screenHeightDp.toFloat()
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            Modifier
                .fillMaxWidth(0.42f)
                .aspectRatio(ratio.coerceIn(0.4f, 0.7f))
                .clip(RoundedCornerShape(20.dp))
                .background(cs.background)
                .border(1.dp, cs.outline, RoundedCornerShape(20.dp)),
        ) {
            Column(Modifier.fillMaxSize()) {
                Text(
                    "民大助手",
                    fontSize = 12.sp,
                    color = cs.onBackground,
                    modifier = Modifier.padding(start = 12.dp, top = 20.dp, bottom = 8.dp),
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .padding(horizontal = 8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(cs.primary.copy(alpha = 0.22f)),
                )
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(cs.surfaceVariant),
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(cs.surfaceVariant),
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(cs.surfaceVariant),
                    )
                }
                if (settings.floatingBar) {
                    Box(
                        Modifier
                            .padding(start = 10.dp, end = 10.dp, bottom = 8.dp, top = 4.dp)
                            .height(18.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(9.dp))
                            .background(
                                if (settings.liquidGlass) cs.surfaceContainer.copy(alpha = 0.92f)
                                else androidx.compose.ui.graphics.lerp(cs.surfaceContainer, Color.Black, 0.045f),
                            )
                            .border(0.5.dp, cs.onBackground.copy(alpha = 0.12f), RoundedCornerShape(9.dp)),
                    )
                } else {
                    Box(Modifier.fillMaxWidth().height(0.5.dp).background(cs.onBackground.copy(alpha = 0.1f)))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(32.dp)
                            .background(androidx.compose.ui.graphics.lerp(cs.surface, Color.Black, 0.045f))
                            .padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        repeat(4) {
                            Box(
                                Modifier
                                    .size(12.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (it == 0) cs.primary else cs.onSurfaceVariantSummary.copy(alpha = 0.45f)),
                            )
                        }
                    }
                }
            }
        }
    }
}
