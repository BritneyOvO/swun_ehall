package cn.edu.swun.swun_ehall.data.session

import android.app.Application
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
import cn.edu.swun.swun_ehall.data.http.KtkqClient
import cn.edu.swun.swun_ehall.data.http.LantuClient
import cn.edu.swun.swun_ehall.data.http.RsGateway
import cn.edu.swun.swun_ehall.data.http.YktClient
import cn.edu.swun.swun_ehall.data.locate.Locator
import cn.edu.swun.swun_ehall.data.http.xkOfficialSxbj
import cn.edu.swun.swun_ehall.data.http.xkSaveCourseBody
import cn.edu.swun.swun_ehall.data.http.xkSubmitAlert
import cn.edu.swun.swun_ehall.data.model.AppRelease
import cn.edu.swun.swun_ehall.data.model.ClockRecord
import cn.edu.swun.swun_ehall.data.model.CreditProgress
import cn.edu.swun.swun_ehall.data.model.Exam
import cn.edu.swun.swun_ehall.data.model.GeoFix
import cn.edu.swun.swun_ehall.data.model.Grade
import cn.edu.swun.swun_ehall.data.model.Lesson
import cn.edu.swun.swun_ehall.data.model.Profile
import cn.edu.swun.swun_ehall.data.model.SchoolTerm
import cn.edu.swun.swun_ehall.data.http.decodeKtkqNav
import cn.edu.swun.swun_ehall.data.http.encodeKtkqNav
import cn.edu.swun.swun_ehall.data.http.matchKtkqSlot
import cn.edu.swun.swun_ehall.data.model.KtkqCourse
import cn.edu.swun.swun_ehall.data.model.KtkqWeek
import cn.edu.swun.swun_ehall.data.model.SignActivity
import cn.edu.swun.swun_ehall.data.model.ktkqSlotFromLesson
import cn.edu.swun.swun_ehall.data.model.slotKey
import cn.edu.swun.swun_ehall.data.model.XkCourse
import cn.edu.swun.swun_ehall.data.model.XkRound
import cn.edu.swun.swun_ehall.data.model.YktBill
import cn.edu.swun.swun_ehall.data.update.UpdateClient
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Session(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("swun_session", 0)
    private val creds = CredentialStore(app)
    val accounts = AccountStore(app, creds)
    val slotPicks = SlotChoiceStore(prefs)
    private val teachers = TeacherCache(File(app.filesDir, "kb_teachers.json"))
    private val locator = Locator(app)
    private val rs = RsGateway(app)
    private val cas = CasClient()
    private val ehall = EhallClient()
    private val lantu = LantuClient(File(app.filesDir, "lantu.json"))
    private val jwxt = JwxtClient()
    private val ktkqClient = KtkqClient(rs)
    private val gy = GyClient(rs)
    private val yktClient = YktClient(rs)
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
    private var lastUpdateCheckAt = 0L
    var lastPunch by mutableStateOf<String?>(null)
    var yktQr by mutableStateOf<String?>(null)
    var yktError by mutableStateOf<String?>(null)
    var loadHint by mutableStateOf<String?>(null)
    var curWeek by mutableStateOf(1)
    var totalWeek by mutableStateOf(16)
    var localAvatarPath by mutableStateOf<String?>(null)
    var remoteAvatarPath by mutableStateOf<String?>(null)
    var avatarEpoch by mutableIntStateOf(0)
    var pendingCropPath by mutableStateOf<String?>(null)
    private val remoteAvatarFile = File(app.filesDir, "remote_avatar")
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
    val clockRecords = mutableStateListOf<ClockRecord>()
    val xkRounds = mutableStateListOf<XkRound>()
    val xkCourses = mutableStateListOf<XkCourse>()
    private val avatarFile = File(app.filesDir, "avatar.png")

    init {
        accounts.load()
        studentId = accounts.currentId.ifBlank { creds.username }
        slotPicks.load(studentId)
        teachers.load(studentId)
        if (avatarFile.exists()) localAvatarPath = avatarFile.absolutePath
        if (remoteAvatarFile.exists()) remoteAvatarPath = remoteAvatarFile.absolutePath
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
            } catch (_: Exception) {
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
        demoMode = true
        loggedIn = true
        error = null
        loadHint = null
        profile = DemoData.profile
        displayName = profile.name
        studentId = profile.studentId
        schedule.clear(); schedule.addAll(DemoData.schedule)
        grades.clear(); grades.addAll(DemoData.grades)
        exams.clear(); exams.addAll(DemoData.exams)
        applyKtkqWeek(DemoData.ktkqWeek)
        yktBills.clear(); yktBills.addAll(DemoData.yktBills)
        yktBalance = DemoData.yktBalance
        yktQr = "SWUN-DEMO-YKT-${studentId.ifEmpty { "202430000000" }}"
        clockRecords.clear(); clockRecords.addAll(DemoData.clockRecords)
        credits = DemoData.credits
        xkRounds.clear(); xkRounds.addAll(DemoData.xkRounds)
        xkCourses.clear(); xkCourses.addAll(DemoData.xkCourses)
        xkProfile = DemoData.xkProfile
        curWeek = 1
        totalWeek = 16
    }

    suspend fun login(user: String, pass: String) {
        if (user.isBlank() || pass.isBlank()) {
            error = "请输入学号和密码"
            return
        }
        busy = true
        error = null
        demoMode = false
        try {
            withContext(Dispatchers.IO) {
                CampusHttp.cookies.clear()
                lantu.clear()
                lantu.login(user.trim(), pass)
                try {
                    cas.login(user.trim(), pass)
                } catch (e: Exception) {
                    lantu.clear()
                    throw Exception("统一身份登录失败，请重试")
                }
                try {
                    val info = lantu.getUserInfo()
                    info.optJSONObject("userBaseInfo")?.let { lantu.userBase = it }
                    info.optJSONObject("userLoginInfo")?.let { lantu.userLogin = it }
                } catch (_: Exception) {
                }
                persistSession(user.trim(), pass)
            }
            applyLantu()
            loggedIn = true
            viewModelScope.launch(Dispatchers.IO) { refreshLiveData() }
        } catch (e: Exception) {
            loggedIn = false
            error = publicError(e)
            Log.e("swun", "login", e)
        } finally {
            busy = false
        }
    }

    fun logout() {
        loggedIn = false
        demoMode = false
        error = null
        lastPunch = null
        loadHint = null
        yktQr = null
        displayName = "同学"
        profile = Profile()
        schedule.clear(); grades.clear(); exams.clear(); ktkq.clear(); ktkqCourses.clear(); yktBills.clear(); clockRecords.clear()
        ktkqWeekNum = 1
        ktkqXnxqmc = ""
        pendingKtkqSlot = null
        xkRounds.clear(); xkCourses.clear()
        yktBalance = null
        credits = null
        yktQr = null
        yktError = null
        viewModelScope.launch(Dispatchers.IO) {
            try { lantu.clear() } catch (_: Exception) {}
            CampusHttp.cookies.clear()
            cookieFile.delete()
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
        accounts.remove(id)
        if (wasCurrent) logout()
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
        avatarFile.writeBytes(bytes)
        localAvatarPath = avatarFile.absolutePath
        avatarEpoch++
    }

    fun clearLocalAvatar() {
        if (avatarFile.exists()) avatarFile.delete()
        localAvatarPath = null
        avatarEpoch++
    }

    suspend fun refreshSchedule() {
        if (demoMode) return
        withContext(Dispatchers.IO) {
            try {
                val course = lantu.getCourse()
                var lessons = teachers.apply(lantu.mapLessons(course))
                val week = course.optString("curWeek").toIntOrNull() ?: 1
                val total = course.optString("totalWeek").toIntOrNull() ?: 16
                withContext(Dispatchers.Main) {
                    schedule.clear()
                    schedule.addAll(lessons)
                    curWeek = week.coerceAtLeast(1)
                    totalWeek = maxOf(total, lessons.maxOfOrNull { it.weeks.length } ?: 16)
                }
                try {
                    ensureCas()
                    jwxt.attachCas(cas)
                    jwxt.ensureSession()
                    if (!jwxt.portalClosed) {
                        teachers.fillFromJwxt(jwxt.scheduleKb())
                        lessons = teachers.apply(lessons)
                        withContext(Dispatchers.Main) {
                            schedule.clear()
                            schedule.addAll(lessons)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("swun", "kb teachers", e)
                }
            } catch (e: Exception) {
                Log.e("swun", "schedule", e)
                withContext(Dispatchers.Main) { loadHint = publicError(e) }
            }
        }
    }

    suspend fun refreshGrades(term: SchoolTerm = SchoolTerm("", "")) {
        if (demoMode) return
        withContext(Dispatchers.IO) {
            try {
                ensureCas()
                jwxt.attachCas(cas)
                jwxt.ensureSession()
                if (jwxt.portalClosed) {
                    withContext(Dispatchers.Main) { loadHint = "教务夜间关闭或不在服务时间" }
                    return@withContext
                }
                val items = jwxt.grades(term.xnm, term.xqm)
                withContext(Dispatchers.Main) {
                    grades.clear()
                    grades.addAll(items)
                }
            } catch (e: Exception) {
                Log.e("swun", "grades", e)
                withContext(Dispatchers.Main) { loadHint = publicError(e) }
            }
        }
    }

    suspend fun refreshExams() {
        if (demoMode) {
            exams.clear(); exams.addAll(DemoData.exams)
            return
        }
        withContext(Dispatchers.IO) {
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
        withContext(Dispatchers.IO) {
            try {
                if (grades.isEmpty()) refreshGrades()
                val snapshot = withContext(Dispatchers.Main) { grades.toList() }
                val p = jwxt.creditFromGrades(snapshot)
                withContext(Dispatchers.Main) { credits = p }
            } catch (e: Exception) {
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
        withContext(Dispatchers.IO) {
            try {
                ensureCas()
                jwxt.attachCas(cas)
                jwxt.ensureSession()
                val (rounds, profile) = jwxt.selectionEntry()
                withContext(Dispatchers.Main) {
                    xkRounds.clear()
                    xkRounds.addAll(rounds)
                    xkProfile = profile
                    if (rounds.isEmpty()) loadHint = "当前没有开放的选课轮次"
                }
            } catch (e: Exception) {
                Log.e("swun", "xk", e)
                withContext(Dispatchers.Main) { loadHint = publicError(e) }
            }
        }
    }

    suspend fun loadXkCourses(round: XkRound, keyword: String = "") {
        if (demoMode) {
            xkCourses.clear()
            xkCourses.addAll(DemoData.xkCourses.filter { keyword.isEmpty() || it.name.contains(keyword) })
            return
        }
        withContext(Dispatchers.IO) {
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
                Log.e("swun", "xk courses", e)
                withContext(Dispatchers.Main) { loadHint = publicError(e) }
            }
        }
    }

    suspend fun loadXkClasses(round: XkRound, course: XkCourse): List<XkCourse> {
        if (demoMode) return listOf(course)
        return withContext(Dispatchers.IO) {
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
        return withContext(Dispatchers.IO) {
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
        withContext(Dispatchers.IO) {
            try {
                if (lantu.isLoggedIn) {
                    val info = lantu.getUserInfo()
                    info.optJSONObject("userBaseInfo")?.let { lantu.userBase = it }
                    info.optJSONObject("userLoginInfo")?.let { lantu.userLogin = it }
                }
            } catch (e: Exception) {
                Log.e("swun", "profile lantu", e)
            }
            var next = if (lantu.isLoggedIn) lantu.profile() else Profile(studentId = studentId)
            try {
                ensureCas()
                next = next.merge(ehall.profile(cas))
            } catch (e: Exception) {
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

    suspend fun refreshKtkq() {
        if (demoMode) {
            applyKtkqWeek(DemoData.ktkqWeek)
            return
        }
        withContext(Dispatchers.IO) {
            try {
                ensureKtkq()
                val week = ktkqClient.weekCourses(refresh = true)
                val items = week.flatten()
                teachers.fillFromKtkq(items, week.week)
                val patched = teachers.apply(schedule.toList())
                withContext(Dispatchers.Main) {
                    loadHint = null
                    applyKtkqWeek(week)
                    if (patched.any { it.teacher.isNotEmpty() }) {
                        schedule.clear()
                        schedule.addAll(patched)
                    }
                }
            } catch (e: Exception) {
                Log.e("swun", "ktkq", e)
                withContext(Dispatchers.Main) { loadHint = publicError(e) }
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
        return withContext(Dispatchers.IO) {
            ensureKtkq()
            ktkqClient.signForLesson(slot, week = slot.week.takeIf { it > 0 } ?: ktkqWeekNum, refresh = refresh)
        }
    }

    suspend fun refreshYkt() {
        if (demoMode) {
            yktBills.clear(); yktBills.addAll(DemoData.yktBills)
            yktBalance = DemoData.yktBalance
            yktQr = "SWUN-DEMO-YKT-$studentId"
            yktError = null
            return
        }
        withContext(Dispatchers.IO) {
            withContext(Dispatchers.Main) { yktError = null }
            try {
                lantu.cardBalanceYuan()?.let { bal ->
                    withContext(Dispatchers.Main) { yktBalance = bal }
                }
            } catch (_: Exception) {
            }
            try {
                ensureCas()
                val sid = studentId.ifBlank { creds.username }
                val school = "${lantu.schoolId}"
                var lastErr: String? = null
                try {
                    val qr = yktClient.fetchQr(sid, school)
                    withContext(Dispatchers.Main) { yktQr = qr }
                } catch (e: Exception) {
                    Log.e("swun", "ykt qr", e)
                    lastErr = publicError(e)
                }
                try {
                    val bal = yktClient.fetchBalance(sid, school)
                    if (bal != null) withContext(Dispatchers.Main) { yktBalance = bal }
                } catch (e: Exception) {
                    Log.e("swun", "ykt bal", e)
                }
                try {
                    val (bal, bills) = yktClient.fetchLedger(sid, school)
                    withContext(Dispatchers.Main) {
                        if (bal != null) yktBalance = bal
                        yktBills.clear()
                        yktBills.addAll(bills)
                    }
                } catch (e: Exception) {
                    Log.e("swun", "ykt bills", e)
                    lastErr = lastErr ?: publicError(e)
                }
                if (lastErr != null) withContext(Dispatchers.Main) { yktError = lastErr; loadHint = lastErr }
            } catch (e: Exception) {
                Log.e("swun", "ykt", e)
                withContext(Dispatchers.Main) {
                    yktError = publicError(e)
                    loadHint = yktError
                }
            }
        }
    }

    suspend fun refreshClock() {
        if (demoMode) {
            clockRecords.clear(); clockRecords.addAll(DemoData.clockRecords)
            return
        }
        withContext(Dispatchers.IO) {
            try {
                ensureGy()
                val rec = gy.records()
                withContext(Dispatchers.Main) {
                    clockRecords.clear()
                    clockRecords.addAll(rec)
                }
            } catch (e: Exception) {
                Log.e("swun", "clock", e)
                withContext(Dispatchers.Main) { loadHint = publicError(e) }
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
        val g = Geo.campusGcj02(lat, lng, source, datum)
        val acc = if (accuracy.isFinite()) accuracy.roundToInt().coerceAtLeast(0) else 8
        if (demoMode) {
            lastPunch = "示例模式：已模拟签到 · GCJ-02 ${"%.5f".format(g.first)}, ${"%.5f".format(g.second)}"
            return lastPunch!!
        }
        return withContext(Dispatchers.IO) {
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
                return@withContext msg
            }
            val punchCode = code.ifBlank { act.signCode }
            val r = ktkqClient.submitSign(id, g.first, g.second, acc, punchCode)
            val rc = r.opt("code")?.toString().orEmpty()
            val msg = r.optString("msg").trim()
            val ok = rc == "0" || rc == "200" || msg.contains("成功") || msg.contains("已签到")
            val out = if (ok) {
                if (msg.isEmpty() || msg == "success") "签到成功 · GCJ-02 ${"%.5f".format(g.first)}, ${"%.5f".format(g.second)}" else msg
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
            datum = "gcj02",
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
            datum = "gcj02",
            code = code,
            accuracy = 8.0,
        )
        return "已用 ${fence.name} 围栏随机点签到 · $msg"
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
        return withContext(Dispatchers.IO) {
            ensureGy()
            val (lat, lng, address) = if (useCampusFence) {
                val f = gy.campusFence() ?: DemoData.clockFence
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
            } catch (_: Exception) {
            }
            out
        }
    }

    private suspend fun refreshLiveData() {
        try { refreshProfile() } catch (_: Exception) {}
        refreshSchedule()
        try { refreshGrades() } catch (_: Exception) {}
        try {
            Thread.sleep(600)
            rs.warmup("https://ktkq.swun.edu.cn/")
            rs.warmup("https://gyglxt.swun.edu.cn/")
            rs.warmup("https://ykth5.swun.edu.cn/")
        } catch (_: Exception) {
        }
    }

    private fun restoreFromDisk() {
        try {
            if (cookieFile.exists()) CampusHttp.importCookies(cookieFile.readText())
            if (!lantu.restoreLocal()) return
            applyLantu()
            loggedIn = true
            demoMode = false
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    lantu.refreshUser()
                    withContext(Dispatchers.Main) { applyLantu() }
                } catch (e: Exception) {
                    Log.e("swun", "restore user", e)
                }
                try {
                    refreshLiveData()
                } catch (e: Exception) {
                    Log.e("swun", "restore data", e)
                }
            }
        } catch (e: Exception) {
            Log.e("swun", "restore", e)
        }
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
    }

    private suspend fun fetchRemoteAvatar(url: String) {
        try {
            val req = okhttp3.Request.Builder().url(url).header("Accept", "image/*").build()
            val ok = CampusHttp.followClient.newCall(req).execute().use { r ->
                if (r.code !in 200..399) return@use false
                val bytes = r.body?.bytes() ?: return@use false
                if (bytes.size < 64) return@use false
                remoteAvatarFile.writeBytes(bytes)
                true
            }
            if (!ok) return
            withContext(Dispatchers.Main) {
                remoteAvatarPath = remoteAvatarFile.absolutePath
                avatarEpoch++
            }
        } catch (e: Exception) {
            Log.e("swun", "avatar", e)
        }
    }

    private fun persistSession(user: String, pass: String) {
        creds.username = user
        creds.password = pass
        accounts.upsert(user, lantu.profile().name, pass)
        cookieFile.writeText(CampusHttp.exportCookies())
    }

    private fun ensureCas() {
        if (cas.hasTgt() || cas.tgtAlive()) return
        val id = studentId.ifBlank { accounts.currentId }.ifBlank { creds.username }
        val pwd = accounts.password(id).ifBlank { creds.password }
        if (id.isBlank() || pwd.isBlank()) error("统一身份已过期，请重新登录")
        cas.login(id, pwd)
        cookieFile.writeText(CampusHttp.exportCookies())
    }

    private fun ensureKtkq() {
        ensureCas()
        ktkqClient.attachCas(cas)
        if (ktkqClient.token.isNullOrEmpty()) ktkqClient.loginWithCas(cas)
    }

    private fun ensureGy() {
        ensureCas()
        gy.attachCas(cas)
        gy.username = studentId.ifEmpty { gy.username }
        if (gy.token.isNullOrEmpty()) gy.loginWithCas(cas)
    }

    private fun publicError(e: Exception): String =
        e.message?.removePrefix("Exception: ")?.removePrefix("java.lang.Exception: ") ?: "请求失败"
}
