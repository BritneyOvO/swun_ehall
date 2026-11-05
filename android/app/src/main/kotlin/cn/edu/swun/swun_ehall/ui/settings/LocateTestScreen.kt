package cn.edu.swun.swun_ehall.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import cn.edu.swun.swun_ehall.data.fences.CampusFences
import cn.edu.swun.swun_ehall.data.geo.Geo
import cn.edu.swun.swun_ehall.data.session.Session
import cn.edu.swun.swun_ehall.ui.common.FeatureNoticeHost
import cn.edu.swun.swun_ehall.ui.common.rememberFeatureNotices
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
    val notices = rememberFeatureNotices(session.studentId, session.demoMode)
    FeatureNoticeHost(notices)
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
                            val wgs = fence.centerLat to fence.centerLng
                            val gcj = Geo.wgs84ToGcj02(wgs.first, wgs.second)
                            val gps = Geo.campusWgs84(wgs.first, wgs.second, "gps", datum = "wgs84")
                            val amap = Geo.campusWgs84(gcj.first, gcj.second, "amap", datum = "gcj02")
                            val log = buildString {
                                appendLine("$room ${fence.name} 签到围栏 WGS-84 ${wgs.first}, ${wgs.second}")
                                appendLine("地图显示 GCJ-02 ${gcj.first}, ${gcj.second}")
                                appendLine("GPS提交 ${gps.first}, ${gps.second}  距围栏 ${"%.1f".format(Geo.meters(gps.first, gps.second, fence.centerLat, fence.centerLng))} m")
                                appendLine("高德反算提交 ${amap.first}, ${amap.second}  距围栏 ${"%.1f".format(Geo.meters(amap.first, amap.second, fence.centerLat, fence.centerLng))} m")
                            }
                            notices.details("$room 定位测试", log)
                        },
                    )
                }
            }
        }
    }
}
