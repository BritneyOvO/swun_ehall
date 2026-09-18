package cn.edu.swun.swun_ehall.ui.mine

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import cn.edu.swun.swun_ehall.data.session.Session
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun MineScreen(session: Session, nav: NavHostController) {
    Scaffold(topBar = { SmallTopAppBar(title = "我的") }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text(session.displayName, style = MiuixTheme.textStyles.title2)
                    Text(session.studentId, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    if (session.profile.college.isNotEmpty()) {
                        Text(session.profile.college, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    }
                    if (session.profile.klass.isNotEmpty()) {
                        Text(session.profile.klass, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Card {
                ArrowPreference(title = "设置", onClick = { nav.navigate("settings") })
                ArrowPreference(title = "关于", onClick = { nav.navigate("about") })
            }
            Spacer(Modifier.height(20.dp))
            TextButton(
                text = "退出登录",
                onClick = {
                    session.logout()
                    nav.navigate("login") {
                        popUpTo("shell") { inclusive = true }
                    }
                },
            )
        }
    }
}
