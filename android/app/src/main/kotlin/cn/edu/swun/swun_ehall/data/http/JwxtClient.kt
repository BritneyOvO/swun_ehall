package cn.edu.swun.swun_ehall.data.http

import cn.edu.swun.swun_ehall.data.model.CreditBucket
import cn.edu.swun.swun_ehall.data.model.CreditProgress
import cn.edu.swun.swun_ehall.data.model.Exam
import cn.edu.swun.swun_ehall.data.model.Grade
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

class JwxtClient {
    var portalClosed: Boolean = false
    private var cas: CasClient? = null
    @Volatile private var ensuring = false

    fun attachCas(c: CasClient) {
        cas = c
    }

    fun loginWithCas(cas: CasClient) {
        this.cas = cas
        portalClosed = false
        var url = cas.ticketFor(K_JWXT_SERVICE)
        for (i in 0 until 15) {
            val r = CampusHttp.get(url, mapOf("Referer" to MENU_REF))
            val body = CampusHttp.text(r)
            val loc = CampusHttp.location(r)
            if (CampusHttp.looksNightClosed(r.code, body)) {
                portalClosed = true
                return
            }
            if (r.code == 200 && !looksLoggedOut(body, loc)) break
            if (loc.isEmpty()) break
            url = CampusHttp.absUrl(K_JWXT, loc)
            if (url.contains("authserver") && !url.contains("ticket=")) {
                error("教务登录失败: 重定向回 CAS")
            }
        }
        val home = CampusHttp.get("$K_JWXT/jwglxt/xtgl/index_initMenu.html?jsdm=xs", mapOf("Referer" to MENU_REF))
        val body = CampusHttp.text(home)
        if (home.code == 200 && !looksLoggedOut(body, CampusHttp.location(home)) &&
            !CampusHttp.looksNightClosed(home.code, body)
        ) {
            portalClosed = false
            return
        }
        if (CampusHttp.looksNightClosed(home.code, body)) {
            portalClosed = true
            return
        }
        error("教务登录失败")
    }

    fun sessionAlive(): Boolean {
        return try {
            val home = CampusHttp.get("$K_JWXT/jwglxt/xtgl/index_initMenu.html?jsdm=xs", mapOf("Referer" to MENU_REF))
            val body = CampusHttp.text(home)
            if (CampusHttp.looksNightClosed(home.code, body)) {
                portalClosed = true
                return false
            }
            home.code == 200 && !looksLoggedOut(body, CampusHttp.location(home))
        } catch (_: Exception) {
            false
        }
    }

    fun ensureSession(force: Boolean = false) {
        if (!force && sessionAlive()) return
        synchronized(this) {
            if (ensuring) return
            ensuring = true
        }
        try {
            val c = cas ?: error("教务登录已过期，请重新登录")
            if (!c.tgtAlive()) error("统一身份已过期，请重新登录")
            loginWithCas(c)
            if (portalClosed) return
            if (!sessionAlive()) error("教务登录失败，请重新登录")
        } finally {
            ensuring = false
        }
    }

    fun allGrades(): List<Grade> = grades(xnm = "", xqm = "")

    fun scheduleKb(xnm: String = "", xqm: String = ""): org.json.JSONArray {
        val t = currentJwxtTerm()
        val yn = xnm.ifBlank { "${t.first}" }
        val yq = xqm.ifBlank { t.second }
        val json = post(
            "/jwglxt/kbcx/xskbcx_cxXsgrkb.html",
            mapOf(
                "xnm" to yn,
                "xqm" to yq,
                "kzlx" to "ck",
                "xsdm" to "",
                "kclbdm" to "",
                "kclxdm" to "",
            ),
            params = mapOf("gnmkdm" to "N2151"),
        )
        val kb = json.optJSONArray("kbList")
            ?: json.optJSONObject("data")?.optJSONArray("kbList")
            ?: org.json.JSONArray()
        android.util.Log.d(
            "swun",
            "[kb] jwxt xnm=$yn xqm=$yq rows=${kb.length()} keys=${json.keys().asSequence().joinToString()}",
        )
        if (kb.length() > 0) {
            android.util.Log.d("swun", "[kb] sample ${kb.optJSONObject(0)}")
        }
        return kb
    }

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
            if (r.code == 403 || CampusHttp.looksNightClosed(r.code, text)) {
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
            return JSONObject(text.ifBlank { "{}" })
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
            if (r.code != 200) error("HTTP ${r.code}")
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
        if (r.code != 200) error("HTTP ${r.code}")
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
        if (r.code != 200) error("HTTP ${r.code}")
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

fun gradePassed(g: Grade): Boolean {
    val mark = g.mark.trim()
    if (mark in setOf("及格", "合格", "优", "良", "中", "通过", "A", "B", "C")) return true
    if (mark in setOf("不及格", "不合格", "不通过", "F")) return false
    g.score.toDoubleOrNull()?.let { return it >= 60 }
    g.gpa.toDoubleOrNull()?.let { return it > 0 }
    return false
}
