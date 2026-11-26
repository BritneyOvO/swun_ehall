package cn.edu.swun.swun_ehall.data.session

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.swun.swun_ehall.data.demo.DemoData
import cn.edu.swun.swun_ehall.data.fences.CampusFences
import cn.edu.swun.swun_ehall.data.geo.Geo
import cn.edu.swun.swun_ehall.data.http.CampusHttp
import cn.edu.swun.swun_ehall.data.http.CasClient
import cn.edu.swun.swun_ehall.data.http.EhallClient
import cn.edu.swun.swun_ehall.data.http.GyClient
import cn.edu.swun.swun_ehall.data.http.JwxtClient
import cn.edu.swun.swun_ehall.data.http.jwxtCurrentWeek
import cn.edu.swun.swun_ehall.data.http.jwxtKbList
import cn.edu.swun.swun_ehall.data.http.KtkqClient
import cn.edu.swun.swun_ehall.data.http.ktkqTokenExpired
import cn.edu.swun.swun_ehall.data.http.LantuClient
import cn.edu.swun.swun_ehall.data.http.LantuSessionExpiredException
import cn.edu.swun.swun_ehall.data.http.RsGateway
import cn.edu.swun.swun_ehall.data.http.SwunLog
import cn.edu.swun.swun_ehall.data.http.StudentClient
import cn.edu.swun.swun_ehall.data.http.StudentSessionExpiredException
import cn.edu.swun.swun_ehall.data.http.YktClient
import cn.edu.swun.swun_ehall.data.locate.Locator
import cn.edu.swun.swun_ehall.data.http.xkOfficialSxbj
import cn.edu.swun.swun_ehall.data.http.xkSaveCourseBody
import cn.edu.swun.swun_ehall.data.http.xkSubmitAlert
import cn.edu.swun.swun_ehall.data.model.AppRelease
import cn.edu.swun.swun_ehall.data.model.ClockFence
import cn.edu.swun.swun_ehall.data.model.ClockRecord
import cn.edu.swun.swun_ehall.data.model.CreditProgress
import cn.edu.swun.swun_ehall.data.model.Exam
import cn.edu.swun.swun_ehall.data.model.GeoFix
import cn.edu.swun.swun_ehall.data.model.Grade
import cn.edu.swun.swun_ehall.data.model.Lesson
import cn.edu.swun.swun_ehall.data.model.LeaveApplyForm
import cn.edu.swun.swun_ehall.data.model.LeaveCancelForm
import cn.edu.swun.swun_ehall.data.model.LeaveCancelPreparation
import cn.edu.swun.swun_ehall.data.model.LeaveDetail
import cn.edu.swun.swun_ehall.data.model.LeaveOption
import cn.edu.swun.swun_ehall.data.model.LeaveRecord
import cn.edu.swun.swun_ehall.data.model.LeaveRule
import cn.edu.swun.swun_ehall.data.model.LeaveStudentInfo
import cn.edu.swun.swun_ehall.data.model.LeaveSummary
import cn.edu.swun.swun_ehall.data.model.Profile
import cn.edu.swun.swun_ehall.data.model.SchoolTerm
import cn.edu.swun.swun_ehall.data.model.SchoolCalendar
import cn.edu.swun.swun_ehall.data.http.decodeKtkqNav
import cn.edu.swun.swun_ehall.data.http.encodeKtkqNav
import cn.edu.swun.swun_ehall.data.http.matchKtkqSlot
import cn.edu.swun.swun_ehall.data.model.KtkqCourse
import cn.edu.swun.swun_ehall.data.model.KtkqWeek
import cn.edu.swun.swun_ehall.data.model.SignActivity
import cn.edu.swun.swun_ehall.data.model.kWeekdayLabels
import cn.edu.swun.swun_ehall.data.model.ktkqSlotFromLesson
import cn.edu.swun.swun_ehall.data.model.slotKey
import cn.edu.swun.swun_ehall.data.model.XkCourse
import cn.edu.swun.swun_ehall.data.model.XkRound
import cn.edu.swun.swun_ehall.data.model.YktBill
import cn.edu.swun.swun_ehall.data.model.ElectricSelection
import cn.edu.swun.swun_ehall.data.model.YktDirectoryOption
import cn.edu.swun.swun_ehall.data.model.YktPayMethod
import cn.edu.swun.swun_ehall.data.model.YktPaymentResult
import cn.edu.swun.swun_ehall.data.model.YktRechargeAccount
import cn.edu.swun.swun_ehall.data.model.currentSchoolTerm
import cn.edu.swun.swun_ehall.data.update.UpdateClient
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import cn.edu.swun.swun_ehall.data.http.RequestCancellation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Session(app: Application) : AndroidViewModel(app) {
    private val requests = AccountRequests(viewModelScope)
    private val casLock = Any()
    private val lantuLock = Any()
    private val ktkqLock = Any()
    private val gyLock = Any()
    private val studentLock = Any()
    private val prefs = app.getSharedPreferences("swun_session", 0)
    private val creds = CredentialStore(app)
    val accounts = AccountStore(app, creds)
    val slotPicks = SlotChoiceStore(prefs)
    private val teachers = TeacherCache(File(app.filesDir, "kb_teachers.json"))
    private val schedules = ScheduleCache(File(app.filesDir, "schedule_cache"))
    private val locator = Locator(app)
    private val rs = RsGateway(app)
    private val cas = CasClient()
    private val ehall = EhallClient()
    private val lantu = LantuClient(File(app.filesDir, "lantu.json"))
    private val jwxt = JwxtClient()
    private val ktkqClient = KtkqClient(rs)
    private val gy = GyClient(rs)
    private val yktClient = YktClient(rs)
    private val studentClient = StudentClient(rs)
    private val cookieFile = File(app.filesDir, "cookies.json")

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
    var latestRelease by mutableStateOf<AppRelease?>(null)
    var dismissedUpdate by mutableStateOf<String?>(null)
    private var lastUpdateCheckAt = 0L
    var lastPunch by mutableStateOf<String?>(null)
    var yktQr by mutableStateOf<String?>(null)
    var yktQrUpdatedAt by mutableStateOf(0L)
    var yktError by mutableStateOf<String?>(null)
    var leaveError by mutableStateOf<String?>(null)
    var leaveSummary by mutableStateOf(LeaveSummary())
    var leaveStudentInfo by mutableStateOf(LeaveStudentInfo())
    var leaveRule by mutableStateOf(LeaveRule())
    var loadHint by mutableStateOf<String?>(null)
    var scheduleError by mutableStateOf<String?>(null)
    var schoolCalendar by mutableStateOf<SchoolCalendar?>(null)
    var calendarError by mutableStateOf<String?>(null)
    var clockError by mutableStateOf<String?>(null)
    var ktkqError by mutableStateOf<String?>(null)
    var xkError by mutableStateOf<String?>(null)
    var curWeek by mutableStateOf(1)
    var totalWeek by mutableStateOf(16)
    var week1Monday by mutableStateOf<java.time.LocalDate?>(null)
    var localAvatarPath by mutableStateOf<String?>(null)
    var remoteAvatarPath by mutableStateOf<String?>(null)
    var avatarEpoch by mutableIntStateOf(0)
    var pendingCropPath by mutableStateOf<String?>(null)
    private val avatarDir = File(app.filesDir, "avatars").apply { if (!exists()) mkdirs() }
    private val legacyCustomAvatar = File(app.filesDir, "avatar.png")
    private val legacyRemoteAvatar = File(app.filesDir, "remote_avatar")
    private val cropSrcFile = File(app.filesDir, "crop_src")
    var credits by mutableStateOf<CreditProgress?>(null)
    var xkPanel by mutableStateOf<Map<String, String>>(emptyMap())
    var xkProfile by mutableStateOf<Map<String, String>>(emptyMap())

    val schedule = mutableStateListOf<Lesson>()
    val grades = mutableStateListOf<Grade>()
    val exams = mutableStateListOf<Exam>()
    val ktkq = mutableStateListOf<SignActivity>()
    val ktkqCourses = mutableStateListOf<KtkqCourse>()
    var ktkqWeekNum by mutableStateOf(1)
    var ktkqXnxqmc by mutableStateOf("")
    var pendingKtkqSlot by mutableStateOf<SignActivity?>(null)
    val yktBills = mutableStateListOf<YktBill>()
    var yktBalance by mutableStateOf<Double?>(null)
    val leaveRecords = mutableStateListOf<LeaveRecord>()
    val leaveTypes = mutableStateListOf<LeaveOption>()
    val leaveSpecificTypes = mutableStateListOf<LeaveOption>()
    val clockRecords = mutableStateListOf<ClockRecord>()
    val clockFences = mutableStateListOf<ClockFence>()
    val xkRounds = mutableStateListOf<XkRound>()
    val xkCourses = mutableStateListOf<XkCourse>()
    init {
        CampusHttp.cookies.onChanged = { persistCookieFile() }
        accounts.load()
        studentId = accounts.currentId.ifBlank { creds.username }
        bindTokenCallbacks(studentId)
        ktkqClient.bindAccount(studentId)
        slotPicks.load(studentId)
        teachers.load(studentId)
        bindAvatars(studentId)
        UpdateClient.purgeStale(app)
        restoreFromDisk()
    }

    fun persistDeveloperMode(v: Boolean) {
        developerMode = v
        prefs.edit().putBoolean("developerMode", v).apply()
    }

    fun persistCheckUpdateOnLaunch(v: Boolean) {
        checkUpdateOnLaunch = v
        prefs.edit().putBoolean("checkUpdateOnLaunch", v).apply()
    }

    fun checkLatestIfEnabled() {
        if (!checkUpdateOnLaunch || demoMode) return
        val now = System.currentTimeMillis()
        if (now - lastUpdateCheckAt < 30_000L && latestRelease != null) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                lastUpdateCheckAt = System.currentTimeMillis()
                val got = UpdateClient.latest()
                withContext(Dispatchers.Main) { latestRelease = got }
            } catch (e: Exception) {
                if (e is java.util.concurrent.CancellationException) throw e
                RequestCancellation.check()
            }
        }
    }

    suspend fun checkLatest(): AppRelease? {
        return withContext(Dispatchers.IO) {
            lastUpdateCheckAt = System.currentTimeMillis()
            val got = UpdateClient.latest()
            withContext(Dispatchers.Main) { latestRelease = got }
            got
        }
    }

    fun enterDemo() {
        val owner = requests.begin()
        clearAccountData()
        viewModelScope.launch { requests.transition(owner) { clearClientSession() } }
        demoMode = true
        loggedIn = true
        error = null
        loadHint = null
        ktkqError = null
        xkError = null
        profile = DemoData.profile
        displayName = profile.name
        studentId = profile.studentId
        schedule.clear(); schedule.addAll(DemoData.schedule)
        grades.clear(); grades.addAll(DemoData.grades)
        exams.clear(); exams.addAll(DemoData.exams)
        schoolCalendar = DemoData.calendar
        applyKtkqWeek(DemoData.ktkqWeek)
        yktBills.clear(); yktBills.addAll(DemoData.yktBills)
        yktBalance = DemoData.yktBalance
        yktQr = "SWUN-DEMO-YKT-${studentId.ifEmpty { "202430000000" }}"
        leaveSummary = DemoData.leaveSummary
        leaveStudentInfo = DemoData.leaveStudentInfo
        leaveRule = DemoData.leaveRule
        leaveRecords.clear(); leaveRecords.addAll(DemoData.leaveRecords)
        leaveTypes.clear(); leaveTypes.addAll(DemoData.leaveTypes)
        leaveSpecificTypes.clear(); leaveSpecificTypes.addAll(DemoData.leaveSpecificTypes)
        clockRecords.clear(); clockRecords.addAll(DemoData.clockRecords)
        clockFences.clear(); clockFences.addAll(DemoData.clockFences)
        credits = DemoData.credits
        xkRounds.clear(); xkRounds.addAll(DemoData.xkRounds)
        xkCourses.clear(); xkCourses.addAll(DemoData.xkCourses)
        xkProfile = DemoData.xkProfile
        curWeek = 1
        totalWeek = 16
        bindAvatars(studentId)
    }

    suspend fun login(user: String, pass: String) {
        if (user.isBlank() || pass.isBlank()) {
            error = "请输入学号和密码"
            return
        }
        val owner = requests.begin()
        busy = true
        error = null
        demoMode = false
        clearAccountData()
        studentId = user.trim()
        bindAvatars(studentId)
        try {
            requests.transition(owner) {
                clearClientSession()
                bindTokenCallbacks(user.trim())
                ktkqClient.bindAccount(user.trim())
                withContext(Dispatchers.IO) {
                    lantu.login(user.trim(), pass)
                    try {
                        cas.login(user.trim(), pass)
                    } catch (e: Exception) {
                        if (e is java.util.concurrent.CancellationException) throw e
                        RequestCancellation.check()
                        lantu.clear()
                        throw Exception("统一身份登录失败，请重试", e)
                    }
                    persistSession(user.trim(), pass)
                }
                applyLantu()
                restoreCachedSchedule()
                loggedIn = true
            }
            if (requests.isCurrent(owner)) requests.launch("startup") { refreshLiveData() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e is java.util.concurrent.CancellationException) throw e
            RequestCancellation.check()
            if (requests.isCurrent(owner)) {
                loggedIn = false
                error = publicError(e)
                Log.e("swun", "login", e)
            }
        } finally {
            if (requests.isCurrent(owner)) busy = false
        }
    }

    private fun clearAccountData() {
        lastPunch = null
        loadHint = null
        scheduleError = null
        schoolCalendar = null
        calendarError = null
        ktkqError = null
        xkError = null
        leaveError = null
        displayName = "同学"
        profile = Profile()
        schedule.clear(); grades.clear(); exams.clear(); ktkq.clear(); ktkqCourses.clear()
        yktBills.clear(); leaveRecords.clear(); leaveTypes.clear(); leaveSpecificTypes.clear()
        clockRecords.clear(); clockFences.clear(); xkRounds.clear(); xkCourses.clear()
        ktkqWeekNum = 1
        ktkqXnxqmc = ""
        pendingKtkqSlot = null
        yktBalance = null
        credits = null
        yktQr = null
        yktQrUpdatedAt = 0L
        yktError = null
        leaveSummary = LeaveSummary()
        leaveStudentInfo = LeaveStudentInfo()
        leaveRule = LeaveRule()
        clockError = null
        xkPanel = emptyMap()
        xkProfile = emptyMap()
        curWeek = 1
        totalWeek = 16
        week1Monday = null
        pendingCropPath = null
        localAvatarPath = null
        remoteAvatarPath = null
        avatarEpoch++
    }

    private suspend fun clearClientSession() {
        // transition() has already cancelled and drained all old account requests.
        rs.clearSession()
        ktkqClient.reset()
        gy.reset()
        studentClient.reset()
        jwxt.reset()
        withContext(Dispatchers.IO) {
            lantu.clear()
            CampusHttp.cookies.clear()
            cookieFile.delete()
        }
    }

    private fun bindTokenCallbacks(id: String) {
        ktkqClient.onToken = { token ->
            requests.commit { prefs.edit().putString("ktkqToken_" + id, token).apply() }
        }
        gy.onToken = { token ->
            requests.commit { prefs.edit().putString("gyToken_" + id, token).apply() }
        }
    }

    fun logout() {
        val oldId = studentId.ifBlank { accounts.currentId }
        val owner = requests.begin()
        prefs.edit().putBoolean("loggedOut", true).commit()
        loggedIn = false
        demoMode = false
        busy = false
        error = null
        clearAccountData()
        studentId = ""
        prefs.edit().remove("ktkqToken_" + oldId).remove("gyToken_" + oldId).apply()
        viewModelScope.launch {
            try {
                requests.transition(owner) { clearClientSession() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is java.util.concurrent.CancellationException) throw e
                RequestCancellation.check()
                Log.e("swun", "logout cleanup", e)
            }
        }
    }

    suspend fun switchTo(id: String) {
        val acc = accounts.items.firstOrNull { it.id == id } ?: return
        val pass = accounts.password(acc.id)
        if (pass.isBlank()) {
            error = "该账号没有保存密码，请重新登录"
            return
        }
        login(acc.id, pass)
    }

    fun removeAccount(id: String) {
        val wasCurrent = id == accounts.currentId || id == studentId
        if (wasCurrent) logout()
        deleteAccountAvatars(id)
        schedules.remove(id)
        requests.commit { accounts.remove(id) }
        prefs.edit().remove("ktkqToken_" + id).remove("gyToken_" + id)
            .remove("ktkqXnxqdm_" + id).remove("ktkqXnxqmc_" + id).apply()
    }

    fun todayLessons(): List<Lesson> {
        val day = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)
        val weekday = if (day == java.util.Calendar.SUNDAY) 7 else day - 1
        return slotPicks.resolve(
            schedule.filter { it.weekday == weekday && it.inWeek(curWeek) },
            curWeek,
        ).sortedBy { it.start }
    }

    fun weekLessons(week: Int = curWeek): List<Lesson> = schedule.filter { it.inWeek(week) }

    fun pickSlot(week: Int, group: List<Lesson>, lesson: Lesson) {
        slotPicks.pick(week, group, lesson)
    }

    fun beginAvatarCrop(bytes: java.io.InputStream): Boolean {
        cropSrcFile.outputStream().use { bytes.copyTo(it) }
        if (!cropSrcFile.exists() || cropSrcFile.length() < 32) return false
        pendingCropPath = cropSrcFile.absolutePath
        return true
    }

    fun finishAvatarCrop() {
        pendingCropPath = null
        if (cropSrcFile.exists()) cropSrcFile.delete()
    }

    fun setLocalAvatar(bytes: ByteArray) {
        val file = customAvatarFile()
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
        localAvatarPath = file.absolutePath
        avatarEpoch++
    }

    fun clearLocalAvatar() {
        val file = customAvatarFile()
        if (file.exists()) file.delete()
        localAvatarPath = null
        avatarEpoch++
    }

    private fun accountKey(id: String = studentId.ifBlank { accounts.currentId }): String {
        val raw = id.trim().ifBlank { "default" }
        return raw.replace(Regex("[^0-9A-Za-z._-]"), "_")
    }

    private fun customAvatarFile(id: String = accountKey()) = File(avatarDir, "$id.png")

    private fun remoteAvatarFileFor(id: String = accountKey()) = File(avatarDir, "$id.remote")

    private fun bindAvatars(id: String) {
        val key = accountKey(id)
        migrateLegacyAvatars(key)
        val custom = customAvatarFile(key)
        val remote = remoteAvatarFileFor(key)
        localAvatarPath = custom.takeIf { it.exists() && it.length() > 32 }?.absolutePath
        remoteAvatarPath = remote.takeIf { it.exists() && it.length() > 32 }?.absolutePath
        avatarEpoch++
    }

    private fun migrateLegacyAvatars(key: String) {
        val custom = customAvatarFile(key)
        if (!custom.exists() && legacyCustomAvatar.exists()) {
            custom.parentFile?.mkdirs()
            legacyCustomAvatar.copyTo(custom, overwrite = false)
            legacyCustomAvatar.delete()
        }
        val remote = remoteAvatarFileFor(key)
        if (!remote.exists() && legacyRemoteAvatar.exists()) {
            remote.parentFile?.mkdirs()
            legacyRemoteAvatar.copyTo(remote, overwrite = false)
            legacyRemoteAvatar.delete()
        }
    }

    private fun deleteAccountAvatars(id: String) {
        val key = accountKey(id)
        customAvatarFile(key).delete()
        remoteAvatarFileFor(key).delete()
    }

    suspend fun refreshSchedule() {
        if (demoMode) return
        accountIo("schedule") {
            withContext(Dispatchers.Main) { scheduleError = null }
            val account = studentId.ifBlank { accounts.currentId }.ifBlank { creds.username }
            var lantuLessons: List<Lesson> = emptyList()
            try {
                val started = System.nanoTime()
                val lantuAt = System.nanoTime()
                val course = withLantuSession { lantu.getCourse() }
                lantuLessons = teachers.apply(lantu.mapLessons(course))
                SwunLog.took(lantuAt, "课表 Lantu ${lantuLessons.size}门")
                if (lantuLessons.isEmpty()) error("Lantu课表没有返回课程")
                val week = course.optString("curWeek").toIntOrNull() ?: 1
                val total = maxOf(
                    course.optString("totalWeek").toIntOrNull() ?: 16,
                    lantuLessons.maxOfOrNull { it.weeks.length } ?: 16,
                )
                SwunLog.took(started, "课表完成 ${lantuLessons.size}门 第${week}周")
                applySchedule(account, lantuLessons, week, total, inferredFirstMonday(week))

                // 教务系统只补教师和日历；课表本身已经由 Lantu 提供。
                try {
                    ensureCas()
                    jwxt.attachCas(cas)
                    jwxt.ensureSession()
                    if (!jwxt.portalClosed) {
                        val page = jwxt.schedulePage()
                        val kb = jwxtKbList(page)
                        teachers.fillFromJwxt(kb)
                        val enriched = teachers.apply(lantuLessons)
                        val cal = jwxtCurrentWeek(page)
                        applySchedule(
                            account,
                            enriched,
                            cal.week,
                            maxOf(total, cal.total),
                            cal.week1Monday ?: inferredFirstMonday(cal.week),
                        )
                    }
                } catch (e: Exception) {
                    if (e is java.util.concurrent.CancellationException) throw e
                    RequestCancellation.check()
                    Log.w("swun", "课表教务补充失败，保留 Lantu 课表", e)
                }
            } catch (e: Exception) {
                if (e is java.util.concurrent.CancellationException) throw e
                RequestCancellation.check()
                Log.e("swun", "schedule", e)
                var failure = e
                if (lantuLessons.isEmpty()) {
                    // Lantu unavailable: retain the existing Jwxt fallback.
                    try {
                        val started = System.nanoTime()
                        ensureCas()
                        jwxt.attachCas(cas)
                        jwxt.ensureSession()
                        if (jwxt.portalClosed) error("教务夜间关闭或不在服务时间")
                        val page = jwxt.schedulePage()
                        val kb = jwxtKbList(page)
                        teachers.fillFromJwxt(kb)
                        val lessons = teachers.apply(jwxt.lessonsFromKb(kb))
                        if (lessons.isEmpty()) error("课表没有返回课程")
                        val cal = jwxtCurrentWeek(page)
                        applySchedule(
                            account,
                            lessons,
                            cal.week,
                            maxOf(cal.total, lessons.maxOfOrNull { it.weeks.length } ?: 16),
                            cal.week1Monday ?: inferredFirstMonday(cal.week),
                        )
                        SwunLog.took(started, "课表教务回退完成 ${lessons.size}门")
                        return@accountIo
                    } catch (fallback: Exception) {
                        if (fallback is java.util.concurrent.CancellationException) throw fallback
                        RequestCancellation.check()
                        Log.e("swun", "schedule fallback", fallback)
                        failure = fallback
                    }
                }
                withContext(Dispatchers.Main) {
                    scheduleError = if (failure === e) publicError(e) else "${publicError(e)}；${publicError(failure)}"
                    loadHint = scheduleError
                }
            }
        }
    }

    suspend fun refreshGrades(term: SchoolTerm = SchoolTerm("", "")) {
        if (demoMode) return
        accountIo("grades:" + term.xnm + ":" + term.xqm) {
            try {
                ensureCas()
                jwxt.attachCas(cas)
                jwxt.ensureSession()
                if (jwxt.portalClosed) {
                    withContext(Dispatchers.Main) { loadHint = "教务夜间关闭或不在服务时间" }
                    return@accountIo
                }
                val items = jwxt.grades(term.xnm, term.xqm)
                withContext(Dispatchers.Main) {
                    grades.clear()
                    grades.addAll(items)
                }
            } catch (e: Exception) {
                if (e is java.util.concurrent.CancellationException) throw e
                RequestCancellation.check()
                Log.e("swun", "grades", e)
                withContext(Dispatchers.Main) { loadHint = publicError(e) }
            }
        }
    }

    suspend fun refreshCalendar() {
        if (demoMode) return
        accountIo("calendar") {
            try {
                withContext(Dispatchers.Main) { calendarError = null }
                ensureCas()
                jwxt.attachCas(cas)
                jwxt.ensureSession()
                if (jwxt.portalClosed) error("教务夜间关闭或不在服务时间")
                val value = jwxt.calendar()
                if (value.startDate == null && value.endDate == null && value.events.isEmpty()) {
                    error("校历没有返回有效内容")
                }
                withContext(Dispatchers.Main) { schoolCalendar = value }
            } catch (e: Exception) {
                if (e is java.util.concurrent.CancellationException) throw e
                RequestCancellation.check()
                Log.e("swun", "calendar", e)
                withContext(Dispatchers.Main) { calendarError = publicError(e) }
            }
        }
    }

    suspend fun refreshExams() {
        if (demoMode) {
            exams.clear(); exams.addAll(DemoData.exams)
            return
        }
        accountIo("exams") {
            try {
                ensureCas()
                jwxt.attachCas(cas)
                jwxt.ensureSession()
                val items = jwxt.exams()
                withContext(Dispatchers.Main) {
                    exams.clear()
                    exams.addAll(items)
                }
            } catch (e: Exception) {
                if (e is java.util.concurrent.CancellationException) throw e
                RequestCancellation.check()
                Log.e("swun", "exams", e)
                withContext(Dispatchers.Main) { loadHint = publicError(e) }
            }
        }
    }

    suspend fun refreshCredits() {
        if (demoMode) {
            credits = DemoData.credits
            return
        }
        accountIo("credits") {
            try {
                if (grades.isEmpty()) refreshGrades()
                val snapshot = withContext(Dispatchers.Main) { grades.toList() }
                val p = jwxt.creditFromGrades(snapshot)
                withContext(Dispatchers.Main) { credits = p }
            } catch (e: Exception) {
                if (e is java.util.concurrent.CancellationException) throw e
                RequestCancellation.check()
                Log.e("swun", "credits", e)
                withContext(Dispatchers.Main) { loadHint = publicError(e) }
            }
        }
    }

    suspend fun refreshXk() {
        if (demoMode) {
            xkRounds.clear(); xkRounds.addAll(DemoData.xkRounds)
            xkCourses.clear(); xkCourses.addAll(DemoData.xkCourses)
            xkProfile = DemoData.xkProfile
            return
        }
        accountIo("selection") {
            withContext(Dispatchers.Main) { xkError = null }
            try {
                ensureCas()
                jwxt.attachCas(cas)
                jwxt.ensureSession()
                val (rounds, profile) = jwxt.selectionEntry()
                withContext(Dispatchers.Main) {
                    xkRounds.clear()
                    xkRounds.addAll(rounds)
                    xkProfile = profile
                    xkError = null
                }
            } catch (e: Exception) {
                if (e is java.util.concurrent.CancellationException) throw e
                RequestCancellation.check()
                Log.e("swun", "xk", e)
                withContext(Dispatchers.Main) { xkError = publicError(e) }
            }
        }
    }

    suspend fun loadXkCourses(round: XkRound, keyword: String = "") {
        if (demoMode) {
            xkCourses.clear()
            xkCourses.addAll(DemoData.xkCourses.filter { keyword.isEmpty() || it.name.contains(keyword) })
            return
        }
        accountIo {
            withContext(Dispatchers.Main) { xkError = null }
            try {
                ensureCas()
                jwxt.attachCas(cas)
                val panel = jwxt.selectionDisplay(round, xkProfile)
                val list = jwxt.selectionCourses(round, xkProfile, keyword, panel)
                withContext(Dispatchers.Main) {
                    xkPanel = panel
                    xkCourses.clear()
                    xkCourses.addAll(list)
                }
            } catch (e: Exception) {
                if (e is java.util.concurrent.CancellationException) throw e
                RequestCancellation.check()
                Log.e("swun", "xk courses", e)
                withContext(Dispatchers.Main) { xkError = publicError(e) }
            }
        }
    }

    suspend fun loadXkClasses(round: XkRound, course: XkCourse): List<XkCourse> {
        if (demoMode) return listOf(course)
        return accountIo {
            ensureCas()
            jwxt.attachCas(cas)
            val query = buildMap {
                putAll(xkPanel)
                put("xkkz_id", round.xkkzId)
                put("xkkz_xh", round.xkkzXh)
                put("kklxdm", round.kklxdm)
            }
            jwxt.selectionJxbs(query, course.kchId, course.name)
        }
    }

    suspend fun submitXk(round: XkRound, course: XkCourse, clazz: XkCourse): String {
        if (demoMode) return "示例模式：已模拟选课 ${clazz.jxbName}"
        return accountIo {
            ensureCas()
            jwxt.attachCas(cas)
            val rlkz = xkPanel["rlkz"] ?: "0"
            val cdrlkz = xkPanel["cdrlkz"] ?: "0"
            val rlzlkz = xkPanel["rlzlkz"] ?: "0"
            val body = xkSaveCourseBody(
                jxbIds = clazz.doJxbId.ifBlank { clazz.jxbId },
                kchId = course.kchId,
                kcmc = course.name,
                rwlx = xkPanel["rwlx"] ?: "1",
                rlkz = rlkz,
                cdrlkz = cdrlkz,
                rlzlkz = rlzlkz,
                sxbj = xkOfficialSxbj(rlkz, cdrlkz, rlzlkz),
                xxkbj = xkPanel["xxkbj"] ?: "0",
                qz = "0",
                cxbj = "0",
                xkkzId = round.xkkzId,
                njdmId = round.njdmId,
                zyhId = round.zyhId,
                kklxdm = round.kklxdm,
                xklc = xkPanel["xklc"].orEmpty(),
                xkxnm = xkProfile["xkxnm"].orEmpty(),
                xkxqm = xkProfile["xkxqm"].orEmpty(),
            )
            val resp = jwxt.selectionSubmit(body)
            xkSubmitAlert(resp) ?: "选课成功"
        }
    }

    suspend fun refreshProfile() {
        if (demoMode) return
        accountIo("profile") {
            try {
                if (lantu.isLoggedIn) {
                    val info = withLantuSession { lantu.getUserInfo() }
                    info.optJSONObject("userBaseInfo")?.let { lantu.userBase = it }
                    info.optJSONObject("userLoginInfo")?.let { lantu.userLogin = it }
                }
            } catch (e: Exception) {
                if (e is java.util.concurrent.CancellationException) throw e
                RequestCancellation.check()
                Log.e("swun", "profile lantu", e)
            }
            var next = if (lantu.isLoggedIn) lantu.profile() else Profile(studentId = studentId)
            try {
                ensureCas()
                next = next.merge(ehall.profile(cas))
            } catch (e: Exception) {
                if (e is java.util.concurrent.CancellationException) throw e
                RequestCancellation.check()
                Log.e("swun", "profile ehall", e)
            }
            withContext(Dispatchers.Main) {
                profile = next.merge(profile)
                if (profile.studentId.isNotEmpty()) studentId = profile.studentId
                displayName = profile.name.ifBlank { studentId.ifBlank { "同学" } }
            }
            val url = profile.avatar.trim()
            if (url.startsWith("http")) fetchRemoteAvatar(url)
        }
    }

    suspend fun refreshKtkq(force: Boolean = false) {
        if (demoMode) {
            applyKtkqWeek(DemoData.ktkqWeek)
            return
        }
        accountIo("ktkq:" + force) {
            withContext(Dispatchers.Main) { ktkqError = null }
            try {
                ensureKtkq()
                val weekNum = curWeek.takeIf { it > 0 }
                val week = ktkqClient.weekCourses(week = weekNum, refresh = force)
                val items = week.flatten()
                teachers.fillFromKtkq(items, week.week)
                val patched = teachers.apply(schedule.toList())
                requests.commit {
                    prefs.edit()
                        .putString(ktkqTokenKey(), ktkqClient.token.orEmpty())
                        .putString(ktkqTermKey(), ktkqClient.knownXnxqdm)
                        .putString(ktkqTermNameKey(), week.xnxqmc)
                        .apply()
                }
                withContext(Dispatchers.Main) {
                    ktkqError = null
                    applyKtkqWeek(week)
                    if (patched.any { it.teacher.isNotEmpty() }) {
                        schedule.clear()
                        schedule.addAll(patched)
                    }
                }
            } catch (e: Exception) {
                if (e is java.util.concurrent.CancellationException) throw e
                RequestCancellation.check()
                Log.e("swun", "ktkq", e)
                withContext(Dispatchers.Main) { ktkqError = publicError(e) }
            }
        }
    }

    fun slotForLesson(lesson: Lesson, week: Int = ktkqWeekNum): SignActivity {
        return matchKtkqSlot(lesson, ktkq.toList()) ?: ktkqSlotFromLesson(lesson, week)
    }

    fun prepareKtkqSign(lesson: Lesson? = null, slot: SignActivity? = null, week: Int? = null): String {
        val w = week ?: ktkqWeekNum
        val s = slot ?: lesson?.let { slotForLesson(it, w) } ?: error("没有这节课")
        pendingKtkqSlot = s
        return "ktkqSign/${encodeKtkqNav(s.slotKey())}"
    }

    fun ktkqSlotByNav(id: String): SignActivity? {
        val key = decodeKtkqNav(id)
        pendingKtkqSlot?.takeIf { it.slotKey() == key }?.let { return it }
        ktkq.firstOrNull { it.slotKey() == key }?.let { return it }
        ktkq.firstOrNull { it.activityId == key || it.activityId == id }?.let { return it }
        return pendingKtkqSlot
    }

    private fun applyKtkqWeek(week: KtkqWeek) {
        ktkqWeekNum = week.week.coerceAtLeast(1)
        ktkqXnxqmc = week.xnxqmc.ifBlank { week.xnxqdm }
        ktkqCourses.clear()
        ktkqCourses.addAll(week.courses)
        ktkq.clear()
        ktkq.addAll(week.flatten())
    }

    suspend fun signForLesson(slot: SignActivity, refresh: Boolean = false): SignActivity {
        if (demoMode) return DemoData.signFor(slot)
        return accountIo {
            val started = System.nanoTime()
            ensureKtkq()
            val teacher = slot.teacher.ifBlank {
                schedule.firstOrNull {
                    it.name == slot.course && it.weekday == slot.weekDay && it.start == slot.startNode
                }?.teacher.orEmpty()
            }
            ktkqClient.signForLesson(
                slot.copy(teacher = teacher),
                week = slot.week.takeIf { it > 0 } ?: ktkqWeekNum,
                refresh = refresh,
                includeExtras = false,
            ).also { SwunLog.took(started, "课堂签到活动可用") }
        }
    }

    suspend fun ktkqSignExtras(slot: SignActivity): SignActivity {
        if (demoMode) return slot
        return accountIo {
            ensureKtkq()
            ktkqClient.signExtras(slot)
        }
    }

    suspend fun refreshYkt(date: java.time.LocalDate? = null) {
        if (demoMode) {
            yktBills.clear(); yktBills.addAll(DemoData.yktBills)
            yktBalance = DemoData.yktBalance
            yktQr = "SWUN-DEMO-YKT-$studentId"
            yktQrUpdatedAt = System.currentTimeMillis()
            yktError = null
            return
        }
        accountIo("ykt") {
            withContext(Dispatchers.Main) { yktError = null }
            try {
                lantu.cardBalanceYuan()?.let { bal ->
                    withContext(Dispatchers.Main) { yktBalance = bal }
                }
            } catch (e: Exception) {
                if (e is java.util.concurrent.CancellationException) throw e
                RequestCancellation.check()
            }
            try {
                ensureCas()
                val sid = studentId.ifBlank { creds.username }
                val school = "${lantu.schoolId}"
                var lastErr: String? = null
                var menuReady = true
                try {
                    val qr = yktClient.fetchQr(sid, school)
                    withContext(Dispatchers.Main) {
                        yktQr = qr
                        yktQrUpdatedAt = System.currentTimeMillis()
                    }
                } catch (e: Exception) {
                    if (e is java.util.concurrent.CancellationException) throw e
                    RequestCancellation.check()
                    Log.e("swun", "ykt qr", e)
                    lastErr = publicError(e)
                    withContext(Dispatchers.Main) {
                        yktQr = null
                        yktQrUpdatedAt = 0L
                    }
                    if (e.message?.contains("一卡通页面未就绪") == true ||
                        e.message?.contains("一卡通夜间关闭") == true
                    ) menuReady = false
                }
                if (menuReady) {
                    try {
                        val bal = yktClient.fetchBalance(sid, school)
                        if (bal != null) withContext(Dispatchers.Main) { yktBalance = bal }
                    } catch (e: Exception) {
                        if (e is java.util.concurrent.CancellationException) throw e
                        RequestCancellation.check()
                        Log.e("swun", "ykt bal", e)
                    }
                    try {
                        val (bal, bills) = yktClient.fetchLedger(sid, school, date)
                        withContext(Dispatchers.Main) {
                            if (bal != null) yktBalance = bal
                            yktBills.clear()
                            yktBills.addAll(bills)
                        }
                    } catch (e: Exception) {
                        if (e is java.util.concurrent.CancellationException) throw e
                        RequestCancellation.check()
                        Log.e("swun", "ykt bills", e)
                        lastErr = lastErr ?: publicError(e)
                    }
                } else {
                    Log.w("swun", "[ykt] skip balance and ledger because menu is not ready")
                }
                if (lastErr != null) withContext(Dispatchers.Main) { yktError = lastErr; loadHint = lastErr }
            } catch (e: Exception) {
                if (e is java.util.concurrent.CancellationException) throw e
                RequestCancellation.check()
                Log.e("swun", "ykt", e)
                withContext(Dispatchers.Main) {
                    yktError = publicError(e)
                    loadHint = yktError
                }
            }
        }
    }

    suspend fun refreshYktQr() {
        if (demoMode) {
            yktQr = "SWUN-DEMO-YKT-$studentId-${System.currentTimeMillis() / 60_000}"
            yktQrUpdatedAt = System.currentTimeMillis()
            return
        }
        accountIo("ykt-qr") {
            try {
                ensureCas()
                val sid = studentId.ifBlank { creds.username }
                val qr = yktClient.fetchQr(sid, "${lantu.schoolId}")
                withContext(Dispatchers.Main) {
                    yktQr = qr
                    yktQrUpdatedAt = System.currentTimeMillis()
                    yktError = null
                }
            } catch (e: Exception) {
                if (e is java.util.concurrent.CancellationException) throw e
                RequestCancellation.check()
                Log.e("swun", "ykt qr refresh", e)
                withContext(Dispatchers.Main) {
                    yktQr = null
                    yktQrUpdatedAt = 0L
                    yktError = publicError(e)
                }
            }
        }
    }

    suspend fun refreshYktLedger(date: java.time.LocalDate?) {
        if (demoMode) {
            yktBills.clear(); yktBills.addAll(DemoData.yktBills)
            return
        }
        accountIo("ykt-ledger:${date ?: "today"}") {
            try {
                ensureCas()
                val sid = studentId.ifBlank { creds.username }
                val (balance, bills) = yktClient.fetchLedger(sid, "${lantu.schoolId}", date)
                withContext(Dispatchers.Main) {
                    if (balance != null) yktBalance = balance
                    yktBills.clear()
                    yktBills.addAll(bills)
                    yktError = null
                }
            } catch (e: Exception) {
                if (e is java.util.concurrent.CancellationException) throw e
                RequestCancellation.check()
                Log.e("swun", "ykt ledger", e)
                withContext(Dispatchers.Main) { yktError = publicError(e) }
            }
        }
    }

    suspend fun yktRechargeAccount(): YktRechargeAccount {
        if (demoMode) return YktRechargeAccount(profile.name, studentId, yktBalance)
        return accountIo {
            ensureCas()
            yktClient.fetchRechargeAccount(studentId.ifBlank { creds.username }, "${lantu.schoolId}")
        }
    }

    suspend fun createYktRecharge(amountYuan: Double, method: YktPayMethod): YktPaymentResult {
        if (demoMode) return YktPaymentResult.Success("预览模式：未创建真实支付订单")
        return accountIo {
            ensureCas()
            yktClient.createCardRecharge(
                studentId.ifBlank { creds.username },
                amountYuan,
                method,
                "${lantu.schoolId}",
            )
        }
    }

    suspend fun waterRechargeAccount(): YktRechargeAccount {
        if (demoMode) return YktRechargeAccount(profile.name, studentId, yktBalance)
        return accountIo {
            ensureCas()
            yktClient.fetchWaterAccount(studentId.ifBlank { creds.username }, "${lantu.schoolId}")
        }
    }

    suspend fun rechargeWater(amountYuan: Double): YktPaymentResult {
        if (demoMode) return YktPaymentResult.Success("预览模式：未扣除一卡通余额")
        return accountIo {
            ensureCas()
            yktClient.rechargeWater(
                studentId.ifBlank { creds.username },
                amountYuan,
                yktBalance,
                "${lantu.schoolId}",
            )
        }
    }

    suspend fun electricSchools(): List<YktDirectoryOption> {
        if (demoMode) return DemoData.electricSchools
        return accountIo {
            ensureCas()
            yktClient.electricSchools(studentId.ifBlank { creds.username }, "${lantu.schoolId}")
        }
    }

    suspend fun electricBuildings(school: YktDirectoryOption): List<YktDirectoryOption> {
        if (demoMode) return DemoData.electricBuildings
        return accountIo { yktClient.electricBuildings(school) }
    }

    suspend fun electricFloors(building: YktDirectoryOption): List<YktDirectoryOption> {
        if (demoMode) return DemoData.electricFloors
        return accountIo { yktClient.electricFloors(building) }
    }

    suspend fun electricRooms(floor: YktDirectoryOption): List<YktDirectoryOption> {
        if (demoMode) return DemoData.electricRooms
        return accountIo { yktClient.electricRooms(floor) }
    }

    suspend fun rechargeElectric(
        selection: ElectricSelection,
        amountYuan: Double,
        method: YktPayMethod,
    ): YktPaymentResult {
        if (demoMode) return YktPaymentResult.Success("预览模式：未创建真实支付订单")
        return accountIo {
            ensureCas()
            yktClient.createElectricRecharge(
                studentId.ifBlank { creds.username },
                selection,
                amountYuan,
                method,
                "${lantu.schoolId}",
            )
        }
    }

    suspend fun refreshLeave() {
        if (demoMode) {
            leaveSummary = DemoData.leaveSummary
            leaveStudentInfo = DemoData.leaveStudentInfo
            leaveRule = DemoData.leaveRule
            leaveRecords.clear(); leaveRecords.addAll(DemoData.leaveRecords)
            leaveTypes.clear(); leaveTypes.addAll(DemoData.leaveTypes)
            leaveSpecificTypes.clear(); leaveSpecificTypes.addAll(DemoData.leaveSpecificTypes)
            leaveError = null
            return
        }
        accountIo("leave") {
            try {
                val snapshot = withStudentSession { readLeaveSnapshot() }
                withContext(Dispatchers.Main) { applyLeaveSnapshot(snapshot) }
            } catch (e: Exception) {
                if (e is java.util.concurrent.CancellationException) throw e
                RequestCancellation.check()
                Log.e("swun", "leave", e)
                withContext(Dispatchers.Main) { leaveError = publicError(e) }
            }
        }
    }

    suspend fun checkCanApplyLeave() {
        if (demoMode) return
        accountIo { withStudentSession { studentClient.checkCanApply() } }
    }

    suspend fun calculateLeaveDays(startTime: String, endTime: String): Double {
        if (demoMode) {
            val start = java.time.LocalDateTime.parse(startTime.replace(" ", "T"))
            val end = java.time.LocalDateTime.parse(endTime.replace(" ", "T"))
            return kotlin.math.ceil(java.time.Duration.between(start, end).toMinutes() / 1440.0).coerceAtLeast(1.0)
        }
        return accountIo { withStudentSession { studentClient.leaveDays(startTime, endTime) } }
    }

    suspend fun leaveDetail(leaveNo: String): LeaveDetail {
        if (demoMode) return DemoData.leaveDetail(leaveNo)
        return accountIo { withStudentSession { studentClient.leaveDetail(leaveNo) } }
    }

    suspend fun submitLeave(form: LeaveApplyForm): String {
        if (demoMode) return "预览模式：未提交请假申请"
        return accountIo {
            val message = withStudentSession { studentClient.createLeave(form) }
            val snapshot = withStudentSession { readLeaveSnapshot() }
            withContext(Dispatchers.Main) { applyLeaveSnapshot(snapshot) }
            message
        }
    }

    suspend fun prepareLeaveCancel(leaveNo: String): LeaveCancelPreparation {
        if (demoMode) return DemoData.leaveCancelPreparation(leaveNo)
        return accountIo { withStudentSession { studentClient.cancelPreparation(leaveNo) } }
    }

    suspend fun submitLeaveCancel(form: LeaveCancelForm): String {
        if (demoMode) return "预览模式：未提交销假申请"
        return accountIo {
            val message = withStudentSession { studentClient.createCancel(form) }
            val snapshot = withStudentSession { readLeaveSnapshot() }
            withContext(Dispatchers.Main) { applyLeaveSnapshot(snapshot) }
            message
        }
    }

    suspend fun leaveLocation(): Pair<String, Boolean> {
        if (demoMode) return "西南民族大学（预览位置）" to true
        val fix = withContext(Dispatchers.Main) { locateCampus() }
        return accountIo {
            val allowed = withStudentSession { studentClient.locationAllowed(fix.latitude, fix.longitude) }
            "GCJ-02 ${"%.6f".format(fix.latitude)}, ${"%.6f".format(fix.longitude)}" to allowed
        }
    }

    suspend fun uploadLeaveImage(uri: Uri): String {
        if (demoMode) return "https://example.invalid/demo-proof.jpg"
        return accountIo {
            val upload = prepareLeaveImage(uri)
            withStudentSession { studentClient.uploadImage(upload.bytes, upload.mimeType, upload.fileName) }
        }
    }

    suspend fun refreshClock() {
        if (demoMode) {
            clockRecords.clear(); clockRecords.addAll(DemoData.clockRecords)
            clockFences.clear(); clockFences.addAll(DemoData.clockFences)
            clockError = null
            return
        }
        accountIo("clock") {
            withContext(Dispatchers.Main) { clockError = null }
            try {
                ensureGy()
                val rec = gy.records()
                val fences = gy.positions()
                withContext(Dispatchers.Main) {
                    clockRecords.clear()
                    clockRecords.addAll(rec)
                    clockFences.clear()
                    clockFences.addAll(fences)
                    clockError = null
                }
            } catch (e: Exception) {
                if (e is java.util.concurrent.CancellationException) throw e
                RequestCancellation.check()
                Log.e("swun", "clock", e)
                withContext(Dispatchers.Main) { clockError = publicError(e) }
            }
        }
    }

    suspend fun signIn(
        activity: SignActivity,
        lat: Double,
        lng: Double,
        source: String,
        datum: String = "gcj02",
        code: String = "",
        accuracy: Double = 8.0,
    ): String {
        val g = Geo.campusWgs84(lat, lng, source, datum)
        val acc = if (accuracy.isFinite()) accuracy.roundToInt().coerceAtLeast(0) else 8
        if (demoMode) {
            lastPunch = "示例模式：已模拟签到 · WGS-84 ${"%.5f".format(g.first)}, ${"%.5f".format(g.second)}"
            return lastPunch!!
        }
        return accountIo {
            ensureKtkq()
            var act = activity
            val rawId = act.activityId
            if (rawId.isEmpty() || rawId.contains("|")) {
                act = ktkqClient.probe(activity)
            }
            val id = act.activityId
            if (id.isEmpty() || id.contains("|")) {
                val msg = act.message.ifBlank { "没有签到活动" }
                withContext(Dispatchers.Main) { lastPunch = msg }
                return@accountIo msg
            }
            val punchCode = code.ifBlank { act.signCode }
            val r = ktkqClient.submitSign(id, g.first, g.second, acc, punchCode)
            val rc = r.opt("code")?.toString().orEmpty()
            val msg = r.optString("msg").trim()
            val ok = rc == "0" || rc == "200" || msg.contains("成功") || msg.contains("已签到")
            val out = if (ok) {
                if (msg.isEmpty() || msg == "success") "签到成功 · WGS-84 ${"%.5f".format(g.first)}, ${"%.5f".format(g.second)}" else msg
            } else {
                msg.ifBlank { "签到失败" }
            }
            withContext(Dispatchers.Main) { lastPunch = out }
            out
        }
    }

    suspend fun signInBuildingCenter(activity: SignActivity, code: String = ""): String {
        val fence = CampusFences.forRoom(activity.classroom)
            ?: return "未识别教室楼栋：${activity.classroom}"
        val msg = signIn(
            activity,
            fence.centerLat,
            fence.centerLng,
            source = "demo",
            datum = "wgs84",
            code = code,
            accuracy = 8.0,
        )
        return if (msg.contains(fence.name)) msg else "已用 ${fence.name} 楼中心签到 · $msg"
    }

    suspend fun signInFenceRandom(activity: SignActivity, code: String = ""): String {
        val fence = CampusFences.forRoom(activity.classroom)
            ?: return "未识别教室楼栋：${activity.classroom}"
        val (lat, lng) = fence.randomInside()
        val msg = signIn(
            activity,
            lat,
            lng,
            source = "demo",
            datum = "wgs84",
            code = code,
            accuracy = 8.0,
        )
        return "已用 ${fence.name} 围栏随机点签到 · $msg"
    }

    suspend fun testFencePunch(slot: SignActivity? = null): String {
        if (!demoMode && ktkq.isEmpty()) {
            try {
                refreshKtkq(force = true)
            } catch (e: Exception) {
                if (e is java.util.concurrent.CancellationException) throw e
                RequestCancellation.check()
            }
        }
        val targets = fenceProbeTargets(slot)
        if (targets.isEmpty()) return "课堂考勤没有课班号，无法向服务器预检"
        if (demoMode) return "示例模式不请求服务器"
        val mapFix = try {
            locateCampus()
        } catch (e: Exception) {
            if (e is java.util.concurrent.CancellationException) throw e
            null
        }
        return accountIo {
            ensureKtkq()
            targets.joinToString("\n") { item -> probeFencePoint(item, mapFix) }
        }
    }

    private fun fenceProbeTargets(slot: SignActivity?): List<SignActivity> {
        if (slot != null && CampusFences.forRoom(slot.classroom) != null &&
            slot.teachClassId.isNotBlank() && slot.scheduleId.isNotBlank()
        ) {
            return listOf(slot)
        }
        val byFence = linkedMapOf<String, SignActivity>()
        fun keep(item: SignActivity) {
            if (item.teachClassId.isBlank() || item.scheduleId.isBlank()) return
            val fence = CampusFences.forRoom(item.classroom) ?: return
            byFence.putIfAbsent(fence.name, item)
        }
        ktkq.forEach { keep(it) }
        for (lesson in schedule) {
            val fence = CampusFences.forRoom(lesson.room) ?: continue
            if (fence.name in byFence) continue
            val donor = ktkq.firstOrNull {
                TeacherCache.normKc(it.course) == TeacherCache.normKc(lesson.name) &&
                    it.teachClassId.isNotBlank() && it.scheduleId.isNotBlank()
            } ?: continue
            keep(
                donor.copy(
                    classroom = lesson.room,
                    weekDay = lesson.weekday,
                    startNode = lesson.start,
                    endNode = lesson.end,
                    week = ktkqWeekNum,
                ),
            )
        }
        return byFence.values.toList()
    }

    private fun probeFencePoint(item: SignActivity, mapFix: GeoFix?): String {
        val fence = CampusFences.forRoom(item.classroom) ?: return item.classroom
        val (lat, lng) = fence.randomInside()
        val week = item.week.takeIf { it > 0 } ?: ktkqWeekNum
        val day = if (item.weekDay in 1..7) item.weekDay else 1
        val activityId = item.activityId.takeIf { it.isNotBlank() && !it.contains("|") }.orEmpty()
        return try {
            fun checkAt(pointLat: Double, pointLng: Double) = ktkqClient.checkAllowSign(
                teachClassId = item.teachClassId,
                scheduleId = item.scheduleId,
                week = week,
                weekDay = day,
                startNode = item.startNode.coerceAtLeast(1),
                endNode = item.endNode.coerceAtLeast(item.startNode.coerceAtLeast(1)),
                activityId = activityId,
                lat = pointLat,
                lng = pointLng,
            )
            fun summary(r: org.json.JSONObject): String {
                val data = r.opt("data")
                val status = (data as? org.json.JSONObject)?.optString("status").orEmpty()
                val label = when (status) {
                    "IS_ALLOW" -> "在范围内"
                    "NOT_IN_SCOPE" -> "不在范围"
                    else -> "判定未知"
                }
                return "$label code=${r.opt("code")} msg=${r.optString("msg")} data=${data?.toString()?.take(300) ?: "null"}"
            }
            val raw = checkAt(lat, lng)
            val converted = Geo.wgs84ToGcj02(lat, lng)
            val shifted = checkAt(converted.first, converted.second)
            val deviceCheck = mapFix?.let { fix ->
                val wgs = Geo.gcj02ToWgs84(fix.latitude, fix.longitude)
                val amapResult = checkAt(fix.latitude, fix.longitude)
                val wgsResult = checkAt(wgs.first, wgs.second)
                "；高德原值 ${"%.6f".format(fix.latitude)}, ${"%.6f".format(fix.longitude)} ${summary(amapResult)}；反算WGS-84 ${"%.6f".format(wgs.first)}, ${"%.6f".format(wgs.second)} ${summary(wgsResult)}"
            }.orEmpty()
            val whenText = if (day in 1..7) kWeekdayLabels[day] else ""
            "${item.classroom} ${item.course} $whenText 围栏原值 ${"%.6f".format(lat)}, ${"%.6f".format(lng)} ${summary(raw)}；转GCJ-02 ${"%.6f".format(converted.first)}, ${"%.6f".format(converted.second)} ${summary(shifted)}$deviceCheck"
        } catch (e: Exception) {
            if (e is java.util.concurrent.CancellationException) throw e
            RequestCancellation.check()
            "${item.classroom} 预检失败 ${e.message?.removePrefix("Exception: ").orEmpty()}"
        }
    }

    suspend fun locateCampus(): GeoFix = withContext(Dispatchers.Main) {
        val raw = locator.getFix()
        val g = Geo.campusGcj02(raw.latitude, raw.longitude, raw.source, raw.datum)
        raw.copy(latitude = g.first, longitude = g.second, datum = "gcj02")
    }

    suspend fun dormPunch(useCampusFence: Boolean): String {
        if (demoMode) {
            val (lat, lng) = DemoData.clockFence
            lastPunch = if (useCampusFence) "示例模式：已模拟校内打卡" else "示例模式：已模拟打卡成功 · ${"%.5f".format(lat)}, ${"%.5f".format(lng)}"
            return lastPunch!!
        }
        return accountIo {
            ensureGy()
            // Going through the positions API first primes the apartment host's WAF session.
            // The campus-fence button already did this as part of campusFence(); keep both
            // punch paths on the same request sequence.
            val positions = gy.positions()
            val (lat, lng, address) = if (useCampusFence) {
                val f = positions.firstOrNull()?.let { it.latitude to it.longitude } ?: DemoData.clockFence
                Triple(f.first, f.second, "西南民族大学（校内）")
            } else {
                val fix = withContext(Dispatchers.Main) { locateCampus() }
                Triple(fix.latitude, fix.longitude, "当前位置")
            }
            val g = Geo.campusGcj02(lat, lng, if (useCampusFence) "amap" else "amap", "gcj02")
            val r = gy.punch(g.first, g.second, address, gy.openTaskId())
            val code = r.opt("code")?.toString().orEmpty()
            val msg = r.optString("msg")
            val out = if (code == "0" || code == "200") {
                msg.ifBlank { "打卡成功" } + " · GCJ-02 ${"%.5f".format(g.first)}, ${"%.5f".format(g.second)}"
            } else {
                msg.ifBlank { "打卡失败" }
            }
            withContext(Dispatchers.Main) { lastPunch = out }
            try {
                val rec = gy.records()
                withContext(Dispatchers.Main) {
                    clockRecords.clear()
                    clockRecords.addAll(rec)
                }
            } catch (e: Exception) {
                if (e is java.util.concurrent.CancellationException) throw e
                RequestCancellation.check()
            }
            out
        }
    }

    private suspend fun refreshLiveData() = coroutineScope {
        // Profile enrichment and avatar downloads must not delay the course table.
        launch { refreshSchedule() }
        launch { refreshGrades() }
        launch { refreshProfile() }
    }

    private fun restoreFromDisk() {
        if (prefs.getBoolean("loggedOut", false)) return
        try {
            if (cookieFile.exists()) CampusHttp.importCookies(cookieFile.readText())
            if (!lantu.restoreLocal()) return
            applyLantu()
            restoreCachedSchedule()
            loggedIn = true
            demoMode = false
            requests.launch("startup") { refreshLiveData() }
        } catch (e: Exception) {
            if (e is java.util.concurrent.CancellationException) throw e
            RequestCancellation.check()
            Log.e("swun", "restore", e)
        }
    }

    private suspend fun <T> accountIo(
        key: String? = null,
        block: suspend CoroutineScope.() -> T,
    ): T = requests.run(key) {
        if (!loggedIn || demoMode) throw CancellationException("账号未登录")
        withContext(Dispatchers.IO, block)
    }

    override fun onCleared() {
        requests.close()
        super.onCleared()
    }

    private fun applyLantu() {
        profile = lantu.profile().merge(profile)
        if (profile.studentId.isNotEmpty()) studentId = profile.studentId
        displayName = profile.name.ifBlank { studentId.ifBlank { "同学" } }
        gy.username = studentId.ifEmpty { null }
        val id = studentId.ifBlank { accounts.currentId }
        if (id.isNotEmpty()) accounts.upsert(id, profile.name)
        slotPicks.load(id)
        teachers.load(id)
        bindAvatars(id)
    }

    private fun restoreCachedSchedule() {
        val account = studentId.ifBlank { accounts.currentId }.ifBlank { creds.username }
        val cached = schedules.load(account, currentSchoolTerm()) ?: return
        schedule.clear()
        schedule.addAll(cached.lessons)
        curWeek = cached.weekOn(java.time.LocalDate.now())
        totalWeek = cached.totalWeeks
        week1Monday = cached.firstMonday
    }

    private suspend fun applySchedule(
        account: String,
        lessons: List<Lesson>,
        week: Int,
        total: Int,
        firstMonday: java.time.LocalDate?,
    ) {
        val safeTotal = maxOf(total, lessons.maxOfOrNull { it.weeks.length } ?: 1).coerceIn(1, 100)
        val safeWeek = week.coerceIn(1, safeTotal)
        withContext(Dispatchers.Main) {
            schedule.clear()
            schedule.addAll(lessons)
            curWeek = safeWeek
            totalWeek = safeTotal
            week1Monday = firstMonday
            loadHint = null
        }
        try {
            requests.commit {
                schedules.save(
                    account,
                    CachedSchedule(
                        currentSchoolTerm(),
                        lessons,
                        safeWeek,
                        safeTotal,
                        firstMonday,
                        System.currentTimeMillis(),
                    ),
                )
            }
        } catch (e: Exception) {
            if (e is java.util.concurrent.CancellationException) throw e
            RequestCancellation.check()
            Log.w("swun", "课表缓存保存失败", e)
        }
    }

    private fun inferredFirstMonday(week: Int): java.time.LocalDate {
        val today = java.time.LocalDate.now()
        val thisMonday = today.minusDays((today.dayOfWeek.value - 1).toLong())
        return thisMonday.minusWeeks((week.coerceAtLeast(1) - 1).toLong())
    }

    private suspend fun fetchRemoteAvatar(url: String) {
        val owner = accountKey()
        val file = remoteAvatarFileFor(owner)
        try {
            val req = okhttp3.Request.Builder().url(url).header("Accept", "image/*").build()
            val ok = CampusHttp.imageClient.newCall(req).execute().use { r ->
                if (r.code !in 200..399) return@use false
                val bytes = r.body?.bytes() ?: return@use false
                if (bytes.size < 64) return@use false
                file.parentFile?.mkdirs()
                requests.commit { file.writeBytes(bytes) }
                true
            }
            if (!ok) return
            withContext(Dispatchers.Main) {
                if (accountKey() != owner) return@withContext
                remoteAvatarPath = file.absolutePath
                avatarEpoch++
            }
        } catch (e: Exception) {
            if (e is java.util.concurrent.CancellationException) throw e
            RequestCancellation.check()
            Log.e("swun", "avatar", e)
        }
    }

    private fun persistSession(user: String, pass: String) = requests.commit {
        creds.username = user
        creds.password = pass
        accounts.upsert(user, lantu.profile().name, pass)
        cookieFile.writeText(CampusHttp.exportCookies())
        prefs.edit().putBoolean("loggedOut", false).commit()
    }

    private fun ensureCas() = synchronized(casLock) {
        RequestCancellation.check()
        if (cas.hasTgt() || cas.tgtAlive()) return@synchronized
        val id = studentId.ifBlank { accounts.currentId }.ifBlank { creds.username }
        val pwd = accounts.password(id).ifBlank { creds.password }
        if (id.isBlank() || pwd.isBlank()) error("统一身份已过期，请重新登录")
        cas.login(id, pwd)
        requests.commit { cookieFile.writeText(CampusHttp.exportCookies()) }
    }

    private fun <T> withLantuSession(block: () -> T): T = synchronized(lantuLock) {
        RequestCancellation.check()
        try {
            block()
        } catch (_: LantuSessionExpiredException) {
            RequestCancellation.check()
            val id = studentId.ifBlank { accounts.currentId }.ifBlank { creds.username }
            val pwd = accounts.password(id).ifBlank { creds.password }
            if (id.isBlank() || pwd.isBlank()) throw LantuSessionExpiredException()
            SwunLog.w("蓝图会话已过期，使用保存账号重新登录")
            lantu.login(id, pwd)
            block()
        }
    }

    private fun ktkqTokenKey() = "ktkqToken_${studentId.ifBlank { accounts.currentId }}"
    private fun ktkqTermKey() = "ktkqXnxqdm_${studentId.ifBlank { accounts.currentId }}"
    private fun ktkqTermNameKey() = "ktkqXnxqmc_${studentId.ifBlank { accounts.currentId }}"

    private fun ensureKtkq() = synchronized(ktkqLock) {
        RequestCancellation.check()
        ensureCas()
        ktkqClient.attachCas(cas)
        if (ktkqClient.token.isNullOrEmpty()) {
            val saved = prefs.getString(ktkqTokenKey(), "").orEmpty()
            if (saved.length > 16 && !ktkqTokenExpired(saved)) ktkqClient.token = saved
            ktkqClient.knownXnxqdm = prefs.getString(ktkqTermKey(), "").orEmpty()
            ktkqClient.knownXnxqmc = prefs.getString(ktkqTermNameKey(), "").orEmpty()
        }
        if (ktkqClient.token.isNullOrEmpty()) {
            ktkqClient.loginWithCas(cas)
            requests.commit {
                prefs.edit().putString(ktkqTokenKey(), ktkqClient.token.orEmpty()).apply()
            }
        }
    }

    private fun gyTokenKey() = "gyToken_${studentId.ifBlank { accounts.currentId }}"

    private fun ensureGy() = synchronized(gyLock) {
        RequestCancellation.check()
        ensureCas()
        gy.attachCas(cas)
        gy.username = studentId.ifEmpty { gy.username }
        if (gy.token.isNullOrEmpty()) {
            val saved = prefs.getString(gyTokenKey(), "").orEmpty()
            if (saved.length > 8) gy.token = saved
        }
        if (gy.token.isNullOrEmpty()) gy.loginWithCas(cas)
    }

    private data class LeaveSnapshot(
        val summary: LeaveSummary,
        val student: LeaveStudentInfo,
        val rule: LeaveRule,
        val records: List<LeaveRecord>,
        val types: List<LeaveOption>,
        val specificTypes: List<LeaveOption>,
    )

    private fun readLeaveSnapshot() = LeaveSnapshot(
        summary = studentClient.leaveSummary(),
        student = studentClient.studentInfo(),
        rule = studentClient.leaveRule(),
        records = studentClient.leaveRecords(),
        types = studentClient.leaveTypes(),
        specificTypes = studentClient.specificLeaveTypes(),
    )

    private fun applyLeaveSnapshot(value: LeaveSnapshot) {
        leaveSummary = value.summary
        leaveStudentInfo = value.student
        leaveRule = value.rule
        leaveRecords.clear(); leaveRecords.addAll(value.records)
        leaveTypes.clear(); leaveTypes.addAll(value.types)
        leaveSpecificTypes.clear(); leaveSpecificTypes.addAll(value.specificTypes)
        leaveError = null
    }

    private fun ensureStudent() = synchronized(studentLock) {
        RequestCancellation.check()
        ensureCas()
        studentClient.attachCas(cas)
        studentClient.ensureReady()
    }

    private fun <T> withStudentSession(block: () -> T): T = synchronized(studentLock) {
        ensureStudent()
        try {
            block()
        } catch (_: StudentSessionExpiredException) {
            studentClient.reset()
            ensureStudent()
            block()
        }
    }

    private data class LeaveUpload(
        val bytes: ByteArray,
        val mimeType: String,
        val fileName: String,
    )

    private fun prepareLeaveImage(uri: Uri): LeaveUpload {
        val resolver = getApplication<Application>().contentResolver
        val original = resolver.openInputStream(uri)?.use { it.readBytes() } ?: error("无法读取图片")
        if (original.size > 10 * 1024 * 1024) error("单张图片不能超过 10 MB")
        val declared = resolver.getType(uri).orEmpty().lowercase()
        if (declared.isNotEmpty() && declared !in setOf("image/jpeg", "image/jpg", "image/png")) {
            error("证明材料仅支持 JPG 或 PNG")
        }
        val baseName = uri.lastPathSegment?.substringAfterLast('/')?.takeLast(80)
            ?.replace(Regex("[^0-9A-Za-z._-]"), "_")
            ?.takeIf { it.isNotBlank() }
            ?: "leave_${System.currentTimeMillis()}.jpg"
        if (original.size <= 900 * 1024) {
            val mime = declared.ifBlank { "image/jpeg" }
            return LeaveUpload(original, mime, baseName)
        }
        val bitmap = BitmapFactory.decodeByteArray(original, 0, original.size) ?: error("图片解码失败")
        val maxSide = maxOf(bitmap.width, bitmap.height)
        val scaled = if (maxSide > 2160) {
            val ratio = 2160.0 / maxSide
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * ratio).toInt().coerceAtLeast(1),
                (bitmap.height * ratio).toInt().coerceAtLeast(1),
                true,
            )
        } else bitmap
        var result = ByteArray(0)
        for (quality in listOf(88, 80, 72, 64, 56, 48)) {
            val output = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, output)
            result = output.toByteArray()
            if (result.size <= 900 * 1024) break
        }
        if (scaled !== bitmap) scaled.recycle()
        bitmap.recycle()
        if (result.isEmpty()) error("图片压缩失败")
        return LeaveUpload(result, "image/jpeg", baseName.substringBeforeLast('.', baseName) + ".jpg")
    }

    private fun persistCookieFile() {
        try {
            requests.commit { cookieFile.writeText(CampusHttp.exportCookies()) }
        } catch (e: Exception) {
            if (e is java.util.concurrent.CancellationException) throw e
            RequestCancellation.check()
        }
    }

    private fun publicError(e: Exception): String {
        val raw = e.message
            ?.removePrefix("Exception: ")
            ?.removePrefix("java.lang.Exception: ")
            ?.trim()
            .orEmpty()
        val lower = raw.lowercase()
        return when {
            raw.isBlank() -> "请求失败，请稍后重试"
            "failed to fetch" in lower || "network request failed" in lower -> "网络连接失败，请稍后重试"
            "unable to resolve host" in lower || "unknownhost" in lower -> "当前网络无法连接学校服务"
            "timeout" in lower || "timed out" in lower -> "请求超时，请稍后重试"
            "connection refused" in lower || "connect failed" in lower -> "学校服务暂时无法连接"
            raw.startsWith("<html", ignoreCase = true) || raw.startsWith("<!doctype", ignoreCase = true) ->
                "学校服务返回异常，请稍后重试"
            else -> raw
        }
    }
}
