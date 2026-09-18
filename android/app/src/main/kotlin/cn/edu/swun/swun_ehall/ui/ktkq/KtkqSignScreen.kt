package cn.edu.swun.swun_ehall.ui.ktkq

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import cn.edu.swun.swun_ehall.data.fences.CampusFences
import cn.edu.swun.swun_ehall.data.session.Session
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun KtkqSignScreen(session: Session, nav: NavHostController, activityId: String) {
    val act = session.ktkq.firstOrNull { it.activityId == activityId }
    var msg by remember { mutableStateOf(session.lastPunch) }
    val scope = rememberCoroutineScope()
    val fence = act?.let { CampusFences.forRoom(it.classroom) }
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "课堂签到",
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
            if (act == null) {
                Text("没有这个签到活动")
                return@Column
            }
            Card {
                Column(Modifier.padding(14.dp)) {
                    Text(act.course, style = MiuixTheme.textStyles.title3)
                    Text("${act.classroom} · ${act.title}")
                    Text("${act.startTime} ~ ${act.endTime}", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    Text(act.status, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    scope.launch {
                        try {
                            val fix = session.locateCampus()
                            msg = session.signIn(act, fix.latitude, fix.longitude, fix.source, fix.datum)
                        } catch (e: Exception) {
                            val fenceOrDemo = fence
                            msg = if (session.demoMode && fenceOrDemo != null) {
                                session.signIn(
                                    act,
                                    fenceOrDemo.centerLat,
                                    fenceOrDemo.centerLng,
                                    "amap",
                                    "gcj02",
                                )
                            } else {
                                e.message ?: "定位失败"
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("立即签到") }
            if (session.developerMode) {
                Spacer(Modifier.height(8.dp))
                TextButton(
                    text = if (fence == null) "楼中心签到（未识别楼栋）" else "用 ${fence.name} 楼中心签到",
                    onClick = { msg = session.signInBuildingCenter(act) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            msg?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
        }
    }
}
