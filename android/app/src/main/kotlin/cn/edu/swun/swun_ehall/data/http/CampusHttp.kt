package cn.edu.swun.swun_ehall.data.http

import java.net.Inet4Address
import java.net.InetAddress
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager
import okhttp3.Call
import okhttp3.EventListener
import java.io.IOException
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Dns
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.util.concurrent.TimeUnit

const val K_UA =
    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

enum class CampusResponseIssue {
    PortalClosed,
    GatewayBlocked,
    AuthenticationExpired,
    NetworkFailure,
    HttpFailure,
}

class MemoryCookieJar : CookieJar {
    private val store = ConcurrentHashMap<String, MutableList<Cookie>>()
    var onChanged: (() -> Unit)? = null

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        val out = mutableListOf<Cookie>()
        store.values.forEach { list ->
            synchronized(list) {
                list.removeAll { it.expiresAt < now }
                list.filter { it.matches(url) }.forEach { out.add(it) }
            }
        }
        return out
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        var changed = false
        cookies.forEach { c ->
            val key = "${c.domain}|${c.name}|${c.path}"
            store.getOrPut(c.domain) { mutableListOf() }.let { list ->
                synchronized(list) {
                    val old = list.firstOrNull { it.name == c.name && it.path == c.path }
                    if (old != c) {
                        list.removeAll { it.name == c.name && it.path == c.path }
                        list.add(c)
                        changed = true
                    }
                }
            }
        }
        if (changed) onChanged?.invoke()
    }

    fun all(): List<Cookie> = store.values.flatMap { list -> synchronized(list) { list.toList() } }

    fun clear() = store.clear()

    fun removeByName(names: Set<String>) {
        val upper = names.map { it.uppercase() }.toSet()
        store.values.forEach { list ->
            synchronized(list) {
                list.removeAll { it.name.uppercase() in upper }
            }
        }
    }

    fun removeHost(host: String, names: Set<String>) {
        val upper = names.map { it.uppercase() }.toSet()
        store.entries.forEach { (domain, list) ->
            val bare = domain.removePrefix(".")
            if (host != bare && !host.endsWith(".$bare")) return@forEach
            synchronized(list) {
                list.removeAll { it.name.uppercase() in upper }
            }
        }
    }

    fun removeWaf(host: String) {
        store.entries.forEach { (domain, list) ->
            val bare = domain.removePrefix(".")
            if (host != bare && !host.endsWith(".$bare")) return@forEach
            synchronized(list) {
                list.removeAll { CampusHttp.isWafCookieName(it.name) }
            }
        }
    }
}

object CampusHttp {
    val cookies = MemoryCookieJar()

    private val trustAll = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    private val sslFactory: SSLSocketFactory = SSLContext.getInstance("TLS").apply {
        init(null, arrayOf(trustAll), SecureRandom())
    }.socketFactory

    private val ipv4Dns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val v4 = InetAddress.getAllByName(hostname).filterIsInstance<Inet4Address>()
            return v4.ifEmpty { Dns.SYSTEM.lookup(hostname) }
        }
    }

    val followClient: OkHttpClient by lazy {
        client.newBuilder()
            .followRedirects(true)
            .followSslRedirects(true)
            .readTimeout(5, TimeUnit.MINUTES)
            .build()
    }

    val imageClient: OkHttpClient by lazy {
        followClient.newBuilder()
            .callTimeout(20, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookies)
        .eventListenerFactory { call ->
            val started = System.nanoTime()
            val cancellation = RequestCancellation.current()?.onCancel { call.cancel() }
            object : EventListener() {
                private var dnsStarted = 0L
                private var sentAt = 0L

                override fun dnsStart(call: Call, domainName: String) {
                    dnsStarted = System.nanoTime()
                }

                override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<InetAddress>) {
                    SwunLog.took(dnsStarted, "DNS " + domainName)
                }

                override fun requestHeadersEnd(call: Call, request: Request) {
                    sentAt = System.nanoTime()
                }

                override fun responseHeadersStart(call: Call) {
                    if (sentAt != 0L) SwunLog.took(sentAt, "首响应 " + call.request().url.host + call.request().url.encodedPath)
                }

                override fun callEnd(call: Call) {
                    cancellation?.close()
                    val request = call.request()
                    SwunLog.took(started, "HTTP " + request.method + " " + request.url.host + request.url.encodedPath)
                }

                override fun callFailed(call: Call, ioe: IOException) {
                    cancellation?.close()
                    val request = call.request()
                    SwunLog.took(started, "HTTP失败 " + request.method + " " + request.url.host + request.url.encodedPath)
                }
            }
        }
        .dns(ipv4Dns)
        .sslSocketFactory(sslFactory, trustAll)
        .hostnameVerifier { _, _ -> true }
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            RequestCancellation.check()
            val req = chain.request().newBuilder()
                .header("User-Agent", K_UA)
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .build()
            val resp = chain.proceed(req)
            if (resp.code == 412) {
                val waf = cookies.loadForRequest(req.url)
                    .filter { isWafCookieName(it.name) }
                    .joinToString("; ") { "${it.name}=${it.value}" }
                val set = resp.headers("Set-Cookie").mapNotNull { header ->
                    val pair = header.substringBefore(';')
                    val name = pair.substringBefore('=')
                    if (!isWafCookieName(name)) null else pair
                }.joinToString("; ")
                SwunLog.w("瑞数412 ${resp.protocol} ${req.url.encodedPath} cookie=[$waf] set=[$set]")
            }
            resp
        }
        .build()

    fun get(url: String, headers: Map<String, String> = emptyMap()): Response {
        val b = Request.Builder().url(url).get()
        headers.forEach { (k, v) -> b.header(k, v) }
        return client.newCall(b.build()).execute()
    }

    fun postForm(url: String, fields: Map<String, String>, headers: Map<String, String> = emptyMap()): Response {
        val body = FormBody.Builder().apply { fields.forEach { (k, v) -> add(k, v) } }.build()
        val b = Request.Builder().url(url).post(body)
        headers.forEach { (k, v) -> b.header(k, v) }
        return client.newCall(b.build()).execute()
    }

    fun postJson(url: String, json: String, headers: Map<String, String> = emptyMap()): Response {
        val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
        val b = Request.Builder().url(url).post(body)
        headers.forEach { (k, v) -> b.header(k, v) }
        return client.newCall(b.build()).execute()
    }

    fun location(r: Response): String = r.header("Location").orEmpty()

    fun isRedirect(r: Response): Boolean = r.code in listOf(301, 302, 303, 307, 308)

    fun absUrl(base: String, next: String): String {
        if (next.startsWith("http")) return upgradeHttps(next)
        return java.net.URI(base).resolve(next).toString().let { upgradeHttps(it) }
    }

    fun upgradeHttps(url: String): String {
        return url
            .replace("http://authserver.swun.edu.cn", "https://authserver.swun.edu.cn")
            .replace("http://jwxt.swun.edu.cn", "https://jwxt.swun.edu.cn")
            .replace("http://ktkq.swun.edu.cn", "https://ktkq.swun.edu.cn")
            .replace("http://gyglxt.swun.edu.cn", "https://gyglxt.swun.edu.cn")
            .replace("http://ykth5.swun.edu.cn", "https://ykth5.swun.edu.cn")
    }

    fun classifyResponse(code: Int, body: String): CampusResponseIssue? = when {
        code == 0 -> CampusResponseIssue.NetworkFailure
        // Campus systems use 403 for scheduled shutdown, even when the body is a gateway page.
        code == 403 -> CampusResponseIssue.PortalClosed
        code == 412 -> CampusResponseIssue.GatewayBlocked
        body.contains("系统已关闭") || body.contains("夜间关闭") || body.contains("不在服务时间") ->
            CampusResponseIssue.PortalClosed
        looksLikeRuishu(code, body) -> CampusResponseIssue.GatewayBlocked
        code == 401 -> CampusResponseIssue.AuthenticationExpired
        code !in 200..299 -> CampusResponseIssue.HttpFailure
        else -> null
    }

    fun looksRuishu(code: Int, body: String): Boolean =
        classifyResponse(code, body) == CampusResponseIssue.GatewayBlocked

    fun looksNightClosed(code: Int, body: String): Boolean =
        classifyResponse(code, body) == CampusResponseIssue.PortalClosed

    fun isWafCookieName(name: String): Boolean {
        return name.contains("3AAFGrVH") ||
            name.contains("FSSBBIl1") ||
            name.startsWith("FSSBB") ||
            name.contains("3AAFG")
    }

    fun postRaw(url: String, body: String, headers: Map<String, String> = emptyMap()): Response {
        val media = (headers["Content-Type"] ?: headers["content-type"] ?: "application/x-www-form-urlencoded; charset=UTF-8")
            .toMediaType()
        val b = Request.Builder().url(url).post(body.toRequestBody(media))
        headers.forEach { (k, v) -> b.header(k, v) }
        return client.newCall(b.build()).execute()
    }

    fun text(r: Response): String = r.use { it.body?.string().orEmpty() }

    fun exportCookies(): String {
        val arr = org.json.JSONArray()
        cookies.all().forEach { c ->
            arr.put(
                org.json.JSONObject().apply {
                    put("name", c.name)
                    put("value", c.value)
                    put("domain", c.domain)
                    put("path", c.path)
                    put("expiresAt", c.expiresAt)
                    put("secure", c.secure)
                    put("httpOnly", c.httpOnly)
                    put("hostOnly", c.hostOnly)
                },
            )
        }
        return arr.toString()
    }

    fun importCookies(raw: String) {
        if (raw.isBlank()) return
        val arr = org.json.JSONArray(raw)
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val name = o.optString("name")
            val value = o.optString("value")
            val domain = o.optString("domain")
            if (name.isBlank() || value.isBlank() || domain.isBlank()) continue
            val builder = Cookie.Builder()
                .name(name)
                .value(value)
                .path(o.optString("path").ifBlank { "/" })
                .expiresAt(o.optLong("expiresAt", Long.MAX_VALUE))
            if (o.optBoolean("hostOnly", false)) builder.hostOnlyDomain(domain) else builder.domain(domain)
            if (o.optBoolean("secure")) builder.secure()
            if (o.optBoolean("httpOnly")) builder.httpOnly()
            val cookie = builder.build()
            val scheme = if (cookie.secure) "https" else "http"
            val url = "$scheme://${cookie.domain}${cookie.path}".toHttpUrl()
            cookies.saveFromResponse(url, listOf(cookie))
        }
    }

    fun dropCasTgt() {
        cookies.removeByName(setOf("CASTGC", "TGC"))
    }
}

fun Response.textBody(): String = CampusHttp.text(this)
