package cn.edu.swun.swun_ehall.ui.mine

import android.app.Application
import android.graphics.BitmapFactory
import android.widget.Toast
import cn.edu.swun.swun_ehall.SwunApplication
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import cn.edu.swun.swun_ehall.data.session.Session
import cn.edu.swun.swun_ehall.ui.common.FeatureSection
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.ContactsBook
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Theme
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun MineScreen(session: Session, nav: NavHostController) {
    val context = LocalContext.current
    val cs = MiuixTheme.colorScheme
    LaunchedEffect(session.loggedIn, session.demoMode) {
        if (session.loggedIn && !session.demoMode) session.refreshProfile()
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        SwunApplication.markPickingMedia(context.applicationContext as Application, false)
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = context.contentResolver.openInputStream(uri)?.use { session.beginAvatarCrop(it) } ?: false
        if (!ok) {
            Toast.makeText(context, "读不了这张图", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
    }
    val avatarBmp = remember(session.localAvatarPath, session.remoteAvatarPath, session.avatarEpoch) {
        val path = session.localAvatarPath ?: session.remoteAvatarPath
        path?.let { BitmapFactory.decodeFile(it) }
    }
    var avatarMenu by remember { mutableStateOf(false) }
    var showLogout by remember { mutableStateOf(false) }
    val customAvatar = session.localAvatarPath != null
    val title = session.displayName.ifBlank { "同学" }
    Box(Modifier.fillMaxSize()) {
    Scaffold(topBar = { SmallTopAppBar(title = "我的") }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Card(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth(),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(64.dp)
                            .clickable {
                                if (customAvatar) {
                                    avatarMenu = true
                                } else {
                                    SwunApplication.markPickingMedia(context.applicationContext as Application, true)
                                    picker.launch("image/*")
                                }
                            },
                    ) {
                        if (avatarBmp != null) {
                            Image(
                                bitmap = avatarBmp.asImageBitmap(),
                                contentDescription = "头像",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape),
                            )
                        } else {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(cs.surfaceContainerHighest),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    title.take(1),
                                    fontSize = 22.sp,
                                )
                            }
                        }
                        Box(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(cs.surface),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                MiuixIcons.Edit,
                                contentDescription = "设置头像",
                                tint = cs.onBackground,
                                modifier = Modifier.size(12.dp),
                            )
                        }
                    }
                    Column(
                        Modifier
                            .padding(start = 14.dp)
                            .weight(1f)
                            .clickable { nav.navigate("profile") },
                    ) {
                        Text(title, style = MiuixTheme.textStyles.title2)
                        Text(
                            listOf(
                                session.studentId,
                                if (session.demoMode) "预览模式" else session.profile.major.ifBlank { session.profile.role },
                            ).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "已登录" },
                            color = cs.onSurfaceVariantSummary,
                            fontSize = 13.sp,
                        )
                    }
                    Text("›", color = cs.onSurfaceVariantSummary, fontSize = 18.sp)
                }
            }
            FeatureSection("账号")
            Card(modifier = Modifier.fillMaxWidth()) {
                MineEntry(
                    title = "个人信息",
                    summary = "姓名、学号、学院",
                    icon = MiuixIcons.Contacts,
                ) { nav.navigate("profile") }
                HorizontalDivider(modifier = Modifier.padding(start = 52.dp))
                MineEntry(
                    title = "账号管理",
                    summary = "多账号切换、添加和删除",
                    icon = MiuixIcons.ContactsBook,
                ) { nav.navigate("accounts") }
            }
            FeatureSection("设置")
            Card(modifier = Modifier.fillMaxWidth()) {
                MineEntry(
                    title = "主题外观",
                    summary = "深浅色、主题色与界面缩放",
                    icon = MiuixIcons.Theme,
                ) { nav.navigate("theme") }
                HorizontalDivider(modifier = Modifier.padding(start = 52.dp))
                MineEntry(
                    title = "关于",
                    summary = "版本、更新与开源仓库",
                    icon = MiuixIcons.Info,
                ) { nav.navigate("about") }
            }
            Card(modifier = Modifier.padding(top = 12.dp).fillMaxWidth()) {
                TextButton(
                    text = "退出登录",
                    onClick = { showLogout = true },
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
        }
    }
    if (avatarMenu) {
        WindowDialog(
            show = true,
            title = "头像",
            summary = "当前是自定义头像，可以换一张，或改回自动获取的头像。",
            onDismissRequest = { avatarMenu = false },
        ) {
            Row(Modifier.fillMaxWidth()) {
                TextButton(
                    text = "取消自定义",
                    onClick = {
                        avatarMenu = false
                        session.clearLocalAvatar()
                        Toast.makeText(context, "已恢复自动获取的头像", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                TextButton(
                    text = "自定义头像",
                    onClick = {
                        avatarMenu = false
                        SwunApplication.markPickingMedia(context.applicationContext as Application, true)
                        picker.launch("image/*")
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
    if (showLogout) {
        WindowDialog(
            show = true,
            title = "退出登录",
            summary = "退出当前账号？已保存的账号会保留。",
            onDismissRequest = { showLogout = false },
        ) {
            Row(Modifier.fillMaxWidth()) {
                TextButton("取消", { showLogout = false }, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
                TextButton(
                    text = "退出",
                    onClick = {
                        showLogout = false
                        session.logout()
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
private fun MineEntry(
    title: String,
    summary: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val cs = MiuixTheme.colorScheme
    ArrowPreference(
        title = title,
        summary = summary,
        startAction = {
            Icon(
                icon,
                contentDescription = title,
                tint = cs.primary,
                modifier = Modifier.padding(end = 6.dp),
            )
        },
        onClick = onClick,
    )
}
