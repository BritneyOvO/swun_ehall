package cn.edu.swun.swun_ehall.data.http

import cn.edu.swun.swun_ehall.data.model.CreditBucket
import cn.edu.swun.swun_ehall.data.model.CreditProgress
import cn.edu.swun.swun_ehall.data.model.Exam
import cn.edu.swun.swun_ehall.data.model.Grade
import cn.edu.swun.swun_ehall.data.model.Lesson
import cn.edu.swun.swun_ehall.data.model.PlanCourse
import cn.edu.swun.swun_ehall.data.model.XkCourse
import cn.edu.swun.swun_ehall.data.model.XkRound
import org.json.JSONArray
import org.json.JSONObject

const val K_JWXT = "https://jwxt.swun.edu.cn"
const val K_JWXT_SERVICE = "http://jwxt.swun.edu.cn/sso/jziotlogin"
const val K_XK_GNMKDM = "N253512"
private const val MENU_REF = "$K_JWXT/jwglxt/xtgl/index_initMenu.html?jsdm=xs"
private const val XK_REF = "$K_JWXT/jwglxt/xsxk/zzxkyzb_cxZzxkYzbIndex.html?gnmkdm=$K_XK_GNMKDM"

fun jwxtPortalClosed(code: Int, body: String): Boolean =
    CampusHttp.looksNightClosed(code, body)

fun throwJwxtResponseIssue(code: Int, body: String) {
    when (CampusHttp.classifyResponse(code, body)) {
        CampusResponseIssue.PortalClosed -> error("教务夜间关闭或不在服务时间")
        CampusResponseIssue.GatewayBlocked -> error("教务系统被网关拦截")
        CampusResponseIssue.AuthenticationExpired -> error("教务登录已过期，请重试")
        CampusResponseIssue.NetworkFailure -> error("请求失败，请检查网络连接")
        CampusResponseIssue.HttpFailure -> error("教务 HTTP $code")
        null -> Unit
    }
}

class JwxtClient {
    var portalClosed: Boolean = false
    private var cas: CasClient? = null
    private val sessionLease = SessionLease()

    fun attachCas(c: CasClient) {
        cas = c
    }

    fun loginWithCas(cas: CasClient) {
        this.cas = cas
        portalClosed = false
        val started = System.nanoTime()
        val ticketAt = System.nanoTime()
        var url = cas.ticketFor(K_JWXT_SERVICE)
        SwunLog.took(ticketAt, "教务换票")
        for (i in 0 until 15) {
            val hop = System.nanoTime()
            val r = CampusHttp.get(url, mapOf("Referer" to MENU_REF))
            val body = CampusHttp.text(r)
            val loc = CampusHttp.location(r)
            SwunLog.took(hop, "教务登录跳转#$i HTTP ${r.code} ${body.length}b")
            if (jwxtPortalClosed(r.code, body)) {
                portalClosed = true
                SwunLog.took(started, "教务登录夜间关闭 HTTP ${r.code}")
                return
            }
            if (r.code == 200 && !looksLoggedOut(body, loc)) break
            if (loc.isEmpty()) break
            url = CampusHttp.absUrl(K_JWXT, loc)
            if (url.contains("authserver") && !url.contains("ticket=")) {
                error("教务登录失败: 重定向回 CAS")
            }
        }
        val menuAt = System.nanoTime()
        val home = CampusHttp.get("$K_JWXT/jwglxt/xtgl/index_initMenu.html?jsdm=xs", mapOf("Referer" to MENU_REF))
        val body = CampusHttp.text(home)
        SwunLog.took(menuAt, "教务首页 HTTP ${home.code} ${body.length}b")
        if (home.code == 200 && !looksLoggedOut(body, CampusHttp.location(home)) &&
            !jwxtPortalClosed(home.code, body)
        ) {
            portalClosed = false
            SwunLog.took(started, "教务登录完成")
            return
        }
        if (jwxtPortalClosed(home.code, body)) {
            portalClosed = true
            SwunLog.took(started, "教务登录夜间关闭 HTTP ${home.code}")
            return
        }
        error("教务登录失败")
    }

    fun sessionAlive(): Boolean {
        val started = System.nanoTime()
        return try {
            val home = CampusHttp.get("$K_JWXT/jwglxt/xtgl/index_initMenu.html?jsdm=xs", mapOf("Referer" to MENU_REF))
            val body = CampusHttp.text(home)
            if (jwxtPortalClosed(home.code, body)) {
                portalClosed = true
                SwunLog.took(started, "教务会话检查 夜间关闭 HTTP ${home.code}")
                return false
            }
            val ok = home.code == 200 && !looksLoggedOut(body, CampusHttp.location(home))
            SwunLog.took(started, "教务会话检查 有效=$ok HTTP ${home.code}")
            ok
        } catch (e: Exception) {
            SwunLog.took(started, "教务会话检查 失败 ${e.message}")
            false
        }
    }

    fun reset() {
        sessionLease.reset()
        portalClosed = false
        cas = null
    }

    fun ensureSession(force: Boolean = false) {
        sessionLease.ensure(force, probe = ::sessionAlive, login = {
            if (portalClosed) return@ensure false
            val c = cas ?: error("教务登录已过期，请重新登录")
            if (!c.tgtAlive()) error("统一身份已过期，请重新登录")
            // loginWithCas already validates the menu; do not fetch it again here.
            loginWithCas(c)
            !portalClosed
        })
    }

    fun allGrades(): List<Grade> = grades(xnm = "", xqm = "")

    fun schedulePage(xnm: String = "", xqm: String = ""): JSONObject {
        val t = currentJwxtTerm()
        val yn = xnm.ifBlank { "${t.first}" }
        val yq = xqm.ifBlank { t.second }
        val json = getJson(mobileKb(yn, yq, zs = ""))
        if ((json.optJSONArray("rqazcList")?.length() ?: 0) == 0) {
            val dated = getJson(mobileKb(yn, yq, zs = "1"))
            val days = dated.optJSONArray("rqazcList")
            if (days != null && days.length() > 0) {
                json.put("rqazcList", days)
                json.put("zs", dated.optString("zs").ifBlank { "1" })
            }
        }
        return json
    }

    private fun mobileKb(xnm: String, xqm: String, zs: String): String {
        val week = if (zs.isBlank()) "" else "&zs=$zs"
        return "/jwglxt/kbcx/xskbcxMobile_cxXsKb.html?gnmkdm=N2154&xnm=$xnm&xqm=$xqm$week&kblx=1&doType=app&xh="
    }

    fun scheduleKb(xnm: String = "", xqm: String = ""): JSONArray = jwxtKbList(schedulePage(xnm, xqm))

    fun lessonsFromKb(kb: org.json.JSONArray): List<Lesson> = lessonsFromJwxtKb(kb)

    fun grades(xnm: String = "", xqm: String = ""): List<Grade> {
        val items = mutableListOf<Grade>()
        var page = 1
        while (page <= 20) {
            val json = post(
                "/jwglxt/cjcx/cjcx_cxXsgrcj.html",
                mapOf(
                    "xnm" to xnm,
                    "xqm" to xqm,
                    "queryModel.showAll" to "true",
                    "queryModel.showCount" to "200",
                    "queryModel.currentPage" to "$page",
                ),
                params = mapOf("doType" to "query"),
            )
            val arr = json.optJSONArray("items") ?: break
            for (i in 0 until arr.length()) {
                val m = arr.optJSONObject(i) ?: continue
                items.add(
                    Grade(
                        name = m.optString("kcmc"),
                        credit = m.optString("xf"),
                        score = m.optString("bfzcj"),
                        gpa = m.optString("jd"),
                        kind = m.optString("kclbmc"),
                        xnm = m.optString("xnm"),
                        xqm = m.optString("xqm"),
                        mark = m.optString("cj"),
                    ),
                )
            }
            val total = json.optInt("totalResult", items.size)
            if (items.size >= total || arr.length() == 0) break
            page++
        }
        return items
    }

    private fun post(path: String, data: Map<String, String>, params: Map<String, String> = emptyMap()): JSONObject {
        var relogged = false
        repeat(3) {
            val q = params.entries.joinToString("&") { "${it.key}=${it.value}" }
            val url = "$K_JWXT$path" + if (q.isEmpty()) "" else "?$q"
            val started = System.nanoTime()
            val r = CampusHttp.postForm(
                url,
                data,
                mapOf(
                    "Referer" to MENU_REF,
                    "X-Requested-With" to "XMLHttpRequest",
                ),
            )
            val loc = CampusHttp.location(r)
            val text = CampusHttp.text(r)
            SwunLog.took(started, "教务 $path HTTP ${r.code} ${text.length}b")
            if (jwxtPortalClosed(r.code, text)) {
                portalClosed = true
                error("教务夜间关闭或不在服务时间")
            }
            if (looksLoggedOut(text, loc) || CampusHttp.isRedirect(r)) {
                if (!relogged) {
                    relogged = true
                    ensureSession(force = true)
                    return@repeat
                }
                error("教务登录已过期，请重新登录")
            }
            if (r.code != 200) error("教务 HTTP ${r.code}")
            val result = JSONObject(text.ifBlank { "{}" })
            sessionLease.confirm()
            return result
        }
        error("教务请求失败")
    }

    private fun getJson(pathAndQuery: String): JSONObject {
        var relogged = false
        repeat(3) {
            val url = "$K_JWXT$pathAndQuery"
            val started = System.nanoTime()
            val r = CampusHttp.get(
                url,
                mapOf("Referer" to url, "X-Requested-With" to "XMLHttpRequest", "Accept" to "application/json, text/javascript, */*; q=0.01"),
            )
            val loc = CampusHttp.location(r)
            val text = CampusHttp.text(r)
            SwunLog.took(started, "教务 $pathAndQuery HTTP ${r.code} ${text.length}b")
            if (jwxtPortalClosed(r.code, text)) {
                portalClosed = true
                error("教务夜间关闭或不在服务时间")
            }
            if (looksLoggedOut(text, loc) || CampusHttp.isRedirect(r)) {
                if (!relogged) {
                    relogged = true
                    ensureSession(force = true)
                    return@repeat
                }
                error("教务登录已过期，请重新登录")
            }
            if (r.code != 200) error("教务 HTTP ${r.code}")
            val result = JSONObject(text.ifBlank { "{}" })
            sessionLease.confirm()
            return result
        }
        error("教务请求失败")
    }

    fun exams(xnm: String = "", xqm: String = ""): List<Exam> {
        val t = currentJwxtTerm()
        val json = post(
            "/jwglxt/kwgl/kscx_cxXsksxxIndex.html",
            mapOf("xnm" to xnm.ifBlank { "${t.first}" }, "xqm" to xqm.ifBlank { t.second }),
            params = mapOf("doType" to "query"),
        )
        val arr = json.optJSONArray("items") ?: return emptyList()
        val out = mutableListOf<Exam>()
        for (i in 0 until arr.length()) {
            val m = arr.optJSONObject(i) ?: continue
            out.add(
                Exam(
                    name = m.optString("kcmc"),
                    time = m.optString("kssj").ifBlank { m.optString("ksqssj") },
                    place = m.optString("cdmc").ifBlank { m.optString("ksdd") },
                    seat = m.optString("zwh"),
                ),
            )
        }
        return out
    }

    fun creditFromGrades(items: List<Grade>): CreditProgress {
        val buckets = linkedMapOf<String, CreditBucket>()
        var xf = 0.0
        var jdXf = 0.0
        var passed = 0
        var failed = 0
        for (g in items) {
            val credit = g.credit.toDoubleOrNull() ?: 0.0
            val kind = g.kind.ifBlank { "其他" }
            val prev = buckets[kind] ?: CreditBucket(kind, 0.0)
            if (gradePassed(g)) {
                passed++
                buckets[kind] = prev.copy(
                    credits = prev.credits + credit,
                    courses = prev.courses + 1,
                    items = prev.items + PlanCourse(g.name, credit, "已修", g.score),
                )
                val jd = g.gpa.toDoubleOrNull()
                if (jd != null && credit > 0) {
                    xf += credit
                    jdXf += jd * credit
                }
            } else if (credit > 0) {
                failed++
                buckets[kind] = prev.copy(
                    failed = prev.failed + credit,
                    failedCourses = prev.failedCourses + 1,
                    items = prev.items + PlanCourse(g.name, credit, "未过", g.score),
                )
            }
        }
        val list = buckets.values.sortedByDescending { it.credits }
        val taken = list.fold(0.0) { a, b -> a + b.credits }
        return CreditProgress(
            taken = taken,
            gpa = if (xf > 0) jdXf / xf else null,
            planPassed = passed,
            planFailed = failed,
            buckets = list,
        )
    }

    fun selectionEntry(): Pair<List<XkRound>, Map<String, String>> {
        var last: Exception? = null
        repeat(2) {
            val r = CampusHttp.get(
                "$K_JWXT/jwglxt/xsxk/zzxkyzb_cxZzxkYzbIndex.html?gnmkdm=$K_XK_GNMKDM&layout=default",
                mapOf("Referer" to MENU_REF),
            )
            val loc = CampusHttp.location(r)
            val html = CampusHttp.text(r)
            if (looksLoggedOut(html, loc) || CampusHttp.isRedirect(r)) {
                last = Exception("选课会话已刷新，请重试")
                ensureSession(force = true)
                return@repeat
            }
            throwJwxtResponseIssue(r.code, html)
            if (html.contains("系统维护页面")) error("选课系统维护中")
            return parseXkRounds(html) to extractXkProfile(html)
        }
        throw last ?: Exception("选课会话已刷新，请重试")
    }

    fun selectionDisplay(round: XkRound, profile: Map<String, String>): Map<String, String> {
        val html = xkHtml(
            "/jwglxt/xsxk/zzxkyzb_cxZzxkYzbDisplay.html",
            mapOf(
                "xkkz_id" to round.xkkzId,
                "kklxdm" to round.kklxdm,
                "xszxzt" to "1",
                "njdm_id" to round.njdmId.ifBlank { profile["njdm_id"].orEmpty() },
                "zyh_id" to round.zyhId.ifBlank { profile["zyh_id"].orEmpty() },
                "kspage" to "0",
                "jspage" to "0",
                "xkkz_xh" to round.xkkzXh,
            ),
        )
        if (html.contains("系统维护")) error("选课系统维护中")
        if (html.contains("加密串错误")) error("选课加密串已过期，请刷新重试")
        return parseXkPanel(html)
    }

    fun selectionCourses(round: XkRound, profile: Map<String, String>, keyword: String, panel: Map<String, String>): List<XkCourse> {
        val size = 10
        val all = mutableListOf<JSONObject>()
        for (page in 1..20) {
            val q = buildXkQuery(round, profile, keyword, panel, page, size)
            val d = xkPost("/jwglxt/xsxk/zzxkyzb_cxZzxkYzbPartDisplay.html", q)
            if (d == JSONObject.NULL || d.toString() == "0") error("选课查询被拒绝，请刷新重试")
            val rows = xkRowsOf(d)
            all.addAll(rows)
            if (xkPartDisplayDone(rows, size)) break
        }
        return xkCollapseByCourse(all).map { jsonToXkCourse(it) }
    }

    fun selectionJxbs(query: Map<String, String>, kchId: String, kcmc: String): List<XkCourse> {
        val d = xkPost(
            "/jwglxt/xsxk/zzxkyzbjk_cxJxbWithKchZzxkYzb.html",
            query + mapOf("kch_id" to kchId, "kcmc" to kcmc),
        )
        val rows = if (d is JSONArray) (0 until d.length()).mapNotNull { d.optJSONObject(it) } else xkRowsOf(d)
        return rows.map { jsonToXkCourse(it) }
    }

    fun selectionSubmit(body: Map<String, String>): JSONObject {
        val d = xkPost("/jwglxt/xsxk/zzxkyzbjk_xkBcZyZzxkYzb.html", body)
        return when (d) {
            is JSONObject -> d
            else -> JSONObject().put("flag", "0").put("msg", d.toString())
        }
    }

    private fun xkHtml(path: String, data: Map<String, String>): String {
        val r = CampusHttp.postForm(
            "$K_JWXT$path?gnmkdm=$K_XK_GNMKDM",
            data,
            mapOf(
                "Referer" to XK_REF,
                "X-Requested-With" to "XMLHttpRequest",
                "Accept" to "text/html, */*; q=0.01",
            ),
        )
        val loc = CampusHttp.location(r)
        val text = CampusHttp.text(r)
        if (r.code == 910 || r.code == 911) error("选课会话已失效，请刷新重试")
        if (looksLoggedOut(text, loc) || CampusHttp.isRedirect(r)) {
            ensureSession(force = true)
            error("选课会话已刷新，请重试")
        }
        throwJwxtResponseIssue(r.code, text)
        return text
    }

    private fun xkPost(path: String, data: Map<String, String>): Any {
        val r = CampusHttp.postForm(
            "$K_JWXT$path?gnmkdm=$K_XK_GNMKDM",
            data,
            mapOf(
                "Referer" to XK_REF,
                "X-Requested-With" to "XMLHttpRequest",
                "Accept" to "application/json, text/javascript, */*; q=0.01",
            ),
        )
        val loc = CampusHttp.location(r)
        val text = CampusHttp.text(r)
        if (r.code == 910 || r.code == 911) error("选课会话已失效，请刷新重试")
        if (looksLoggedOut(text, loc) || CampusHttp.isRedirect(r)) {
            ensureSession(force = true)
            error("选课会话已刷新，请重试")
        }
        if (text.contains("加密串错误")) error("选课加密串已过期，请刷新重试")
        if (text.contains("系统维护")) error("选课系统维护中")
        throwJwxtResponseIssue(r.code, text)
        val t = text.trim()
        if (t.startsWith("[")) return JSONArray(t)
        if (t.startsWith("{")) return JSONObject(t)
        if (t == "0") return JSONObject.NULL
        return t
    }

    private fun looksLoggedOut(body: String, loc: String): Boolean {
        if (body.contains("login_slogin") || body.contains("统一身份认证")) return true
        val u = loc.lowercase()
        return u.contains("login_slogin") || u.contains("/sso/login") || u.contains("authserver")
    }
}

fun currentJwxtTerm(): Pair<Int, String> {
    val now = java.util.Calendar.getInstance()
    val y = now.get(java.util.Calendar.YEAR)
    val m = now.get(java.util.Calendar.MONTH) + 1
    return if (m >= 8 || m <= 1) y to "3" else (y - 1) to "12"
}

fun jwxtKbList(json: JSONObject): JSONArray {
    return json.optJSONArray("kbList")
        ?: json.optJSONObject("data")?.optJSONArray("kbList")
        ?: JSONArray()
}

data class JwxtWeek(val week: Int, val total: Int, val week1Monday: java.time.LocalDate?)

fun jwxtCurrentWeek(json: JSONObject, today: java.time.LocalDate = java.time.LocalDate.now()): JwxtWeek {
    val listed = json.optString("zs").toIntOrNull()?.takeIf { it in 1..30 }
    val days = json.optJSONArray("rqazcList") ?: JSONArray()
    var maxZc = listed ?: 0
    var todayZc = 0
    var anchor: java.time.LocalDate? = null
    for (i in 0 until days.length()) {
        val m = days.optJSONObject(i) ?: continue
        val date = parseJwxtDate(m.optString("rq")) ?: continue
        val zc = m.optString("zc").toIntOrNull()?.takeIf { it in 1..30 } ?: listed ?: continue
        if (zc > maxZc) maxZc = zc
        if (date == today) todayZc = zc
        if (anchor == null) {
            val weekday = m.optString("xqj").toIntOrNull()?.takeIf { it in 1..7 } ?: date.dayOfWeek.value
            val monday = date.minusDays((weekday - 1).toLong())
            anchor = monday.minusWeeks((zc - 1).toLong())
        }
    }
    val week = when {
        todayZc > 0 -> todayZc
        anchor != null -> {
            val thisMonday = today.minusDays((today.dayOfWeek.value - 1).toLong())
            java.time.temporal.ChronoUnit.WEEKS.between(anchor, thisMonday).toInt() + 1
        }
        else -> 1
    }.coerceAtLeast(1)
    return JwxtWeek(week, maxOf(maxZc, week, 16), anchor)
}

private fun parseJwxtDate(raw: String): java.time.LocalDate? {
    val found = Regex("""(\d{4})\D+(\d{1,2})\D+(\d{1,2})""").find(raw.trim()) ?: return null
    return runCatching {
        java.time.LocalDate.of(found.groupValues[1].toInt(), found.groupValues[2].toInt(), found.groupValues[3].toInt())
    }.getOrNull()
}

fun lessonsFromJwxtKb(kb: JSONArray): List<Lesson> {
    val out = ArrayList<Lesson>(kb.length())
    for (i in 0 until kb.length()) {
        val m = kb.optJSONObject(i) ?: continue
        val name = m.optString("kcmc").trim()
        if (name.isEmpty() || name == "null") continue
        val day = m.optString("xqj").toIntOrNull() ?: continue
        if (day !in 1..7) continue
        val period = jwxtPeriod(m) ?: continue
        val room = m.optString("cdmc").ifBlank { m.optString("jash") }
        val teacher = m.optString("xm")
        out.add(
            Lesson(
                name = name,
                room = room.takeUnless { it == "null" }.orEmpty(),
                teacher = teacher.takeUnless { it == "null" }.orEmpty(),
                weekday = day,
                start = period.first,
                span = period.second,
                weeks = jwxtWeekMask(m),
            ),
        )
    }
    return out
}

private fun jwxtPeriod(m: JSONObject): Pair<Int, Int>? {
    val raw = listOf("jcor", "jcs", "jc", "skjc").firstNotNullOfOrNull { key ->
        m.optString(key).takeIf { it.isNotBlank() && it != "null" }
    } ?: return null
    val nums = Regex("\\d+").findAll(raw).map { it.value.toInt() }.toList()
    val start = nums.firstOrNull() ?: return null
    if (start !in 1..14) return null
    val end = nums.getOrNull(1) ?: start
    return start to (end - start + 1).coerceAtLeast(1)
}

private fun jwxtWeekMask(m: JSONObject): String {
    val parsed = cn.edu.swun.swun_ehall.data.session.TeacherCache.parseWeekSet(
        skzc = m.optString("skzc"),
        zcd = m.optString("zcd"),
    )
    val weeks = if (parsed.isNotEmpty()) {
        parsed
    } else {
        val bits = m.optString("oldzc").toIntOrNull() ?: 0
        if (bits <= 0) return "1".repeat(16)
        (0 until 24).mapNotNull { i -> if ((bits shr i) and 1 == 1) i + 1 else null }.toSet()
    }
    if (weeks.isEmpty()) return "1".repeat(16)
    val max = weeks.max().coerceIn(1, 30)
    return (1..max).joinToString("") { if (it in weeks) "1" else "0" }
}

fun gradePassed(g: Grade): Boolean {
    val mark = g.mark.trim()
    if (mark in setOf("及格", "合格", "优", "良", "中", "通过", "A", "B", "C")) return true
    if (mark in setOf("不及格", "不合格", "不通过", "F")) return false
    g.score.toDoubleOrNull()?.let { return it >= 60 }
    g.gpa.toDoubleOrNull()?.let { return it > 0 }
    return false
}
