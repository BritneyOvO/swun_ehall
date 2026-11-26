package cn.edu.swun.swun_ehall.data.http

import android.util.Log
import cn.edu.swun.swun_ehall.data.model.ElectricSelection
import cn.edu.swun.swun_ehall.data.model.YktBill
import cn.edu.swun.swun_ehall.data.model.YktDirectoryOption
import cn.edu.swun.swun_ehall.data.model.YktPayMethod
import cn.edu.swun.swun_ehall.data.model.YktPayOrder
import cn.edu.swun.swun_ehall.data.model.YktPaymentResult
import cn.edu.swun.swun_ehall.data.model.YktRechargeAccount
import java.math.BigDecimal
import java.net.URLEncoder
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

const val K_YKT = "https://ykth5.swun.edu.cn"
private const val K_YKT_MENU = "$K_YKT/menu/menu.do?menu=qrcode"
private const val K_YKT_QR = "$K_YKT/qrcode/queryCardInfo.do"
private const val K_YKT_BILL = "$K_YKT/bill/fun_bill.do"
private const val K_YKT_CARD_RECHARGE = "$K_YKT/app/poscardcircle.do"
private const val K_YKT_WATER_RECHARGE = "$K_YKT/water/watertransferpay.do"
private const val K_YKT_ELECTRIC_SCHOOL = "$K_YKT/app/electric_school.do"
private const val K_YKT_ELECTRIC_BUILDING = "$K_YKT/app/electric_building.do"
private const val K_YKT_ELECTRIC_FLOOR = "$K_YKT/app/electric_floor.do"
private const val K_YKT_ELECTRIC_ROOM = "$K_YKT/app/electric_room.do"
private const val K_YKT_ELECTRIC_PAY = "$K_YKT/app/poscardpay.do"

class YktClient(private val rs: RsGateway) {
    fun fetchQr(studentId: String, schoolId: String = "187"): String {
        if (studentId.isBlank()) error("没有学号，无法打开一卡通")
        val fn = openFunction(studentId, schoolId, "qrcode")
        val hit = rs.call(
            "POST",
            K_YKT_QR,
            headers = xhrHeaders(fn),
            inPage = true,
        )
        Log.d("swun", "[ykt] qr HTTP ${hit.status} ${hit.body.length}b ${hit.body.take(200)}")
        if (CampusHttp.looksNightClosed(hit.status, hit.body) || CampusHttp.looksRuishu(hit.status, hit.body) || payloadOf(hit.body) == null) {
            SwunLog.api("一卡通二维码", hit.status, hit.url, hit.body)
        }
        throwYktResponseIssue(hit, "二维码")
        return payloadOf(hit.body) ?: error("用户无卡片，无法生成二维码")
    }

    fun fetchLedger(studentId: String, schoolId: String = "187", date: LocalDate? = null): Pair<Double?, List<YktBill>> {
        if (studentId.isBlank()) error("没有学号，无法打开一卡通")
        val fn = openFunction(studentId, schoolId, "bill")
        val hit = rs.call(
            "POST",
            K_YKT_BILL,
            headers = xhrHeaders(fn),
            body = yktLedgerBody(date),
            inPage = true,
        )
        Log.d("swun", "[ykt] bill HTTP ${hit.status} ${hit.body.length}b ${hit.body.take(400)}")
        if (CampusHttp.looksNightClosed(hit.status, hit.body) || CampusHttp.looksRuishu(hit.status, hit.body)) {
            SwunLog.api("一卡通明细", hit.status, hit.url, hit.body)
        }
        throwYktResponseIssue(hit, "明细")
        val items = parseYktBills(hit.body)
        val bal = items.firstOrNull { it.balanceYuan != null }?.balanceYuan
        return bal to items
    }

    fun fetchBalance(studentId: String, schoolId: String = "187"): Double? {
        if (studentId.isBlank()) error("没有学号，无法打开一卡通")
        openFunction(studentId, schoolId, "data")
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

    fun fetchRechargeAccount(studentId: String, schoolId: String = "187"): YktRechargeAccount {
        openFunction(studentId, schoolId, "transfer")
        return currentRechargeAccount(studentId)
    }

    fun createCardRecharge(
        studentId: String,
        amountYuan: Double,
        method: YktPayMethod,
        schoolId: String = "187",
    ): YktPaymentResult {
        requireYuan(amountYuan, 200.0, "圈存金额")
        val referer = openFunction(studentId, schoolId, "transfer")
        val body = formBody(
            "payMethod" to method.code,
            "payMoney" to amountYuan.payText(),
        )
        return paymentCall(K_YKT_CARD_RECHARGE, referer, body, PaymentKind.Card)
    }

    fun fetchWaterAccount(studentId: String, schoolId: String = "187"): YktRechargeAccount {
        openFunction(studentId, schoolId, "watertransfer")
        val account = currentRechargeAccount(studentId)
        val balance = account.balanceYuan ?: fetchBalance(studentId, schoolId)
            ?: error("未能读取一卡通余额")
        return account.copy(balanceYuan = balance)
    }

    fun rechargeWater(
        studentId: String,
        amountYuan: Double,
        cardBalanceYuan: Double?,
        schoolId: String = "187",
    ): YktPaymentResult {
        requireYuan(amountYuan, 20.0, "热水充值金额")
        if (cardBalanceYuan != null && amountYuan > cardBalanceYuan + 0.0001) {
            error("一卡通余额不足")
        }
        val referer = openFunction(studentId, schoolId, "watertransfer")
        val money = amountYuan.payText()
        val body = formBody(
            "payMethod" to "poscardpay",
            "payMoney" to money,
            "money" to money,
        )
        return paymentCall(K_YKT_WATER_RECHARGE, referer, body, PaymentKind.Water)
    }

    fun electricSchools(studentId: String, schoolId: String = "187"): List<YktDirectoryOption> {
        prepareElectric(studentId, schoolId)
        val page = "$K_YKT/app/school.do"
        rs.open(page, RsWait.Ready, RsSites.YKT)
        return electricOptions(K_YKT_ELECTRIC_SCHOOL, page, "")
    }

    fun electricBuildings(school: YktDirectoryOption): List<YktDirectoryOption> {
        val page = "$K_YKT/app/building.do?dormid=${enc(school.id)}&dormname=${enc(school.name)}"
        rs.open(page, RsWait.Ready, RsSites.YKT)
        return electricOptions(K_YKT_ELECTRIC_BUILDING, page, formBody("dormid" to school.id))
    }

    fun electricFloors(building: YktDirectoryOption): List<YktDirectoryOption> {
        val page = "$K_YKT/app/floor.do?buildingid=${enc(building.id)}&buildingname=${enc(building.name)}"
        rs.open(page, RsWait.Ready, RsSites.YKT)
        return electricOptions(K_YKT_ELECTRIC_FLOOR, page, formBody("buildingid" to building.id))
    }

    fun electricRooms(floor: YktDirectoryOption): List<YktDirectoryOption> {
        val page = "$K_YKT/app/room.do?floorid=${enc(floor.id)}&floorname=${enc(floor.name)}"
        rs.open(page, RsWait.Ready, RsSites.YKT)
        return electricOptions(K_YKT_ELECTRIC_ROOM, page, formBody("floorid" to floor.id))
    }

    fun createElectricRecharge(
        studentId: String,
        selection: ElectricSelection,
        amountYuan: Double,
        method: YktPayMethod,
        schoolId: String = "187",
    ): YktPaymentResult {
        requireYuan(amountYuan, 200.0, "电费充值金额")
        // The old server stores each selected directory level in the HTTP session.
        // Replaying the official pages immediately before ordering prevents a stale room order.
        prepareElectric(studentId, schoolId)
        rs.open("$K_YKT/app/school.do", RsWait.Ready, RsSites.YKT)
        rs.open(
            "$K_YKT/app/building.do?dormid=${enc(selection.school.id)}&dormname=${enc(selection.school.name)}",
            RsWait.Ready,
            RsSites.YKT,
        )
        rs.open(
            "$K_YKT/app/floor.do?buildingid=${enc(selection.building.id)}&buildingname=${enc(selection.building.name)}",
            RsWait.Ready,
            RsSites.YKT,
        )
        rs.open(
            "$K_YKT/app/room.do?floorid=${enc(selection.floor.id)}&floorname=${enc(selection.floor.name)}",
            RsWait.Ready,
            RsSites.YKT,
        )
        val referer = "$K_YKT/app/electric.do?roomid=${enc(selection.room.id)}&roomname=${enc(selection.room.name)}"
        rs.open(referer, RsWait.Ready, RsSites.YKT)
        val body = formBody(
            "payMethod" to method.code,
            "orderid" to "",
            "payMinAmount" to "",
            "payMaxAmount" to "",
            "payType" to "",
            "payWay" to "",
            "timeout" to "",
            "money" to amountYuan.payText(),
            method.code to method.code,
        )
        return paymentCall(K_YKT_ELECTRIC_PAY, referer, body, PaymentKind.Electric)
    }

    private fun prepareElectric(studentId: String, schoolId: String) {
        // Any valid function page establishes the account-bound ykt session. The electric
        // function endpoint itself currently returns an empty page, so use the stable data page.
        openFunction(studentId, schoolId, "data")
        rs.open("$K_YKT/app/electric.do?studentId=${enc(studentId)}", RsWait.Ready, RsSites.YKT)
    }

    private fun currentRechargeAccount(studentId: String): YktRechargeAccount {
        val raw = rs.evalJs(
            "ykth5.swun.edu.cn",
            """
            try {
              var text = document.body ? (document.body.innerText || '') : '';
              return JSON.stringify({text: text});
            } catch (e) { return '{}'; }
            """.trimIndent(),
        )
        return parseYktRechargeAccount(raw, studentId)
    }

    private fun electricOptions(url: String, referer: String, body: String): List<YktDirectoryOption> {
        val hit = rs.call(
            "POST",
            url,
            headers = xhrHeaders(referer),
            body = body,
            inPage = true,
        )
        throwYktResponseIssue(hit, "电费目录")
        return parseElectricOptions(hit.body)
    }

    private fun paymentCall(url: String, referer: String, body: String, kind: PaymentKind): YktPaymentResult {
        val hit = rs.call(
            "POST",
            url,
            headers = xhrHeaders(referer),
            body = body,
            inPage = true,
        )
        throwYktResponseIssue(hit, "充值")
        return parseYktPaymentResponse(hit.body, kind)
    }

    private fun xhrHeaders(referer: String) = mapOf(
        "Accept" to "application/json, text/javascript, */*; q=0.01",
        "X-Requested-With" to "XMLHttpRequest",
        "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8",
        "Referer" to referer,
    )

    private fun openFunction(studentId: String, schoolId: String, menu: String): String {
        val started = System.nanoTime()
        rs.open(K_YKT_MENU, RsWait.Ready, RsSites.YKT)
        val menuOpened = System.nanoTime()
        if (menuIsClosed()) error("一卡通夜间关闭或不在服务时间")
        val ts = menuTimestamp()
        val timestampRead = System.nanoTime()
        Log.d("swun", "[ykt] $menu menu=${(menuOpened - started) / 1_000_000}ms timestamp=${(timestampRead - menuOpened) / 1_000_000}ms")
        if (ts.isEmpty()) {
            val state = rs.evalJs(
                "ykth5.swun.edu.cn",
                """
                try {
                  var h = document.documentElement ? document.documentElement.innerHTML : '';
                  var t = document.body ? (document.body.innerText || '') : '';
                  return JSON.stringify({title: document.title || '', ready: document.readyState || '',
                    global: typeof timestamp, hasToken: /timestamp/i.test(h),
                    single: /timestamp\\s*=\\s*'/.test(h), double: /timestamp\\s*=\\s*\"/.test(h),
                    input: !!document.querySelector('[name=timestamp]'), closed: /服务时间|夜间|维护中/.test(t),
                    blocked: /访问验证|访问受限|安全检查|网关/.test(t), login: /登录|认证|会话过期/.test(t)});
                } catch (e) { return String(e); }
                """.trimIndent(),
            )
            Log.w("swun", "[ykt] menu timestamp unavailable state=$state")
            error("一卡通页面未就绪")
        }
        val fn = "$K_YKT/menu/function.do?expire=$ts&stu_code=$studentId&acco_id=$studentId&school_id=$schoolId&menu=$menu"
        Log.d("swun", "[ykt] open $menu ts=$ts")
        rs.open(fn, RsWait.Ready, RsSites.YKT)
        Log.d("swun", "[ykt] $menu function page=${(System.nanoTime() - timestampRead) / 1_000_000}ms")
        return fn
    }

    private fun menuIsClosed(): Boolean {
        val marker = rs.evalJs(
            "ykth5.swun.edu.cn",
            """
            try {
              var title = document.title || '';
              var text = document.body ? (document.body.innerText || '') : '';
              return /(?:403|系统已关闭|夜间关闭|不在服务时间|维护中)/i.test(title + ' ' + text) ? '1' : '';
            } catch (e) { return ''; }
            """.trimIndent(),
        ).trim()
        if (marker == "1") {
            Log.w("swun", "[ykt] menu reports scheduled closure")
            return true
        }
        return false
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

private enum class PaymentKind { Card, Water, Electric }

internal fun requireYuan(amount: Double, max: Double, label: String) {
    if (!amount.isFinite() || amount <= 0.0) error("请输入有效的$label")
    if (amount > max + 0.0001) error("${label}不能超过 ${max.payText()} 元")
    if (BigDecimal.valueOf(amount).scale() > 2) error("金额最多保留两位小数")
}

private fun Double.payText(): String = BigDecimal.valueOf(this).stripTrailingZeros().toPlainString()

private fun enc(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

private fun formBody(vararg values: Pair<String, String>): String = values.joinToString("&") {
    "${enc(it.first)}=${enc(it.second)}"
}

fun parseYktRechargeAccount(raw: String, fallbackStudentId: String = ""): YktRechargeAccount {
    val text = runCatching { JSONObject(raw).optString("text") }.getOrDefault(raw)
    fun field(label: String): String = Regex("""$label[：:]\s*([^\n]+)""")
        .find(text)?.groupValues?.getOrNull(1)?.trim().orEmpty()
    val id = field("用户编号").replace(Regex("""\s+"""), "").ifBlank { fallbackStudentId }
    val name = field("用户姓名").substringBefore(" ").trim()
    val balance = parseYktYuan(field("用户余额"))
    return YktRechargeAccount(name, id, balance)
}

fun parseElectricOptions(raw: String): List<YktDirectoryOption> {
    val root = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyList()
    val rows = root.optJSONArray("room") ?: return emptyList()
    return buildList {
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            val id = row.optString("elecid").trim()
            val name = row.optString("elecname").trim()
            if (id.isNotEmpty() && name.isNotEmpty()) add(YktDirectoryOption(id, name))
        }
    }
}

private fun parseYktPaymentResponse(raw: String, kind: PaymentKind): YktPaymentResult {
    val text = raw.trim().trim('"')
    if (text.startsWith("{")) {
        val json = runCatching { JSONObject(text) }.getOrNull()
        val orderInfo = json?.optString("orderInfo").orEmpty()
        if (orderInfo.isNotBlank()) {
            return YktPaymentResult.OrderCreated(
                YktPayOrder(json?.optString("payType").orEmpty(), orderInfo),
            )
        }
    }
    if (text == "true") {
        val message = when (kind) {
            PaymentKind.Card -> "圈存成功"
            PaymentKind.Water -> "热水充值成功"
            PaymentKind.Electric -> "电费充值成功"
        }
        return YktPaymentResult.Success(message)
    }
    val message = when (text) {
        "studentfalse" -> "一卡通账户状态异常，无法充值"
        "cardfalse" -> "用户卡片状态异常，无法充值"
        "timeout" -> "当前时间不允许充值"
        "webapifalse" -> "连接控水前置机失败，无法充值"
        "checkuseridfalse" -> "热水预下单失败，无法充值"
        "payqrcodepayfalse" -> "订单通知失败，请到官方系统核对"
        "false", "" -> "充值失败，请稍后再试"
        else -> "充值失败：$text"
    }
    return YktPaymentResult.Failure(message)
}

internal fun yktLedgerBody(date: LocalDate?): String = "date=${date?.toString() ?: "null"}"

private fun throwYktResponseIssue(hit: RsHit, action: String) {
    when (CampusHttp.classifyResponse(hit.status, hit.body)) {
        CampusResponseIssue.PortalClosed -> error("一卡通夜间关闭或不在服务时间")
        CampusResponseIssue.GatewayBlocked -> error("一卡通${action}仍被网关拦截")
        CampusResponseIssue.AuthenticationExpired -> error("一卡通登录已过期，请重试")
        CampusResponseIssue.NetworkFailure -> error("请求失败，请检查网络连接")
        CampusResponseIssue.HttpFailure -> error("一卡通 HTTP ${hit.status}")
        null -> Unit
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
