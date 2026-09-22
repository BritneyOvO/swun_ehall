package cn.edu.swun.swun_ehall.data.http

import cn.edu.swun.swun_ehall.data.model.Profile
import org.json.JSONObject

class EhallClient {
    fun profile(cas: CasClient): Profile {
        val d = ensureUser(cas)
        val dept = d.optString("deptName")
        var college = ""
        var grade = d.optString("enterSchoolDate")
        dept.split('/').forEach { p ->
            if (p.contains("学院") && college.isEmpty()) college = p
            if (p.matches(Regex("""^\d{4}$""")) && (grade.isEmpty() || !grade.matches(Regex("""^\d{4}$""")))) {
                grade = p
            }
        }
        val sex = d.opt("sexCode")?.toString().orEmpty()
        val gender = when (sex) {
            "1", "男" -> "男"
            "2", "女" -> "女"
            else -> ""
        }
        var avatar = listOf("userIcon", "headImageIcon", "defaultUserAvatar")
            .map { d.opt(it)?.toString().orEmpty().trim() }
            .firstOrNull { it.isNotEmpty() && it != "null" }
            .orEmpty()
        if (avatar.startsWith("http://gateway.swun.edu.cn")) {
            avatar = avatar.replaceFirst("http://", "https://")
        }
        return Profile(
            studentId = d.optString("userAccount"),
            name = d.optString("userName"),
            gender = gender,
            college = college,
            grade = grade,
            phone = d.optString("phone"),
            role = "学生",
            avatar = avatar,
        )
    }

    private fun ensureUser(cas: CasClient): JSONObject {
        loginUser()?.let { return it }
        var url = cas.ticketFor(K_EHALL_SERVICE)
        repeat(12) {
            val r = CampusHttp.get(url)
            val loc = CampusHttp.location(r)
            val code = r.code
            CampusHttp.text(r)
            if (code == 200 && loc.isEmpty()) return@repeat
            if (!CampusHttp.isRedirect(r) || loc.isEmpty()) return@repeat
            url = CampusHttp.absUrl(url, loc)
        }
        return loginUser() ?: error("办事大厅未登录")
    }

    private fun loginUser(): JSONObject? {
        val r = CampusHttp.get(
            "$K_EHALL/getLoginUser",
            mapOf(
                "X-Requested-With" to "XMLHttpRequest",
                "Accept" to "application/json",
                "Referer" to "$K_EHALL/index.html",
            ),
        )
        val text = CampusHttp.text(r)
        return try {
            val m = JSONObject(text)
            if (m.opt("errcode")?.toString() != "0") return null
            val data = m.optJSONObject("data") ?: return null
            if (data.optString("userAccount").isEmpty()) null else data
        } catch (_: Exception) {
            null
        }
    }
}

fun normalizeEhallAvatarUrl(raw: String): String {
    var avatar = raw.trim()
    if (avatar.startsWith("http://gateway.swun.edu.cn")) {
        avatar = avatar.replaceFirst("http://", "https://")
    }
    return avatar
}
