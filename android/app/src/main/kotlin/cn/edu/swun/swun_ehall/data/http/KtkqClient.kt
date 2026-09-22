package cn.edu.swun.swun_ehall.data.http

import android.util.Base64
import android.util.Log
import cn.edu.swun.swun_ehall.data.model.KtkqActivity
import cn.edu.swun.swun_ehall.data.model.KtkqCourse
import cn.edu.swun.swun_ehall.data.model.KtkqHistory
import cn.edu.swun.swun_ehall.data.model.KtkqWeek
import cn.edu.swun.swun_ehall.data.model.Lesson
import cn.edu.swun.swun_ehall.data.model.SignActivity
import cn.edu.swun.swun_ehall.data.model.kWeekdayLabels
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Calendar
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject

const val K_KTKQ = "https://ktkq.swun.edu.cn"
const val K_KTKQ_SERVICE = "$K_KTKQ/jwmobile/auth/index"

class KtkqClient(private val rs: RsGateway) {
    var token: String? = null
    private var cas: CasClient? = null
    private var weekCacheKey: String? = null
    private var weekCache: JSONObject? = null

    fun attachCas(c: CasClient) {
        cas = c
    }

    fun loginWithCas(cas: CasClient) {
        this.cas = cas
        var lastErr: Exception? = null
        repeat(2) { attempt ->
            try {
                loginOnce(cas)
                return
            } catch (e: Exception) {
                lastErr = e
                val msg = e.message.orEmpty()
                if (msg.contains("夜间关闭") || msg.contains("被踢回 CAS")) throw e
                token = null
                Log.w("swun", "[ktkq] login attempt ${attempt + 1} $e")
            }
        }
        throw lastErr ?: Exception("未拿到课堂考勤 token")
    }

    private fun loginOnce(cas: CasClient) {
        var url = cas.ticketFor(K_KTKQ_SERVICE)
        var last = url
        var webTried = false
        for (i in 0 until 12) {
            val r = CampusHttp.get(url)
            val setCookies = r.headers("Set-Cookie")
            val loc = CampusHttp.location(r)
            val body = CampusHttp.text(r)
            last = r.request.url.toString()
            if (CampusHttp.looksNightClosed(r.code, body)) error("课堂考勤夜间关闭或不在服务时间")
            token = parseKtkqToken(body = body, urls = listOf(loc, last, url), setCookies = setCookies)
            if (!token.isNullOrEmpty()) {
                Log.d("swun", "[ktkq] token from hop $i len=${token!!.length}")
                break
            }
            if (looksLikeRuishu(r.code, body)) {
                token = tokenViaWeb(url)
                webTried = true
                break
            }
            if (!CampusHttp.isRedirect(r) || loc.isEmpty()) break
            url = CampusHttp.absUrl(K_KTKQ, loc)
            last = url
            if (url.contains("authserver") && !url.contains("ticket=")) {
                error("课堂考勤登录失败: 被踢回 CAS")
            }
        }
        if (token.isNullOrEmpty()) token = tokenFromCookies()
        if (token.isNullOrEmpty() && !webTried) {
            val nav = if (last.contains("ticket=")) last else cas.ticketFor(K_KTKQ_SERVICE)
            token = tokenViaWeb(nav)
            webTried = true
        }
        if (token.isNullOrEmpty()) error("未拿到课堂考勤 token")
        persistToken(token!!)
        if (!webTried) syncWebSession() else writeLsToken()
    }

    fun weekCourses(week: Int? = null, refresh: Boolean = false): KtkqWeek {
        var data = JSONObject()
        var xnxqdm = ""
        try {
            val st = get("/jwmobile/biz/v410/schedule/school/time")
            data = jsonDataOf(st)
            xnxqdm = pickKtkqXnxqdm(data, emptyList())
            Log.d("swun", "[ktkq] schoolTime code=${st.opt("code")} keys=${data.keys().asSequence().toList()}")
        } catch (e: Exception) {
            Log.w("swun", "[ktkq] schoolTime $e")
        }
        if (xnxqdm.isEmpty()) {
            try {
                val terms = get("/jwmobile/biz/v410/schedule/termList")
                val rows = termRows(terms.opt("data"))
                xnxqdm = pickKtkqXnxqdm(data, rows)
                if (xnxqdm.isNotEmpty()) {
                    try {
                        val st = get("/jwmobile/biz/v410/schedule/school/time", mapOf("xnxqdm" to xnxqdm))
                        data = jsonDataOf(st)
                        val again = pickKtkqXnxqdm(data, emptyList())
                        if (again.isNotEmpty()) xnxqdm = again
                    } catch (e: Exception) {
                        Log.w("swun", "[ktkq] schoolTime($xnxqdm) $e")
                    }
                }
            } catch (e: Exception) {
                Log.w("swun", "[ktkq] termList $e")
            }
        }
        if (xnxqdm.isEmpty()) {
            xnxqdm = ktkqXnxqdmNow()
            Log.d("swun", "[ktkq] calendar xnxqdm=$xnxqdm")
        }
        if (data.length() == 0 && xnxqdm.isNotEmpty()) {
            try {
                data = jsonDataOf(get("/jwmobile/biz/v410/schedule/school/time", mapOf("xnxqdm" to xnxqdm)))
            } catch (e: Exception) {
                Log.w("swun", "[ktkq] schoolTime($xnxqdm) $e")
            }
        }
        val skzc = week ?: kqInt(data, "todayWeekNum", kqInt(data, "skzc", 1))
        val key = "$xnxqdm|$skzc"
        val cached = weekCache
        if (!refresh && cached != null && weekCacheKey == key) {
            return ktkqWeekOf(cached, xnxqdm, skzc, data)
        }
        fun pull() = post(
            "/jwmobile/biz/v410/schedule/querySchedule",
            JSONObject().put("xnxqdm", xnxqdm).put("skzc", skzc),
        )
        val raw = try {
            pull()
        } catch (e: Exception) {
            Log.w("swun", "[ktkq] querySchedule $e")
            if (ktkqLooksAuthError(e)) throw e
            Thread.sleep(400)
            pull()
        }
        val out = normalizeKtkqWeek(raw)
        val n = kqObjList(out.opt("data")).size
        Log.d("swun", "[ktkq] week $xnxqdm skzc=$skzc courses=$n")
        if (n > 0) {
            weekCacheKey = key
            weekCache = out
        }
        return ktkqWeekOf(out, xnxqdm, skzc, data)
    }

    fun signForLesson(lesson: SignActivity, week: Int? = null, refresh: Boolean = false): SignActivity {
        var weekData = try {
            weekCourses(week = week, refresh = refresh)
        } catch (e: Exception) {
            Log.w("swun", "[ktkq] week for sign $e")
            if (ktkqLooksAuthError(e)) throw e
            KtkqWeek()
        }
        if (weekData.flatten().isEmpty()) {
            try {
                weekData = weekCourses(week = week, refresh = true)
            } catch (e: Exception) {
                Log.w("swun", "[ktkq] week retry $e")
                if (ktkqLooksAuthError(e)) throw e
            }
        }
        val weekNum = week ?: weekData.week
        val slots = weekData.flatten()
        var hit = lesson
        if (ktkqIdsOf(hit).incomplete) {
            matchKtkqSlot(hit.toLesson(), slots)?.let { hit = mergeKtkqSlot(hit, it) }
        }
        if (ktkqIdsOf(hit).incomplete) {
            fillKtkqIds(hit, slots)?.let { hit = it }
        }
        if (hit.startNode <= 0) hit = hit.copy(startNode = lesson.startNode)
        if (hit.endNode <= 0) hit = hit.copy(endNode = lesson.endNode.coerceAtLeast(hit.startNode))
        if (hit.weekDay !in 1..7) hit = hit.copy(weekDay = lesson.weekDay)
        Log.d("swun", "[ktkq] sign ${hit.course} jxb=${hit.teachClassId} kb=${hit.scheduleId.length} lx=${hit.teachClassType}")
        if (ktkqIdsOf(hit).incomplete) {
            return hit.copy(
                week = weekNum,
                status = "not_in_ktkq",
                message = ktkqStatusLabel("not_in_ktkq"),
                activities = emptyList(),
                history = emptyList(),
            )
        }
        val probed = probeSlot(hit, weekNum)
        val id = probed.teachClassId
        val history = if (id.isNotEmpty()) {
            try {
                historyRows(studentHistory(id), probed.course.ifBlank { hit.course })
            } catch (_: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
        return probed.copy(history = history)
    }

    fun probe(activity: SignActivity): SignActivity = probeSlot(activity, activity.week.coerceAtLeast(1))

    fun probeSlot(item: SignActivity, week: Int): SignActivity {
        val ids = ktkqIdsOf(item)
        val day = if (item.weekDay in 1..7) item.weekDay else 1
        val start = item.startNode
        val end = item.endNode.coerceAtLeast(start)
        var entry = item.copy(
            week = week,
            weekDay = day,
            startNode = start,
            endNode = end,
            timeText = item.timeText.ifBlank { ktkqSlotTime(item) },
            activities = emptyList(),
            status = "missing_schedule",
            message = ktkqStatusLabel("missing_schedule"),
        )
        if (ids.incomplete) return entry
        return try {
            try {
                val detail = post(
                    "/jwmobile/biz/v410/schedule/queryScheduleDetail",
                    JSONObject()
                        .put("jxbid", ids.jxbid)
                        .put("kbid", ids.kbid)
                        .put("jxblx", ids.jxblx)
                        .put("wid", ""),
                )
                val teachers = kqObjList(detail.optJSONObject("data")?.opt("teacherInfo"))
                val xm = teachers.firstOrNull()?.kqStr("xm").orEmpty()
                if (xm.isNotEmpty()) entry = entry.copy(teacher = xm)
            } catch (_: Exception) {
            }
            val current = post(
                "/jwmobile/biz/v410/lesson/queryCurrentLesson",
                JSONObject()
                    .put("teachClassId", ids.jxbid)
                    .put("teachClassType", ids.jxblx)
                    .put("scheduleId", ids.kbid)
                    .put("week", week)
                    .put("weekDay", day)
                    .put("startNode", start)
                    .put("endNode", end),
            )
            val acts = kqObjList(current.optJSONObject("data")?.opt("activityList"))
            if (acts.isEmpty()) {
                return entry.copy(status = "no_activity", message = ktkqStatusLabel("no_activity"))
            }
            val detailed = acts.map { probeActivity(it) }
            val status = ktkqRollup(detailed)
            val pending = detailed.firstOrNull { it.status == "pending_signin" }
            entry.copy(
                activities = detailed,
                status = status,
                message = ktkqStatusLabel(status),
                activityId = pending?.activityId ?: entry.activityId,
                signType = pending?.signType ?: entry.signType,
                signCode = pending?.signCode ?: entry.signCode,
                startTime = pending?.startTime ?: entry.startTime,
                endTime = pending?.endTime ?: entry.endTime,
                title = pending?.title ?: entry.title,
            )
        } catch (e: Exception) {
            entry.copy(
                status = "inactive",
                message = e.message?.removePrefix("Exception: ").orEmpty().ifBlank { ktkqStatusLabel("inactive") },
            )
        }
    }

    fun submitSign(activityId: String, lat: Double, lng: Double, accuracy: Int, code: String = ""): JSONObject {
        return post(
            "/jwmobile/biz/v410/signin/sign",
            JSONObject().apply {
                put("activityId", activityId)
                put("accuracy", accuracy)
                put("latitude", lat)
                put("longitude", lng)
                put("code", code)
            },
        )
    }

    fun checkAllowSign(
        teachClassId: String,
        scheduleId: String,
        week: Int,
        weekDay: Int,
        startNode: Int,
        endNode: Int,
        activityId: String,
        lat: Double,
        lng: Double,
    ): JSONObject {
        return get(
            "/jwmobile/biz/v410/signin/checkAllowSign",
            mapOf(
                "teachClassId" to teachClassId,
                "scheduleId" to scheduleId,
                "week" to "$week",
                "weekDay" to "$weekDay",
                "startNode" to "$startNode",
                "endNode" to "$endNode",
                "activityId" to activityId,
                "latitude" to "$lat",
                "longitude" to "$lng",
                "accuracy" to "8",
            ),
        )
    }

    private fun studentHistory(teachClassId: String): JSONObject =
        post("/jwmobile/biz/v410/signin/queryStudentHistory", JSONObject().put("teachClassId", teachClassId))

    private fun probeActivity(activity: JSONObject): KtkqActivity {
        val id = activity.kqStr("activityId")
        var query = JSONObject()
        var detail = JSONObject()
        if (id.isNotEmpty()) {
            try {
                query = post("/jwmobile/biz/v410/signin/querySigninDetail", JSONObject().put("activityId", id))
                    .optJSONObject("data") ?: JSONObject()
            } catch (_: Exception) {
            }
            try {
                detail = post("/jwmobile/biz/v410/signin/detail", JSONObject().put("activityId", id))
                    .optJSONObject("data") ?: JSONObject()
            } catch (_: Exception) {
            }
        }
        val status = ktkqActivityStatus(activity, query, detail)
        val type = detail.kqStr("signinType").ifBlank { activity.kqStr("signType") }
        val title = activity.kqStr("title").ifBlank { "课堂签到" }
        return KtkqActivity(
            activityId = id,
            title = title,
            signType = type.uppercase(),
            status = status,
            message = ktkqStatusLabel(status),
            startTime = detail.kqStr("startTime"),
            endTime = detail.kqStr("endTime"),
            signCode = detail.kqStr("code"),
            leftSeconds = if (detail.has("leftSeconds") && !detail.isNull("leftSeconds")) kqInt(detail, "leftSeconds") else null,
        )
    }

    private fun persistToken(t: String) {
        token = t
        val paths = listOf("/", "/jwmobile", "/jwmobile/", "/jwmobile/index", "/jwmobile/auth/index")
        paths.forEach { path ->
            val url = "https://ktkq.swun.edu.cn$path".toHttpUrlOrNull() ?: return@forEach
            val c = okhttp3.Cookie.Builder()
                .name("Authorization")
                .value(t)
                .domain("ktkq.swun.edu.cn")
                .path(if (path == "/jwmobile") "/jwmobile" else path)
                .secure()
                .httpOnly()
                .build()
            CampusHttp.cookies.saveFromResponse(url, listOf(c))
        }
        rs.pushCookies("https://ktkq.swun.edu.cn/")
        rs.pushCookies("https://ktkq.swun.edu.cn/jwmobile/")
        writeLsToken()
    }

    private fun writeLsToken() {
        val t = token ?: return
        try {
            rs.writeLocalStorage("Authorization", t, "ktkq.swun.edu.cn")
            rs.writeLocalStorage("EM_TOKEN", t, "ktkq.swun.edu.cn")
        } catch (_: Exception) {
        }
    }

    private fun syncWebSession() {
        val t = token ?: return
        try {
            val synced = tokenViaWeb("$K_KTKQ/jwmobile/index#/index/kb/course/list?token=$t")
            if (!synced.isNullOrEmpty() && synced != t) persistToken(synced)
        } catch (e: Exception) {
            Log.w("swun", "[ktkq] webview sync $e")
        }
        writeLsToken()
    }

    private fun tokenViaWeb(url: String): String? {
        val hit = rs.open(url, RsWait.Token, RsSites.KTKQ)
        return parseKtkqToken(body = hit.body, urls = listOf(hit.url, url))
            ?: rs.token(RsSites.KTKQ)
            ?: tokenFromCookies()
    }

    private fun tokenFromCookies(): String? {
        val c = CampusHttp.cookies.all().firstOrNull {
            it.name.equals("Authorization", true) && it.value.length > 16
        }
        return cleanKtkqToken(c?.value)
    }

    private fun headers(): Map<String, String> = buildMap {
        put("Accept", "application/json, text/plain, */*")
        put("Referer", "$K_KTKQ/jwmobile/index")
        put("X-Requested-With", "XMLHttpRequest")
        val t = token
        if (!t.isNullOrEmpty()) {
            put("Authorization", t)
            put("EM-TOKEN", t)
        }
    }

    private fun get(path: String, params: Map<String, String> = emptyMap()): JSONObject {
        val p = if (params.isEmpty()) {
            path
        } else {
            val q = params.entries.joinToString("&") {
                "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}"
            }
            if (path.contains("?")) "$path&$q" else "$path?$q"
        }
        return api("GET", p)
    }

    private fun post(path: String, body: JSONObject): JSONObject = api("POST", path, body.toString())

    private fun api(method: String, path: String, body: String? = null, retry401: Boolean = true): JSONObject {
        val hit = rs.call(
            method = method,
            url = "$K_KTKQ$path",
            headers = headers() + if (body != null) mapOf("Content-Type" to "application/json") else emptyMap(),
            body = body,
        )
        if (CampusHttp.looksNightClosed(hit.status, hit.body)) error("课堂考勤夜间关闭或不在服务时间")
        val map = try {
            hit.asJson()
        } catch (_: Exception) {
            if (CampusHttp.looksRuishu(hit.status, hit.body)) error("课堂考勤仍被网关拦截")
            error("$path 非 JSON")
        }
        if (authFailed(map, hit.status)) {
            if (retry401 && cas != null) {
                token = null
                weekCache = null
                weekCacheKey = null
                loginWithCas(cas!!)
                return api(method, path, body, retry401 = false)
            }
            error(map.optString("msg").ifBlank { "课堂考勤认证失败" })
        }
        return map
    }

    private fun authFailed(json: JSONObject, http: Int): Boolean {
        if (http == 401 || http == 403) return true
        val code = json.opt("code")?.toString()
        if (code == "401" || code == "403") return true
        val msg = json.optString("msg") + json.optString("message")
        if (msg.contains("认证失败") || msg.contains("未登录") || msg.contains("登录过期")) return true
        val lower = msg.lowercase()
        return lower.contains("token") && (msg.contains("失效") || msg.contains("过期"))
    }
}

data class KtkqIds(val jxbid: String, val jxblx: String, val kbid: String) {
    val incomplete: Boolean get() = jxbid.isEmpty() || jxblx.isEmpty() || kbid.isEmpty()
}

fun ktkqIdsOf(a: SignActivity) = KtkqIds(a.teachClassId, a.teachClassType, a.scheduleId)

fun SignActivity.toLesson(): Lesson {
    val start = startNode.coerceAtLeast(1)
    val end = endNode.coerceAtLeast(start)
    return Lesson(course, classroom, teacher, weekDay.coerceIn(1, 7), start, end - start + 1)
}

fun mergeKtkqSlot(hit: SignActivity, s: SignActivity): SignActivity = hit.copy(
    teachClassId = s.teachClassId.ifBlank { hit.teachClassId },
    teachClassType = s.teachClassType.ifBlank { hit.teachClassType },
    scheduleId = s.scheduleId.ifBlank { hit.scheduleId },
    classroom = hit.classroom.ifBlank { s.classroom },
    teacher = hit.teacher.ifBlank { s.teacher },
    course = hit.course.ifBlank { s.course },
    courseCode = hit.courseCode.ifBlank { s.courseCode },
    className = hit.className.ifBlank { s.className },
    timeText = hit.timeText.ifBlank { s.timeText },
    weekDay = if (hit.weekDay in 1..7) hit.weekDay else s.weekDay,
    startNode = if (hit.startNode > 0) hit.startNode else s.startNode,
    endNode = if (hit.endNode > 0) hit.endNode else s.endNode,
    week = if (hit.week > 0) hit.week else s.week,
)

fun fillKtkqIds(hit: SignActivity, slots: List<SignActivity>): SignActivity? {
    val jxb = hit.teachClassId
    if (jxb.isNotEmpty()) {
        val s = slots.firstOrNull { it.teachClassId == jxb && !ktkqIdsOf(it).incomplete }
        if (s != null) return mergeKtkqSlot(hit, s)
    }
    return matchKtkqSlot(hit.toLesson(), slots)
}

fun matchKtkqSlot(lesson: Lesson, slots: List<SignActivity>): SignActivity? {
    var best: SignActivity? = null
    var bestScore = 0
    for (item in slots) {
        val score = ktkqSlotScore(lesson, item)
        if (score > bestScore) {
            bestScore = score
            best = item
        }
    }
    return if (bestScore < 40) null else best
}

fun ktkqSlotScore(lesson: Lesson, item: SignActivity): Int {
    val day = item.weekDay
    if (day != 0 && day != lesson.weekday) return 0
    var s = if (day == lesson.weekday) 20 else 0
    val name = ktkqNormName(item.course)
    val lname = ktkqNormName(lesson.name)
    if (name.isEmpty() || lname.isEmpty()) return 0
    s += when {
        name == lname -> 50
        name.contains(lname) || lname.contains(name) -> 30
        else -> {
            val a = ktkqCoreName(name)
            val b = ktkqCoreName(lname)
            if (a.length >= 2 && a == b) 28 else return 0
        }
    }
    val ks = item.startNode
    val js = item.endNode.coerceAtLeast(ks)
    s += when {
        ks == lesson.start && js == lesson.end -> 20
        ks <= lesson.end && js >= lesson.start -> 10
        else -> 0
    }
    val room = ktkqNormName(item.classroom)
    if (room.isNotEmpty() && room == ktkqNormName(lesson.room)) s += 10
    return s
}

fun ktkqNormName(s: String): String = s.lowercase()
    .replace(Regex("\\s+"), "")
    .replace('（', '(')
    .replace('）', ')')
    .replace('【', '[')
    .replace('】', ']')

fun ktkqCoreName(s: String): String = s.replace(Regex("\\([^)]*\\)"), "")

fun ktkqSlotTime(item: SignActivity): String {
    val sksj = item.startTime.ifBlank {
        if (item.weekDay in 1..7) kWeekdayLabels[item.weekDay] else ""
    }
    val node = if (item.startNode > 0) "第 ${item.startNode}-${item.endNode.coerceAtLeast(item.startNode)} 节" else ""
    return listOf(sksj, node).filter { it.isNotEmpty() }.joinToString("  ")
}

fun normalizeKtkqWeek(raw: JSONObject): JSONObject {
    val data = raw.opt("data")
    if (data is JSONArray) return raw
    val obj = raw.optJSONObject("data") ?: return raw
    val slots = JSONArray()
    for (key in listOf("theorySchedule", "practiceSchedule", "experimentSchedule", "changeSchedule", "list")) {
        val arr = obj.optJSONArray(key) ?: continue
        for (i in 0 until arr.length()) slots.put(arr.optJSONObject(i))
    }
    val groups = linkedMapOf<String, JSONObject>()
    for (i in 0 until slots.length()) {
        val s = slots.optJSONObject(i) ?: continue
        val name = s.kqStr("kcm", fallback = "课程")
        val code = s.kqStr("kch")
        val id = s.kqStr("jxbid")
        val gkey = id.ifBlank { "$name|$code" }
        val g = groups.getOrPut(gkey) {
            JSONObject().apply {
                put("kcm", name)
                put("kch", code)
                put("jxbid", id)
                put("jxblx", s.kqStr("jxblx"))
                put("xf", s.kqStr("xf"))
                put("xs", s.kqStr("xs"))
                put("list", JSONArray())
            }
        }
        val day = kqInt(s, "skxq")
        if (s.kqStr("sksj").isEmpty() && day in 1..7) s.put("sksj", kWeekdayLabels[day])
        if (s.kqStr("kcm").isEmpty()) s.put("kcm", name)
        g.getJSONArray("list").put(s)
    }
    val out = JSONArray()
    groups.values.forEach { out.put(it) }
    return JSONObject(raw.toString()).put("code", raw.opt("code") ?: 200).put("data", out)
}

fun ktkqWeekOf(raw: JSONObject, xnxqdm: String, week: Int, school: JSONObject): KtkqWeek {
    val courses = mutableListOf<KtkqCourse>()
    for (c in kqObjList(raw.opt("data"))) {
        val nested = kqObjList(c.opt("list"))
        val rows = if (nested.isEmpty()) {
            if (c.kqStr("kbid").isEmpty() && c.kqStr("jxbid").isEmpty() && !c.has("ksjc")) emptyList()
            else listOf(c)
        } else {
            nested
        }
        val slots = rows.map { ktkqSlotOf(c, it, week) }
        courses.add(
            KtkqCourse(
                name = c.kqStr("kcm", fallback = "课程"),
                code = c.kqStr("kch"),
                credits = c.kqStr("xf"),
                hours = c.kqStr("xs"),
                teachClassId = c.kqStr("jxbid"),
                teachClassType = c.kqStr("jxblx"),
                slots = slots,
            ),
        )
    }
    return KtkqWeek(
        xnxqdm = xnxqdm,
        xnxqmc = school.kqStr("xnxqmc").ifBlank { xnxqdm },
        week = week,
        courses = courses,
    )
}

fun ktkqSlotOf(course: JSONObject, item: JSONObject, week: Int): SignActivity {
    val jxbid = item.kqStr("jxbid").ifBlank { course.kqStr("jxbid") }
    val kbid = item.kqStr("kbid").ifBlank { course.kqStr("kbid") }
    val jxblx = item.kqStr("jxblx").ifBlank { course.kqStr("jxblx") }
    val day = kqInt(item, "skxq")
    val start = kqInt(item, "ksjc", 1)
    val end = kqInt(item, "jsjc", start)
    val name = item.kqStr("kcm").ifBlank { course.kqStr("kcm") }
    val sksj = item.kqStr("sksj").ifBlank { if (day in 1..7) kWeekdayLabels[day] else "" }
    val node = if (item.has("ksjc") || item.has("jsjc")) "第 $start-$end 节" else ""
    return SignActivity(
        activityId = "",
        title = item.kqStr("jxbmc").ifBlank { name },
        status = "",
        signType = "",
        startTime = sksj,
        endTime = "",
        classroom = item.kqStr("jasmc"),
        course = name,
        courseCode = item.kqStr("kch").ifBlank { course.kqStr("kch") },
        className = item.kqStr("jxbmc"),
        timeText = listOf(sksj, node).filter { it.isNotEmpty() }.joinToString("  "),
        teachClassId = jxbid,
        teachClassType = jxblx,
        scheduleId = kbid,
        week = week,
        weekDay = day,
        startNode = start,
        endNode = end,
        teacher = item.kqStr("skjs"),
    )
}

fun jsonDataOf(resp: JSONObject): JSONObject {
    val raw = resp.opt("data") ?: return JSONObject()
    return when (raw) {
        is JSONObject -> raw
        is String -> {
            val s = raw.trim()
            when {
                s.isEmpty() || s == "null" -> JSONObject()
                s.startsWith("{") -> JSONObject(s)
                s.startsWith("[") -> JSONObject()
                else -> JSONObject().put("xnxqdm", s)
            }
        }
        else -> JSONObject()
    }
}

fun termRows(data: Any?): List<JSONObject> {
    val direct = kqObjList(data)
    if (direct.isNotEmpty()) return direct
    if (data is JSONObject) {
        for (k in listOf("list", "records", "termList", "rows")) {
            val rows = kqObjList(data.opt(k))
            if (rows.isNotEmpty()) return rows
        }
    }
    return emptyList()
}

fun termCodeOf(m: JSONObject): String {
    for (k in listOf("xnxqdm", "termCode", "dm", "id")) {
        val s = m.kqStr(k)
        if (s.isNotEmpty()) return s
    }
    return ""
}

fun pickKtkqXnxqdm(schoolTimeData: JSONObject, terms: List<JSONObject>): String {
    val fromSchool = termCodeOf(schoolTimeData)
    if (fromSchool.isNotEmpty()) return fromSchool
    for (t in terms) {
        if (truthyKq(t.opt("currentFlag")) || truthyKq(t.opt("sfdq"))) {
            val code = termCodeOf(t)
            if (code.isNotEmpty()) return code
        }
    }
    if (terms.isNotEmpty()) return termCodeOf(terms.first())
    return ""
}

fun ktkqXnxqdmNow(now: Calendar = Calendar.getInstance()): String {
    val y = now.get(Calendar.YEAR)
    val m = now.get(Calendar.MONTH) + 1
    return when {
        m >= 8 -> "$y-${y + 1}-1"
        m == 1 -> "${y - 1}-$y-1"
        else -> "${y - 1}-$y-2"
    }
}

fun ktkqLooksAuthError(e: Throwable): Boolean {
    val s = e.message.orEmpty() + e.toString()
    return s.contains("认证失败") || s.contains("未登录") || s.contains("登录过期") || s.contains("课堂考勤认证失败")
}

fun truthyKq(v: Any?): Boolean = v == true || v == 1 || v == "1" || v == "true"

fun alreadySignedKq(m: JSONObject): Boolean = m.kqStr("signStatus") == "1"

fun ktkqActivityStatus(activity: JSONObject, queryDetail: JSONObject, signDetail: JSONObject): String {
    if (alreadySignedKq(queryDetail) || alreadySignedKq(signDetail)) return "already_signed"
    if (truthyKq(activity.opt("isEnd"))) return "expired"
    val start = parseKtkqDateTime(signDetail.opt("startTime"))
    val end = parseKtkqDateTime(signDetail.opt("endTime"))
    val now = System.currentTimeMillis()
    if (start != null && now < start) return "not_started"
    if (end != null && now > end) return "expired"
    if (signDetail.has("leftSeconds") && !signDetail.isNull("leftSeconds") && kqInt(signDetail, "leftSeconds", 1) <= 0) {
        return "expired"
    }
    val st = activity.kqStr("status")
    if (st.isNotEmpty() && st != "1") return "inactive"
    return "pending_signin"
}

fun ktkqRollup(acts: List<KtkqActivity>): String {
    val order = listOf("pending_signin", "already_signed", "expired", "outside_time", "not_started", "no_activity")
    for (s in order) if (acts.any { it.status == s }) return s
    return "inactive"
}

fun historyRows(raw: JSONObject, courseName: String): List<KtkqHistory> {
    val src = raw.optJSONArray("data") ?: return emptyList()
    val out = mutableListOf<KtkqHistory>()
    for (i in 0 until src.length()) {
        val m = src.optJSONObject(i) ?: continue
        val status = when {
            m.kqStr("attendanceStatus") == "10" -> "正常"
            m.kqStr("signStatus") == "1" -> "已签到"
            else -> ""
        }
        out.add(
            KtkqHistory(
                time = m.kqStr("startTime"),
                course = courseName,
                status = status,
                activityId = m.kqStr("activityId"),
            ),
        )
    }
    return out
}

fun parseKtkqDateTime(v: Any?): Long? {
    if (v == null || v == JSONObject.NULL) return null
    if (v is Number) {
        val n = v.toLong()
        if (n <= 0) return null
        return if (n > 100000000000L) n else n * 1000
    }
    val orig = v.toString().trim()
    if (orig.isEmpty() || orig == "null") return null
    orig.toLongOrNull()?.takeIf { orig.length >= 10 }?.let {
        return if (it > 100000000000L) it else it * 1000
    }
    var s = orig.replace('/', '-')
    val hasOffset = s.endsWith("Z", true) || Regex("""[+-]\d{2}:?\d{2}$""").containsMatchIn(s)
    val looksIso = hasOffset || orig.contains('T')
    if (!s.contains('T')) {
        val sp = s.indexOf(' ')
        if (sp > 0) s = "${s.substring(0, sp)}T${s.substring(sp + 1)}"
    }
    if (!s.contains('T')) return null
    if (!hasOffset) s = "${s}Z"
    if (Regex("""T\d{2}:\d{2}(Z|[+-])""").containsMatchIn(s) && !Regex("""T\d{2}:\d{2}:\d{2}""").containsMatchIn(s)) {
        s = s.replace(Regex("""(T\d{2}:\d{2})"""), "$1:00")
    }
    return try {
        val utc = java.time.OffsetDateTime.parse(s).toInstant().toEpochMilli()
        if (!looksIso) utc - 8 * 3600_000L else utc
    } catch (_: Exception) {
        null
    }
}

fun formatKtkqCst(v: Any?): String {
    val ms = parseKtkqDateTime(v)
    if (ms == null) {
        val s = v?.toString()?.trim().orEmpty()
        return if (s == "null") "" else s
    }
    val cst = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneOffset.ofHours(8))
    fun two(n: Int) = n.toString().padStart(2, '0')
    return "${cst.year}-${two(cst.monthValue)}-${two(cst.dayOfMonth)} ${two(cst.hour)}:${two(cst.minute)}"
}

fun formatKtkqCstRange(start: Any?, end: Any?): String {
    val a = formatKtkqCst(start)
    val b = formatKtkqCst(end)
    if (a.isEmpty()) return b
    if (b.isEmpty()) return a
    if (a.length >= 16 && b.length >= 16 && a.substring(0, 10) == b.substring(0, 10)) {
        return "$a ~ ${b.substring(11)}"
    }
    return "$a ~ $b"
}

fun ktkqObjList(v: Any?): List<JSONObject> = kqObjList(v)

fun kqObjList(v: Any?): List<JSONObject> {
    val arr = v as? JSONArray ?: return emptyList()
    return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
}

fun JSONObject.kqStr(key: String, fallback: String = ""): String {
    if (!has(key) || isNull(key)) return fallback
    val s = opt(key)?.toString()?.trim().orEmpty()
    return if (s.isEmpty() || s == "null") fallback else s
}

fun kqInt(o: JSONObject, key: String, fallback: Int = 0): Int {
    if (!o.has(key) || o.isNull(key)) return fallback
    return when (val v = o.opt(key)) {
        null -> fallback
        is Number -> v.toInt()
        else -> v.toString().toIntOrNull() ?: fallback
    }
}

fun ktkqStatusLabel(s: String): String = when (s) {
    "pending_signin" -> "待签到"
    "already_signed" -> "已签到"
    "outside_time" -> "不在签到时间"
    "no_activity" -> "无签到活动"
    "inactive" -> "当前无进行中签到"
    "missing_schedule" -> "课程信息不完整"
    "not_in_ktkq" -> "课堂考勤没有这节课"
    "expired" -> "已结束"
    "not_started" -> "未开始"
    "not_in_scope" -> "不在签到范围"
    else -> s
}

fun ktkqSignTypeLabel(t: String): String = when (t.uppercase()) {
    "NUMBER" -> "数字签到"
    "LOCATION" -> "定位签到"
    "SCAN", "QR" -> "扫码签到"
    "GESTURE" -> "手势签到"
    "GENERAL" -> "课堂签到"
    else -> t.ifBlank { "课堂签到" }
}

fun ktkqNeedsCode(type: String): Boolean {
    val t = type.uppercase()
    return t == "NUMBER" || t == "SCAN" || t == "QR"
}

fun encodeKtkqNav(key: String): String =
    Base64.encodeToString(key.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP)

fun decodeKtkqNav(id: String): String = try {
    String(Base64.decode(id, Base64.URL_SAFE), Charsets.UTF_8)
} catch (_: Exception) {
    id.replace("__PIPE__", "|")
}

fun parseKtkqToken(body: String = "", urls: List<String> = emptyList(), setCookies: List<String> = emptyList()): String? {
    for (c in setCookies) {
        val m = Regex("""(?:^|[,;\s])Authorization=([^;]+)""", RegexOption.IGNORE_CASE).find(c)
        cleanKtkqToken(m?.groupValues?.get(1))?.let { return it }
    }
    for (u in urls) {
        tokenFromKtkqUrl(u)?.let { return it }
    }
    return tokenFromKtkqBody(body)
}

fun tokenFromKtkqUrl(u: String): String? {
    if (u.isEmpty() || !u.contains("token=")) return null
    val m = Regex("""[?&#]token=([^&\s#]+)""").find(u)
    return cleanKtkqToken(m?.groupValues?.get(1))
}

fun tokenFromKtkqBody(data: String): String? {
    if (data.isBlank()) return null
    Regex("""["']token["']\s*[:=]\s*["']([^"']+)["']""").find(data)?.groupValues?.get(1)?.let {
        cleanKtkqToken(it)?.let { t -> return t }
    }
    return null
}

fun cleanKtkqToken(raw: String?): String? {
    var s = (raw ?: "").trim()
    if (s.length >= 2 && ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'")))) {
        s = s.substring(1, s.length - 1).trim()
    }
    try {
        s = URLDecoder.decode(s, "UTF-8")
    } catch (_: Exception) {
    }
    s = s.trim()
    if (s.startsWith("Bearer ", ignoreCase = true)) s = s.substring(7).trim()
    if (s.isEmpty() || s == "null" || s == "undefined" || s.length < 16) return null
    return s
}
