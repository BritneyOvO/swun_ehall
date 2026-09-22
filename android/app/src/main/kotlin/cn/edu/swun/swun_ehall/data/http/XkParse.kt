package cn.edu.swun.swun_ehall.data.http

import cn.edu.swun.swun_ehall.data.model.XkCourse
import cn.edu.swun.swun_ehall.data.model.XkRound
import org.json.JSONArray
import org.json.JSONObject

fun parseXkRounds(html: String): List<XkRound> {
    val re = Regex("""queryCourse\(this,'([^']+)','([^']+)','([^']+)','([^']+)','([^']*)'\)""")
    return re.findAll(html).map { m ->
        XkRound(
            kklxdm = m.groupValues[1],
            xkkzId = m.groupValues[2],
            njdmId = m.groupValues[3],
            zyhId = m.groupValues[4],
            xkkzXh = m.groupValues[5],
            name = xkTabName(html, m.range.first),
        )
    }.toList()
}

private fun xkTabName(html: String, at: Int): String {
    val open = html.indexOf("\">", at)
    val gt = if (open < 0) -1 else html.indexOf('>', open)
    val stop = if (gt < 0) -1 else html.indexOf("</a>", gt)
    if (gt < 0 || stop < 0) return ""
    val name = html.substring(gt + 1, stop).trim()
    if (name.isEmpty() || name.contains("input")) return ""
    return name
}

fun extractXkProfile(html: String): Map<String, String> {
    val keys = listOf(
        "xh_id", "xqh_id", "jg_id", "jg_id_1", "zyh_id", "zyfx_id", "njdm_id",
        "bh_id", "xbm", "xslbdm", "mzm", "xz", "ccdm", "xsbj", "njdm_id_1",
        "zyh_id_1", "xkxnm", "xkxqm", "xkkz_xh", "jxbzbkg", "jxbzhkg", "qzz",
        "xkxfqzfs", "xkmcjzxskcs", "xszxzt",
    )
    val out = mutableMapOf<String, String>()
    for (k in keys) {
        val v = xkAttr(html, k)
        if (v.isNotEmpty()) out[k] = v
    }
    return out
}

fun parseXkPanel(html: String): Map<String, String> {
    val out = mutableMapOf<String, String>()
    Regex("""<input[^>]*type="hidden"[^>]*>""", RegexOption.IGNORE_CASE).findAll(html).forEach { m ->
        val tag = m.value
        val name = Regex("""\bname="([^"]+)"""").find(tag)?.groupValues?.get(1) ?: return@forEach
        if (name.isEmpty()) return@forEach
        out[name] = Regex("""\bvalue="([^"]*)"""").find(tag)?.groupValues?.get(1).orEmpty()
    }
    return out
}

private fun xkAttr(html: String, name: String): String {
    Regex("""name="$name"[^>]*value="([^"]*)"""").find(html)?.groupValues?.get(1)?.let { return it }
    return Regex("""value="([^"]*)"[^>]*name="$name"""").find(html)?.groupValues?.get(1).orEmpty()
}

fun xkPageRange(page: Int, size: Int = 10): Pair<Int, Int> {
    val s = if (size < 1) 10 else size
    val p = if (page < 1) 1 else page
    return (p - 1) * s + 1 to p * s
}

fun xkPartDisplayDone(rows: List<JSONObject>, size: Int): Boolean {
    if (rows.isEmpty()) return true
    val kcs = rows.mapNotNull { it.optString("kcrow").toIntOrNull() }
    if (kcs.isEmpty()) return rows.size < size
    return (kcs.last() - kcs.first() + 1) < size
}

fun xkRowsOf(d: Any?): List<JSONObject> {
    when (d) {
        is JSONArray -> return (0 until d.length()).mapNotNull { d.optJSONObject(it) }
        is JSONObject -> {
            for (k in listOf("tmpList", "items", "list", "data")) {
                val v = d.opt(k)
                if (v is JSONArray) return (0 until v.length()).mapNotNull { v.optJSONObject(it) }
            }
        }
    }
    return emptyList()
}

fun xkRemain(row: JSONObject): Int {
    val cap = xkInt(row, "jxbrl") ?: xkInt(row, "jxbrs")
    val used = xkInt(row, "yxzrs")
    if (cap != null && cap > 0 && used != null) return (cap - used).coerceAtLeast(0)
    val bl = xkInt(row, "blzyl")
    if (bl != null && bl > 0) return bl
    val bx = xkInt(row, "blyxrs")
    if (bx != null && bx > 0) return bx
    if (cap != null && cap > 0) return cap
    return -1
}

private fun xkInt(row: JSONObject, key: String): Int? {
    val v = row.opt(key) ?: return null
    return v.toString().trim().toIntOrNull()
}

fun xkCollapseByCourse(rows: List<JSONObject>): List<JSONObject> {
    val seen = linkedMapOf<String, JSONObject>()
    val extra = mutableListOf<JSONObject>()
    for (m in rows) {
        val id = m.optString("kch_id")
        if (id.isEmpty()) {
            extra.add(m)
            continue
        }
        val prev = seen[id]
        if (prev == null) {
            seen[id] = m
            continue
        }
        if (xkRemain(m) > xkRemain(prev)) seen[id] = m
    }
    return seen.values.toList() + extra
}

fun buildXkQuery(
    round: XkRound,
    profile: Map<String, String>,
    kcmc: String = "",
    panel: Map<String, String> = emptyMap(),
    page: Int = 1,
    size: Int = 10,
): Map<String, String> {
    val q = mutableMapOf(
        "rwlx" to "1",
        "xklc" to "",
        "xkly" to "0",
        "zyfx_id" to "wfx",
        "xkzgbj" to "0",
        "rlkz" to "0",
        "kzkcgs" to "0",
    )
    val skip = setOf("kspage", "jspage", "globJsPage", "isEnd", "js_kcrow")
    panel.forEach { (k, v) -> if (v.isNotEmpty() && k !in skip) q[k] = v }
    val range = xkPageRange(page, size)
    q["xkkz_id"] = round.xkkzId
    q["xkkz_xh"] = round.xkkzXh
    q["kklxdm"] = round.kklxdm
    q["njdm_id_1"] = profile["njdm_id_1"] ?: round.njdmId
    q["zyh_id_1"] = profile["zyh_id_1"] ?: round.zyhId
    q["zyh_id"] = round.zyhId.ifBlank { profile["zyh_id"].orEmpty() }
    q["njdm_id"] = round.njdmId.ifBlank { profile["njdm_id"].orEmpty() }
    q["zyfx_id"] = profile["zyfx_id"] ?: q["zyfx_id"] ?: "wfx"
    q["xqh_id"] = profile["xqh_id"].orEmpty()
    q["bh_id"] = profile["bh_id"].orEmpty()
    q["xh_id"] = profile["xh_id"].orEmpty()
    q["jg_id"] = profile["jg_id"] ?: profile["jg_id_1"].orEmpty()
    q["xbm"] = profile["xbm"].orEmpty()
    q["xslbdm"] = profile["xslbdm"].orEmpty()
    q["mzm"] = profile["mzm"].orEmpty()
    q["xz"] = profile["xz"].orEmpty()
    q["ccdm"] = profile["ccdm"].orEmpty()
    q["xsbj"] = profile["xsbj"].orEmpty()
    q["xkxnm"] = profile["xkxnm"].orEmpty()
    q["xkxqm"] = profile["xkxqm"].orEmpty()
    q["kcmc"] = kcmc
    q["kspage"] = "${range.first}"
    q["jspage"] = "${range.second}"
    return q
}

fun jsonToXkCourse(m: JSONObject): XkCourse {
    val cap = m.optString("jxbrl").ifBlank { m.optString("jxbrs") }
    val used = m.optString("yxzrs")
    return XkCourse(
        kchId = m.optString("kch_id"),
        name = m.optString("kcmc"),
        credit = m.optString("xf"),
        teacher = m.optString("jsmc").ifBlank { m.optString("jsxx") },
        time = m.optString("sksj"),
        jxbId = m.optString("jxb_id"),
        jxbName = m.optString("jxbmc"),
        remain = xkRemain(m),
        used = used,
        cap = cap,
        doJxbId = m.optString("do_jxb_id"),
        raw = buildMap {
            m.keys().forEach { k -> put(k, m.opt(k)?.toString().orEmpty()) }
        },
    )
}

fun xkSaveCourseBody(
    jxbIds: String,
    kchId: String,
    kcmc: String,
    rwlx: String,
    rlkz: String,
    cdrlkz: String,
    rlzlkz: String,
    sxbj: String,
    xxkbj: String,
    qz: String,
    cxbj: String,
    xkkzId: String,
    njdmId: String,
    zyhId: String,
    kklxdm: String,
    xklc: String,
    xkxnm: String,
    xkxqm: String,
): Map<String, String> = mapOf(
    "jxb_ids" to jxbIds,
    "kch_id" to kchId,
    "kcmc" to kcmc,
    "rwlx" to rwlx,
    "rlkz" to rlkz,
    "cdrlkz" to cdrlkz,
    "rlzlkz" to rlzlkz,
    "sxbj" to sxbj,
    "xxkbj" to xxkbj,
    "qz" to qz,
    "cxbj" to cxbj,
    "xkkz_id" to xkkzId,
    "njdm_id" to njdmId,
    "zyh_id" to zyhId,
    "kklxdm" to kklxdm,
    "xklc" to xklc,
    "xkxnm" to xkxnm,
    "xkxqm" to xkxqm,
    "jcxx_id" to "",
)

fun xkSubmitAlert(resp: JSONObject): String? {
    val flag = resp.opt("flag")?.toString() ?: resp.optString("jg")
    val msg = resp.optString("msg").trim()
    if (flag == "1" || flag == "6" || flag == "3") return null
    if (flag == "-1" || xkLooksCapacityMsg(msg)) return "对不起，该教学班已无余量，不可选！"
    if (flag == "2") return msg.ifBlank { "上课时间冲突" }
    return msg.ifBlank { "选课失败" }
}

fun xkLooksCapacityMsg(msg: String): Boolean {
    if (msg.isEmpty() || Regex("""[\u4e00-\u9fff]""").containsMatchIn(msg)) return false
    val parts = msg.split(',')
    if (parts.size < 3) return false
    return parts.first().trim().toIntOrNull() != null && parts[2].trim().toIntOrNull() != null
}

fun xkOfficialSxbj(rlkz: String, cdrlkz: String, rlzlkz: String): String =
    if (rlkz == "1" || cdrlkz == "1" || rlzlkz == "1") "1" else "0"
