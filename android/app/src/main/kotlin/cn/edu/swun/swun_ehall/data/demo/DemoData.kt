package cn.edu.swun.swun_ehall.data.demo

import cn.edu.swun.swun_ehall.data.model.ClockRecord
import cn.edu.swun.swun_ehall.data.model.Grade
import cn.edu.swun.swun_ehall.data.model.Lesson
import cn.edu.swun.swun_ehall.data.model.Profile
import cn.edu.swun.swun_ehall.data.model.SignActivity
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
        Lesson("网络工程学期实训 (网络仿真)", "BS-222", "徐小琼", 1, 5, 2),
        Lesson("网络工程学期实训(网络开发)(上)", "BS-243", "梅林", 1, 7, 4),
        Lesson("形势与政策（五）", "BX-317", "吴音萃", 2, 3, 2),
        Lesson("网络攻防", "BS-223", "陈浩", 3, 3, 2),
        Lesson("数字通信原理及协议", "BW-106", "李成杰", 3, 5, 2),
        Lesson("计算机组成原理实验", "BS-241", "姜玥", 3, 3, 2),
        Lesson("计算机组成原理", "BW-107", "姜玥", 4, 1, 4),
        Lesson("无线网络与移动计算", "H-206", "陈建英", 4, 9, 2),
        Lesson("网络攻防", "BS-223", "陈浩", 5, 9, 2),
    )

    val grades = listOf(
        Grade("高等数学Ⅰ（上）", "4.5", "89.6", "3.70", "学科基础课"),
        Grade("大学英语I(一)", "2.0", "79.5", "3.00", "公共基础课"),
        Grade("大学物理实验Ⅳ", "0.5", "87.4", "3.70", "学科基础课"),
        Grade("马克思主义基本原理", "3.0", "87.4", "3.70", "公共基础课"),
        Grade("计算机科学与技术引论", "1.5", "92.7", "4.00", "专业选修课"),
        Grade("计算机组成原理", "3.5", "88.0", "3.70", "专业必修课"),
    )

    val ktkq = listOf(
        SignActivity(
            activityId = "demo-act-1",
            title = "第1周课堂签到",
            status = "pending_signin",
            signType = "LOCATION",
            startTime = "14:00",
            endTime = "15:35",
            classroom = "BS-222",
            course = "网络工程学期实训 (网络仿真)",
        ),
        SignActivity(
            activityId = "demo-act-2",
            title = "课堂签到",
            status = "pending_signin",
            signType = "LOCATION",
            startTime = "14:00",
            endTime = "15:35",
            classroom = "BW-106",
            course = "数字通信原理及协议",
        ),
        SignActivity(
            activityId = "demo-act-h",
            title = "课堂签到",
            status = "pending_signin",
            signType = "LOCATION",
            startTime = "16:20",
            endTime = "17:55",
            classroom = "H-206",
            course = "无线网络与移动计算",
        ),
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
