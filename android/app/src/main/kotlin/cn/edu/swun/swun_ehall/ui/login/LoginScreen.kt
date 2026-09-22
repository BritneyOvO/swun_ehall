package cn.edu.swun.swun_ehall.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.edu.swun.swun_ehall.data.session.SavedAccount
import cn.edu.swun.swun_ehall.data.session.Session
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Hide
import top.yukonga.miuix.kmp.icon.extended.Show
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun LoginScreen(session: Session) {
    var user by remember { mutableStateOf(session.studentId) }
    var pass by remember { mutableStateOf("") }
    var hide by remember { mutableStateOf(true) }
    var pickSaved by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val cs = MiuixTheme.colorScheme
    val saved = session.accounts.items
    fun fillAccount(acc: SavedAccount) {
        user = acc.id
        val pwd = session.accounts.password(acc.id)
        if (pwd.isNotEmpty()) pass = pwd
        pickSaved = false
    }
    LaunchedEffect(Unit) {
        val id = user.trim()
        if (id.isNotEmpty() && pass.isEmpty()) {
            val pwd = session.accounts.password(id)
            if (pwd.isNotEmpty()) pass = pwd
        }
    }
    fun submit() {
        if (session.busy) return
        focus.clearFocus()
        scope.launch { session.login(user, pass) }
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(cs.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 12.dp),
    ) {
        Spacer(Modifier.height(36.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(
                Modifier
                    .width(3.dp)
                    .height(16.dp)
                    .background(cs.primary, RoundedCornerShape(2.dp)),
            )
            Spacer(Modifier.width(8.dp))
            Text("西南民族大学", color = cs.onSurfaceVariantSummary, fontSize = 13.sp)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "登录",
            fontSize = 32.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 36.sp,
            color = cs.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text("使用统一身份认证账号", color = cs.onSurfaceVariantSummary, fontSize = 14.sp)
        Spacer(Modifier.height(28.dp))
        TextField(
            value = user,
            onValueChange = { user = it },
            label = "学号",
            enabled = !session.busy,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
            trailingIcon = if (saved.isNotEmpty()) {
                {
                    IconButton(onClick = { if (!session.busy) pickSaved = true }) {
                        Text("▾", color = cs.onSurfaceVariantSummary, fontSize = 18.sp)
                    }
                }
            } else {
                null
            },
        )
        Spacer(Modifier.height(14.dp))
        TextField(
            value = pass,
            onValueChange = { pass = it },
            label = "密码",
            enabled = !session.busy,
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (hide) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            trailingIcon = {
                IconButton(onClick = { hide = !hide }) {
                    Icon(
                        if (hide) MiuixIcons.Show else MiuixIcons.Hide,
                        contentDescription = if (hide) "显示密码" else "隐藏密码",
                        tint = cs.onSurfaceVariantSummary,
                    )
                }
            },
        )
        session.error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = cs.error, fontSize = 13.sp)
        }
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = { submit() },
            modifier = Modifier.fillMaxWidth(),
            enabled = !session.busy,
            colors = ButtonDefaults.buttonColorsPrimary(),
        ) { Text(if (session.busy) "登录中…" else "登录") }
        Spacer(Modifier.height(8.dp))
        TextButton(
            text = "先看看界面",
            onClick = { session.enterDemo() },
            modifier = Modifier.fillMaxWidth(),
            enabled = !session.busy,
        )
        Spacer(Modifier.height(24.dp))
    }
    if (pickSaved) {
        WindowDialog(
            show = true,
            title = "已保存的账号",
            onDismissRequest = { pickSaved = false },
        ) {
            Column {
                saved.forEach { acc ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { fillAccount(acc) }
                            .padding(vertical = 10.dp),
                    ) {
                        Text(acc.label)
                        if (acc.name.isNotBlank()) {
                            Text(acc.id, color = cs.onSurfaceVariantSummary, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}
