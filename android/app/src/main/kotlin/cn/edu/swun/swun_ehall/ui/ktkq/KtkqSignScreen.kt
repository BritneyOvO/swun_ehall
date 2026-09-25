package cn.edu.swun.swun_ehall.ui.ktkq

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import cn.edu.swun.swun_ehall.data.fences.CampusFences
import cn.edu.swun.swun_ehall.data.geo.Geo
import cn.edu.swun.swun_ehall.data.http.formatKtkqCst
import cn.edu.swun.swun_ehall.data.http.formatKtkqCstRange
import cn.edu.swun.swun_ehall.data.http.ktkqNeedsCode
import cn.edu.swun.swun_ehall.data.http.ktkqSignTypeLabel
import cn.edu.swun.swun_ehall.data.http.ktkqStatusLabel
import cn.edu.swun.swun_ehall.data.model.ClockFence
import cn.edu.swun.swun_ehall.data.model.GeoFix
import cn.edu.swun.swun_ehall.data.model.KtkqActivity
import cn.edu.swun.swun_ehall.data.model.SignActivity
import cn.edu.swun.swun_ehall.data.session.Session
import cn.edu.swun.swun_ehall.ui.clock.CampusMap
import cn.edu.swun.swun_ehall.ui.common.BackNav
import cn.edu.swun.swun_ehall.ui.common.FeatureBottomSpace
import cn.edu.swun.swun_ehall.ui.common.FeatureColumn
import cn.edu.swun.swun_ehall.ui.common.FeatureHint
import cn.edu.swun.swun_ehall.ui.common.FeatureLoadingPage
import cn.edu.swun.swun_ehall.ui.common.FeatureSection
import cn.edu.swun.swun_ehall.ui.common.RefreshNav
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun KtkqSignScreen(session: Session, nav: NavHostController, activityId: String) {
    val slot = remember(activityId, session.ktkq.size, session.pendingKtkqSlot) { session.ktkqSlotByNav(activityId) }
    var loading by remember { mutableStateOf(true) }
    var locating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<SignActivity?>(null) }
    var pos by remember { mutableStateOf<GeoFix?>(null) }
    var locError by remember { mutableStateOf<String?>(null) }
    var punchingId by remember { mutableStateOf<String?>(null) }
    val codes = remember { mutableStateMapOf<String, String>() }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val cs = MiuixTheme.colorScheme

    fun toast(m: String) {
        Toast.makeText(ctx, m, Toast.LENGTH_SHORT).show()
    }

    suspend fun locate(force: Boolean = false) {
        locating = true
        try {
            val fix = session.locateCampus()
            pos = fix
            locError = null
            if (fix.accuracy > 80) toast("精度偏大，教室里建议打开 Wi‑Fi 后再定位")
        } catch (e: Exception) {
            if (pos == null) locError = e.message ?: "定位失败，请打开系统定位后重试"
        } finally {
            locating = false
        }
    }

    suspend fun reload(refresh: Boolean = false) {
        val base = slot ?: return
        loading = true
        error = null
        try {
            info = session.signForLesson(base, refresh = refresh)
        } catch (e: Exception) {
            error = e.message?.removePrefix("Exception: ") ?: "加载失败"
        } finally {
            loading = false
        }
        locate()
    }

    suspend fun punch(
        act: KtkqActivity,
        data: SignActivity,
        room: String,
        mode: PunchSpot,
        fenceName: String?,
        toast: (String) -> Unit,
    ) {
        val id = act.activityId
        if (id.isEmpty()) {
            toast("没有签到活动")
            return
        }
        val type = act.signType.ifBlank { data.signType }
        var code = (codes[id] ?: act.signCode).trim()
        if (code.isEmpty()) code = data.signCode
        if (!session.demoMode && ktkqNeedsCode(type) && code.isEmpty()) {
            toast("请输入教师展示的签到码")
            return
        }
        if (mode == PunchSpot.Gps) {
            locate(force = true)
            if (pos == null) {
                toast(locError ?: "还没有定位")
                return
            }
        }
        punchingId = id
        try {
            if (session.demoMode) {
                info = data.copy(
                    status = "already_signed",
                    message = "已签到",
                    activities = data.activities.map {
                        if (it.activityId == id) it.copy(status = "already_signed", message = "已签到") else it
                    },
                )
                toast(
                    when (mode) {
                        PunchSpot.Building -> "示例模式：已用 ${fenceName ?: "楼"} 楼中心签到"
                        PunchSpot.Fence -> "示例模式：已用 ${fenceName ?: "楼"} 围栏随机点签到"
                        PunchSpot.Gps -> "示例模式：已模拟签到成功"
                    },
                )
                return
            }
            val msg = when (mode) {
                PunchSpot.Building -> session.signInBuildingCenter(act.toSign(data, room), code)
                PunchSpot.Fence -> session.signInFenceRandom(act.toSign(data, room), code)
                PunchSpot.Gps -> {
                    val fix = pos ?: return
                    session.signIn(
                        act.toSign(data, room),
                        fix.latitude,
                        fix.longitude,
                        fix.source,
                        fix.datum,
                        code = code,
                        accuracy = fix.accuracy,
                    )
                }
            }
            toast(msg)
            if (msg.contains("成功") || msg.contains("已签到") || msg.contains("楼中心") || msg.contains("围栏")) {
                reload(refresh = true)
            }
        } catch (e: Exception) {
            toast(e.message?.removePrefix("Exception: ") ?: "签到失败")
        } finally {
            punchingId = null
        }
    }

    LaunchedEffect(activityId, session.loggedIn, session.demoMode) {
        if (slot == null) {
            loading = false
            error = "没有这个签到活动"
            return@LaunchedEffect
        }
        reload()
    }

    val data = info
    val room = data?.classroom?.ifBlank { slot?.classroom }.orEmpty()
    val fence = CampusFences.forRoom(room)
    val fenceRing = CampusFences.outlineFor(room).orEmpty()
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "课堂签到",
                navigationIcon = { BackNav { nav.popBackStack() } },
                actions = {
                    RefreshNav {
                        scope.launch { reload(refresh = true) }
                    }
                },
            )
        },
    ) { padding ->
        if (loading) {
            FeatureLoadingPage(padding)
            return@Scaffold
        }
        FeatureColumn(padding) {
            if (session.developerMode) {
                FenceProbeButton(session, slot)
            }
            error?.let { FeatureHint(it, error = true) }
            if (data == null && error != null) {
                FeatureBottomSpace()
                return@FeatureColumn
            }
            if (data != null) {
                CourseCard(data, slot)
                Spacer(Modifier.height(12.dp))
                LocCard(
                    pos = pos,
                    locating = locating,
                    locError = locError,
                    onRelocate = { scope.launch { locate(force = true) } },
                )
                if (fenceRing.size >= 3) {
                    val ring = fenceRing.map { (lat, lng) -> Geo.wgs84ToGcj02(lat, lng) }
                    CampusMap(
                        pos,
                        listOf(
                            ClockFence(
                                name = fence?.name ?: room,
                                latitude = ring.map { it.first }.average(),
                                longitude = ring.map { it.second }.average(),
                                radiusMeters = 0.0,
                                polygon = ring,
                            ),
                        ),
                        modifier = Modifier.padding(top = 12.dp),
                        zoom = 17f,
                    )
                }
                FeatureSection("签到活动")
                if (data.activities.isEmpty()) {
                    FeatureHint(data.message.ifBlank { "无签到活动" })
                } else {
                    data.activities.forEach { act ->
                        ActivityCard(
                            act = act,
                            fenceName = fence?.name,
                            developer = session.developerMode,
                            punching = punchingId == act.activityId,
                            code = codes[act.activityId] ?: act.signCode,
                            onCode = { codes[act.activityId] = it },
                            onPunch = {
                                scope.launch {
                                    punch(act, data, room, PunchSpot.Gps, fence?.name, ::toast)
                                }
                            },
                            onFence = {
                                scope.launch {
                                    punch(act, data, room, PunchSpot.Fence, fence?.name, ::toast)
                                }
                            },
                            onBuilding = {
                                scope.launch {
                                    punch(act, data, room, PunchSpot.Building, fence?.name, ::toast)
                                }
                            },
                        )
                    }
                }
                FeatureSection("本课记录")
                if (data.history.isEmpty()) {
                    FeatureHint("暂无签到记录")
                } else {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            data.history.take(10).forEachIndexed { i, h ->
                                if (i > 0) top.yukonga.miuix.kmp.basic.HorizontalDivider()
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(formatKtkqCst(h.time).ifBlank { h.time })
                                        if (h.course.isNotEmpty()) {
                                            Text(h.course, color = cs.onSurfaceVariantSummary, fontSize = 13.sp)
                                        }
                                    }
                                    Text(h.status, color = cs.onSurfaceVariantSummary, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
            FeatureBottomSpace()
        }
    }
}

@Composable
private fun CourseCard(data: SignActivity, slot: SignActivity?) {
    val cs = MiuixTheme.colorScheme
    val status = data.status
    Card(modifier = Modifier.padding(top = 12.dp).fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    data.course.ifBlank { slot?.course.orEmpty() },
                    style = MiuixTheme.textStyles.title3,
                    modifier = Modifier.weight(1f),
                )
                if (status.isNotEmpty()) {
                    Text(
                        ktkqStatusLabel(status),
                        color = ktkqTone(status, cs.primary),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            val time = data.timeText.ifBlank { slot?.timeText.orEmpty() }
            if (time.isNotEmpty()) {
                Text(time, color = cs.onSurfaceVariantSummary, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            }
            val teacher = data.teacher.ifBlank { slot?.teacher.orEmpty() }
            val room = data.classroom.ifBlank { slot?.classroom.orEmpty() }
            val sub = listOf(teacher, room).filter { it.isNotBlank() }.joinToString("  ·  ")
            if (sub.isNotEmpty()) {
                Text(sub, color = cs.onSurfaceVariantSummary, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            }
            val week = data.week.takeIf { it > 0 } ?: slot?.week
            if (week != null && week > 0) {
                Text("第 $week 周", color = cs.onSurfaceVariantSummary, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun LocCard(
    pos: GeoFix?,
    locating: Boolean,
    locError: String?,
    onRelocate: () -> Unit,
) {
    val cs = MiuixTheme.colorScheme
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("当前位置", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(
                    text = if (locating) "定位中" else "重新定位",
                    onClick = onRelocate,
                    enabled = !locating,
                )
            }
            locError?.let {
                Text(it, color = cs.error, modifier = Modifier.padding(top = 4.dp))
            }
            if (pos == null && locError == null) {
                Text(if (locating) "正在定位…" else "尚未定位", color = cs.onSurfaceVariantSummary)
            }
            pos?.let { p ->
                Text(
                    if (p.source == "demo") "西南民族大学（示例定位）" else "当前位置 ${p.coordText}",
                )
                Text(
                    "${p.sourceLabel} · 精度 ${p.accuracy.toInt()} 米",
                    color = cs.onSurfaceVariantSummary,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun ActivityCard(
    act: KtkqActivity,
    fenceName: String?,
    developer: Boolean,
    punching: Boolean,
    code: String,
    onCode: (String) -> Unit,
    onPunch: () -> Unit,
    onFence: () -> Unit,
    onBuilding: () -> Unit,
) {
    val cs = MiuixTheme.colorScheme
    val pending = act.status == "pending_signin"
    val range = formatKtkqCstRange(act.startTime, act.endTime)
    Card(modifier = Modifier.padding(bottom = 12.dp).fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${act.title} · ${ktkqSignTypeLabel(act.signType)}",
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    ktkqStatusLabel(act.status),
                    color = ktkqTone(act.status, cs.primary),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (range.isNotEmpty()) {
                Text(range, color = cs.onSurfaceVariantSummary, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
            if (pending && ktkqNeedsCode(act.signType)) {
                Spacer(Modifier.height(10.dp))
                TextField(
                    value = code,
                    onValueChange = onCode,
                    label = "教师口令 / 数字码",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (pending) {
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = onPunch,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !punching,
                ) { Text(if (punching) "签到中…" else "立即签到") }
                if (developer) {
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onFence,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !punching && fenceName != null,
                    ) {
                        Text(if (fenceName == null) "一键定位打卡（未识别楼栋）" else "一键定位打卡")
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        text = if (fenceName == null) "楼中心签到（未识别楼栋）" else "用 $fenceName 楼中心签到",
                        onClick = onBuilding,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !punching && fenceName != null,
                    )
                }
            }
        }
    }
}

@Composable
private fun FenceProbeButton(session: Session, slot: SignActivity?) {
    var testing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Button(
        onClick = {
            if (testing) return@Button
            scope.launch {
                testing = true
                result = try {
                    session.testFencePunch(slot)
                } catch (e: Exception) {
                    e.message?.removePrefix("Exception: ") ?: "预检失败"
                }
                testing = false
            }
        },
        modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
        enabled = !testing,
    ) { Text(if (testing) "预检中…" else "测试课堂打卡") }
    result?.let { FeatureHint(it) }
}

private enum class PunchSpot { Gps, Fence, Building }

private fun ktkqTone(status: String, primary: Color): Color = when (status) {
    "pending_signin", "not_in_scope" -> primary
    "already_signed" -> Color(0xFF2E7D32)
    else -> Color(0xFF8A8A8A)
}

private fun KtkqActivity.toSign(data: SignActivity, room: String): SignActivity = data.copy(
    activityId = activityId,
    title = title,
    status = status,
    signType = signType,
    startTime = startTime,
    endTime = endTime,
    signCode = signCode,
    message = message,
    classroom = room.ifBlank { data.classroom },
)
