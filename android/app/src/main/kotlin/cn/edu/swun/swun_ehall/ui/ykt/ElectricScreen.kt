package cn.edu.swun.swun_ehall.ui.ykt

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import cn.edu.swun.swun_ehall.data.model.ElectricSelection
import cn.edu.swun.swun_ehall.data.model.YktDirectoryOption
import cn.edu.swun.swun_ehall.data.model.YktPayMethod
import cn.edu.swun.swun_ehall.data.model.YktPaymentResult
import cn.edu.swun.swun_ehall.data.session.Session
import cn.edu.swun.swun_ehall.ui.common.BackNav
import cn.edu.swun.swun_ehall.ui.common.FeatureBottomSpace
import cn.edu.swun.swun_ehall.ui.common.FeatureColumn
import cn.edu.swun.swun_ehall.ui.common.FeatureLoading
import cn.edu.swun.swun_ehall.ui.common.FeatureNoticeHost
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
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

private enum class ElectricLevel(val title: String) { School("校区 / 电表类型"), Building("楼宇"), Floor("楼层"), Room("房间") }

@Composable
fun ElectricScreen(session: Session, nav: NavHostController) {
    var schools by remember { mutableStateOf<List<YktDirectoryOption>>(emptyList()) }
    var buildings by remember { mutableStateOf<List<YktDirectoryOption>>(emptyList()) }
    var floors by remember { mutableStateOf<List<YktDirectoryOption>>(emptyList()) }
    var rooms by remember { mutableStateOf<List<YktDirectoryOption>>(emptyList()) }
    var school by remember { mutableStateOf<YktDirectoryOption?>(null) }
    var building by remember { mutableStateOf<YktDirectoryOption?>(null) }
    var floor by remember { mutableStateOf<YktDirectoryOption?>(null) }
    var room by remember { mutableStateOf<YktDirectoryOption?>(null) }
    var chooser by remember { mutableStateOf<ElectricLevel?>(null) }
    var loading by remember { mutableStateOf(true) }
    var submitting by remember { mutableStateOf(false) }
    var amount by remember { mutableStateOf("") }
    var method by remember { mutableStateOf(YktPayMethod.Wechat) }
    var result by remember { mutableStateOf<YktPaymentResult?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val notices = rememberFeatureNotices(session.studentId, session.demoMode)
    FeatureNoticeHost(notices)

    LaunchedEffect(session.studentId, session.demoMode) {
        loading = true
        try {
            schools = session.electricSchools()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            notices.error(e.message ?: "电费目录加载失败")
        } finally {
            loading = false
        }
    }

    Scaffold(
        topBar = { SmallTopAppBar(title = "宿舍电费充值", navigationIcon = { BackNav { nav.popBackStack() } }) },
    ) { padding ->
        FeatureColumn(padding) {
            val cs = MiuixTheme.colorScheme
            Text(
                "依次选择校区、电表类型、楼宇、楼层和房间。学校系统会将这组选择写入当前充值会话。",
                color = cs.onSurfaceVariantSummary,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 14.dp),
            )
            if (loading && schools.isEmpty()) FeatureLoading()
            ElectricField(ElectricLevel.School.title, school?.name, schools.isNotEmpty()) { chooser = ElectricLevel.School }
            ElectricField(ElectricLevel.Building.title, building?.name, school != null && buildings.isNotEmpty()) { chooser = ElectricLevel.Building }
            ElectricField(ElectricLevel.Floor.title, floor?.name, building != null && floors.isNotEmpty()) { chooser = ElectricLevel.Floor }
            ElectricField(ElectricLevel.Room.title, room?.name, floor != null && rooms.isNotEmpty()) { chooser = ElectricLevel.Room }
            QuickAmounts(listOf(10, 20, 50, 100, 200)) { amount = it.toString() }
            Spacer(Modifier.height(8.dp))
            TextField(
                value = amount,
                onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
                label = "充值金额（最高 200 元）",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            PayMethodPicker(method, onSelected = { method = it })
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    val selectedSchool = school
                    val selectedBuilding = building
                    val selectedFloor = floor
                    val selectedRoom = room
                    val money = amount.toDoubleOrNull()
                    if (selectedSchool == null || selectedBuilding == null || selectedFloor == null || selectedRoom == null) {
                        notices.error("请完整选择宿舍房间")
                        return@Button
                    }
                    if (money == null) {
                        notices.error("请输入有效的充值金额")
                        return@Button
                    }
                    scope.launch {
                        submitting = true
                        try {
                            result = session.rechargeElectric(
                                ElectricSelection(selectedSchool, selectedBuilding, selectedFloor, selectedRoom),
                                money,
                                method,
                            )
                            if (result is YktPaymentResult.Failure) notices.error((result as YktPaymentResult.Failure).message)
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            notices.error(e.message ?: "电费订单创建失败")
                        } finally {
                            submitting = false
                        }
                    }
                },
                enabled = !submitting,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) { Text(if (submitting) "正在创建订单…" else "创建官方电费订单") }
            PaymentResultCard(result) { order ->
                openCcbOrder(context, order).onFailure { notices.error("无法打开建行收银台") }
            }
            Text(
                "学校每天 23:50–00:30 对账，期间不能充值。付款结果请以宿舍电表和一卡通账单为准。",
                color = cs.onSurfaceVariantSummary,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 14.dp),
            )
            FeatureBottomSpace()
        }
    }

    chooser?.let { level ->
        val options = when (level) {
            ElectricLevel.School -> schools
            ElectricLevel.Building -> buildings
            ElectricLevel.Floor -> floors
            ElectricLevel.Room -> rooms
        }
        WindowDialog(show = true, title = "选择${level.title}", onDismissRequest = { chooser = null }) {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                items(options, key = { it.id }) { option ->
                    Text(
                        option.name,
                        modifier = Modifier.fillMaxWidth().clickable {
                            chooser = null
                            scope.launch {
                                loading = true
                                try {
                                    when (level) {
                                        ElectricLevel.School -> {
                                            school = option; building = null; floor = null; room = null
                                            buildings = session.electricBuildings(option); floors = emptyList(); rooms = emptyList()
                                        }
                                        ElectricLevel.Building -> {
                                            building = option; floor = null; room = null
                                            floors = session.electricFloors(option); rooms = emptyList()
                                        }
                                        ElectricLevel.Floor -> {
                                            floor = option; room = null
                                            rooms = session.electricRooms(option)
                                        }
                                        ElectricLevel.Room -> room = option
                                    }
                                } catch (e: Exception) {
                                    if (e is CancellationException) throw e
                                    notices.error(e.message ?: "目录加载失败")
                                } finally {
                                    loading = false
                                }
                            }
                        }.padding(vertical = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ElectricField(label: String, value: String?, enabled: Boolean, onClick: () -> Unit) {
    val cs = MiuixTheme.colorScheme
    Card(
        modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
        onClick = { if (enabled) onClick() },
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(label, color = cs.onSurfaceVariantSummary, fontSize = 12.sp)
            Text(value ?: if (enabled) "请选择" else "请先完成上一步", modifier = Modifier.padding(top = 3.dp))
        }
    }
}
