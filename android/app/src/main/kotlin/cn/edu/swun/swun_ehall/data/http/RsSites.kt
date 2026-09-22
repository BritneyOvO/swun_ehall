package cn.edu.swun.swun_ehall.data.http

/**
 * 瑞数站点约定。解瑞数只认这一套，不在业务客户端里各写各的。
 *
 * 1. unlock — 打开首页，等到 WAF Cookie（FSSBB / 3AAF）
 * 2. open   — 推 CASTGC，加载 URL（可过 CAS），等到 [RsWait]
 * 3. call   — OkHttp；412 则 unlock，再在页内 jQuery → XHR → fetch
 * 4. token  — 跳转 URL / Cookie / localStorage
 * [RsGateway.followSso] 把 CAS ticket 和 1–4 串起来。
 */
enum class RsWait {
    /** 已回到目标站，挑战 Cookie 就绪（或页面不再是纯挑战页）。 */
    Ready,
    /** Ready 并且已经读到登录 token。 */
    Token,
}

data class RsSite(
    val host: String,
    val home: String,
    val label: String,
    val tokenKey: String,
    val storeKeys: List<String> = listOf(tokenKey),
) {
    fun extract(blob: String): String? {
        extractUrlToken(blob, tokenKey)?.let { return it }
        if (tokenKey.equals("Authorization", true)) {
            return parseKtkqToken(body = blob, urls = listOf(blob))
        }
        return null
    }
}

object RsSites {
    val KTKQ = RsSite(
        host = "ktkq.swun.edu.cn",
        home = "https://ktkq.swun.edu.cn/",
        label = "课堂考勤",
        tokenKey = "Authorization",
        storeKeys = listOf("Authorization", "EM_TOKEN"),
    )
    val GY = RsSite(
        host = "gyglxt.swun.edu.cn",
        home = "https://gyglxt.swun.edu.cn/",
        label = "公寓系统",
        tokenKey = "token",
    )
    val YKT = RsSite(
        host = "ykth5.swun.edu.cn",
        home = "https://ykth5.swun.edu.cn/",
        label = "一卡通",
        tokenKey = "token",
    )

    fun ofHost(host: String): RsSite? = when (host) {
        KTKQ.host -> KTKQ
        GY.host -> GY
        YKT.host -> YKT
        else -> null
    }
}

fun looksLikeRuishu(code: Int, body: String): Boolean {
    if (code == 412) return true
    val s = body
    if (s.contains("\$_ts") &&
        (s.contains("nsd=") || s.contains("3AAFGrVH") || s.contains("FSSBBIl1"))
    ) {
        return true
    }
    return false
}
