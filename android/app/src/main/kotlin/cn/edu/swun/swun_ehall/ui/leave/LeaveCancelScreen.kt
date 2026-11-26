package cn.edu.swun.swun_ehall.ui.leave

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import cn.edu.swun.swun_ehall.SwunApplication
import cn.edu.swun.swun_ehall.data.model.LeaveCancelForm
import cn.edu.swun.swun_ehall.data.model.LeaveCancelPreparation
import cn.edu.swun.swun_ehall.data.session.Session
import cn.edu.swun.swun_ehall.ui.common.BackNav
import cn.edu.swun.swun_ehall.ui.common.FeatureBottomSpace
import cn.edu.swun.swun_ehall.ui.common.FeatureColumn
import cn.edu.swun.swun_ehall.ui.common.FeatureLoadingPage
import cn.edu.swun.swun_ehall.ui.common.FeatureNoticeHost
import cn.edu.swun.swun_ehall.ui.common.FeatureSection
import cn.edu.swun.swun_ehall.ui.common.rememberFeatureNotices
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun LeaveCancelScreen(session: Session, nav: NavHostController, leaveNo: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val notices = rememberFeatureNotices(session.studentId, leaveNo, session.demoMode, "leave-cancel")
    FeatureNoticeHost(notices)
    var loading by remember(leaveNo) { mutableStateOf(true) }
    var preparation by remember(leaveNo) { mutableStateOf<LeaveCancelPreparation?>(null) }
    var remark by rememberSaveable(leaveNo) { mutableStateOf("") }
    var locating by remember { mutableStateOf(false) }
    var locationText by rememberSaveable(leaveNo) { mutableStateOf("") }
    var locationAllowed by remember { mutableStateOf<Boolean?>(null) }
    var uploading by remember { mutableStateOf(false) }
    var pendingSelfieUri by remember { mutableStateOf<Uri?>(null) }
    val selfies = remember(leaveNo) { mutableStateListOf<UploadedLeaveImage>() }
    var confirm by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var completedMessage by remember { mutableStateOf<String?>(null) }

    suspend fun locateAndValidate() {
        locating = true
        locationAllowed = null
        try {
            val (text, allowed) = session.leaveLocation()
            locationText = text
            locationAllowed = allowed
            if (!allowed) notices.error("当前位置不符合学校销假规则，请到允许范围后重新定位", "定位校验未通过")
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            locationText = ""
            notices.error(e.message ?: "定位失败，请打开系统定位后重试")
        } finally {
            locating = false
        }
    }

    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) scope.launch { locateAndValidate() }
        else notices.error("需要定位权限才能按学校规则校验销假位置")
    }

    fun requestLocation() {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fine || coarse) {
            scope.launch { locateAndValidate() }
        } else {
            locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        SwunApplication.markPickingMedia(context.applicationContext as Application, false)
        val uri = pendingSelfieUri
        pendingSelfieUri = null
        if (!saved || uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            uploading = true
            try {
                val url = session.uploadLeaveImage(uri)
                selfies.clear()
                selfies += UploadedLeaveImage(url, "现场自拍")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                notices.error(e.message ?: "自拍上传失败")
            } finally {
                uploading = false
            }
        }
    }

    fun takeSelfie() {
        runCatching { createLeavePhotoUri(context) }
            .onSuccess { uri ->
                pendingSelfieUri = uri
                SwunApplication.markPickingMedia(context.applicationContext as Application, true)
                camera.launch(uri)
            }
            .onFailure { notices.error(it.message ?: "无法打开相机") }
    }

    LaunchedEffect(leaveNo, session.demoMode) {
        loading = true
        try {
            if (session.leaveRecords.isEmpty()) session.refreshLeave()
            preparation = session.prepareLeaveCancel(leaveNo)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            notices.error(e.message ?: "销假信息加载失败")
        } finally {
            loading = false
        }
    }

    fun validationError(): String? = when {
        preparation == null -> "销假信息尚未加载"
        remark.trim().isEmpty() -> "请填写销假说明"
        session.leaveRule.requireLocation && locationText.isBlank() -> "请先获取并校验当前位置"
        session.leaveRule.requireLocation && locationAllowed != true -> "当前位置未通过学校规则校验"
        session.leaveRule.requireSelfie && selfies.isEmpty() -> "请拍摄并上传现场自拍"
        locating -> "正在定位，请稍候"
        uploading -> "自拍仍在上传，请稍候"
        submitting -> "正在提交，请稍候"
        else -> null
    }

    fun submit() {
        val value = preparation ?: return
        if (submitting) return
        confirm = false
        submitting = true
        scope.launch {
            try {
                completedMessage = session.submitLeaveCancel(
                    LeaveCancelForm(
                        leaveNo = value.leaveNo,
                        startTime = value.startTime,
                        endTime = value.endTime,
                        days = value.days,
                        remark = remark.trim(),
                        location = locationText,
                        selfieUrls = selfies.map { it.url },
                    ),
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                notices.error(e.message ?: "销假申请提交失败")
            } finally {
                submitting = false
            }
        }
    }

    val cs = MiuixTheme.colorScheme
    Scaffold(
        topBar = { SmallTopAppBar(title = "申请销假", navigationIcon = { BackNav { nav.popBackStack() } }) },
    ) { padding ->
        if (loading) {
            FeatureLoadingPage(padding)
            return@Scaffold
        }
        FeatureColumn(padding) {
            preparation?.let { value ->
                Card(modifier = Modifier.padding(top = 12.dp).fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("销假范围", style = MiuixTheme.textStyles.title3)
                        LeaveInfoRow("申请编号", value.leaveNo)
                        LeaveInfoRow("开始时间", value.startTime)
                        LeaveInfoRow("结束时间", value.endTime)
                        LeaveInfoRow("销假时长", formatLeaveDays(value.days), strong = true)
                    }
                }
            }
            FeatureSection("销假说明")
            TextField(
                value = remark,
                onValueChange = { remark = it.take(500) },
                label = "请说明返校和销假情况",
                modifier = Modifier.fillMaxWidth(),
            )
            if (session.leaveRule.requireLocation) {
                FeatureSection("位置校验")
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text("学校要求在允许范围内完成销假定位。", color = cs.onSurfaceVariantSummary, fontSize = 12.sp)
                        if (locationText.isNotBlank()) {
                            Text(locationText, modifier = Modifier.padding(top = 8.dp))
                            Text(
                                if (locationAllowed == true) "位置校验已通过" else "位置校验未通过",
                                color = if (locationAllowed == true) ColorSuccess else cs.primary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        Button(
                            onClick = ::requestLocation,
                            enabled = !locating,
                            modifier = Modifier.padding(top = 10.dp).fillMaxWidth(),
                        ) { Text(if (locating) "正在定位…" else if (locationText.isBlank()) "获取并校验位置" else "重新定位") }
                    }
                }
            }
            if (session.leaveRule.requireSelfie) {
                FeatureSection("现场自拍")
                UploadedImages(
                    title = "销假自拍",
                    images = selfies,
                    uploading = uploading,
                    addLabel = if (selfies.isEmpty()) "打开相机拍摄" else "重新拍摄",
                    onAdd = ::takeSelfie,
                    onRemove = { selfies.remove(it) },
                )
            }
            Button(
                onClick = {
                    val error = validationError()
                    if (error != null) notices.error(error, "请检查销假信息") else confirm = true
                },
                enabled = !submitting && preparation != null,
                modifier = Modifier.padding(top = 16.dp).fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) { Text(if (submitting) "正在提交…" else "检查并提交销假") }
            Text(
                "确认后销假申请会立即提交到学校学生管理平台。",
                color = cs.onSurfaceVariantSummary,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
            )
            FeatureBottomSpace()
        }
    }

    if (confirm) {
        val value = preparation
        LeaveConfirmDialog(
            title = "确认提交销假申请",
            summary = buildString {
                append(value?.startTime.orEmpty()).append(" 至 ").append(value?.endTime.orEmpty())
                append("\n销假时长：").append(formatLeaveDays(value?.days ?: 0.0))
                append("\n说明：").append(remark.trim())
                if (session.leaveRule.requireLocation) append("\n定位：").append(locationText)
                if (session.leaveRule.requireSelfie) append("\n自拍：").append(selfies.size).append(" 张")
            },
            confirmText = "确认销假",
            onDismiss = { confirm = false },
            onConfirm = ::submit,
        )
    }

    completedMessage?.let { message ->
        WindowDialog(show = true, title = "提交成功", onDismissRequest = {}) {
            Text(message, modifier = Modifier.fillMaxWidth())
            TextButton(
                text = "返回申请记录",
                onClick = {
                    completedMessage = null
                    if (!nav.popBackStack("leave", false)) nav.navigate("leave")
                },
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            )
        }
    }
}

private val ColorSuccess = androidx.compose.ui.graphics.Color(0xFF2E9E5B)
