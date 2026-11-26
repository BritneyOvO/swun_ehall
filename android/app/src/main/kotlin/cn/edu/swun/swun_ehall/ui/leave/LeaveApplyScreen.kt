package cn.edu.swun.swun_ehall.ui.leave

import android.app.Application
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import cn.edu.swun.swun_ehall.SwunApplication
import cn.edu.swun.swun_ehall.data.model.LeaveApplyForm
import cn.edu.swun.swun_ehall.data.session.Session
import cn.edu.swun.swun_ehall.ui.common.BackNav
import cn.edu.swun.swun_ehall.ui.common.FeatureBottomSpace
import cn.edu.swun.swun_ehall.ui.common.FeatureColumn
import cn.edu.swun.swun_ehall.ui.common.FeatureLoadingPage
import cn.edu.swun.swun_ehall.ui.common.FeatureNoticeHost
import cn.edu.swun.swun_ehall.ui.common.FeatureSection
import cn.edu.swun.swun_ehall.ui.common.rememberFeatureNotices
import java.time.LocalDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
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

private enum class ApplyChoice { LeaveType, SpecificType, Province, City, County }

@Composable
fun LeaveApplyScreen(session: Session, nav: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val notices = rememberFeatureNotices(session.studentId, session.demoMode, "leave-apply")
    FeatureNoticeHost(notices)
    val areas = remember {
        runCatching {
            context.assets.open("china_area.json").bufferedReader().use { ChinaAreaCatalog.fromJson(it.readText()) }
        }.getOrNull()
    }
    val now = remember { LocalDateTime.now().withSecond(0).withNano(0) }
    var loading by remember { mutableStateOf(true) }
    var unavailable by remember { mutableStateOf<String?>(null) }
    var choice by remember { mutableStateOf<ApplyChoice?>(null) }
    var leaveTypeCode by rememberSaveable { mutableStateOf("") }
    var specificTypeCode by rememberSaveable { mutableStateOf("") }
    var startText by rememberSaveable { mutableStateOf(now.format(leaveDateTimeFormatter)) }
    var endText by rememberSaveable { mutableStateOf(now.plusDays(1).format(leaveDateTimeFormatter)) }
    var days by remember { mutableStateOf(0.0) }
    var calculatingDays by remember { mutableStateOf(false) }
    var daysError by remember { mutableStateOf<String?>(null) }
    var reason by rememberSaveable { mutableStateOf("") }
    var parentMobile by rememberSaveable { mutableStateOf("") }
    var provinceCode by rememberSaveable { mutableStateOf("") }
    var cityCode by rememberSaveable { mutableStateOf("") }
    var countyCode by rememberSaveable { mutableStateOf("") }
    var addressDetail by rememberSaveable { mutableStateOf("") }
    var internshipAddress by rememberSaveable { mutableStateOf("") }
    var uploading by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var completedMessage by remember { mutableStateOf<String?>(null) }
    val proofImages = remember { mutableStateListOf<UploadedLeaveImage>() }

    val proofPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        SwunApplication.markPickingMedia(context.applicationContext as Application, false)
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            uploading = true
            try {
                val url = session.uploadLeaveImage(uri)
                val label = uri.lastPathSegment?.substringAfterLast('/')?.takeLast(48).orEmpty().ifBlank { "证明材料" }
                proofImages += UploadedLeaveImage(url, label)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                notices.error(e.message ?: "证明材料上传失败")
            } finally {
                uploading = false
            }
        }
    }

    LaunchedEffect(session.studentId, session.demoMode) {
        loading = true
        unavailable = null
        try {
            if (session.leaveTypes.isEmpty() || session.leaveSpecificTypes.isEmpty()) session.refreshLeave()
            session.checkCanApplyLeave()
            session.leaveError?.let { error(it) }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            unavailable = e.message ?: "当前不能申请请假"
            notices.error(unavailable!!, "暂不能申请")
        } finally {
            loading = false
        }
    }

    LaunchedEffect(startText, endText, session.demoMode) {
        val start = runCatching { LocalDateTime.parse(startText, leaveDateTimeFormatter) }.getOrNull()
        val end = runCatching { LocalDateTime.parse(endText, leaveDateTimeFormatter) }.getOrNull()
        if (start == null || end == null || !end.isAfter(start)) {
            days = 0.0
            daysError = "结束时间必须晚于开始时间"
            return@LaunchedEffect
        }
        delay(250)
        calculatingDays = true
        daysError = null
        try {
            days = session.calculateLeaveDays(startText, endText)
            if (days <= 0.0) daysError = "学校系统未返回有效请假时长"
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            days = 0.0
            daysError = e.message ?: "请假时长计算失败"
        } finally {
            calculatingDays = false
        }
    }

    fun optionName(code: String, specific: Boolean = false): String? {
        val source = if (specific) session.leaveSpecificTypes else session.leaveTypes
        return source.firstOrNull { it.code == code }?.name
    }

    val province = areas?.provinces?.firstOrNull { it.code == provinceCode }
    val cities = remember(areas, provinceCode) { areas?.cities(provinceCode).orEmpty() }
    val city = cities.firstOrNull { it.code == cityCode }
    val counties = remember(areas, cityCode) { areas?.counties(cityCode).orEmpty() }
    val county = counties.firstOrNull { it.code == countyCode }
    val addressName = listOfNotNull(province?.name, city?.name, county?.name)
        .fold(emptyList<String>()) { result, name -> if (result.lastOrNull() == name) result else result + name }
        .joinToString("")

    fun validationError(): String? {
        val start = runCatching { LocalDateTime.parse(startText, leaveDateTimeFormatter) }.getOrNull()
        val end = runCatching { LocalDateTime.parse(endText, leaveDateTimeFormatter) }.getOrNull()
        return when {
            unavailable != null -> unavailable
            leaveTypeCode.isBlank() -> "请选择请假类型"
            leaveTypeCode == "1" && specificTypeCode.isBlank() -> "请选择事假类型"
            start == null || end == null || !end.isAfter(start) -> "结束时间必须晚于开始时间"
            calculatingDays -> "正在计算请假时长，请稍候"
            days <= 0.0 -> daysError ?: "请假时长无效"
            reason.trim().isEmpty() -> "请填写请假事由"
            parentMobile.length !in 7..20 -> "请填写有效的家长联系电话"
            session.leaveRule.requireAddress && county == null -> "请选择完整的外出去向"
            session.leaveRule.requireAddress && addressDetail.trim().isEmpty() -> "请填写详细地址"
            specificTypeCode == "5" && internshipAddress.trim().isEmpty() -> "请填写实习地址"
            uploading -> "证明材料仍在上传，请稍候"
            submitting -> "正在提交，请稍候"
            else -> null
        }
    }

    fun submit() {
        if (submitting) return
        submitting = true
        confirm = false
        scope.launch {
            try {
                completedMessage = session.submitLeave(
                    LeaveApplyForm(
                        leaveTypeCode = leaveTypeCode,
                        specificTypeCode = specificTypeCode,
                        startTime = startText,
                        endTime = endText,
                        days = days,
                        reason = reason.trim(),
                        parentMobile = parentMobile,
                        addressCode = countyCode,
                        addressName = addressName,
                        addressDetail = addressDetail.trim(),
                        internshipAddress = internshipAddress.trim(),
                        proofUrls = proofImages.map { it.url },
                    ),
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                notices.error(e.message ?: "请假申请提交失败")
            } finally {
                submitting = false
            }
        }
    }

    val cs = MiuixTheme.colorScheme
    Scaffold(
        topBar = { SmallTopAppBar(title = "申请请假", navigationIcon = { BackNav { nav.popBackStack() } }) },
    ) { padding ->
        if (loading) {
            FeatureLoadingPage(padding)
            return@Scaffold
        }
        FeatureColumn(padding) {
            unavailable?.let { message ->
                Card(modifier = Modifier.padding(top = 12.dp).fillMaxWidth()) {
                    Text(message, color = cs.primary, modifier = Modifier.padding(16.dp))
                }
            }
            FeatureSection("请假信息")
            LeaveSelectionField("请假类型", optionName(leaveTypeCode), enabled = unavailable == null) { choice = ApplyChoice.LeaveType }
            if (leaveTypeCode == "1") {
                LeaveSelectionField("事假类型", optionName(specificTypeCode, specific = true), enabled = unavailable == null) { choice = ApplyChoice.SpecificType }
            }
            LeaveSelectionField("开始时间", startText, enabled = unavailable == null) {
                val initial = LocalDateTime.parse(startText, leaveDateTimeFormatter)
                showLeaveDateTimePicker(context, initial) { startText = it.withSecond(0).format(leaveDateTimeFormatter) }
            }
            LeaveSelectionField("结束时间", endText, enabled = unavailable == null) {
                val initial = LocalDateTime.parse(endText, leaveDateTimeFormatter)
                showLeaveDateTimePicker(context, initial) { endText = it.withSecond(0).format(leaveDateTimeFormatter) }
            }
            Card(modifier = Modifier.padding(top = 8.dp).fillMaxWidth()) {
                Row(Modifier.padding(14.dp)) {
                    Text("请假时长", color = cs.onSurfaceVariantSummary, modifier = Modifier.weight(1f))
                    Text(
                        when {
                            calculatingDays -> "计算中…"
                            daysError != null -> daysError.orEmpty()
                            else -> formatLeaveDays(days)
                        },
                        color = if (daysError == null) cs.onBackground else cs.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            TextField(
                value = reason,
                onValueChange = { reason = it.take(500) },
                label = "请假事由",
                modifier = Modifier.padding(top = 10.dp).fillMaxWidth(),
            )
            TextField(
                value = parentMobile,
                onValueChange = { parentMobile = it.filter(Char::isDigit).take(20) },
                label = "家长联系电话",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.padding(top = 10.dp).fillMaxWidth(),
            )
            FeatureSection("外出去向")
            Text(
                if (session.leaveRule.requireAddress) "学校要求填写行政区划和详细地址。" else "地址为选填项，可用于说明请假期间去向。",
                color = cs.onSurfaceVariantSummary,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )
            LeaveSelectionField("省 / 自治区 / 直辖市", province?.name, enabled = areas != null) { choice = ApplyChoice.Province }
            LeaveSelectionField("城市", city?.name, enabled = province != null && cities.isNotEmpty()) { choice = ApplyChoice.City }
            LeaveSelectionField("区 / 县", county?.name, enabled = city != null && counties.isNotEmpty()) { choice = ApplyChoice.County }
            TextField(
                value = addressDetail,
                onValueChange = { addressDetail = it.take(200) },
                label = "详细地址",
                modifier = Modifier.padding(top = 10.dp).fillMaxWidth(),
            )
            if (specificTypeCode == "5") {
                TextField(
                    value = internshipAddress,
                    onValueChange = { internshipAddress = it.take(200) },
                    label = "实习地址",
                    modifier = Modifier.padding(top = 10.dp).fillMaxWidth(),
                )
            }
            FeatureSection("证明材料")
            UploadedImages(
                title = "图片附件",
                images = proofImages,
                uploading = uploading,
                addLabel = "选择 JPG / PNG 图片",
                onAdd = {
                    SwunApplication.markPickingMedia(context.applicationContext as Application, true)
                    proofPicker.launch("image/*")
                },
                onRemove = { proofImages.remove(it) },
            )
            Button(
                onClick = {
                    val error = validationError()
                    if (error != null) notices.error(error, "请检查申请信息") else confirm = true
                },
                enabled = !submitting && unavailable == null,
                modifier = Modifier.padding(top = 16.dp).fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) { Text(if (submitting) "正在提交…" else "检查并提交") }
            Text(
                "确认后申请会立即进入学校审批流程。",
                color = cs.onSurfaceVariantSummary,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
            )
            FeatureBottomSpace()
        }
    }

    choice?.let { current ->
        val options = when (current) {
            ApplyChoice.LeaveType -> session.leaveTypes.map { it.code to it.name }
            ApplyChoice.SpecificType -> session.leaveSpecificTypes.map { it.code to it.name }
            ApplyChoice.Province -> areas?.provinces.orEmpty().map { it.code to it.name }
            ApplyChoice.City -> cities.map { it.code to it.name }
            ApplyChoice.County -> counties.map { it.code to it.name }
        }
        LeaveOptionDialog(
            title = when (current) {
                ApplyChoice.LeaveType -> "选择请假类型"
                ApplyChoice.SpecificType -> "选择事假类型"
                ApplyChoice.Province -> "选择省份"
                ApplyChoice.City -> "选择城市"
                ApplyChoice.County -> "选择区县"
            },
            options = options,
            onDismiss = { choice = null },
            onSelected = { code ->
                when (current) {
                    ApplyChoice.LeaveType -> {
                        leaveTypeCode = code
                        if (code != "1") specificTypeCode = ""
                    }
                    ApplyChoice.SpecificType -> specificTypeCode = code
                    ApplyChoice.Province -> { provinceCode = code; cityCode = ""; countyCode = "" }
                    ApplyChoice.City -> { cityCode = code; countyCode = "" }
                    ApplyChoice.County -> countyCode = code
                }
                choice = null
            },
        )
    }

    if (confirm) {
        LeaveConfirmDialog(
            title = "确认提交请假申请",
            summary = buildString {
                append(optionName(leaveTypeCode).orEmpty())
                optionName(specificTypeCode, specific = true)?.let { append(" · ").append(it) }
                append("\n").append(startText).append(" 至 ").append(endText)
                append("\n请假时长：").append(formatLeaveDays(days))
                append("\n事由：").append(reason.trim())
                if (addressName.isNotBlank() || addressDetail.isNotBlank()) append("\n去向：").append(addressName).append(addressDetail.trim())
                append("\n证明材料：").append(proofImages.size).append(" 张")
            },
            confirmText = "确认提交",
            onDismiss = { confirm = false },
            onConfirm = ::submit,
        )
    }

    completedMessage?.let { message ->
        WindowDialog(show = true, title = "提交成功", onDismissRequest = {}) {
            Text(message, modifier = Modifier.fillMaxWidth())
            TextButton(
                text = "返回申请记录",
                onClick = { completedMessage = null; nav.popBackStack() },
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            )
        }
    }
}
