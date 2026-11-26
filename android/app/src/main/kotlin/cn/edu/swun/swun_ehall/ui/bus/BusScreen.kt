package cn.edu.swun.swun_ehall.ui.bus

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import cn.edu.swun.swun_ehall.data.model.BusDayType
import cn.edu.swun.swun_ehall.data.model.BusTimetable
import cn.edu.swun.swun_ehall.data.model.nextBus
import cn.edu.swun.swun_ehall.data.model.swunBusTimetables
import cn.edu.swun.swun_ehall.ui.common.BackNav
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun BusScreen(nav: NavHostController) {
    var lineIndex by remember { mutableIntStateOf(0) }
    var reverse by remember { mutableStateOf(false) }
    var day by remember {
        mutableStateOf(
            if (LocalDate.now().dayOfWeek in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)) {
                BusDayType.Weekend
            } else {
                BusDayType.Weekday
            },
        )
    }
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = LocalTime.now()
        }
    }
    val line = swunBusTimetables[lineIndex]
    val times = line.times(day, reverse)
    val next = nextBus(times, now)
    val from = if (reverse) line.toName else line.fromName
    val to = if (reverse) line.fromName else line.toName

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "校车时刻",
                navigationIcon = { BackNav { nav.popBackStack() } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            TabRow(
                tabs = swunBusTimetables.map { it.compactLabel() },
                selectedTabIndex = lineIndex,
                onTabSelected = {
                    lineIndex = it
                    reverse = false
                },
                modifier = Modifier.padding(top = 10.dp),
            )
            TabRow(
                tabs = listOf("去${line.toName.shortName()}", "去${line.fromName.shortName()}"),
                selectedTabIndex = if (reverse) 1 else 0,
                onTabSelected = { reverse = it == 1 },
                modifier = Modifier.padding(top = 10.dp),
            )
            TabRow(
                tabs = BusDayType.entries.map { if (it == BusDayType.Holiday) "节假日" else it.label },
                selectedTabIndex = BusDayType.entries.indexOf(day),
                onTabSelected = { day = BusDayType.entries[it] },
                modifier = Modifier.padding(top = 10.dp),
            )
            Card(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Column(Modifier.padding(18.dp)) {
                    Text("下一班", color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 13.sp)
                    Text(
                        next?.time?.toString() ?: "今日已收班",
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        color = MiuixTheme.colorScheme.onBackground,
                    )
                    Text("$from → $to · ${day.label}", modifier = Modifier.padding(top = 5.dp))
                    next?.let {
                        Text("距发车约 ${it.minutes} 分钟", color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }

            Text("发车时刻", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp, start = 4.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    times.chunked(4).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { time ->
                                val selected = next?.time == time
                                Text(
                                    time.toString(),
                                    Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selected) MiuixTheme.colorScheme.primary else Color.Transparent)
                                        .padding(vertical = 8.dp),
                                    textAlign = TextAlign.Center,
                                    color = if (selected) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onBackground,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("乘车提示", fontWeight = FontWeight.SemiBold)
                    Text(line.note, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 13.sp, modifier = Modifier.padding(top = 5.dp))
                    Text("临时调整以学校后勤处通知为准。", color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 12.sp, modifier = Modifier.padding(top = 7.dp))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun BusTimetable.compactLabel(): String =
    fromName.tabName() + toName.tabName()

private fun String.shortName(): String = removeSuffix("校区")

private fun String.tabName(): String = shortName().replace("航空港", "空港").replace("太平园", "太平")
