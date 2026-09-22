package cn.edu.swun.swun_ehall.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import cn.edu.swun.swun_ehall.data.session.Session
import cn.edu.swun.swun_ehall.ui.common.BackNav
import cn.edu.swun.swun_ehall.ui.common.RefreshNav
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ProfileScreen(session: Session, nav: NavHostController) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(session.loggedIn) { session.refreshProfile() }
    val p = session.profile
    val rows = listOf(
        "姓名" to p.name,
        "学号" to p.studentId.ifBlank { session.studentId },
        "性别" to p.gender,
        "身份" to p.role,
        "学院" to p.college,
        "专业" to p.major,
        "班级" to p.klass,
        "年级" to p.grade,
        "校区" to p.campus,
        "手机" to p.phone,
    ).filter { it.second.isNotBlank() }
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "个人信息",
                navigationIcon = { BackNav { nav.popBackStack() } },
                actions = { RefreshNav { scope.launch { session.refreshProfile() } } },
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
            if (rows.isEmpty()) {
                Card { Text(if (session.demoMode) "预览模式" else "暂无个人信息", modifier = Modifier.padding(16.dp)) }
            } else {
                Card {
                    Column(Modifier.padding(4.dp)) {
                        rows.forEach { (k, v) ->
                            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
                                Text(k, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.weight(0.3f))
                                Text(v, modifier = Modifier.weight(0.7f))
                            }
                        }
                    }
                }
            }
        }
    }
}
