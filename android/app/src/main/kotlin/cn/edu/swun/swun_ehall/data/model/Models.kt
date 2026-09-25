package cn.edu.swun.swun_ehall.data.model

data class Lesson(
    val name: String,
    val room: String,
    val teacher: String,
    val weekday: Int,
    val start: Int,
    val span: Int,
    val weeks: String = "",
) {
    val end: Int get() = start + span - 1

    fun ended(nowMinutes: Int = minutesNow()): Boolean = lessonEnded(end, nowMinutes)

    fun inWeek(week: Int): Boolean {
        val mask = weeks.trim()
        if (mask.isEmpty() || mask == "null") return true
        if (mask.all { it == '0' || it == '1' } && mask.contains('1')) {
            return week in 1..mask.length && mask[week - 1] == '1'
        }
        return true
    }
}

data class Grade(
    val name: String,
    val credit: String,
    val score: String,
    val gpa: String,
    val kind: String,
    val xnm: String = "",
    val xqm: String = "",
    val mark: String = "",
)

data class Profile(
    val name: String = "同学",
    val studentId: String = "",
    val college: String = "",
    val major: String = "",
    val klass: String = "",
    val campus: String = "",
    val gender: String = "",
    val grade: String = "",
    val phone: String = "",
    val role: String = "学生",
    val avatar: String = "",
) {
    fun merge(other: Profile) = Profile(
        name = prefer(name, other.name),
        studentId = prefer(studentId, other.studentId),
        college = prefer(college, other.college),
        major = prefer(major, other.major),
        klass = prefer(klass, other.klass),
        campus = prefer(campus, other.campus),
        gender = prefer(gender, other.gender),
        grade = prefer(grade, other.grade),
        phone = prefer(phone, other.phone),
        role = prefer(role, other.role),
        avatar = prefer(avatar, other.avatar),
    )

    private fun prefer(a: String, b: String): String {
        val x = a.trim()
        return if (x.isNotEmpty() && x != "同学") x else b.trim()
    }
}

data class SignActivity(
    val activityId: String,
    val title: String,
    val status: String,
    val signType: String,
    val startTime: String,
    val endTime: String,
    val classroom: String,
    val course: String,
    val teachClassId: String = "",
    val teachClassType: String = "",
    val scheduleId: String = "",
    val week: Int = 0,
    val weekDay: Int = 0,
    val startNode: Int = 0,
    val endNode: Int = 0,
    val signCode: String = "",
    val teacher: String = "",
    val message: String = "",
    val courseCode: String = "",
    val className: String = "",
    val timeText: String = "",
    val activities: List<KtkqActivity> = emptyList(),
    val history: List<KtkqHistory> = emptyList(),
)

data class KtkqActivity(
    val activityId: String,
    val title: String,
    val signType: String,
    val status: String,
    val message: String = "",
    val startTime: String = "",
    val endTime: String = "",
    val signCode: String = "",
    val leftSeconds: Int? = null,
)

data class KtkqHistory(
    val time: String,
    val course: String,
    val status: String,
    val activityId: String = "",
)

data class KtkqCourse(
    val name: String,
    val code: String = "",
    val credits: String = "",
    val hours: String = "",
    val teachClassId: String = "",
    val teachClassType: String = "",
    val slots: List<SignActivity> = emptyList(),
)

data class KtkqWeek(
    val xnxqdm: String = "",
    val xnxqmc: String = "",
    val week: Int = 1,
    val courses: List<KtkqCourse> = emptyList(),
) {
    fun flatten(): List<SignActivity> = courses.flatMap { it.slots }
}

fun SignActivity.slotKey(): String {
    val jxb = teachClassId.ifBlank { course }
    return "$jxb|$scheduleId|$weekDay|$startNode|$endNode"
}

fun ktkqSlotFromLesson(lesson: Lesson, week: Int): SignActivity {
    val day = lesson.weekday.coerceIn(1, 7)
    val start = lesson.start.coerceAtLeast(1)
    val end = lesson.end.coerceAtLeast(start)
    val dayLabel = kWeekdayLabels.getOrElse(day) { "" }
    return SignActivity(
        activityId = "",
        title = lesson.name,
        status = "",
        signType = "",
        startTime = "",
        endTime = "",
        classroom = lesson.room,
        course = lesson.name,
        teacher = lesson.teacher,
        week = week,
        weekDay = day,
        startNode = start,
        endNode = end,
        timeText = listOf(dayLabel, "第 $start-$end 节").filter { it.isNotEmpty() }.joinToString("  "),
    )
}

data class YktBill(
    val title: String,
    val time: String,
    val amountYuan: Double,
    val balanceYuan: Double?,
)

data class ClockRecord(
    val time: String,
    val address: String,
    val status: String,
)

/** Apartment punch range from gyglxt positions. Not a classroom building fence. */
data class ClockFence(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double,
    val polygon: List<Pair<Double, Double>> = emptyList(),
)

data class GeoFix(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Double = 0.0,
    val source: String = "",
    val datum: String = "",
) {
    val coordText: String get() = "${"%.5f".format(latitude)}, ${"%.5f".format(longitude)}"
    val sourceLabel: String
        get() = when (source) {
            "amap" -> "高德"
            "network" -> "网络"
            "gps" -> "GPS"
            "fused" -> "融合"
            "last" -> "缓存"
            "demo" -> "示例"
            else -> source.ifBlank { "定位" }
        }
}

data class Exam(
    val name: String,
    val time: String,
    val place: String,
    val seat: String,
)

data class PlanCourse(
    val name: String,
    val credits: Double,
    val status: String,
    val score: String = "",
)

data class CreditBucket(
    val name: String,
    val credits: Double,
    val required: Double = 0.0,
    val failed: Double = 0.0,
    val courses: Int = 0,
    val failedCourses: Int = 0,
    val items: List<PlanCourse> = emptyList(),
)

data class CreditProgress(
    val taken: Double = 0.0,
    val required: Double = 0.0,
    val earned: Double = 0.0,
    val gpa: Double? = null,
    val planPassed: Int = 0,
    val planFailed: Int = 0,
    val buckets: List<CreditBucket> = emptyList(),
)

data class SchoolTerm(
    val xnm: String,
    val xqm: String,
) {
    val isAll: Boolean get() = xnm.isEmpty() && xqm.isEmpty()
    val label: String
        get() {
            if (isAll) return "全部学期"
            val y = xnm.toIntOrNull() ?: 0
            val season = when (normalizeXqm(xqm)) {
                "12" -> "第2学期"
                "16" -> "小学期"
                else -> "第1学期"
            }
            return if (y <= 0) season else "$y-${y + 1}学年 $season"
        }
}

data class XkRound(
    val kklxdm: String,
    val xkkzId: String,
    val njdmId: String,
    val zyhId: String,
    val xkkzXh: String,
    val name: String,
)

data class XkCourse(
    val kchId: String,
    val name: String,
    val credit: String,
    val teacher: String,
    val time: String,
    val jxbId: String,
    val jxbName: String,
    val remain: Int,
    val used: String,
    val cap: String,
    val doJxbId: String = "",
    val raw: Map<String, String> = emptyMap(),
)

data class AppRelease(
    val version: String,
    val htmlUrl: String,
    val apkUrl: String?,
    val notes: String,
    val prerelease: Boolean,
    val versionCode: Int = 0,
    val apkName: String = "",
)

fun currentSchoolTerm(): SchoolTerm {
    val now = java.util.Calendar.getInstance()
    val y = now.get(java.util.Calendar.YEAR)
    val m = now.get(java.util.Calendar.MONTH) + 1
    return if (m >= 8 || m <= 1) SchoolTerm("$y", "3") else SchoolTerm("${y - 1}", "12")
}

fun buildGradeTerms(studentId: String, grade: String): List<SchoolTerm> {
    val cur = currentSchoolTerm()
    val curY = cur.xnm.toIntOrNull() ?: 2024
    var start = curY - 5
    val enroll = Regex("""(19|20)\d{2}""").find(grade)?.value?.toIntOrNull()
        ?: Regex("""^(19|20)\d{2}""").find(studentId)?.value?.toIntOrNull()
    if (enroll != null && enroll in 2000..curY) start = enroll
    val out = mutableListOf(SchoolTerm("", ""))
    for (xnm in curY downTo start) {
        val isCur = xnm == curY
        if (!isCur || cur.xqm == "12" || cur.xqm == "16") out.add(SchoolTerm("$xnm", "12"))
        out.add(SchoolTerm("$xnm", "3"))
    }
    return out
}

fun normalizeXqm(raw: String): String {
    val s = raw.trim()
    return when {
        s == "12" || s == "02" || s == "2" -> "12"
        s == "16" -> "16"
        s == "3" || s == "03" || s == "1" || s == "01" -> "3"
        s.contains("三") || s.contains("小学期") -> "16"
        s.contains("二") -> "12"
        s.contains("一") -> "3"
        else -> s
    }
}

fun Grade.matches(term: SchoolTerm): Boolean {
    if (term.isAll) return true
    if (xnm != term.xnm && !xnm.startsWith(term.xnm)) return false
    return normalizeXqm(xqm) == normalizeXqm(term.xqm)
}

fun formatXf(v: Double): String {
    if (v == v.toInt().toDouble()) return "${v.toInt()}"
    val s = "%.1f".format(v)
    return if (s.endsWith(".0")) s.dropLast(2) else s
}

val kWeekdayLabels = listOf("", "周一", "周二", "周三", "周四", "周五", "周六", "周日")

fun minutesNow(now: java.util.Calendar = java.util.Calendar.getInstance()): Int =
    now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)

fun periodEndMinutes(period: Int): Int? {
    val hm = kPeriodTimes.firstOrNull { it.first == period }?.second?.second ?: return null
    val parts = hm.split(':')
    val h = parts.getOrNull(0)?.toIntOrNull() ?: return null
    val m = parts.getOrNull(1)?.toIntOrNull() ?: return null
    return h * 60 + m
}

fun lessonEnded(endPeriod: Int, nowMinutes: Int = minutesNow()): Boolean {
    val endAt = periodEndMinutes(endPeriod) ?: return false
    return nowMinutes >= endAt
}

val kPeriodTimes = listOf(
    1 to ("8:30" to "9:15"),
    2 to ("9:20" to "10:05"),
    3 to ("10:25" to "11:10"),
    4 to ("11:15" to "12:00"),
    5 to ("14:00" to "14:45"),
    6 to ("14:50" to "15:35"),
    7 to ("15:55" to "16:40"),
    8 to ("16:45" to "17:30"),
    9 to ("19:00" to "19:45"),
    10 to ("19:50" to "20:35"),
    11 to ("20:40" to "21:25"),
)
