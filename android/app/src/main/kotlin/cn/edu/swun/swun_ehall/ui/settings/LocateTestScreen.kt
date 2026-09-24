package cn.edu.swun.swun_ehall.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import cn.edu.swun.swun_ehall.data.fences.CampusFences
import cn.edu.swun.swun_ehall.data.geo.Geo
import cn.edu.swun.swun_ehall.data.session.Session
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference

@Composable
fun LocateTestScreen(session: Session, nav: NavHostController) {
    var log by remember { mutableStateOf("") }
    val rooms = listOf("BS-223", "BW-106", "H-206", "BX-317")
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "测试定位",
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        top.yukonga.miuix.kmp.basic.Icon(MiuixIcons.Back, contentDescription = "返回")
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
            Card {
                rooms.forEach { room ->
                    ArrowPreference(
                        title = room,
                        summary = CampusFences.forRoom(room)?.let { "${it.name} 中心" },
                        onClick = {
                            val fence = CampusFences.forRoom(room) ?: return@ArrowPreference
                            val wgs = Geo.gcj02ToWgs84(fence.centerLat, fence.centerLng)
                            val gps = Geo.campusGcj02(wgs.first, wgs.second, "gps")
                            val fused = Geo.campusGcj02(wgs.first, wgs.second, "fused")
                            val skip = Geo.campusGcj02(wgs.first, wgs.second, "gps", datum = "gcj02")
                            log = buildString {
                                appendLine("$room ${fence.name} 中心 ${fence.centerLat}, ${fence.centerLng}")
                                appendLine("模拟 WGS ${wgs.first}, ${wgs.second}")
                                appendLine("gps  ${gps.first}, ${gps.second}  距中心 ${"%.1f".format(Geo.meters(gps.first, gps.second, fence.centerLat, fence.centerLng))} m")
                                appendLine("fused ${fused.first}, ${fused.second}  距中心 ${"%.1f".format(Geo.meters(fused.first, fused.second, fence.centerLat, fence.centerLng))} m")
                                appendLine("不转  ${skip.first}, ${skip.second}  距中心 ${"%.0f".format(Geo.meters(skip.first, skip.second, fence.centerLat, fence.centerLng))} m")
                            }
                        },
                    )
                }
            }
            if (log.isNotEmpty()) {
                Card(modifier = Modifier.padding(top = 12.dp)) {
                    Text(log, modifier = Modifier.padding(14.dp))
                }
            }
        }
    }
}
