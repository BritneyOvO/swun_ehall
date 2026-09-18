package cn.edu.swun.swun_ehall.ui.login

import androidx.compose.foundation.background
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import cn.edu.swun.swun_ehall.data.session.Session
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun LoginScreen(session: Session, onLoggedIn: () -> Unit) {
    var user by remember { mutableStateOf(session.studentId) }
    var pass by remember { mutableStateOf("") }
    var hide by remember { mutableStateOf(true) }
    Scaffold(
        topBar = { SmallTopAppBar(title = "民大助手") },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .background(MiuixTheme.colorScheme.background)
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Text("西南民族大学", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            Spacer(Modifier.height(8.dp))
            Text("登录", style = MiuixTheme.textStyles.title1)
            Spacer(Modifier.height(6.dp))
            Text("使用统一身份认证账号，或先看看界面", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            Spacer(Modifier.height(20.dp))
            Card {
                Column(Modifier.padding(16.dp)) {
                    TextField(
                        value = user,
                        onValueChange = { user = it },
                        label = "学号",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    TextField(
                        value = pass,
                        onValueChange = { pass = it },
                        label = "密码",
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (hide) PasswordVisualTransformation() else VisualTransformation.None,
                    )
                }
            }
            session.error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = MiuixTheme.colorScheme.error)
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    session.login(user, pass)
                    if (session.loggedIn) onLoggedIn()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("登录") }
            Spacer(Modifier.height(10.dp))
            TextButton(
                text = "先看看界面",
                onClick = {
                    session.enterDemo()
                    onLoggedIn()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
