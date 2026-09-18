package cn.edu.swun.swun_ehall.data.session

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import cn.edu.swun.swun_ehall.data.demo.DemoData
import cn.edu.swun.swun_ehall.data.fences.CampusFences
import cn.edu.swun.swun_ehall.data.geo.Geo
import cn.edu.swun.swun_ehall.data.locate.Locator
import cn.edu.swun.swun_ehall.data.model.GeoFix
import cn.edu.swun.swun_ehall.data.model.Grade
import cn.edu.swun.swun_ehall.data.model.Lesson
import cn.edu.swun.swun_ehall.data.model.Profile
import cn.edu.swun.swun_ehall.data.model.SignActivity
import cn.edu.swun.swun_ehall.data.model.YktBill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class Session(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("swun_session", 0)
    private val locator = Locator(app)

    var ready by mutableStateOf(true)
    var loggedIn by mutableStateOf(false)
    var demoMode by mutableStateOf(false)
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var displayName by mutableStateOf("同学")
    var studentId by mutableStateOf("")
    var profile by mutableStateOf(Profile())
    var developerMode by mutableStateOf(prefs.getBoolean("developerMode", false))
    var checkUpdateOnLaunch by mutableStateOf(prefs.getBoolean("checkUpdateOnLaunch", true))
    var lastPunch by mutableStateOf<String?>(null)

    val schedule = mutableStateListOf<Lesson>()
    val grades = mutableStateListOf<Grade>()
    val ktkq = mutableStateListOf<SignActivity>()
    val yktBills = mutableStateListOf<YktBill>()
    var yktBalance by mutableStateOf<Double?>(null)

    fun persistDeveloperMode(v: Boolean) {
        developerMode = v
        prefs.edit().putBoolean("developerMode", v).apply()
    }

    fun persistCheckUpdateOnLaunch(v: Boolean) {
        checkUpdateOnLaunch = v
        prefs.edit().putBoolean("checkUpdateOnLaunch", v).apply()
    }

    fun enterDemo() {
        demoMode = true
        loggedIn = true
        error = null
        profile = DemoData.profile
        displayName = profile.name
        studentId = profile.studentId
        schedule.clear()
        schedule.addAll(DemoData.schedule)
        grades.clear()
        grades.addAll(DemoData.grades)
        ktkq.clear()
        ktkq.addAll(DemoData.ktkq)
        yktBills.clear()
        yktBills.addAll(DemoData.yktBills)
        yktBalance = DemoData.yktBalance
    }

    fun login(user: String, pass: String) {
        if (user.isBlank() || pass.isBlank()) {
            error = "请输入学号和密码"
            return
        }
        enterDemo()
        demoMode = false
        error = null
        studentId = user.trim()
        displayName = "同学"
        profile = profile.copy(name = displayName, studentId = studentId)
    }

    fun logout() {
        loggedIn = false
        demoMode = false
        error = null
        lastPunch = null
    }

    fun todayLessons(): List<Lesson> {
        val day = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)
        val weekday = if (day == java.util.Calendar.SUNDAY) 7 else day - 1
        return schedule.filter { it.weekday == weekday }.sortedBy { it.start }
    }

    fun signIn(activity: SignActivity, lat: Double, lng: Double, source: String, datum: String = "gcj02"): String {
        val g = Geo.campusGcj02(lat, lng, source, datum)
        val idx = ktkq.indexOfFirst { it.activityId == activity.activityId }
        if (idx >= 0) {
            ktkq[idx] = activity.copy(status = "already_signed")
        }
        lastPunch = "提交 GCJ-02 ${"%.5f".format(g.first)}, ${"%.5f".format(g.second)}"
        return lastPunch!!
    }

    fun signInBuildingCenter(activity: SignActivity): String {
        val fence = CampusFences.forRoom(activity.classroom)
            ?: return "未识别教室楼栋：${activity.classroom}"
        return signIn(activity, fence.centerLat, fence.centerLng, source = "demo", datum = "gcj02")
            .let { "已用 ${fence.name} 楼中心签到 · $it" }
    }

    suspend fun locateCampus(): GeoFix = withContext(Dispatchers.Main) {
        val raw = locator.getFix()
        val g = Geo.campusGcj02(raw.latitude, raw.longitude, raw.source, raw.datum)
        raw.copy(latitude = g.first, longitude = g.second, datum = "gcj02")
    }

    fun dormPunch(useCampusFence: Boolean): String {
        val (lat, lng) = if (useCampusFence) {
            DemoData.clockFence
        } else {
            DemoData.clockFence
        }
        val g = Geo.campusGcj02(lat, lng, source = "amap", datum = "gcj02")
        lastPunch = "公寓打卡 GCJ-02 ${"%.5f".format(g.first)}, ${"%.5f".format(g.second)}"
        return lastPunch!!
    }
}
