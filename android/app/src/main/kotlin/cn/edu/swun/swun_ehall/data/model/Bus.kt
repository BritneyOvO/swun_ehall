package cn.edu.swun.swun_ehall.data.model

import java.time.Duration
import java.time.LocalTime

enum class BusDayType(val label: String) {
    Weekday("工作日"),
    Weekend("周末"),
    Holiday("法定节假日"),
}

data class BusTimetable(
    val label: String,
    val fromName: String,
    val toName: String,
    val weekdayOut: List<String>,
    val weekdayBack: List<String>,
    val weekendOut: List<String>,
    val weekendBack: List<String>,
    val holidayOut: List<String>,
    val holidayBack: List<String>,
    val note: String,
) {
    fun times(day: BusDayType, reverse: Boolean): List<LocalTime> {
        val raw = when (day) {
            BusDayType.Weekday -> if (reverse) weekdayBack else weekdayOut
            BusDayType.Weekend -> if (reverse) weekendBack else weekendOut
            BusDayType.Holiday -> if (reverse) holidayBack else holidayOut
        }
        return raw.mapNotNull { runCatching { LocalTime.parse(it.replace('：', ':').padTime()) }.getOrNull() }
    }
}

data class NextBus(val time: LocalTime, val minutes: Long)

fun nextBus(times: List<LocalTime>, now: LocalTime): NextBus? {
    val time = times.firstOrNull { !it.isBefore(now) } ?: return null
    return NextBus(time, Duration.between(now, time).toMinutes().coerceAtLeast(0))
}

private fun String.padTime(): String {
    val parts = split(':')
    if (parts.size != 2) return this
    return parts[0].padStart(2, '0') + ":" + parts[1].padStart(2, '0')
}

/** 后勤处三校区通勤车表；页面同时提示用户以临时调度通知为准。 */
val swunBusTimetables = listOf(
    BusTimetable(
        "武侯 ↔ 航空港", "武侯校区", "航空港校区",
        listOf("7:40", "9:40", "11:20", "13:10", "15:10", "17:20", "18:10", "21:40"),
        listOf("7:40", "8:30", "10:30", "12:20", "13:10", "15:10", "16:00", "17:20", "17:50", "20:50", "21:40", "22:30"),
        listOf("7:40", "9:40", "11:20", "13:10", "15:10", "17:20", "18:10", "21:40"),
        listOf("7:40", "8:30", "10:30", "12:20", "13:10", "15:10", "16:00", "17:20", "17:50", "20:50", "21:40", "22:30"),
        listOf("8:30", "9:40", "14:30", "17:10", "19:00", "21:30"),
        listOf("9:00", "10:30", "15:30", "18:00", "20:00", "22:30"),
        "武侯→航空港 7:40 在武侯校区发车；航空港→武侯 7:40 班次在图书馆廊桥旁候车。",
    ),
    BusTimetable(
        "太平园 ↔ 航空港", "太平园校区", "航空港校区",
        listOf("7:40", "9:40", "10:30", "12:20", "13:10", "16:00", "17:50", "20:50"),
        listOf("7:40", "9:40", "10:30", "13:10", "15:10", "16:00", "17:50", "20:50"),
        listOf("7:40", "9:40", "13:10", "17:00", "20:00"),
        listOf("8:30", "12:20", "16:00", "17:50", "20:50"),
        listOf("9:00", "13:10", "20:00"),
        listOf("9:40", "14:00", "20:50"),
        "周末与法定节假日班次不同，节假日按表内“法定节假日”一栏执行。",
    ),
    BusTimetable(
        "武侯 ↔ 太平园", "武侯校区", "太平园校区",
        listOf("8:00", "9:50", "13:30", "15:20", "18:20"),
        listOf("8:30", "10:20", "12:10", "15:50", "17:45", "20:45"),
        listOf("8:30", "16:30"), listOf("9:00", "17:00"),
        listOf("8:30", "16:30"), listOf("9:00", "17:00"),
        "武侯校区乘车点在教工宿舍（一教楼）与站台之间的转弯处；周末与节假日班次较少。",
    ),
)
