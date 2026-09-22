package cn.edu.swun.swun_ehall.data.http

import cn.edu.swun.swun_ehall.data.model.Lesson
import cn.edu.swun.swun_ehall.data.model.Profile
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.KeyFactory
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import org.json.JSONArray
import org.json.JSONObject

private const val APP_KEY = "GiITvn"
private const val BASE = "https://app.swun.edu.cn/baseCampus/"
private const val JW = "https://app.swun.edu.cn/jwCampus/"
private const val INFO = "https://app.swun.edu.cn/infoCampus/"
private const val UA =
    "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/107.0.0.0 Mobile Safari/537.36 lantuMobilecampus lantuMC"

class LantuClient(private val persistFile: File? = null) {
    var token: String = ""
    var uuid: String = "web" + System.currentTimeMillis().toString(16)
    var schoolId: Int = 187
    var publicKey: String? = null
    var userBase: JSONObject = JSONObject()
    var userLogin: JSONObject = JSONObject()
    val isLoggedIn get() = token.isNotEmpty()

    fun login(username: String, password: String) {
        ensureRsa()
        val r = post(
            "${BASE}login/login.do",
            JSONObject().apply {
                put("userName", username)
                put("password", password)
                put("uuId", uuid)
                put("schoolId", schoolId)
            },
            secure = true,
        )
        applyLogin(r)
        save()
    }

    fun getCourse(): JSONObject = post("${JW}course/getCourse.do", JSONObject().apply {
        put("curTime", System.currentTimeMillis())
    })

    fun getUserInfo(): JSONObject = post("${BASE}user/getUserInfo.do", JSONObject())

    fun getCardBrief(): JSONObject = post("${INFO}playCampus/getCardBreifInfo.do", JSONObject())

    fun profile(): Profile {
        val b = userBase
        val l = userLogin
        val sex = b.opt("sex") ?: l.opt("sex")
        val gender = when ("$sex") {
            "1" -> "男"
            "2" -> "女"
            else -> ""
        }
        return Profile(
            name = label(b.opt("realName")),
            studentId = l.optString("userName"),
            college = label(b.opt("collegeName")),
            major = label(b.opt("zymc"), b.opt("majorId")),
            klass = label(b.opt("className"), b.opt("bjmc")),
            campus = "",
            gender = gender,
            grade = label(b.opt("sznj")),
            phone = b.optString("tel"),
        )
    }

    fun mapLessons(course: JSONObject): List<Lesson> {
        val list = course.optJSONArray("courseList") ?: return emptyList()
        val out = mutableListOf<Lesson>()
        for (i in 0 until list.length()) {
            val m = list.optJSONObject(i) ?: continue
            val start = m.optString("skjc").toIntOrNull() ?: 1
            val dur = m.optString("cxjc").toIntOrNull() ?: 1
            val xqj = m.optString("skxq").toIntOrNull() ?: 0
            if (xqj !in 1..7) continue
            out.add(
                Lesson(
                    name = m.optString("kcmc"),
                    room = m.optString("jash"),
                    teacher = "",
                    weekday = xqj,
                    start = start,
                    span = dur,
                    weeks = m.optString("skzc"),
                ),
            )
        }
        return out
    }

    fun restoreLocal(): Boolean {
        val f = persistFile ?: return false
        if (!f.exists()) return false
        return try {
            val m = JSONObject(f.readText())
            token = m.optString("token")
            uuid = m.optString("uuid").ifBlank { uuid }
            schoolId = m.optString("schoolId").toIntOrNull() ?: schoolId
            userBase = m.optJSONObject("userBaseInfo") ?: JSONObject()
            userLogin = m.optJSONObject("userLoginInfo") ?: JSONObject()
            token.isNotEmpty()
        } catch (_: Exception) {
            token = ""
            false
        }
    }

    fun refreshUser() {
        val info = getUserInfo()
        info.optJSONObject("userBaseInfo")?.let { userBase = it }
        info.optJSONObject("userLoginInfo")?.let { userLogin = it }
    }

    fun restore() {
        if (!restoreLocal()) return
        try {
            refreshUser()
        } catch (_: Exception) {
        }
    }

    fun clear() {
        token = ""
        userBase = JSONObject()
        userLogin = JSONObject()
        persistFile?.takeIf { it.exists() }?.delete()
    }

    fun cardBalanceYuan(): Double? {
        return try {
            val r = getCardBrief()
            val keys = listOf("balance", "cardBalance", "recBal", "dbBalance", "ye", "accBal", "cardYe")
            for (k in keys) {
                val v = r.opt(k) ?: r.optJSONObject("data")?.opt(k)
                val n = v?.toString()?.replace("元", "")?.replace(",", "")?.toDoubleOrNull()
                if (n != null) return n
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun save() {
        val f = persistFile ?: return
        val safeBase = JSONObject(userBase.toString()).apply {
            remove("pid"); remove("rfid"); remove("inviteCode")
        }
        val safeLogin = JSONObject(userLogin.toString()).apply {
            remove("pid"); remove("securityPassword"); remove("password")
        }
        f.writeText(
            JSONObject().apply {
                put("token", token)
                put("uuid", uuid)
                put("schoolId", schoolId)
                put("userBaseInfo", safeBase)
                put("userLoginInfo", safeLogin)
            }.toString(),
        )
    }

    private fun applyLogin(r: JSONObject) {
        val tok = r.opt("token")
        token = if (tok is JSONArray) {
            (0 until tok.length()).joinToString("_") { tok.optString(it) }
        } else tok?.toString().orEmpty()
        userBase = r.optJSONObject("userBaseInfo") ?: JSONObject()
        userLogin = r.optJSONObject("userLoginInfo") ?: JSONObject()
        schoolId = userLogin.optString("schoolId").toIntOrNull() ?: schoolId
    }

    private fun ensureRsa() {
        if (!publicKey.isNullOrEmpty()) return
        val r = post("${BASE}login/getRsa.do", JSONObject().put("schoolId", schoolId))
        publicKey = r.optString("publicKey").ifBlank { error("未拿到登录公钥") }
    }

    private fun post(url: String, data: JSONObject, secure: Boolean = false): JSONObject {
        data.put("campusType", 1)
        data.put("wxCode", JSONObject.NULL)
        data.put("mcClient", JSONObject.NULL)
        data.put("openId", JSONObject.NULL)
        val param = data.toString()
        val wrapped = JSONObject().apply {
            put("appKey", APP_KEY)
            put("param", param)
            put("time", System.currentTimeMillis())
            put("secure", if (secure) 1 else 0)
            if (secure) put("schoolId", schoolId)
        }
        wrapped.put("sign", lantuSign(wrapped))
        if (secure) wrapped.put("param", rsaEncrypt(param))
        val r = CampusHttp.postJson(
            url,
            wrapped.toString(),
            headers = buildMap {
                put("User-Agent", UA)
                put("Content-Type", "application/json;charset=UTF-8")
                put("token", token)
            },
        )
        val result = JSONObject(CampusHttp.text(r).ifBlank { "{}" })
        val state = result.opt("msgState")
        if (state != null && state.toString().toIntOrNull() != 1) {
            error(result.optString("msg").ifBlank { "请求失败" })
        }
        return result
    }

    private fun rsaEncrypt(plain: String): String {
        val der = Base64.getDecoder().decode(publicKey!!.replace(Regex("\\s"), ""))
        val pub = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(der)) as RSAPublicKey
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, pub)
        val max = pub.modulus.bitLength() / 8 - 11
        val src = plain.toByteArray()
        val out = ByteArrayOutputStream()
        var i = 0
        while (i < src.size) {
            val end = minOf(i + max, src.size)
            out.write(cipher.doFinal(src, i, end - i))
            i = end
        }
        return Base64.getEncoder().encodeToString(out.toByteArray())
    }

    private fun label(a: Any?, b: Any? = null): String {
        for (v in listOf(a, b)) {
            val s = v?.toString()?.trim().orEmpty()
            if (s.isEmpty() || s == "null") continue
            if (s.matches(Regex("^\\d+$"))) continue
            return s
        }
        return ""
    }
}

fun lantuSign(obj: JSONObject): String {
    val keys = obj.keys().asSequence().toList().sorted()
    val chain = keys.joinToString("&") { "$it=${obj.get(it)}" }
    val md = java.security.MessageDigest.getInstance("MD5")
    return md.digest(chain.toByteArray()).joinToString("") { "%02x".format(it) }
}
