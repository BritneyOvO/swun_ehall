package cn.edu.swun.swun_ehall.data.http

import android.annotation.SuppressLint
import android.content.Context
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.webkit.WebStorage
import android.view.ViewGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject

data class RsHit(val status: Int, val body: String, val url: String = "") {
    val usable: Boolean
        get() {
            if (status !in 200..299) return false
            if (CampusHttp.looksRuishu(status, body)) return false
            val t = body.trimStart()
            if (t.startsWith("<") || t.startsWith("<!DOCTYPE", ignoreCase = true)) return false
            return true
        }

    fun asJson(): JSONObject {
        var s = body.trim()
        if (s.startsWith("<") || s.startsWith("<!DOCTYPE", ignoreCase = true)) {
            error("非 JSON")
        }
        if (!s.startsWith("{") && !s.startsWith("[")) {
            val i = s.indexOf('{')
            val j = s.lastIndexOf('}')
            if (i >= 0 && j > i) s = s.substring(i, j + 1)
        }
        return JSONObject(s)
    }
}

internal data class PSample(val name: String, val value: String, val enableName: String, val enable: String)

class RsJsBridge {
    @Volatile var json: String = ""
    @Volatile var latch: CountDownLatch? = null
    @Volatile var traceStartNs: Long = 0
    @Volatile var lastWriteNs: Long = 0
    @Volatile var lastPLen: Int = 0
    @Volatile var readyKind: String = ""
    @Volatile var readyLatch: CountDownLatch? = null
    private val writes = mutableListOf<String>()
    private val samples = mutableListOf<PSample>()
    private var enableName: String = ""
    private var enableValue: String = ""

    @JavascriptInterface
    fun result(s: String) {
        json = s
        latch?.countDown()
    }

    /** 只在内存里留下每一版 P，日志只记名字和长度。 */
    @JavascriptInterface
    fun keep(name: String, value: String) {
        if (name.isEmpty() || name.any { !it.isLetterOrDigit() && it != '_' }) return
        val len = value.length
        val ms = if (traceStartNs == 0L) 0 else (System.nanoTime() - traceStartNs) / 1_000_000
        synchronized(writes) { writes.add("#${writes.size + 1} $name:${len} @${ms}ms") }
        lastWriteNs = System.nanoTime()
        if (name.startsWith("enable")) {
            enableName = name
            enableValue = value
        }
        if (name.endsWith("P") && len >= 300) {
            lastPLen = len
            readyLatch?.countDown()
            synchronized(samples) {
                if (samples.lastOrNull()?.value != value) {
                    samples.add(PSample(name, value, enableName, enableValue))
                }
            }
        }
    }

    @JavascriptInterface
    fun ready(kind: String) {
        val name = kind.take(16).filter { it.isLetter() }
        if (name.isEmpty()) return
        readyKind = name
        readyLatch?.countDown()
    }

    fun beginTrace() {
        synchronized(writes) { writes.clear() }
        synchronized(samples) { samples.clear() }
        enableName = ""
        enableValue = ""
        traceStartNs = System.nanoTime()
        lastWriteNs = 0
        lastPLen = 0
        readyKind = ""
    }

    fun trace(): String = synchronized(writes) { writes.joinToString(" | ") }

    internal fun samples(): List<PSample> = synchronized(samples) { samples.toList() }
}

private class RsSlot {
    var view: WebView? = null
    val bridge = RsJsBridge()
    var cookieAt: Long = 0
    val lock = Any()
    @Volatile var lastUrl: String = ""
    @Volatile var tokenUrl: String = ""
    @Volatile var boundToken: String = ""
    @Volatile var loading: Boolean = false
    @Volatile var bridgeReturn: String = ""
    @Volatile var wafOkAt: Long = 0

    fun note(url: String) {
        if (url.isBlank() || url.startsWith("about:")) return
        lastUrl = url
        if (url.contains("token=")) tokenUrl = url
    }
}

private const val COOKIE_HOOK = """
(function(){
  if (window.__swunHook) return;
  window.__swunHook = true;
  var d = Object.getOwnPropertyDescriptor(Document.prototype, 'cookie');
  if (!d || !d.set || !d.get) return;
  var set = d.set, get = d.get;
  Object.defineProperty(Document.prototype, 'cookie', {
    configurable: true,
    enumerable: d.enumerable,
    get: function(){ return get.call(this); },
    set: function(v){
      set.call(this, v);
      try {
        var s = String(v), i = s.indexOf('='), name = i < 0 ? s : s.slice(0, i);
        var val = i < 0 ? '' : s.slice(i + 1).split(';')[0];
        if (window.SwunRs) SwunRs.keep(name, val);
      } catch (e) {}
    }
  });
  try {
    var loc = window.location;
    if (!loc.__swunLoc) {
      loc.replace = function() { try { SwunRs.ready('replace'); } catch (e) {} };
      loc.assign = function() { try { SwunRs.ready('assign'); } catch (e) {} };
      loc.reload = function() { try { SwunRs.ready('reload'); } catch (e) {} };
      loc.__swunLoc = true;
    }
  } catch (e) {}
})();
"""

class RsGateway(private val app: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val knownWaf = setOf("3AAFGrVH3FUNO", "3AAFGrVH3FUNP", "enable_3AAFGrVH3FUN")
    private val slots = ConcurrentHashMap<String, RsSlot>()
    private val rsHosts = ConcurrentHashMap.newKeySet<String>()

    fun unlock(url: String, keepUrl: String? = null) {
        val host = url.toHttpUrlOrNull()?.host ?: return
        synchronized(slot(host).lock) {
            RequestCancellation.check()
            if (unlocked(host) && wafSig(RsSites.ofHost(host)?.home ?: "https://$host/").isNotEmpty()) return
            refreshChallenge(url, keepUrl)
        }
    }

    fun unlocked(host: String): Boolean = rsHosts.contains(host) && cookiesFresh(host)

    fun open(url: String, wait: RsWait = RsWait.Ready, site: RsSite? = null): RsHit {
        val host = url.toHttpUrlOrNull()?.host ?: error("网关地址无效")
        val rsSite = site ?: RsSites.ofHost(host) ?: RsSite(host, "https://$host/", host, "token")
        synchronized(slot(host).lock) {
            RequestCancellation.check()
            val s = ensureView(host)
            s.lastUrl = url
            s.tokenUrl = ""
            pushCookies(url)
            pushCookies("https://authserver.swun.edu.cn/")
            pushCookies("https://authserver.swun.edu.cn/authserver/")
            load(s, url, waitMs = if (wait == RsWait.Token) 8_000 else 4_000)
            val snap = waitOpen(rsSite, wait)
            pullCookies(url)
            pullCookies("https://$host/")
            if (url.contains("/jwmobile")) pullCookies("https://$host/jwmobile/")
            s.cookieAt = System.currentTimeMillis()
            rsHosts.add(host)
            val finalUrl = s.tokenUrl.ifBlank { s.lastUrl.ifBlank { snap.href } }
            return RsHit(200, snap.text, finalUrl)
        }
    }

    fun call(
        method: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
        inPage: Boolean = false,
        pageUrl: String? = null,
    ): RsHit {
        val host = url.toHttpUrlOrNull()?.host ?: error("网关地址无效")
        val path = url.substringBefore('?').substringAfter("://").let { raw ->
            val slash = raw.indexOf('/')
            if (slash >= 0) raw.substring(slash) else raw
        }
        val queued = System.nanoTime()
        synchronized(slot(host).lock) {
            RequestCancellation.check()
            val t0 = System.nanoTime()
            SwunLog.took(queued, "$method $path 等到锁")
            val s = ensureView(host)
            if (s.view == null) error("网关未就绪")
            SwunLog.took(t0, "$method $path WebView就绪")
            val page = challengePage(url)
            val retryHeaders = LinkedHashMap(headers)
            if (retryHeaders.keys.none { it.equals("Referer", true) }) retryHeaders["Referer"] = page
            val prepareAt = System.nanoTime()
            val prepared = prepareRequest(s, method, url)
            SwunLog.took(prepareAt, "$method $path 更新请求凭证=$prepared")
            if (prepared || wafReady(s, url)) {
                val httpAt = System.nanoTime()
                val reused = dioOnce(method, url, retryHeaders, body)
                val sent = url.toHttpUrlOrNull()?.let { http ->
                    CampusHttp.cookies.loadForRequest(http)
                        .filter { CampusHttp.isWafCookieName(it.name) }
                        .joinToString("; ") { "${it.name}:${it.value.length}" }
                }.orEmpty()
                SwunLog.w("瑞数沿用 $path [$sent]")
                SwunLog.took(httpAt, "$method $path 沿用cookie HTTP ${reused?.status ?: 0} ${reused?.body?.length ?: 0}b")
                val reusedIssue = reused?.let { CampusHttp.classifyResponse(it.status, it.body) }
                if (reusedIssue == CampusResponseIssue.PortalClosed) {
                    val label = RsSites.ofHost(host)?.label ?: host
                    error("${label}夜间关闭或不在服务时间")
                }
                if (reused != null && reusedIssue != CampusResponseIssue.GatewayBlocked &&
                    reusedIssue != CampusResponseIssue.NetworkFailure
                ) {
                    s.wafOkAt = System.currentTimeMillis()
                    SwunLog.took(t0, "$method $path 结束")
                    return reused
                }
                s.wafOkAt = 0
                CampusHttp.cookies.removeWaf(host)
            }
            val stale = collectWaf(cookieUrls(host, page)).mapValues { it.value.toSet() }
            CampusHttp.cookies.removeWaf(host)
            dropPageWaf(host, stale.keys + knownWaf)
            val before = wafSnapshot(page)
            val staleP = stale.filterKeys { it.endsWith("P") }.values.flatten().toSet()
            SwunLog.took(t0, "$method $path 清掉旧cookie before=[$before]")
            var ready = awaitCookie(s, page, staleP)
            SwunLog.took(t0, "$method $path cookie返回=$ready 来源=${s.bridgeReturn} fresh=[${wafSnapshot(page)}]")
            SwunLog.w("瑞数写入 $path [${s.bridge.trace()}]")
            var hit = sendCookie(host, page, s, method, url, retryHeaders, body, path)
            val blocked = hit?.let { CampusHttp.classifyResponse(it.status, it.body) } == CampusResponseIssue.GatewayBlocked
            if (blocked) {
                SwunLog.w("瑞数再开一页 $path")
                ready = awaitCookie(s, page, staleP)
                SwunLog.took(t0, "$method $path 第二轮 cookie返回=$ready 来源=${s.bridgeReturn} fresh=[${wafSnapshot(page)}]")
                SwunLog.w("瑞数写入 $path [${s.bridge.trace()}]")
                hit = sendCookie(host, page, s, method, url, retryHeaders, body, path)
            }
            onMain(2_000) { s.view?.settings?.cacheMode = WebSettings.LOAD_DEFAULT }
            if (hit == null) error("请求失败，请检查网络连接")
            val issue = hit?.let { CampusHttp.classifyResponse(it.status, it.body) }
            if (issue == CampusResponseIssue.PortalClosed) {
                val label = RsSites.ofHost(host)?.label ?: host
                error("${label}夜间关闭或不在服务时间")
            }
            if (issue == CampusResponseIssue.NetworkFailure) error("请求失败，请检查网络连接")
            if (issue == CampusResponseIssue.HttpFailure || issue == CampusResponseIssue.AuthenticationExpired) return hit!!
            val passed = hit != null && issue == null
            if (passed) s.wafOkAt = System.currentTimeMillis() else {
                s.wafOkAt = 0
                CampusHttp.cookies.removeWaf(host)
            }
            SwunLog.took(t0, "$method $path 结束")
            if (hit != null && hit.usable) return hit
            if (passed) return hit!!
            if (issue == CampusResponseIssue.GatewayBlocked) {
                SwunLog.api("网关仍被拦截", hit?.status ?: 0, url, hit?.body.orEmpty())
                error("网关仍被拦截")
            }
            error("HTTP ${hit?.status ?: 0}")
        }
    }

    fun callInPage(
        method: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
        pageUrl: String? = null,
    ): RsHit = call(method, url, headers, body, inPage = true, pageUrl = pageUrl)

    suspend fun clearSession() = withContext(Dispatchers.Main) {
        slots.values.forEach { slot ->
            slot.view?.let { view ->
                view.stopLoading()
                view.removeJavascriptInterface("SwunRs")
                (view.parent as? ViewGroup)?.removeView(view)
                view.destroy()
            }
            slot.view = null
        }
        slots.clear()
        rsHosts.clear()
        WebStorage.getInstance().deleteAllData()
        suspendCancellableCoroutine<Unit> { continuation ->
            CookieManager.getInstance().removeAllCookies {
                if (continuation.isActive) continuation.resume(Unit)
            }
        }
        CookieManager.getInstance().flush()
    }

    fun prepareSites() {
        main.post {
            listOf(RsSites.KTKQ, RsSites.GY, RsSites.YKT).forEach { ensureView(it.host) }
        }
    }

    private fun stampNamedCookie(host: String, name: String, token: String) {
        val cm = CookieManager.getInstance()
        cm.setCookie("https://$host/", "$name=$token; Path=/")
        if (host == "ktkq.swun.edu.cn") {
            cm.setCookie("https://$host/jwmobile/", "$name=$token; Path=/jwmobile")
        }
        cm.flush()
    }

    fun followSso(ticketUrl: String, site: RsSite): String {
        var url = ticketUrl
        var got: String? = null
        var usedWeb = false
        android.util.Log.d("swun", "[rs] sso ${site.label} $ticketUrl")
        for (i in 0 until 12) {
            val r = CampusHttp.get(url)
            val loc = CampusHttp.location(r)
            val body = CampusHttp.text(r)
            val blob = "$url $loc $body ${r.request.url}"
            if (CampusHttp.looksNightClosed(r.code, body)) error("${site.label}夜间关闭或不在服务时间")
            got = site.extract(blob)
            android.util.Log.d("swun", "[rs] sso hop $i HTTP ${r.code} loc=$loc token=${got?.length ?: 0}")
            if (!got.isNullOrEmpty()) break
            if (CampusHttp.looksRuishu(r.code, body)) {
                open(url, RsWait.Token, site)
                got = token(site)
                usedWeb = true
                break
            }
            if (!CampusHttp.isRedirect(r) || loc.isEmpty()) break
            url = CampusHttp.absUrl(site.home, loc)
            if (url.contains("authserver") && !url.contains("ticket=")) {
                error("${site.label}登录失败: 被踢回 CAS")
            }
        }
        if (got.isNullOrEmpty() && !usedWeb) {
            val nav = if (url.contains("ticket=")) url else ticketUrl
            open(nav, RsWait.Token, site)
            got = token(site)
        }
        var i = 0
        while (got.isNullOrEmpty() && i < 20) {
            RequestCancellation.check()
            got = token(site)
            if (got.isNullOrEmpty()) Thread.sleep(200)
            i++
        }
        val value = got ?: error("未拿到${site.label} token")
        persistToken(site, value)
        android.util.Log.d("swun", "[rs] sso ${site.label} token len=${value.length}")
        return value
    }

    fun token(site: RsSite): String? = token(site.host, site.tokenKey, site.storeKeys)

    fun persistToken(site: RsSite, value: String) {
        val httpUrl = site.home.toHttpUrlOrNull() ?: return
        val c = okhttp3.Cookie.Builder()
            .name(site.tokenKey)
            .value(value)
            .domain(site.host)
            .path("/")
            .secure()
            .build()
        CampusHttp.cookies.saveFromResponse(httpUrl, listOf(c))
        site.storeKeys.forEach { k ->
            try {
                writeLocalStorage(k, value, site.host)
            } catch (e: Exception) {
                if (e is java.util.concurrent.CancellationException) throw e
                RequestCancellation.check()
            }
        }
    }

    fun warmup(url: String) {
        try {
            unlock(url)
        } catch (e: Exception) {
            if (e is java.util.concurrent.CancellationException) throw e
            RequestCancellation.check()
            android.util.Log.w("swun", "[rs] unlock $url $e")
        }
    }

    fun request(
        method: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
        stayOnPage: Boolean = false,
    ): RsHit = call(method, url, headers, body, inPage = stayOnPage)

    fun navigate(startUrl: String, waitForToken: Boolean = false, tokenKey: String = "Authorization"): RsHit {
        val host = startUrl.toHttpUrlOrNull()?.host ?: error("网关地址无效")
        val base = RsSites.ofHost(host) ?: RsSite(host, "https://$host/", host, tokenKey)
        val site = if (base.tokenKey == tokenKey) base else base.copy(tokenKey = tokenKey, storeKeys = listOf(tokenKey) + base.storeKeys)
        return open(startUrl, if (waitForToken) RsWait.Token else RsWait.Ready, site)
    }

    fun readToken(host: String, key: String = "token"): String? {
        val site = RsSites.ofHost(host)
        val keys = if (site != null && site.tokenKey.equals(key, true)) site.storeKeys else listOf(key)
        return token(host, key, keys)
    }

    private fun token(host: String, key: String, storeKeys: List<String>): String? {
        val s = slots[host]
        val blob = "${s?.tokenUrl.orEmpty()} ${s?.lastUrl.orEmpty()}"
        extractUrlToken(blob, key)?.let { return it }
        storeKeys.forEach { k -> extractUrlToken(blob, k)?.let { return it } }
        val href = evalJs(host, "return location.href || '';")
        extractUrlToken(href, key)?.let { return it }
        val quoted = storeKeys.joinToString(" || ") { "localStorage.getItem(${JSONObject.quote(it)})" }
        val ls = evalJs(
            host,
            "try { return $quoted || localStorage.getItem('token') || ''; } catch (e) { return ''; }",
        )
        cleanKtkqToken(ls)?.let { return it }
        if (ls.length >= 16) return ls.trim()
        val cookie = CookieManager.getInstance().getCookie("https://$host/").orEmpty()
        extractUrlToken(cookie.replace(";", "&"), key)?.let { return it }
        for (k in storeKeys + key) {
            Regex("""(?:^|;\s*)$k=([^;]+)""", RegexOption.IGNORE_CASE).find(cookie)?.groupValues?.get(1)
                ?.trim()?.takeIf { it.length >= 16 }?.let { return it }
        }
        if (key.equals("Authorization", true)) {
            cleanKtkqToken(cookie.substringAfter("Authorization=", "").substringBefore(";"))?.let { return it }
        }
        return null
    }

    private data class PageSnap(
        val href: String,
        val htmlLen: Int,
        val hasRs: Boolean,
        val text: String,
    )

    private fun snapshot(host: String): PageSnap {
        val raw = evalJs(
            host,
            """
            try {
              var h = document.documentElement ? document.documentElement.innerHTML : '';
              return JSON.stringify({
                href: location.href || '',
                htmlLen: h.length,
                rs: h.indexOf('${'$'}_ts') >= 0,
                text: document.body ? (document.body.innerText || '').slice(0, 400) : ''
              });
            } catch (e) { return '{}'; }
            """.trimIndent(),
        )
        return try {
            val o = JSONObject(raw.ifBlank { "{}" })
            PageSnap(
                href = o.optString("href"),
                htmlLen = o.optInt("htmlLen"),
                hasRs = o.optBoolean("rs"),
                text = o.optString("text"),
            )
        } catch (e: Exception) {
            if (e is java.util.concurrent.CancellationException) throw e
            RequestCancellation.check()
            PageSnap("", 0, false, "")
        }
    }

    private fun waitOpen(site: RsSite, wait: RsWait): PageSnap {
        val host = site.host
        val until = System.currentTimeMillis() + if (wait == RsWait.Token) 18_000 else 16_000
        var last = PageSnap("", 0, false, "")
        while (System.currentTimeMillis() < until) {
            RequestCancellation.check()
            pullCookies(site.home)
            last = snapshot(host)
            val nowHost = last.href.toHttpUrlOrNull()?.host.orEmpty()
            val waf = wafSig(site.home).isNotEmpty()
            android.util.Log.d("swun", "[rs] open ${site.label} href=${last.href} html=${last.htmlLen} rs=${last.hasRs} waf=$waf wait=$wait")
            val onHost = nowHost == host
            val ready = onHost && last.htmlLen > 40 && !(last.hasRs && !waf)
            if (wait == RsWait.Token) {
                if (!token(site).isNullOrEmpty()) return last
            } else if (ready) {
                return last
            }
            if (nowHost.contains("authserver")) {
                Thread.sleep(160)
                continue
            }
            Thread.sleep(120)
        }
        if (wait == RsWait.Token && token(site).isNullOrEmpty()) {
            android.util.Log.w("swun", "[rs] open ${site.label} token timeout href=${last.href}")
        }
        return last
    }

    fun evalJs(host: String, functionBody: String): String {
        val s = ensureView(host)
        if (s.view == null) return ""
        val script = """
            (function(){
              var v = '';
              try {
                v = (function(){ $functionBody })();
              } catch (e) { v = ''; }
              try { SwunRs.result(v == null ? '' : String(v)); } catch (e) {}
              return v == null ? '' : String(v);
            })();
        """.trimIndent()
        return try {
            jsCall(s, script, 6_000)
        } catch (e: Exception) {
            if (e is java.util.concurrent.CancellationException) throw e
            RequestCancellation.check()
            ""
        }
    }

    fun writeLocalStorage(key: String, value: String, host: String) {
        evalJs(host, "try { localStorage.setItem(${JSONObject.quote(key)}, ${JSONObject.quote(value)}); } catch (e) {} return '1';")
    }

    fun clearLocalStorage(host: String, keys: List<String>) {
        val js = keys.joinToString("") { "localStorage.removeItem(${JSONObject.quote(it)});" }
        evalJs(host, "try { $js } catch (e) {} return '1';")
    }

    fun clearSiteCookies(host: String, names: Collection<String>) {
        val cm = CookieManager.getInstance()
        val paths = listOf("/", "/jwmobile", "/jwmobile/")
        names.forEach { name ->
            paths.forEach { path ->
                val url = if (path == "/") "https://$host/" else "https://$host$path"
                cm.setCookie(url, "$name=; Path=$path; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Max-Age=0")
            }
        }
        cm.flush()
    }

    fun cookieHeader(url: String): String = CookieManager.getInstance().getCookie(url).orEmpty()

    private fun slot(host: String): RsSlot = slots.getOrPut(host) { RsSlot() }

    private fun noteUnlocked(host: String) {
        rsHosts.add(host)
        val s = slot(host)
        if (s.cookieAt == 0L) s.cookieAt = System.currentTimeMillis()
    }

    private fun cookiesFresh(host: String): Boolean {
        val s = slots[host] ?: return false
        return s.view != null && s.cookieAt > 0 && System.currentTimeMillis() - s.cookieAt < 8 * 60_000
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun ensureView(host: String): RsSlot {
        val s = slot(host)
        if (s.view != null) {
            onMain(2_000) { s.view?.let { WebViewHost.attach(it) } }
            return s
        }
        onMain(8_000) {
            if (s.view != null) {
                s.view?.let { WebViewHost.attach(it) }
                return@onMain
            }
            if (cn.edu.swun.swun_ehall.BuildConfig.DEBUG) {
                WebView.setWebContentsDebuggingEnabled(true)
            }
            val ctx = WebViewHost.context(android.view.ContextThemeWrapper(app, android.R.style.Theme_DeviceDefault))
            val wv = WebView(ctx)
            wv.settings.javaScriptEnabled = true
            wv.settings.domStorageEnabled = true
            wv.settings.databaseEnabled = true
            wv.settings.userAgentString = K_UA
            wv.settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            wv.settings.cacheMode = WebSettings.LOAD_DEFAULT
            wv.settings.blockNetworkImage = true
            wv.settings.javaScriptCanOpenWindowsAutomatically = true
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)
            wv.addJavascriptInterface(s.bridge, "SwunRs")
            wv.webViewClient = sslClient(s)
            WebViewHost.attach(wv)
            s.view = wv
        }
        return s
    }

    private fun sslClient(slot: RsSlot, onFinished: ((String?) -> Unit)? = null) = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            request?.url?.toString()?.let { slot.note(it) }
            return false
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
            slot.loading = true
            url?.let { slot.note(it) }
            view?.evaluateJavascript(COOKIE_HOOK, null)
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            slot.loading = false
            url?.let { slot.note(it) }
            onFinished?.invoke(url)
        }

        override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
            handler?.proceed()
        }
    }

    private fun load(
        slot: RsSlot,
        url: String,
        waitMs: Long,
        noCache: Boolean = false,
        completion: CountDownLatch = CountDownLatch(1),
    ): Boolean {
        val latch = completion
        val target = url.substringBefore('#').substringBefore('?')
        onMain(2_000) {
            val view = slot.view ?: return@onMain
            if (noCache) view.settings.cacheMode = WebSettings.LOAD_NO_CACHE
            view.webViewClient = sslClient(slot) { finishedUrl ->
                val cur = finishedUrl.orEmpty().substringBefore('#').substringBefore('?')
                if (cur == target || cur.startsWith("$target/")) latch.countDown()
            }
            val here = view.url.orEmpty().substringBefore('#').substringBefore('?')
            if (here == target || here.startsWith("$target/")) view.reload() else view.loadUrl(url)
        }
        return RequestCancellation.await(latch, waitMs)
    }

    private fun refreshChallenge(url: String, keepUrl: String?) {
        val host = url.toHttpUrlOrNull()?.host ?: return
        val s = ensureView(host)
        var target = homeOf(host)
        val keep = keepUrl?.toHttpUrlOrNull()
        if (keep != null && keep.host == host) {
            target = keep.newBuilder().fragment(null).build().toString()
        }
        load(s, target, 8_000)
        s.cookieAt = System.currentTimeMillis()
        rsHosts.add(host)
    }

    private fun wafSig(url: String): String {
        val header = CookieManager.getInstance().getCookie(url).orEmpty()
        val parts = header.split(';').map { it.trim() }.filter { part ->
            val name = part.substringBefore('=', "")
            CampusHttp.isWafCookieName(name)
        }.sorted()
        return parts.joinToString("|")
    }

    fun pushCookies(url: String) {
        val httpUrl = url.toHttpUrlOrNull() ?: return
        val cm = CookieManager.getInstance()
        CampusHttp.cookies.loadForRequest(httpUrl).forEach { c ->
            if (c.value.isEmpty() || CampusHttp.isWafCookieName(c.name)) return@forEach
            val bits = StringBuilder("${c.name}=${c.value}; Path=${c.path.ifBlank { "/" }}")
            if (c.secure) bits.append("; Secure")
            cm.setCookie("https://${c.domain}${c.path}", bits.toString())
            cm.setCookie(url, "${c.name}=${c.value}")
        }
        cm.flush()
    }

    fun pullCookies(url: String) {
        val httpUrl = url.toHttpUrlOrNull() ?: return
        val header = CookieManager.getInstance().getCookie(url).orEmpty()
        if (header.isBlank()) return
        header.split(';').forEach { part ->
            val c = Cookie.parse(httpUrl, part.trim()) ?: return@forEach
            if (c.value.isEmpty() || CampusHttp.isWafCookieName(c.name)) return@forEach
            if ((c.name == "Authorization" || c.name == "token" || c.name == "CASTGC") && c.value.length < 12) {
                return@forEach
            }
            CampusHttp.cookies.saveFromResponse(httpUrl, listOf(c))
        }
    }

    private fun dioOnce(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String?,
    ): RsHit? {
        return try {
            val r = when (method.uppercase()) {
                "GET" -> CampusHttp.get(url, headers)
                "POST" -> {
                    val ct = headers["Content-Type"] ?: headers["content-type"].orEmpty()
                    when {
                        body != null && ct.contains("json") -> CampusHttp.postJson(url, body, headers)
                        body != null -> CampusHttp.postRaw(url, body, headers)
                        else -> CampusHttp.postForm(url, emptyMap(), headers)
                    }
                }
                else -> CampusHttp.get(url, headers)
            }
            RsHit(r.code, CampusHttp.text(r), CampusHttp.location(r).ifEmpty { url })
        } catch (e: Exception) {
            if (e is java.util.concurrent.CancellationException) throw e
            RequestCancellation.check()
            null
        }
    }

    private fun jsFetch(
        slot: RsSlot,
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String?,
    ): RsHit {
        val headerJson = JSONObject().apply { headers.forEach { (k, v) -> put(k, v) } }.toString()
        val script = """
            (function(){
              var url = ${JSONObject.quote(url)};
              var method = ${JSONObject.quote(method.uppercase())};
              var headerJson = ${JSONObject.quote(headerJson)};
              var body = ${JSONObject.quote(body ?: "")};
              var done = function(obj) {
                try { SwunRs.result(JSON.stringify(obj)); } catch (e) {}
              };
              var pack = function(status, text, finalUrl, error) {
                return {status: status || 0, body: text || '', url: finalUrl || url, error: error || ''};
              };
              (async function(){
                try {
                  var hdr = {};
                  try { hdr = headerJson ? JSON.parse(headerJson) : {}; } catch (e) {}
                  try {
                    var auth = hdr.Authorization || hdr.authorization || hdr.token || hdr['EM-TOKEN'] || '';
                    if (auth) {
                      localStorage.setItem('EM_TOKEN', auth);
                      localStorage.setItem('Authorization', auth);
                      localStorage.setItem('token', auth);
                    }
                  } catch (e) {}
                  var reqUrl = url;
                  try {
                    var u = new URL(url, location.href);
                    if (u.origin === location.origin) reqUrl = u.pathname + u.search;
                  } catch (e) {}
                  var ct = String(hdr['Content-Type'] || hdr['content-type'] || '').toLowerCase();
                  var sendBody = body || null;
                  var jsonish = ct.indexOf('json') >= 0 || (body && (body.charAt(0) === '{' || body.charAt(0) === '['));
                  var viaJquery = function() {
                    var jqLib = (typeof jQuery !== 'undefined') ? jQuery : null;
                    if (!jqLib || !jqLib.ajax) return Promise.resolve(null);
                    return new Promise(function(resolve) {
                      jqLib.ajax({
                        type: method,
                        url: reqUrl,
                        data: sendBody || undefined,
                        cache: false,
                        dataType: 'text',
                        headers: hdr,
                        timeout: 8000,
                        success: function(data, _s, xhr) {
                          resolve(pack(xhr && xhr.status, typeof data === 'string' ? data : String(data == null ? '' : data), (xhr && xhr.responseURL) || url, ''));
                        },
                        error: function(xhr) {
                          resolve(pack(xhr && xhr.status, xhr && xhr.responseText, url, ''));
                        }
                      });
                    });
                  };
                  var viaXhr = function() {
                    return new Promise(function(resolve) {
                      try {
                        var xhr = new XMLHttpRequest();
                        xhr.open(method, reqUrl, true);
                        xhr.withCredentials = true;
                        xhr.timeout = 8000;
                        Object.keys(hdr).forEach(function(k) {
                          var lk = k.toLowerCase();
                          if (lk === 'referer' || lk === 'origin' || lk === 'host' || lk === 'cookie' || lk === 'user-agent') return;
                          try { xhr.setRequestHeader(k, hdr[k]); } catch (e) {}
                        });
                        xhr.onload = function() {
                          resolve(pack(xhr.status, xhr.responseText, xhr.responseURL || url, ''));
                        };
                        xhr.onerror = function() { resolve(pack(0, '', url, 'xhr')); };
                        xhr.ontimeout = function() { resolve(pack(0, '', url, 'timeout')); };
                        xhr.send(sendBody);
                      } catch (e) {
                        resolve(pack(0, '', url, String(e)));
                      }
                    });
                  };
                  var viaFetch = async function() {
                    if (!hdr.Referer && !hdr.referer) {
                      try { hdr.Referer = (location.href || '').split('#')[0] || (location.origin + '/'); } catch (e) {}
                    }
                    var opt = {method: method, credentials: 'include', redirect: 'follow', headers: hdr};
                    if (sendBody) opt.body = sendBody;
                    var resp = await fetch(url, opt);
                    var text = await resp.text();
                    return pack(resp.status, text, resp.url, '');
                  };
                  var xhr = await Promise.race([
                    viaXhr(),
                    new Promise(function(resolve){ setTimeout(function(){ resolve(pack(0, '', url, 'timeout')); }, 8000); })
                  ]);
                  if (xhr && xhr.status) { done(xhr); return; }
                  try {
                    var f = await Promise.race([
                      viaFetch(),
                      new Promise(function(resolve){ setTimeout(function(){ resolve(pack(0, '', url, 'timeout')); }, 4000); })
                    ]);
                    done(f);
                  } catch (e) {
                    done(xhr && xhr.status ? xhr : pack(0, '', url, String(e)));
                  }
                } catch (e) {
                  done({status: 0, body: '', url: url, error: String(e)});
                }
              })();
            })();
        """.trimIndent()
        val raw = try {
            jsCall(slot, script, 14_000)
        } catch (e: IllegalStateException) {
            SwunLog.w("WebView 请求超时 $method ${url.substringBefore('?')} last=${slot.lastUrl.substringBefore("token=")} ${e.message}")
            return RsHit(0, "", url)
        }
        val v = JSONObject(raw.ifBlank { "{}" })
        if (v.optInt("status") == 0 && v.optString("error").isNotEmpty()) {
            SwunLog.w("WebView 请求失败 $method $url ${v.optString("error")} last=${slot.lastUrl}")
        }
        return RsHit(v.optInt("status"), v.optString("body"), v.optString("url").ifBlank { url })
    }

    private fun wafSnapshot(url: String): String {
        val header = CookieManager.getInstance().getCookie(url).orEmpty()
        if (header.isEmpty()) return ""
        return header.split(';').map { it.trim() }.mapNotNull { part ->
            val name = part.substringBefore('=', "")
            if (!CampusHttp.isWafCookieName(name)) return@mapNotNull null
            "$name:${part.length - name.length - 1}"
        }.sorted().joinToString(",")
    }

    /** 公寓 /appao 要用 /wxweb/ 的挑战页，考勤 /jwmobile/biz 要用 /jwmobile/index。根路径算出的 cookie 过不了接口。 */
    private fun challengePage(url: String): String {
        val http = url.toHttpUrlOrNull() ?: return url
        return when (http.host) {
            "ktkq.swun.edu.cn" -> "https://ktkq.swun.edu.cn/jwmobile/index"
            "gyglxt.swun.edu.cn" -> "https://gyglxt.swun.edu.cn/wxweb/"
            else -> "https://${http.host}/"
        }
    }

    private fun collectWaf(urls: List<String>): Map<String, List<String>> {
        val out = LinkedHashMap<String, MutableList<String>>()
        urls.forEach { url ->
            CookieManager.getInstance().getCookie(url).orEmpty().split(';').forEach { part ->
                val trimmed = part.trim()
                if (trimmed.isEmpty()) return@forEach
                val name = trimmed.substringBefore('=', "")
                val value = trimmed.substringAfter('=', "")
                if (!CampusHttp.isWafCookieName(name) || value.isEmpty()) return@forEach
                val list = out.getOrPut(name) { mutableListOf() }
                if (value !in list) list.add(value)
            }
        }
        return out
    }

    private fun dropPageWaf(host: String, names: Collection<String>) {
        if (names.isEmpty()) return
        val cm = CookieManager.getInstance()
        val paths = listOf("/", "/wxweb", "/jwmobile", "/appao", "/appsys", "/appcas", "/menu")
        names.forEach { name ->
            if (!CampusHttp.isWafCookieName(name)) return@forEach
            paths.forEach { path ->
                val url = if (path == "/") "https://$host/" else "https://$host$path/"
                cm.setCookie(url, "$name=; Path=$path; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Max-Age=0")
            }
        }
        cm.flush()
    }

    private fun hasNewP(url: String, staleP: Set<String>): Boolean {
        return collectWaf(listOf(url))
            .filterKeys { it.endsWith("P") }
            .values
            .flatten()
            .any { it.length >= 300 && it !in staleP }
    }

    private fun wafReady(slot: RsSlot, url: String): Boolean {
        // Reuse cookies until the server rejects them; age alone does not invalidate WAF state.
        if (slot.wafOkAt <= 0L) return false
        val http = url.toHttpUrlOrNull() ?: return false
        return CampusHttp.cookies.loadForRequest(http).any {
            CampusHttp.isWafCookieName(it.name) && it.name.endsWith("P") && it.value.length >= 300
        }
    }

    /** Opening an unsent XHR lets the loaded site's script sign this request. */
    private fun prepareRequest(slot: RsSlot, method: String, url: String): Boolean {
        val http = url.toHttpUrlOrNull() ?: return false
        if (slot.loading || slot.lastUrl.toHttpUrlOrNull()?.host != http.host) return false
        val page = challengePage(url)
        return try {
            val raw = jsCall(slot, """
                (function(){
                  if (location.origin !== ${JSONObject.quote("https://${http.host}")}) return '{}';
                  var before = document.cookie;
                  var xhr = new XMLHttpRequest();
                  xhr.open(${JSONObject.quote(method.uppercase())}, ${JSONObject.quote(http.encodedPath + if (http.encodedQuery != null) "?${http.encodedQuery}" else "")}, true);
                  xhr.abort();
                  return JSON.stringify({before: before, after: document.cookie});
                })();
            """.trimIndent(), 2_000)
            if (raw.isBlank()) return false
            val snapshot = JSONObject(raw)
            val stored = collectWaf(listOf("https://${http.host}/"))
                .mapValues { it.value.lastOrNull().orEmpty() }
            val pairs = rsRequestCookies(snapshot.optString("before"), snapshot.optString("after"), stored)
                ?: return false
            putExact(http.host, page, pairs) > 0
        } catch (e: Exception) {
            if (e is java.util.concurrent.CancellationException) throw e
            RequestCancellation.check()
            false
        }
    }

    /** 脚本本轮写完并要跳转时返回，不再跟着 location.replace 重载。 */
    private fun awaitCookie(slot: RsSlot, page: String, staleP: Set<String>): Boolean {
        // Disk-restored native sessions must reach WebView before a cold challenge load.
        pushCookies(page)
        pushCookies("https://authserver.swun.edu.cn/")
        pushCookies("https://authserver.swun.edu.cn/authserver/")
        slot.bridge.beginTrace()
        val ready = CountDownLatch(1)
        slot.bridge.readyLatch = ready
        return try {
            load(slot, page, 8_000, noCache = true, completion = ready)
            val deadline = System.nanoTime() + 2_000_000_000L
            while (System.nanoTime() < deadline) {
                RequestCancellation.check()
                val hasP = slot.bridge.lastPLen >= 300 && hasNewP(page, staleP)
                if (hasP && slot.bridge.readyKind.isNotEmpty()) {
                    slot.bridgeReturn = "跳转"
                    return true
                }
                val last = slot.bridge.lastWriteNs
                if (hasP && last > 0L && System.nanoTime() - last >= 250_000_000L) {
                    slot.bridgeReturn = "本轮写完"
                    return true
                }
                Thread.sleep(20)
            }
            slot.bridgeReturn = if (hasNewP(page, staleP)) "超时后已有" else "没有新cookie"
            hasNewP(page, staleP)
        } finally {
            slot.bridge.readyLatch = null
        }
    }

    private fun sendCookie(
        host: String,
        page: String,
        slot: RsSlot,
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String?,
        path: String,
    ): RsHit? {
        // A page-load P is not necessarily signed for this API request.
        val preparedAt = System.nanoTime()
        if (prepareRequest(slot, method, url)) {
            SwunLog.took(preparedAt, "$method $path 重载后更新请求凭证")
            return dioOnce(method, url, headers, body)
        }
        val sample = slot.bridge.samples().lastOrNull() ?: return null
        val jar = collectWaf(listOf(page))
        val oName = jar.keys.firstOrNull { it.endsWith("O") } ?: return null
        val oValue = jar[oName]?.lastOrNull().orEmpty()
        if (oValue.isEmpty()) return null
        val pairs = linkedMapOf(oName to oValue, sample.name to sample.value)
        if (sample.enableName.isNotEmpty() && sample.enable.isNotEmpty()) pairs[sample.enableName] = sample.enable
        putExact(host, page, pairs)
        SwunLog.w("瑞数发送 $path [" + pairs.entries.joinToString("; ") { "${it.key}:${it.value.length}" } + "]")
        val httpAt = System.nanoTime()
        val hit = dioOnce(method, url, headers, body)
        SwunLog.took(httpAt, "$method $path 接口 P:${sample.value.length} HTTP ${hit?.status ?: 0} ${hit?.body?.length ?: 0}b")
        return hit
    }

    private fun cookieUrls(host: String, page: String): List<String> = listOf(
        page,
        "https://$host/",
        "https://$host/wxweb/",
        "https://$host/jwmobile/",
        "https://$host/jwmobile/index",
        "https://$host/appao/",
    )

    private fun replaceWafCookies(host: String, page: String, stale: Map<String, Set<String>>, lastPLen: Int): Int {
        val httpUrl = page.toHttpUrlOrNull() ?: return 0
        val current = collectWaf(listOf(page))
        val chosen = LinkedHashMap<String, String>()
        current.forEach { (name, values) ->
            val fresh = values.filter { it !in stale[name].orEmpty() }
            val value = when {
                name.endsWith("P") && lastPLen > 0 -> fresh.lastOrNull { it.length == lastPLen } ?: fresh.lastOrNull()
                fresh.isNotEmpty() -> fresh.last()
                name.endsWith("P") -> null
                else -> values.last()
            } ?: return@forEach
            chosen[name] = value
        }
        if (chosen.keys.none { it.endsWith("P") }) return 0
        CampusHttp.cookies.removeWaf(host)
        val cookies = chosen.mapNotNull { (name, value) ->
            runCatching {
                Cookie.Builder().name(name).value(value).hostOnlyDomain(host).path("/").secure().build()
            }.getOrElse {
                SwunLog.w("瑞数cookie丢弃 $name len=${value.length}")
                null
            }
        }
        if (cookies.isNotEmpty()) CampusHttp.cookies.saveFromResponse(httpUrl, cookies)
        return cookies.size
    }

    private fun putExact(host: String, page: String, pairs: Map<String, String>): Int {
        val httpUrl = page.toHttpUrlOrNull() ?: return 0
        CampusHttp.cookies.removeWaf(host)
        val cookies = pairs.mapNotNull { (name, value) ->
            if (value.isEmpty()) return@mapNotNull null
            runCatching {
                Cookie.Builder().name(name).value(value).hostOnlyDomain(host).path("/").secure().build()
            }.getOrNull()
        }
        if (cookies.isNotEmpty()) CampusHttp.cookies.saveFromResponse(httpUrl, cookies)
        return cookies.size
    }

    private fun jsCall(slot: RsSlot, script: String, timeoutMs: Long): String {
        val latch = CountDownLatch(1)
        slot.bridge.json = ""
        slot.bridge.latch = latch
        onMain(2_000) {
            slot.view?.evaluateJavascript(script) { value ->
                if (latch.count == 0L) return@evaluateJavascript
                if (slot.bridge.json.isNotEmpty()) return@evaluateJavascript
                val decoded = decodeEval(value)
                if (decoded.isNotEmpty()) {
                    slot.bridge.json = decoded
                    latch.countDown()
                }
            }
        }
        if (!RequestCancellation.await(latch, timeoutMs)) error("WebView 请求超时")
        return slot.bridge.json
    }

    private fun decodeEval(value: String?): String {
        if (value.isNullOrBlank() || value == "null") return ""
        return try {
            org.json.JSONTokener(value).nextValue()?.toString() ?: value
        } catch (e: Exception) {
            if (e is java.util.concurrent.CancellationException) throw e
            RequestCancellation.check()
            if (value.length >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                value.substring(1, value.length - 1).replace("\\\"", "\"").replace("\\n", "\n")
            } else value
        }
    }

    private fun homeOf(host: String): String = when (host) {
        "ktkq.swun.edu.cn" -> "https://ktkq.swun.edu.cn/"
        "gyglxt.swun.edu.cn" -> "https://gyglxt.swun.edu.cn/"
        "ykth5.swun.edu.cn" -> "https://ykth5.swun.edu.cn/"
        "card.swun.edu.cn" -> "https://card.swun.edu.cn/"
        else -> "https://$host/"
    }

    private fun <T> onMain(timeoutMs: Long, fn: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return fn()
        val latch = CountDownLatch(1)
        var result: T? = null
        var err: Throwable? = null
        val owner = RequestCancellation.current()
        main.post {
            try {
                owner?.checkActive()
                result = fn()
            } catch (t: Throwable) {
                err = t
            } finally {
                latch.countDown()
            }
        }
        if (!RequestCancellation.await(latch, timeoutMs)) error("主线程超时")
        err?.let { throw it }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }
}

fun extractUrlToken(raw: String, key: String = "token"): String? {
    if (raw.isBlank()) return null
    val re = Regex("""[?&#/]$key=([A-Za-z0-9._\-]{16,})""", RegexOption.IGNORE_CASE)
    return re.find(raw)?.groupValues?.get(1)
}

object RsWebView {
    fun harvest(context: Context, startUrl: String, timeoutMs: Long = 20_000): String {
        return RsGateway(context.applicationContext).navigate(startUrl, waitForToken = true).body
    }

    fun cookieHeader(url: String): String = CookieManager.getInstance().getCookie(url).orEmpty()
}
