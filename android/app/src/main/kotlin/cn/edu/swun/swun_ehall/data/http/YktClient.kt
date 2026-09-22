package cn.edu.swun.swun_ehall.data.http

import android.util.Log
import cn.edu.swun.swun_ehall.data.model.YktBill
import org.json.JSONArray
import org.json.JSONObject

const val K_YKT = "https://ykth5.swun.edu.cn"
private const val K_YKT_MENU = "$K_YKT/menu/menu.do?menu=qrcode"
private const val K_YKT_QR = "$K_YKT/qrcode/queryCardInfo.do"
private const val K_YKT_BILL = "$K_YKT/bill/fun_bill.do"

class YktClient(private val rs: RsGateway) {
    fun fetchQr(studentId: String, schoolId: String = "187"): String {
        if (studentId.isBlank()) error("没有学号，无法打开一卡通")
        val fn = openFunction(studentId, schoolId, "qrcode")
        Thread.sleep(280)
        val hit = rs.call(
            "POST",
            K_YKT_QR,
            headers = xhrHeaders(fn),
            inPage = true,
        )
        Log.d("swun", "[ykt] qr HTTP ${hit.status} ${hit.body.length}b ${hit.body.take(200)}")
        if (CampusHttp.looksNightClosed(hit.status, hit.body)) error("一卡通夜间关闭或不在服务时间")
        if (CampusHttp.looksRuishu(hit.status, hit.body)) error("一卡通二维码仍被网关拦截")
        return payloadOf(hit.body) ?: error("用户无卡片，无法生成二维码")
    }

    fun fetchLedger(studentId: String, schoolId: String = "187"): Pair<Double?, List<YktBill>> {
        if (studentId.isBlank()) error("没有学号，无法打开一卡通")
        val fn = openFunction(studentId, schoolId, "bill")
        Thread.sleep(280)
        val hit = rs.call(
            "POST",
            K_YKT_BILL,
            headers = xhrHeaders(fn),
            body = "date=null",
            inPage = true,
        )
        Log.d("swun", "[ykt] bill HTTP ${hit.status} ${hit.body.length}b ${hit.body.take(400)}")
        if (CampusHttp.looksNightClosed(hit.status, hit.body)) error("一卡通夜间关闭或不在服务时间")
        if (CampusHttp.looksRuishu(hit.status, hit.body)) error("一卡通明细仍被网关拦截")
        val items = parseYktBills(hit.body)
        val bal = items.firstOrNull { it.balanceYuan != null }?.balanceYuan
        return bal to items
    }

    fun fetchBalance(studentId: String, schoolId: String = "187"): Double? {
        if (studentId.isBlank()) error("没有学号，无法打开一卡通")
        openFunction(studentId, schoolId, "data")
        Thread.sleep(200)
        val raw = rs.evalJs(
            "ykth5.swun.edu.cn",
            """
            try {
              var ps = document.getElementsByTagName('p');
              for (var i = 0; i < ps.length; i++) {
                var t = ps[i].innerText || '';
                if (t.indexOf('账户余额') >= 0) {
                  var inp = ps[i].querySelector('input');
                  if (inp && inp.value) return String(inp.value);
                  return t;
                }
              }
              return '';
            } catch (e) { return ''; }
            """.trimIndent(),
        )
        Log.d("swun", "[ykt] balance raw=$raw")
        return parseYktYuan(raw)
    }

    private fun xhrHeaders(referer: String) = mapOf(
        "Accept" to "application/json, text/javascript, */*; q=0.01",
        "X-Requested-With" to "XMLHttpRequest",
        "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8",
        "Referer" to referer,
    )

    private fun openFunction(studentId: String, schoolId: String, menu: String): String {
        rs.open(K_YKT_MENU, RsWait.Ready, RsSites.YKT)
        val ts = menuTimestamp()
        if (ts.isEmpty()) error("一卡通页面未就绪")
        val fn = "$K_YKT/menu/function.do?expire=$ts&stu_code=$studentId&acco_id=$studentId&school_id=$schoolId&menu=$menu"
        Log.d("swun", "[ykt] open $menu ts=$ts")
        rs.open(fn, RsWait.Ready, RsSites.YKT)
        return fn
    }

    private fun menuTimestamp(): String {
        repeat(12) {
            val ts = rs.evalJs(
                "ykth5.swun.edu.cn",
                """
                try {
                  if (typeof timestamp === 'string' && timestamp.length) return timestamp;
                  if (window.timestamp) return String(window.timestamp);
                  var h = document.documentElement ? document.documentElement.innerHTML : '';
                  var m = h.match(/var\s+timestamp\s*=\s*'([^']+)'/);
                  return m ? m[1] : '';
                } catch (e) { return ''; }
                """.trimIndent(),
            ).trim()
            if (ts.isNotEmpty() && ts != "null") return ts
            Thread.sleep(250)
        }
        return ""
    }
}

fun payloadOf(body: String): String? {
    val t = body.trim()
    if (t.isEmpty() || t == "null") return null
    extractPayload(parseLooseJson(t))?.let { return it }
    if (t.startsWith("\"") && t.endsWith("\"") && t.length > 2) return t.substring(1, t.length - 1)
    if (t.startsWith("<")) return null
    return t
}

private fun parseLooseJson(t: String): Any? {
    return try {
        when {
            t.startsWith("{") -> JSONObject(t)
            t.startsWith("[") -> JSONArray(t)
            t.startsWith("\"") && t.endsWith("\"") && t.length > 2 -> t.substring(1, t.length - 1)
            else -> t
        }
    } catch (_: Exception) {
        t
    }
}

private fun extractPayload(d: Any?): String? {
    when (d) {
        null, JSONObject.NULL -> return null
        is String -> return d.trim().takeIf { it.isNotEmpty() && it != "null" }
        is Number -> return d.toString()
        is JSONObject -> {
            for (k in listOf("qrcode", "qrCode", "code", "data", "barCode", "barcode", "msg")) {
                if (!d.has(k) || d.isNull(k)) continue
                extractPayload(d.opt(k))?.let { return it }
            }
        }
        is JSONArray -> if (d.length() > 0) return extractPayload(d.opt(0))
    }
    return null
}

fun parseYktYuan(raw: String): Double? {
    val m = Regex("""(-?\d+(?:\.\d+)?)""").find(raw.replace(",", "")) ?: return null
    return m.groupValues[1].toDoubleOrNull()
}

fun parseYktBills(raw: String): List<YktBill> {
    val s = raw.trim()
    if (s.isEmpty()) return emptyList()
    if (s.startsWith("{") || s.startsWith("[")) {
        return try {
            parseYktBillsJson(if (s.startsWith("[")) JSONArray(s) else JSONObject(s))
        } catch (_: Exception) {
            parseYktBillsFromHtml(s)
        }
    }
    return parseYktBillsFromHtml(s)
}

private fun parseYktBillsJson(raw: Any): List<YktBill> {
    when (raw) {
        is JSONArray -> {
            val out = mutableListOf<YktBill>()
            for (i in 0 until raw.length()) {
                val o = raw.optJSONObject(i) ?: continue
                billFromMap(o)?.let { out.add(it) }
            }
            return out
        }
        is JSONObject -> {
            for (k in listOf("bill", "list", "rows", "data", "items", "tmpList", "result")) {
                val v = raw.opt(k)
                if (v is JSONArray || v is JSONObject) {
                    val nested = parseYktBillsJson(v)
                    if (nested.isNotEmpty()) return nested
                }
            }
            return billFromMap(raw)?.let { listOf(it) } ?: emptyList()
        }
        else -> return emptyList()
    }
}

private fun billFromMap(m: JSONObject): YktBill? {
    if (m.opt("cells") is JSONArray) {
        val cells = m.getJSONArray("cells")
        val list = (0 until cells.length()).map { cells.opt(it)?.toString()?.trim().orEmpty() }
        return billFromCells(list)
    }
    val text = m.optString("text").trim()
    if (text.isNotEmpty()) return billFromLine(text)
    val titleKeys = listOf("generalOperateTypeName", "dealName", "mercName", "shmc", "title", "remark", "recName", "typeName", "jyzy", "name")
    val amountKeys = listOf("consumeAmount", "monDeal", "txamt", "amount", "jyje", "dealMoney", "money", "txAmt", "je")
    val timeKeys = listOf("consumeTime", "dealTime", "jysj", "occTime", "recDate", "time", "txdate", "date")
    val balKeys = listOf("accStatus", "ye", "balance", "accBal", "balanceYuan")
    val area = m.optString("area")
    val branch = m.optString("tradeBranchName")
    var title = listOf(area, branch).filter { it.isNotBlank() }.joinToString("-")
    if (title.isEmpty()) title = firstNonEmpty(m, titleKeys)
    val amountRaw = firstNonEmpty(m, amountKeys)
    var amount = yuanOf(m, amountKeys) ?: return null
    val type = firstNonEmpty(m, listOf("generalOperateTypeName", "typeName", "dealType"))
    val signed = amountRaw.startsWith("+") || amountRaw.startsWith("-")
    if (!signed && amount > 0 && yktSpendType(type)) amount = -amount
    val time = firstNonEmpty(m, timeKeys)
    val bal = yuanOf(m, balKeys)
    return YktBill(title.ifBlank { type.ifBlank { "消费" } }, time, amount, bal)
}

private fun billFromCells(cells: List<String>): YktBill? {
    val joined = cells.joinToString(" ")
    if (Regex("""时间|商户|金额|余额|摘要""").containsMatchIn(joined) && !Regex("""-?\d+\.\d{2}""").containsMatchIn(joined)) {
        return null
    }
    return billFromLine(joined, cells)
}

private fun parseYktBillsFromHtml(text: String): List<YktBill> {
    val lines = text.split(Regex("""[\r\n]+""")).map { it.replace(Regex("""\s+"""), " ").trim() }.filter { it.isNotEmpty() }
    return lines.mapNotNull { billFromLine(it) }
}

private fun billFromLine(line: String, cells: List<String>? = null): YktBill? {
    val amount = yuanIn(line) ?: return null
    if (line.contains("账户余额") && !Regex("""[+-]\d+\.\d{2}""").containsMatchIn(line) && cells == null) return null
    val time = Regex("""(\d{4}[-/]\d{1,2}[-/]\d{1,2}(?:\s+\d{1,2}:\d{2}(?::\d{2})?)?)""").find(line)?.groupValues?.get(1).orEmpty()
    var title = line
    if (time.isNotEmpty()) title = title.replaceFirst(time, "")
    title = title.replace(Regex("""[+-]?\d+(?:\.\d+)?\s*元?"""), " ").replace(Regex("""\s+"""), " ").trim()
    if (title.isEmpty() && cells != null) {
        title = cells.firstOrNull { it != time && yuanIn(it) == null && it.isNotBlank() }.orEmpty()
    }
    val bal = if (cells != null && cells.size >= 4) yuanIn(cells.last()) else null
    return YktBill(title.ifBlank { "消费" }, time, amount, bal)
}

private fun yktSpendType(type: String): Boolean =
    type.contains("消费") || type.contains("扣款") || type.contains("扣费") || type.contains("支出") || type.contains("罚款")

private fun yuanIn(raw: String): Double? {
    val s = raw.replace(",", "")
    Regex("""(?<![\d.])([+-]?\d+\.\d{2})(?![\d])""").find(s)?.groupValues?.get(1)?.toDoubleOrNull()?.let { return it }
    Regex("""([+-]?\d+(?:\.\d+)?)\s*元""").find(s)?.groupValues?.get(1)?.toDoubleOrNull()?.let { return it }
    return null
}

private fun firstNonEmpty(m: JSONObject, keys: List<String>): String {
    for (k in keys) {
        val v = m.opt(k)?.toString()?.trim().orEmpty()
        if (v.isNotEmpty() && v != "null") return v
    }
    return ""
}

private fun yuanOf(m: JSONObject, keys: List<String>): Double? {
    for (k in keys) {
        val v = m.opt(k) ?: continue
        val raw = v.toString().replace(",", "").replace("元", "").trim()
        raw.toDoubleOrNull()?.let { return it }
        yuanIn(v.toString())?.let { return it }
    }
    return null
}
