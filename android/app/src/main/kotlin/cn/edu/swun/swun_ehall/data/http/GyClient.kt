package cn.edu.swun.swun_ehall.data.http

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
    private var cas: CasClient? = null

    fun attachCas(c: CasClient) {
        cas = c
    }

    fun loginWithCas(cas: CasClient) {
        this.cas = cas
        token = rs.followSso(cas.ticketFor(K_GY_SERVICE), RsSites.GY)
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

    fun campusFence(): Pair<Double, Double>? {
        val uid = studentNo()
        val json = try {
            api("GET", "/appao/appApi/getPositionListByParams?personId=$uid&type=gcj02")
        } catch (_: Exception) {
            return null
        }
        val list = json.optJSONArray("list") ?: json.optJSONObject("data")?.optJSONArray("list") ?: return null
        for (i in 0 until list.length()) {
            val m = list.optJSONObject(i) ?: continue
            val lat = m.optString("lat").toDoubleOrNull() ?: m.optDouble("lat", 0.0)
            val lng = m.optString("lng").toDoubleOrNull() ?: m.optDouble("lng", 0.0)
            if (lat != 0.0 && lng != 0.0) return lat to lng
        }
        return null
    }

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
