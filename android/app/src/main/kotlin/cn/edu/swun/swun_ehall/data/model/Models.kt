package cn.edu.swun.swun_ehall.data.model

data class Lesson(
    val name: String,
    val room: String,
    val teacher: String,
    val weekday: Int,
    val start: Int,
    val span: Int,
    val weeks: String = "",
)

data class Grade(
    val name: String,
    val credit: String,
    val score: String,
    val gpa: String,
    val kind: String,
)

data class Profile(
    val name: String = "同学",
    val studentId: String = "",
    val college: String = "",
    val major: String = "",
    val klass: String = "",
    val campus: String = "",
)

data class SignActivity(
    val activityId: String,
    val title: String,
    val status: String,
    val signType: String,
    val startTime: String,
    val endTime: String,
    val classroom: String,
    val course: String,
)

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

data class GeoFix(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Double = 0.0,
    val source: String = "",
    val datum: String = "",
)
