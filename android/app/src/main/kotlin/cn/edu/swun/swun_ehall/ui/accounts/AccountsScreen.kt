package cn.edu.swun.swun_ehall.ui.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import cn.edu.swun.swun_ehall.data.session.SavedAccount
import cn.edu.swun.swun_ehall.data.session.Session
import cn.edu.swun.swun_ehall.ui.common.BackNav
import cn.edu.swun.swun_ehall.ui.common.FeatureMutedPanel
import cn.edu.swun.swun_ehall.ui.common.FeatureNoticeHost
import cn.edu.swun.swun_ehall.ui.common.FeatureSection
import cn.edu.swun.swun_ehall.ui.common.rememberFeatureNotices
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
import top.yukonga.miuix.kmp.icon.extended.Hide
import top.yukonga.miuix.kmp.icon.extended.Show
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun AccountsScreen(session: Session, nav: NavHostController) {
    val notices = rememberFeatureNotices(session)
    FeatureNoticeHost(notices)
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<SavedAccount?>(null) }
    val cs = MiuixTheme.colorScheme
    val current = session.accounts.currentId.ifBlank { session.studentId }
    LaunchedEffect(session.studentId, session.profile.name) {
        val id = session.studentId
        val name = session.profile.name
        if (session.loggedIn && !session.demoMode && id.isNotEmpty() && name.isNotEmpty()) {
            session.accounts.upsert(id, name)
        }
    }
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "账号管理",
                navigationIcon = { BackNav { nav.popBackStack() } },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            FeatureSection("已保存账号 · ${session.accounts.items.size} 个")
            Card(
                modifier = Modifier
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
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(if (on) cs.primary.copy(alpha = 0.1f) else cs.surfaceContainerHighest),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    acc.name.ifBlank { acc.id }.take(1),
                                    color = if (on) cs.primary else cs.onSurfaceVariantSummary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                )
                            }
                            Column(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
                                Text(acc.name.ifBlank { acc.id }, fontWeight = FontWeight.SemiBold)
                                Text(
                                    buildString {
                                        append(acc.id)
                                        if (on) append(" · 当前账号")
                                    },
                                    color = if (on) cs.primary else cs.onSurfaceVariantSummary,
                                )
                            }
                            if (!on) {
                                Text(
                                    "切换",
                                    color = cs.primary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.clickable(enabled = !session.busy) {
                                    if (!on && !session.busy) scope.launch {
                                        session.switchTo(acc.id)
                                        session.error?.let { notices.error(it, "切换账号未完成") }
                                    }
                                    }.padding(8.dp),
                                )
                            }
                            Text(
                                "删除",
                                color = cs.onSurfaceVariantSummary,
                                fontSize = 13.sp,
                                modifier = Modifier.clickable { pending = acc }.padding(8.dp),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { nav.navigate("accounts/add") },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) { Text("添加账号") }
            Text(
                "账号凭据仅保存在本机应用数据中。",
                color = cs.onSurfaceVariantSummary,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
            )
            Spacer(Modifier.height(24.dp))
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
    val notices = rememberFeatureNotices(session)
    FeatureNoticeHost(notices)
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var hide by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "添加账号",
                navigationIcon = { BackNav { nav.popBackStack() } },
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
            Text("登录并切换到另一个学生账号", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            Text(
                "成功登录后会自动保存到账号管理。",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 5.dp, bottom = 18.dp),
            )
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
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    if (session.busy) return@Button
                    scope.launch {
                        session.login(user, pass)
                        val failure = session.error
                        if (failure != null) notices.error(failure, "添加账号未完成")
                        else if (session.loggedIn && !session.demoMode) nav.popBackStack()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !session.busy,
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) { Text(if (session.busy) "登录中…" else "登录并切换") }
            FeatureMutedPanel(
                title = "安全提示",
                summary = "请只登录本人的统一身份认证账号。密码不会上传到项目仓库或第三方服务。",
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}
