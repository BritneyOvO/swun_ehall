package cn.edu.swun.swun_ehall.data.http

import java.net.URLEncoder
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import okhttp3.Response

const val K_AUTH = "https://authserver.swun.edu.cn/authserver"
const val K_EHALL = "https://ehall.swun.edu.cn"
const val K_EHALL_SERVICE = "$K_EHALL/login"
private const val RAND_CHARS = "ABCDEFGHJKMNPQRSTWXYZabcdefhijkmnprstwxyz2345678"

object CasCrypto {
    fun encryptPassword(password: String, salt: String): String {
        val key = salt.trim().toByteArray(Charsets.UTF_8)
        val iv = rand(16).toByteArray(Charsets.UTF_8)
        val plain = pkcs7((rand(64) + password).toByteArray(Charsets.UTF_8), 16)
        val c = Cipher.getInstance("AES/CBC/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return Base64.getEncoder().encodeToString(c.doFinal(plain))
    }

    fun rand(n: Int): String {
        val r = SecureRandom()
        return CharArray(n) { RAND_CHARS[r.nextInt(RAND_CHARS.length)] }.concatToString()
    }

    fun pkcs7(data: ByteArray, block: Int): ByteArray {
        val n = block - (data.size % block)
        return data + ByteArray(n) { n.toByte() }
    }
}

class CasClient {
    fun login(username: String, password: String) {
        CampusHttp.dropCasTgt()
        var page = loginPage()
        if (CampusHttp.isRedirect(page) && CampusHttp.location(page).contains("ticket=")) {
            CampusHttp.dropCasTgt()
            page.close()
            page = loginPage()
        }
        if (CampusHttp.isRedirect(page) && CampusHttp.location(page).contains("ticket=")) {
            consume(CampusHttp.location(page))
            if (hasTgt()) return
            CampusHttp.dropCasTgt()
            page.close()
            page = loginPage()
        }
        if (page.code != 200) {
            val code = page.code
            page.close()
            error("登录页 HTTP $code")
        }
        val html = CampusHttp.text(page)
        val execution = Regex("""id="execution"[^>]*value="([^"]*)"""").find(html)?.groupValues?.get(1)
        val salt = Regex("""id="pwdEncryptSalt"[^>]*value="([^"]*)"""").find(html)?.groupValues?.get(1)
        if (execution.isNullOrEmpty() || salt.isNullOrEmpty()) error("登录页解析失败")
        val cap = CampusHttp.postForm("$K_AUTH/checkNeedCaptcha.htl", mapOf("username" to username))
        val capBody = CampusHttp.text(cap)
        if (capBody.contains("\"isNeed\":true") || capBody.contains("\"isNeed\": true")) {
            error("该账号需要验证码, 请稍后再试")
        }
        val service = URLEncoder.encode(K_EHALL_SERVICE, "UTF-8")
        val r = CampusHttp.postForm(
            "$K_AUTH/login?service=$service",
            mapOf(
                "username" to username,
                "password" to CasCrypto.encryptPassword(password, salt),
                "captcha" to "",
                "execution" to execution,
                "_eventId" to "submit",
                "cllt" to "userNameLogin",
                "dllt" to "generalLogin",
                "lt" to "",
            ),
        )
        val loc = CampusHttp.location(r)
        if (r.code == 200) {
            val msg = Regex("""id="showErrorTip"[^>]*>\s*(?:<p[^>]*>)?([^<]{2,200})""")
                .find(CampusHttp.text(r))?.groupValues?.get(1)?.trim()
            error(msg ?: "登录失败")
        }
        r.close()
        if (!CampusHttp.isRedirect(r) || !loc.contains("ticket=")) {
            error("登录异常: 未拿到 ticket")
        }
        consume(loc)
        if (!hasTgt()) error("统一身份未拿到登录凭证")
    }

    fun hasTgt(): Boolean = CampusHttp.cookies.all().any {
        val n = it.name.uppercase()
        (n == "CASTGC" || n == "TGC") && it.value.isNotEmpty()
    }

    fun tgtAlive(): Boolean {
        if (hasTgt()) return true
        return try {
            val service = URLEncoder.encode(K_EHALL_SERVICE, "UTF-8")
            val r = CampusHttp.get("$K_AUTH/login?service=$service")
            val loc = CampusHttp.location(r)
            r.close()
            loc.contains("ticket=")
        } catch (_: Exception) {
            false
        }
    }

    fun ticketFor(service: String): String {
        var url = "$K_AUTH/login?service=${URLEncoder.encode(service, "UTF-8")}"
        repeat(6) {
            val r = CampusHttp.get(url)
            val next = CampusHttp.location(r)
            r.close()
            if (next.contains("ticket=")) return CampusHttp.upgradeHttps(next)
            if (!CampusHttp.isRedirect(r) || next.isEmpty()) error("未能换到 ticket (会话可能已过期)")
            url = CampusHttp.absUrl(K_AUTH, next)
        }
        error("未能换到 ticket (会话可能已过期)")
    }

    private fun loginPage(): Response {
        var url = "$K_AUTH/login?service=${URLEncoder.encode(K_EHALL_SERVICE, "UTF-8")}"
        repeat(8) {
            val page = CampusHttp.get(url)
            if (page.code == 200) return page
            if (!CampusHttp.isRedirect(page) || CampusHttp.location(page).isEmpty()) {
                val code = page.code
                page.close()
                error("登录页 HTTP $code")
            }
            val next = CampusHttp.location(page)
            if (next.contains("ticket=")) return page
            page.close()
            url = CampusHttp.absUrl(K_AUTH, next)
        }
        error("登录页跳转过多")
    }

    private fun consume(url: String) {
        var u = CampusHttp.upgradeHttps(url)
        repeat(12) {
            val r = CampusHttp.get(u)
            if (r.code == 200) {
                r.close()
                return
            }
            if (!CampusHttp.isRedirect(r) || CampusHttp.location(r).isEmpty()) {
                r.close()
                return
            }
            val next = CampusHttp.location(r)
            r.close()
            u = CampusHttp.absUrl(u, next)
        }
    }
}
