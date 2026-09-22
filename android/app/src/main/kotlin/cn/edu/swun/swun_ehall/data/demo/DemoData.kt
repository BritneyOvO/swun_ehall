package cn.edu.swun.swun_ehall.data.demo

import cn.edu.swun.swun_ehall.data.model.ClockRecord
import cn.edu.swun.swun_ehall.data.model.CreditBucket
import cn.edu.swun.swun_ehall.data.model.CreditProgress
import cn.edu.swun.swun_ehall.data.model.Exam
import cn.edu.swun.swun_ehall.data.model.Grade
import cn.edu.swun.swun_ehall.data.model.Lesson
import cn.edu.swun.swun_ehall.data.model.PlanCourse
import cn.edu.swun.swun_ehall.data.model.Profile
import cn.edu.swun.swun_ehall.data.http.ktkqNormName
import cn.edu.swun.swun_ehall.data.http.ktkqStatusLabel
import cn.edu.swun.swun_ehall.data.model.KtkqActivity
import cn.edu.swun.swun_ehall.data.model.KtkqCourse
import cn.edu.swun.swun_ehall.data.model.KtkqHistory
import cn.edu.swun.swun_ehall.data.model.KtkqWeek
import cn.edu.swun.swun_ehall.data.model.SignActivity
import cn.edu.swun.swun_ehall.data.model.XkCourse
import cn.edu.swun.swun_ehall.data.model.XkRound
import cn.edu.swun.swun_ehall.data.model.YktBill

object DemoData {
    val profile = Profile(
        name = "预览同学",
        studentId = "202430000000",
        college = "计算机科学与工程学院",
        major = "网络工程",
        klass = "网络工程2024-1班",
        campus = "武侯校区",
    )

    val schedule = listOf(
        Lesson("网络工程学期实训 (网络仿真)", "BS-222", "徐小琼", 1, 5, 2, "1111111111111111"),
        Lesson("网络工程学期实训(网络开发)(上)", "BS-243", "梅林", 1, 7, 4, "1111111111111111"),
        Lesson("形势与政策（五）", "BX-317", "吴音萃", 2, 3, 2, "0000111100000000"),
        Lesson("网络攻防", "BS-223", "陈浩", 3, 3, 2, "1111111111111111"),
        Lesson("数字通信原理及协议", "BW-106", "李成杰", 3, 5, 2, "1111111111111111"),
        Lesson("计算机组成原理实验", "BS-241", "姜玥", 3, 3, 2, "0000000011111111"),
        Lesson("计算机组成原理", "BW-107", "姜玥", 4, 1, 4, "1111111100000000"),
        Lesson("无线网络与移动计算", "H-206", "陈建英", 4, 9, 2, "1111111111111111"),
        Lesson("网络攻防", "BS-223", "陈浩", 5, 9, 2, "1111111111111111"),
    )

    val grades = listOf(
        Grade("高等数学Ⅰ（上）", "4.5", "89.6", "3.70", "学科基础课", "2024", "3"),
        Grade("大学英语I(一)", "2.0", "79.5", "3.00", "公共基础课", "2024", "3"),
        Grade("大学物理实验Ⅳ", "0.5", "87.4", "3.70", "学科基础课", "2024", "12"),
        Grade("马克思主义基本原理", "3.0", "87.4", "3.70", "公共基础课", "2025", "3"),
        Grade("计算机科学与技术引论", "1.5", "92.7", "4.00", "专业选修课", "2025", "12"),
        Grade("计算机组成原理", "3.5", "88.0", "3.70", "专业必修课", "2026", "3"),
    )

    val exams = listOf(
        Exam("数字通信原理及协议", "第16周 周三 14:00-16:00", "BW-106", "12"),
        Exam("计算机组成原理", "第17周 周一 09:00-11:00", "BW-107", "08"),
    )

    val credits = CreditProgress(
        taken = 15.0,
        required = 160.5,
        earned = 15.0,
        gpa = 3.63,
        planPassed = 6,
        planFailed = 0,
        buckets = listOf(
            CreditBucket("学科基础课", 5.0, courses = 2, items = listOf(PlanCourse("高等数学Ⅰ（上）", 4.5, "已修", "89.6"))),
            CreditBucket("公共基础课", 5.0, courses = 2, items = listOf(PlanCourse("大学英语I(一)", 2.0, "已修", "79.5"))),
            CreditBucket("专业必修课", 3.5, courses = 1, items = listOf(PlanCourse("计算机组成原理", 3.5, "已修", "88.0"))),
            CreditBucket("专业选修课", 1.5, courses = 1, items = listOf(PlanCourse("计算机科学与技术引论", 1.5, "已修", "92.7"))),
        ),
    )

    val xkRounds = listOf(
        XkRound("01", "demo-01", "2024", "1106", "demo", "主修课程"),
        XkRound("10", "demo-10", "2024", "1106", "demo", "通识选修课"),
    )

    val xkCourses = listOf(
        XkCourse("56050556", "网络舆情智能分析", "2.0", "王老师", "星期三第5-6节{1-16周}", "demo-jxb-1", "(2026-2027-1)-56050556-01", 4, "46", "50", "demo-do-1"),
        XkCourse("56050548", "网络工程学期实训(网络开发)(上)", "2.0", "李老师", "星期四第3-4节{1-16周}", "demo-jxb-2", "(2026-2027-1)-56050548-01", 0, "50", "50"),
        XkCourse("11180500", "专业英语（网络工程）", "2.0", "张老师", "星期五第3-4节{1-16周}", "demo-jxb-3", "(2026-2027-1)-11180500-01", 10, "44", "54", "demo-do-3"),
    )

    val xkProfile = mapOf("xkxnm" to "2026", "xkxqm" to "3", "njdm_id" to "2024", "zyh_id" to "1106", "xqh_id" to "2")

    val ktkqWeek = KtkqWeek(
        xnxqdm = "2026-2027-1",
        xnxqmc = "2026-2027学年 秋季学期",
        week = 1,
        courses = listOf(
            KtkqCourse(
                name = "网络工程学期实训 (网络仿真)",
                code = "56050553",
                credits = "1.0",
                hours = "32",
                teachClassId = "demo-jxb-1",
                teachClassType = "THEORY",
                slots = listOf(
                    demoSlot(
                        course = "网络工程学期实训 (网络仿真)",
                        code = "56050553",
                        className = "(2026-2027-1)-56050553-01",
                        room = "BS-222",
                        teacher = "张老师",
                        day = 1,
                        start = 5,
                        end = 6,
                        sksj = "周一 14:00~15:35",
                        jxb = "demo-jxb-1",
                        kb = "demo-kb-1",
                    ),
                ),
            ),
            KtkqCourse(
                name = "数字通信原理及协议",
                code = "56040248",
                credits = "3.0",
                hours = "48",
                teachClassId = "demo-jxb-2",
                teachClassType = "THEORY",
                slots = listOf(
                    demoSlot(
                        course = "数字通信原理及协议",
                        code = "56040248",
                        className = "(2026-2027-1)-56040248-01",
                        room = "BW-106",
                        teacher = "李老师",
                        day = 2,
                        start = 5,
                        end = 6,
                        sksj = "周二 14:00~15:35",
                        jxb = "demo-jxb-2",
                        kb = "demo-kb-2",
                    ),
                ),
            ),
            KtkqCourse(
                name = "无线网络与移动计算",
                code = "56050560",
                credits = "2.0",
                hours = "32",
                teachClassId = "demo-jxb-h",
                teachClassType = "THEORY",
                slots = listOf(
                    demoSlot(
                        course = "无线网络与移动计算",
                        code = "56050560",
                        className = "(2026-2027-1)-56050560-01",
                        room = "H-206",
                        teacher = "陈建英",
                        day = 4,
                        start = 9,
                        end = 10,
                        sksj = "周四 19:00~20:35",
                        jxb = "demo-jxb-h",
                        kb = "demo-kb-h",
                    ),
                ),
            ),
        ),
    )

    val ktkq: List<SignActivity> get() = ktkqWeek.flatten()

    fun signFor(slot: SignActivity): SignActivity {
        val name = ktkqNormName(slot.course)
        val hit = ktkqWeek.flatten().firstOrNull {
            val a = ktkqNormName(it.course)
            a.isNotEmpty() && name.isNotEmpty() && (a == name || a.contains(name) || name.contains(a))
        }
        val course = hit ?: slot
        val pending = course.course.contains("网络仿真")
        val signed = course.course.contains("数字通信")
        val status = when {
            pending -> "pending_signin"
            signed -> "already_signed"
            else -> "no_activity"
        }
        val actId = when {
            pending -> "demo-act-1"
            signed -> "demo-act-2"
            else -> ""
        }
        val type = if (pending) "NUMBER" else "LOCATION"
        val activities = if (status == "no_activity") {
            emptyList()
        } else {
            listOf(
                KtkqActivity(
                    activityId = actId,
                    title = "第1周课堂签到",
                    signType = type,
                    status = status,
                    message = ktkqStatusLabel(status),
                    startTime = "14:00",
                    endTime = "15:35",
                ),
            )
        }
        val history = demoHistory.filter { it.course == course.course }
        return course.copy(
            activityId = actId,
            status = status,
            message = ktkqStatusLabel(status),
            signType = type,
            activities = activities,
            history = history,
            teacher = course.teacher.ifBlank { slot.teacher },
            classroom = course.classroom.ifBlank { slot.classroom },
            timeText = course.timeText.ifBlank { slot.timeText },
            week = course.week.coerceAtLeast(1),
        )
    }

    private val demoHistory = listOf(
        KtkqHistory("昨天 15:10", "网络攻防", "正常"),
        KtkqHistory("周一 14:18", "数字通信原理及协议", "已签到"),
    )

    private fun demoSlot(
        course: String,
        code: String,
        className: String,
        room: String,
        teacher: String,
        day: Int,
        start: Int,
        end: Int,
        sksj: String,
        jxb: String,
        kb: String,
    ) = SignActivity(
        activityId = "",
        title = className,
        status = "",
        signType = "",
        startTime = sksj,
        endTime = "",
        classroom = room,
        course = course,
        courseCode = code,
        className = className,
        timeText = "$sksj  第 $start-$end 节",
        teachClassId = jxb,
        teachClassType = "THEORY",
        scheduleId = kb,
        week = 1,
        weekDay = day,
        startNode = start,
        endNode = end,
        teacher = teacher,
    )

    val yktBills = listOf(
        YktBill("学生食堂", "今天 12:31", -8.0, 10.7),
        YktBill("校园超市", "今天 18:04", -2.5, 18.7),
        YktBill("圈存转入", "昨天 09:12", 50.0, 21.2),
    )

    const val yktBalance = 18.7

    val clockRecords = listOf(
        ClockRecord("昨天 22:01", "武侯校区", "正常"),
    )

    val clockFence = 30.58120 to 103.97048
}
