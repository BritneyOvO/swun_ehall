package cn.edu.swun.swun_ehall.ui.accounts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import cn.edu.swun.swun_ehall.data.session.SavedAccount
import cn.edu.swun.swun_ehall.data.session.Session
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Hide
import top.yukonga.miuix.kmp.icon.extended.Replace
import top.yukonga.miuix.kmp.icon.extended.Show
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun AccountsScreen(session: Session, nav: NavHostController) {
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<SavedAccount?>(null) }
    val cs = MiuixTheme.colorScheme
    val current = session.accounts.currentId.ifBlank { session.studentId }
    LaunchedEffect(session.studentId, session.profile.name) {
        val id = session.studentId
        val name = session.profile.name
        if (id.isNotEmpty() && name.isNotEmpty()) session.accounts.upsert(id, name)
    }
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "账号管理",
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(MiuixIcons.Back, contentDescription = "返回")
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
            Card(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth(),
            ) {
                if (session.accounts.items.isEmpty()) {
                    Text("还没有保存的账号", modifier = Modifier.padding(16.dp), color = cs.onSurfaceVariantSummary)
                } else {
                    session.accounts.items.forEachIndexed { i, acc ->
                        if (i > 0) HorizontalDivider()
                        val on = session.loggedIn && !session.demoMode && acc.id == current
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(acc.name.ifBlank { acc.id })
                                Text(
                                    buildString {
                                        append(acc.id)
                                        if (on) append(" · 当前账号")
                                    },
                                    color = if (on) cs.primary else cs.onSurfaceVariantSummary,
                                )
                            }
                            IconButton(
                                onClick = {
                                    if (!on && !session.busy) scope.launch { session.switchTo(acc.id) }
                                },
                                enabled = !on && !session.busy,
                            ) {
                                Icon(
                                    MiuixIcons.Replace,
                                    contentDescription = "切换",
                                    tint = if (on) cs.onSurfaceVariantSummary else cs.onBackground,
                                )
                            }
                            IconButton(onClick = { pending = acc }) {
                                Icon(MiuixIcons.Delete, contentDescription = "删除", tint = cs.onBackground)
                            }
                        }
                    }
                }
            }
            session.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = cs.error)
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { nav.navigate("accounts/add") },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("添加账号") }
            Spacer(Modifier.height(96.dp))
        }
        pending?.let { acc ->
            WindowDialog(
                show = true,
                title = "删除账号",
                summary = "确定删除 ${acc.label}？本机保存的密码会一起删除。",
                onDismissRequest = { pending = null },
            ) {
                Row(Modifier.fillMaxWidth()) {
                    TextButton(
                        text = "取消",
                        onClick = { pending = null },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    TextButton(
                        text = "删除",
                        onClick = {
                            session.removeAccount(acc.id)
                            pending = null
                            if (!session.loggedIn) {
                                nav.navigate("login") { popUpTo("shell") { inclusive = true } }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            }
        }
    }
}

@Composable
fun AddAccountScreen(session: Session, nav: NavHostController) {
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var hide by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "添加账号",
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(MiuixIcons.Back, contentDescription = "返回")
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
            TextField(value = user, onValueChange = { user = it }, label = "学号", modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            TextField(
                value = pass,
                onValueChange = { pass = it },
                label = "密码",
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (hide) PasswordVisualTransformation() else VisualTransformation.None,
                trailingIcon = {
                    IconButton(onClick = { hide = !hide }) {
                        Icon(
                            if (hide) MiuixIcons.Show else MiuixIcons.Hide,
                            contentDescription = if (hide) "显示密码" else "隐藏密码",
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                },
            )
            session.error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = MiuixTheme.colorScheme.error)
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    if (session.busy) return@Button
                    scope.launch {
                        session.login(user, pass)
                        if (session.loggedIn && !session.demoMode) nav.popBackStack()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !session.busy,
            ) { Text(if (session.busy) "登录中…" else "登录并切换") }
        }
    }
}
