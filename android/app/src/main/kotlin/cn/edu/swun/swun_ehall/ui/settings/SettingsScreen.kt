package cn.edu.swun.swun_ehall.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import cn.edu.swun.swun_ehall.data.session.Session
import cn.edu.swun.swun_ehall.data.update.K_GITHUB_REPO
import cn.edu.swun.swun_ehall.data.update.Versions
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
                ArrowPreference(title = "个人信息", onClick = { nav.navigate("profile") })
                ArrowPreference(title = "主题外观", onClick = { nav.navigate("theme") })
                ArrowPreference(title = "关于", onClick = { nav.navigate("about") })
            }
        }
    }
}

@Composable
fun AboutScreen(session: Session, nav: NavHostController) {
    var taps by remember { mutableIntStateOf(0) }
    var checking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
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
                    summary = Versions.APP,
                    onClick = {
                        taps++
                        if (taps >= 7) session.persistDeveloperMode(true)
                    },
                )
                ArrowPreference(title = "制作者", summary = "Britney")
                ArrowPreference(
                    title = "开源仓库",
                    summary = K_GITHUB_REPO,
                    onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/$K_GITHUB_REPO")),
                        )
                    },
                )
                SwitchPreference(
                    title = "检查更新",
                    summary = "打开后进入首页会查询 GitHub 发行版",
                    checked = session.checkUpdateOnLaunch,
                    onCheckedChange = { session.persistCheckUpdateOnLaunch(it) },
                )
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    if (checking) return@Button
                    scope.launch {
                        checking = true
                        try {
                            val got = session.checkLatest()
                            val text = when {
                                got == null -> "没有找到发行版"
                                Versions.isNewer(got) -> null
                                else -> "已是最新版本 ${got.version}"
                            }
                            if (text != null) Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                            else session.dismissedUpdate = null
                        } catch (e: Exception) {
                            Toast.makeText(context, e.message ?: "检查更新失败", Toast.LENGTH_SHORT).show()
                        }
                        checking = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !checking,
            ) { Text(if (checking) "检查中…" else "检查更新") }
            if (session.developerMode) {
                Spacer(Modifier.height(12.dp))
                Card {
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
