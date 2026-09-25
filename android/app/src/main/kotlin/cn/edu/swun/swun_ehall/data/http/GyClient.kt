package cn.edu.swun.swun_ehall.data.http

import cn.edu.swun.swun_ehall.data.geo.Geo
import cn.edu.swun.swun_ehall.data.model.ClockFence
import cn.edu.swun.swun_ehall.data.model.ClockRecord
import java.util.Base64
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Calendar
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject

const val K_GY = "https://gyglxt.swun.edu.cn"
const val K_GY_SERVICE = "$K_GY/appcas/ssoLogin.jsp"
private const val AES_KEY = "appcoChangeLocat"

class GyClient(private val rs: RsGateway) {
    var token: String? = null
    var username: String? = null
    var onToken: ((String) -> Unit)? = null
    private var cas: CasClient? = null

    fun attachCas(c: CasClient) {
        cas = c
    }

    fun loginWithCas(cas: CasClient) {
        this.cas = cas
        token = rs.followSso(cas.ticketFor(K_GY_SERVICE), RsSites.GY)
        token?.let { onToken?.invoke(it) }
    }

    fun punch(lat: Double, lng: Double, address: String = "武侯校区", taskId: String? = null): JSONObject {
        val uid = studentNo()
        if (uid.isEmpty()) error("没有学号，无法打卡")
        val clockTime = clockTime()
        val inner = JSONObject().apply {
            put("clockTime", clockTime)
            put("lat", lat)
            put("lng", lng)
            put("personId", uid)
            put("location", address)
            put("type", "gcj02")
            put("imgUrl", JSONObject.NULL)
        }.toString()
        val data = JSONObject().apply {
            put("t", System.currentTimeMillis())
            put("userId", uid)
            put("clockTime", clockTime)
            if (!taskId.isNullOrEmpty()) put("taskId", taskId)
            put("data", gyAesEcb(inner))
        }
        return api("POST", "/appao/appApi/saveClockRecordByMobileAction", data.toString())
    }

    fun records(): List<ClockRecord> {
        val uid = studentNo()
        val json = api(
            "GET",
            "/appao/appApi/getStudentClockRecordListByParams?userId=$uid&personId=$uid&currPage=1&pageSize=20",
        )
        val list = json.optJSONObject("page")?.optJSONArray("list")
            ?: json.optJSONArray("list")
            ?: return emptyList()
        val out = mutableListOf<ClockRecord>()
        for (i in 0 until list.length()) {
            val m = list.optJSONObject(i) ?: continue
            out.add(
                ClockRecord(
                    time = m.optString("clockTime").ifBlank { m.optString("createTime") },
                    address = m.optString("address").ifBlank { m.optString("location") },
                    status = m.optString("clockStatus").ifBlank { m.optString("status") },
                ),
            )
        }
        return out
    }

    fun positions(): List<ClockFence> {
        val uid = studentNo()
        val json = try {
            api("GET", "/appao/appApi/getPositionListByParams?personId=$uid&type=gcj02")
        } catch (_: Exception) {
            return emptyList()
        }
        return parseClockFences(json)
    }

    fun campusFence(): Pair<Double, Double>? = positions().firstOrNull()?.let { it.latitude to it.longitude }

    fun openTaskId(): String? {
        val uid = studentNo()
        val json = try {
            api("GET", "/appao/appApi/getMobileScheduleByDate?userId=$uid")
        } catch (_: Exception) {
            return null
        }
        val list = json.optJSONArray("list") ?: return null
        for (i in 0 until list.length()) {
            val t = list.optJSONObject(i) ?: continue
            val open = t.optBoolean("isOpen") || t.opt("checked") == false
            val id = t.optString("id")
            if (open && id.isNotEmpty()) return id
        }
        return list.optJSONObject(0)?.optString("id")?.takeIf { it.isNotEmpty() }
    }

    private fun studentNo(): String {
        if (!username.isNullOrEmpty()) return username!!
        try {
            val r = api("GET", "/appsys/sys/user/info")
            val u = r.optJSONObject("user")?.optString("username").orEmpty()
            if (u.isNotEmpty()) username = u
        } catch (_: Exception) {
        }
        return username.orEmpty()
    }

    private fun headers(): Map<String, String> = buildMap {
        put("Accept", "application/json, text/plain, */*")
        put("Content-Type", "application/json; charset=utf-8")
        val t = token
        if (!t.isNullOrEmpty()) put("token", t)
    }

    private fun api(method: String, path: String, body: String? = null, retry401: Boolean = true): JSONObject {
        val sep = if (path.contains('?')) "&" else "?"
        val url = "$K_GY$path${sep}t=${System.currentTimeMillis()}"
        val hit = rs.call(method, url, headers(), body)
        val map = try {
            hit.asJson()
        } catch (_: Exception) {
            if (CampusHttp.looksRuishu(hit.status, hit.body)) error("公寓系统仍被网关拦截")
            error("$path 非 JSON")
        }
        if (gyTokenExpired(map, hit.status)) {
            if (retry401 && cas != null) {
                token = null
                loginWithCas(cas!!)
                return api(method, path, body, retry401 = false)
            }
            error(map.optString("msg").ifBlank { "公寓登录已过期，请重试" })
        }
        return map
    }

    }

/** Same threshold as the Flutter clock page: nearest position within 800 m counts as in range. */
const val CLOCK_FENCE_METERS = 800.0

fun parseClockFences(json: JSONObject): List<ClockFence> {
    val list = json.optJSONArray("list")
        ?: json.optJSONObject("data")?.optJSONArray("list")
        ?: json.optJSONArray("data")
        ?: return emptyList()
    val out = ArrayList<ClockFence>(list.length())
    for (i in 0 until list.length()) {
        val m = list.optJSONObject(i) ?: continue
        val lat = jsonNumber(m, "lat", "latitude") ?: continue
        val lng = jsonNumber(m, "lng", "longitude") ?: continue
        if (lat !in -90.0..90.0 || lng !in -180.0..180.0) continue
        if (lat == 0.0 && lng == 0.0) continue
        val radius = clockFenceRadiusMeters(m) ?: CLOCK_FENCE_METERS
        val named = jsonText(m, "positionName", "name", "address", "location")
        val polygon = fencePolygon(m)
        out.add(
            ClockFence(
                name = named.ifBlank { if (list.length() == 1) "打卡范围" else "打卡范围 ${out.size + 1}" },
                latitude = lat,
                longitude = lng,
                radiusMeters = if (polygon.size >= 3) 0.0 else radius,
                polygon = polygon,
            ),
        )
    }
    return collapseFenceVertices(out)
}

private val polygonKeys = arrayOf(
    "pointList", "points", "polygon", "area", "path", "latLngs", "latlngs",
    "fencePoints", "positionList", "coordinateList", "coordinates", "locs",
)

private fun fencePolygon(m: JSONObject): List<Pair<Double, Double>> {
    for (key in polygonKeys) {
        if (!m.has(key) || m.isNull(key)) continue
        val array = m.optJSONArray(key)
        if (array != null) {
            val pts = pointsOf(array)
            if (pts.size >= 3) return pts
        }
        val text = m.optString(key)
        val pts = pointsOfText(text)
        if (pts.size >= 3) return pts
    }
    return emptyList()
}

private fun pointsOf(array: org.json.JSONArray): List<Pair<Double, Double>> {
    val out = ArrayList<Pair<Double, Double>>(array.length())
    for (i in 0 until array.length()) {
        val obj = array.optJSONObject(i)
        if (obj != null) {
            val lat = jsonNumber(obj, "lat", "latitude")
            val lng = jsonNumber(obj, "lng", "longitude")
            if (lat != null && lng != null) {
                val p = latLng(lat, lng)
                if (p != null) out.add(p)
            }
            continue
        }
        val nested = array.optJSONArray(i)
        if (nested != null && nested.length() >= 2) {
            val a = nested.optDouble(0, Double.NaN)
            val b = nested.optDouble(1, Double.NaN)
            val p = latLng(a, b)
            if (p != null) out.add(p)
        }
    }
    return out
}

private fun pointsOfText(text: String): List<Pair<Double, Double>> {
    if (!text.contains(',') && !text.contains('，')) return emptyList()
    val out = ArrayList<Pair<Double, Double>>()
    for (part in text.split(';', '|', '\n')) {
        val bits = part.split(',', '，').map { it.trim() }.filter { it.isNotEmpty() }
        if (bits.size < 2) continue
        val a = bits[0].toDoubleOrNull() ?: continue
        val b = bits[1].toDoubleOrNull() ?: continue
        val p = latLng(a, b) ?: continue
        out.add(p)
    }
    return out
}

private fun latLng(a: Double, b: Double): Pair<Double, Double>? {
    if (!a.isFinite() || !b.isFinite()) return null
    val (lat, lng) = if (kotlin.math.abs(a) > 90.0) b to a else a to b
    if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
    if (lat == 0.0 && lng == 0.0) return null
    return lat to lng
}

/** A list of bare coordinates close together is one fence outline, not a pile of pins. */
internal fun collapseFenceVertices(fences: List<ClockFence>): List<ClockFence> {
    if (fences.size < 3) return fences
    if (fences.any { it.polygon.size >= 3 }) return fences
    if (fences.any { it.radiusMeters != CLOCK_FENCE_METERS }) return fences
    val origin = fences.first()
    if (fences.any { Geo.meters(origin.latitude, origin.longitude, it.latitude, it.longitude) > 3_000.0 }) {
        return fences
    }
    val polygon = fences.map { it.latitude to it.longitude }
    return listOf(
        ClockFence(
            name = "打卡范围",
            latitude = polygon.map { it.first }.average(),
            longitude = polygon.map { it.second }.average(),
            radiusMeters = 0.0,
            polygon = polygon,
        ),
    )
}

private fun clockFenceRadiusMeters(m: JSONObject): Double? {
    for (key in arrayOf("radius", "range", "distance", "scope", "effectiveRange", "clockRadius")) {
        val raw = jsonNumber(m, key) ?: continue
        if (raw <= 0.0 || !raw.isFinite()) continue
        val meters = if (raw <= 20.0) raw * 1000.0 else raw
        if (meters in 30.0..20_000.0) return meters
    }
    return null
}

private fun jsonNumber(m: JSONObject, vararg keys: String): Double? {
    for (key in keys) {
        if (!m.has(key) || m.isNull(key)) continue
        val n = m.optString(key).trim().toDoubleOrNull() ?: m.optDouble(key, Double.NaN)
        if (n.isFinite()) return n
    }
    return null
}

private fun jsonText(m: JSONObject, vararg keys: String): String {
    for (key in keys) {
        val s = m.optString(key).trim()
        if (s.isNotEmpty() && s != "null") return s
    }
    return ""
}

fun gyTokenFrom(raw: String): String? {
    extractUrlToken(raw, "token")?.let { return it }
    return Regex("""casredirectsvc\?token=([A-Za-z0-9_-]{16,})""").find(raw)?.groupValues?.get(1)
}

fun gyTokenExpired(map: JSONObject, httpStatus: Int): Boolean {
    if (httpStatus == 401 || httpStatus == 403) return true
    val code = map.opt("code")?.toString().orEmpty()
    if (code == "401" || code == "403") return true
    val msg = map.optString("msg") + map.optString("message")
    if (msg.contains("请重新登录") || msg.contains("未登录") || msg.contains("登录失效") || msg.contains("登录过期")) return true
    return (msg.contains("token", true) || msg.contains("令牌")) && (msg.contains("失效") || msg.contains("过期"))
}

fun gyAesEcb(plain: String): String {
    val c = Cipher.getInstance("AES/ECB/PKCS5Padding")
    c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(AES_KEY.toByteArray(), "AES"))
    return Base64.getEncoder().encodeToString(c.doFinal(plain.toByteArray()))
}

private fun clockTime(): String {
    val n = Calendar.getInstance()
    fun two(v: Int) = String.format(Locale.US, "%02d", v)
    return "${n.get(Calendar.YEAR)}-${two(n.get(Calendar.MONTH) + 1)}-${two(n.get(Calendar.DAY_OF_MONTH))} " +
        "${two(n.get(Calendar.HOUR_OF_DAY))}:${two(n.get(Calendar.MINUTE))}:${two(n.get(Calendar.SECOND))}"
}
