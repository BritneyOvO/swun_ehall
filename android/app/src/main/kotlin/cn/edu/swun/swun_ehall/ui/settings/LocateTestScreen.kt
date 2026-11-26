package cn.edu.swun.swun_ehall.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import cn.edu.swun.swun_ehall.data.fences.CampusFences
import cn.edu.swun.swun_ehall.data.geo.Geo
import cn.edu.swun.swun_ehall.data.session.Session
import cn.edu.swun.swun_ehall.ui.common.BackNav
import cn.edu.swun.swun_ehall.ui.common.FeatureBottomSpace
import cn.edu.swun.swun_ehall.ui.common.FeatureColumn
import cn.edu.swun.swun_ehall.ui.common.FeatureInlineNotice
import cn.edu.swun.swun_ehall.ui.common.FeatureSection
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private data class LocateProbeResult(
    val room: String,
    val fenceName: String,
    val distance: Double,
    val coordinates: String,
)

@Composable
fun LocateTestScreen(session: Session, nav: NavHostController) {
    val rooms = listOf("BS-223", "BW-106", "H-206", "BX-317")
    var last by remember { mutableStateOf<LocateProbeResult?>(null) }
    val cs = MiuixTheme.colorScheme
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "测试定位",
                navigationIcon = { BackNav { nav.popBackStack() } },
            )
        },
    ) { padding ->
        FeatureColumn(padding) {
            FeatureInlineNotice(
                title = "开发者工具",
                summary = "仅验证坐标转换和教学楼围栏，不会提交签到。",
                tone = Color(0xFF486A8E),
                modifier = Modifier.padding(top = 10.dp),
            )
            FeatureSection("选择测试地点")
            Card(modifier = Modifier.fillMaxWidth()) {
                rooms.forEachIndexed { index, room ->
                    if (index > 0) HorizontalDivider()
                    val fence = CampusFences.forRoom(room)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = fence != null) {
                                val value = fence ?: return@clickable
                                val wgs = value.centerLat to value.centerLng
                                val gcj = Geo.wgs84ToGcj02(wgs.first, wgs.second)
                                val submitted = Geo.campusWgs84(gcj.first, gcj.second, "amap", datum = "gcj02")
                                last = LocateProbeResult(
                                    room = room,
                                    fenceName = value.name,
                                    distance = Geo.meters(submitted.first, submitted.second, value.centerLat, value.centerLng),
                                    coordinates = "${"%.5f".format(submitted.first)}, ${"%.5f".format(submitted.second)}",
                                )
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(room, fontWeight = FontWeight.SemiBold)
                            Text(
                                fence?.let { "${it.name} · ${if (CampusFences.outlineFor(room).isNullOrEmpty()) "中心围栏" else "多边形围栏"}" }
                                    ?: "没有匹配的围栏",
                                color = cs.onSurfaceVariantSummary,
                                fontSize = 12.sp,
                            )
                        }
                        Text("测试", color = if (fence == null) cs.onSurfaceVariantSummary else cs.primary, fontSize = 13.sp)
                    }
                }
            }
            last?.let { result ->
                FeatureSection("最近结果")
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(result.fenceName, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text(
                                if (result.distance < 100) "范围内" else "请检查",
                                color = if (result.distance < 100) Color(0xFF347A59) else cs.primary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                            )
                        }
                        Text(
                            "${result.room} · 距围栏中心 ${"%.1f".format(result.distance)} 米",
                            color = cs.onSurfaceVariantSummary,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        Text(result.coordinates, color = cs.onSurfaceVariantSummary, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
                    }
                }
            }
            FeatureBottomSpace()
        }
    }
}
