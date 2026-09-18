package cn.edu.swun.swun_ehall.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import cn.edu.swun.swun_ehall.data.session.Session
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

@Composable
fun SettingsScreen(session: Session, nav: NavHostController) {
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "设置",
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
                .padding(16.dp),
        ) {
            Card {
                ArrowPreference(title = "关于", onClick = { nav.navigate("about") })
            }
        }
    }
}

@Composable
fun AboutScreen(session: Session, nav: NavHostController) {
    var taps by remember { mutableIntStateOf(0) }
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "关于",
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
                .padding(16.dp),
        ) {
            Card {
                ArrowPreference(
                    title = "民大助手",
                    summary = "1.0.5 · Compose + MIUIX",
                    onClick = {
                        taps++
                        if (taps >= 7) session.persistDeveloperMode(true)
                    },
                )
                SwitchPreference(
                    title = "启动时检查更新",
                    checked = session.checkUpdateOnLaunch,
                    onCheckedChange = { session.persistCheckUpdateOnLaunch(it) },
                )
            }
            if (session.developerMode) {
                Card(modifier = Modifier.padding(top = 12.dp)) {
                    SwitchPreference(
                        title = "开发者模式",
                        checked = session.developerMode,
                        onCheckedChange = { session.persistDeveloperMode(it) },
                    )
                    ArrowPreference(
                        title = "测试定位",
                        summary = "围栏预检，不记签到",
                        onClick = { nav.navigate("locateTest") },
                    )
                }
            }
        }
    }
}
