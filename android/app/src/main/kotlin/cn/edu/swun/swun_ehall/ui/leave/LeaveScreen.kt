package cn.edu.swun.swun_ehall.ui.leave

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import cn.edu.swun.swun_ehall.data.model.LeaveDetail
import cn.edu.swun.swun_ehall.data.model.LeaveRecord
import cn.edu.swun.swun_ehall.data.session.Session
import cn.edu.swun.swun_ehall.ui.common.BackNav
import cn.edu.swun.swun_ehall.ui.common.FeatureBottomSpace
import cn.edu.swun.swun_ehall.ui.common.FeatureColumn
import cn.edu.swun.swun_ehall.ui.common.FeatureHint
import cn.edu.swun.swun_ehall.ui.common.FeatureLoading
import cn.edu.swun.swun_ehall.ui.common.FeatureLoadingPage
import cn.edu.swun.swun_ehall.ui.common.FeatureNoticeHost
import cn.edu.swun.swun_ehall.ui.common.FeatureSection
import cn.edu.swun.swun_ehall.ui.common.RefreshNav
import cn.edu.swun.swun_ehall.ui.common.rememberFeatureNotices
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun LeaveScreen(session: Session, nav: NavHostController) {
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val notices = rememberFeatureNotices(session.studentId, session.demoMode)
    FeatureNoticeHost(notices)

    suspend fun refresh() {
        loading = true
        try {
            session.refreshLeave()
            session.leaveError?.let { notices.error(it, "请销假服务暂不可用") }
        } finally {
            loading = false
        }
    }

    LaunchedEffect(session.studentId, session.demoMode) { refresh() }
    val cs = MiuixTheme.colorScheme
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "请假与销假",
                navigationIcon = { BackNav { nav.popBackStack() } },
                actions = { RefreshNav { scope.launch { refresh() } } },
            )
        },
    ) { padding ->
        if (loading && session.leaveRecords.isEmpty()) {
            FeatureLoadingPage(padding)
            return@Scaffold
        }
        FeatureColumn(padding) {
            Card(modifier = Modifier.padding(top = 12.dp).fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 18.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    LeaveMetric("请假次数", session.leaveSummary.count.toString())
                    LeaveMetric("累计天数", formatLeaveDays(session.leaveSummary.days))
                    LeaveMetric("待销假", session.leaveSummary.pendingCancel.toString())
                }
            }
            Card(modifier = Modifier.padding(top = 12.dp).fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(session.leaveStudentInfo.name.ifBlank { session.displayName }, style = MiuixTheme.textStyles.title3)
                    LeaveInfoRow("学号", session.leaveStudentInfo.studentId.ifBlank { session.studentId })
                    LeaveInfoRow("学院", session.leaveStudentInfo.college)
                    LeaveInfoRow("班级", session.leaveStudentInfo.klass)
                    LeaveInfoRow("宿舍", session.leaveStudentInfo.dorm)
                }
            }
            Button(
                onClick = { nav.navigate("leave/apply") },
                modifier = Modifier.padding(top = 14.dp).fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) { Text("申请请假") }
            FeatureSection("申请记录")
            when {
                loading && session.leaveRecords.isEmpty() -> FeatureLoading()
                session.leaveRecords.isEmpty() -> FeatureHint(
                    if (session.leaveError == null) "暂无请假记录" else "记录暂未加载，请点右上角重试",
                )
                else -> session.leaveRecords.forEach { record ->
                    LeaveRecordCard(record) {
                        nav.navigate("leave/detail/${Uri.encode(record.leaveNo)}")
                    }
                }
            }
            Text(
                "申请和销假将提交到学校学生管理平台，请核对时间、事由和材料后再确认。",
                color = cs.onSurfaceVariantSummary,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 14.dp),
            )
            FeatureBottomSpace()
        }
    }
}

@Composable
private fun LeaveMetric(label: String, value: String) {
    val cs = MiuixTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 6.dp)) {
        Text(value, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, textAlign = TextAlign.Center)
        Text(label, color = cs.onSurfaceVariantSummary, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun LeaveRecordCard(record: LeaveRecord, onClick: () -> Unit) {
    val cs = MiuixTheme.colorScheme
    Card(modifier = Modifier.padding(bottom = 10.dp).fillMaxWidth(), onClick = onClick) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    listOf(record.leaveTypeName, record.specificTypeName).filter { it.isNotBlank() }.joinToString(" · ")
                        .ifBlank { record.leaveName.ifBlank { "请假" } },
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    record.statusName.ifBlank { "处理中" },
                    color = leaveStatusTone(record.statusCode, cs.primary),
                    fontSize = 13.sp,
                )
            }
            Text(
                "${record.startTime} 至 ${record.endTime}",
                color = cs.onSurfaceVariantSummary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 5.dp),
            )
            Row(Modifier.fillMaxWidth().padding(top = 5.dp)) {
                Text(formatLeaveDays(record.totalDays.takeIf { it > 0 } ?: record.days), fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                if (record.canCancel) Text("可申请销假", color = cs.primary, fontSize = 12.sp)
            }
            if (record.reason.isNotBlank()) {
                Text(record.reason, color = cs.onSurfaceVariantSummary, fontSize = 13.sp, modifier = Modifier.padding(top = 5.dp))
            }
        }
    }
}

@Composable
fun LeaveDetailScreen(session: Session, nav: NavHostController, leaveNo: String) {
    var loading by remember(leaveNo) { mutableStateOf(true) }
    var detail by remember(leaveNo) { mutableStateOf<LeaveDetail?>(null) }
    val scope = rememberCoroutineScope()
    val notices = rememberFeatureNotices(session.studentId, leaveNo, session.demoMode)
    FeatureNoticeHost(notices)
    val record = session.leaveRecords.firstOrNull { it.leaveNo == leaveNo }

    suspend fun refresh() {
        loading = true
        try {
            detail = session.leaveDetail(leaveNo)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            notices.error(e.message ?: "请假详情加载失败")
        } finally {
            loading = false
        }
    }

    LaunchedEffect(leaveNo, session.demoMode) { refresh() }
    val cs = MiuixTheme.colorScheme
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "请假详情",
                navigationIcon = { BackNav { nav.popBackStack() } },
                actions = { RefreshNav { scope.launch { refresh() } } },
            )
        },
    ) { padding ->
        if (loading && detail == null) {
            FeatureLoadingPage(padding)
            return@Scaffold
        }
        FeatureColumn(padding) {
            record?.let {
                Card(modifier = Modifier.padding(top = 12.dp).fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(it.leaveTypeName.ifBlank { "请假申请" }, style = MiuixTheme.textStyles.title3, modifier = Modifier.weight(1f))
                            Text(
                                it.statusName.ifBlank { "处理中" },
                                color = leaveStatusTone(it.statusCode, cs.primary),
                                fontSize = 13.sp,
                            )
                        }
                        LeaveInfoRow("申请编号", it.leaveNo)
                        LeaveInfoRow("开始时间", it.startTime)
                        LeaveInfoRow("结束时间", it.endTime)
                        LeaveInfoRow("总时长", formatLeaveDays(it.totalDays.takeIf { days -> days > 0 } ?: it.days), strong = true)
                    }
                }
            }
            val value = detail
            if (value == null || value.entries.isEmpty()) {
                FeatureHint("暂无申请详情")
            } else {
                FeatureSection("申请内容")
                value.entries.forEachIndexed { index, entry ->
                    Card(modifier = Modifier.padding(bottom = 10.dp).fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                entry.leaveTypeName.ifBlank {
                                    when (entry.leaveCode) { 2 -> "续假"; 3 -> "销假"; else -> "请假" }
                                },
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (entry.specificTypeName.isNotBlank()) LeaveInfoRow("类别", entry.specificTypeName)
                            LeaveInfoRow("开始时间", entry.startTime)
                            LeaveInfoRow("结束时间", entry.endTime)
                            LeaveInfoRow("时长", formatLeaveDays(entry.days))
                            LeaveInfoRow("说明", entry.reason)
                            if (entry.address.isNotBlank()) LeaveInfoRow("去向", entry.address)
                            if (entry.proofUrls.isNotEmpty()) LeaveInfoRow("证明材料", "${entry.proofUrls.size} 张")
                            if (entry.statusName.isNotBlank()) LeaveInfoRow("状态", entry.statusName)
                        }
                    }
                    if (index < value.entries.lastIndex) Spacer(Modifier.height(2.dp))
                }
            }
            FeatureSection("审批轨迹")
            if (value == null || value.approvals.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text("暂无审批记录", color = cs.onSurfaceVariantSummary, modifier = Modifier.padding(16.dp))
                }
            } else {
                Card(modifier = Modifier.fillMaxWidth()) {
                    value.approvals.forEachIndexed { index, step ->
                        if (index > 0) HorizontalDivider()
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(step.title.ifBlank { step.roleName.ifBlank { "审批节点" } }, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                Text(
                                    step.statusName,
                                    color = if (step.active) cs.primary else cs.onSurfaceVariantSummary,
                                    fontSize = 12.sp,
                                )
                            }
                            val operator = listOf(step.roleName, step.operatorName).filter { it.isNotBlank() }.joinToString(" · ")
                            if (operator.isNotBlank()) Text(operator, color = cs.onSurfaceVariantSummary, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                            if (step.time.isNotBlank()) Text(step.time, color = cs.onSurfaceVariantSummary, fontSize = 12.sp)
                            if (step.remark.isNotBlank()) Text(step.remark, fontSize = 13.sp, modifier = Modifier.padding(top = 5.dp))
                        }
                    }
                }
            }
            if (record?.canCancel == true) {
                Button(
                    onClick = { nav.navigate("leave/cancel/${Uri.encode(leaveNo)}") },
                    modifier = Modifier.padding(top = 16.dp).fillMaxWidth(),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) { Text("申请销假") }
            }
            FeatureBottomSpace()
        }
    }
}
