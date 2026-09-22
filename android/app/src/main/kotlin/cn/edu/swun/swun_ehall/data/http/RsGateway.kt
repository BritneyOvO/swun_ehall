package cn.edu.swun.swun_ehall.data.http

import android.annotation.SuppressLint
import android.content.Context
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
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
            if (status == 0 || status == 412) return false
            if (looksLikeRuishu(status, body)) return false
            val t = body.trimStart()
            if (t.startsWith("<") || t.startsWith("<!DOCTYPE", ignoreCase = true)) return false
            if (status in 200..299) return true
            return body.isNotBlank()
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

class RsJsBridge {
    @Volatile var json: String = ""
    @Volatile var latch: CountDownLatch? = null

    @JavascriptInterface
    fun result(s: String) {
        json = s
        latch?.countDown()
    }
}

private class RsSlot {
    var view: WebView? = null
    val bridge = RsJsBridge()
    var cookieAt: Long = 0
    val lock = Any()
    @Volatile var lastUrl: String = ""
    @Volatile var tokenUrl: String = ""

    fun note(url: String) {
        if (url.isBlank() || url.startsWith("about:")) return
        lastUrl = url
        if (url.contains("token=")) tokenUrl = url
    }
}

class RsGateway(private val app: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val slots = ConcurrentHashMap<String, RsSlot>()
    private val rsHosts = ConcurrentHashMap.newKeySet<String>()

    fun unlock(url: String, keepUrl: String? = null) {
        val host = url.toHttpUrlOrNull()?.host ?: return
        synchronized(slot(host).lock) {
            if (unlocked(host) && wafSig(RsSites.ofHost(host)?.home ?: "https://$host/").isNotEmpty()) return
            refreshChallenge(url, keepUrl)
        }
    }

    fun unlocked(host: String): Boolean = rsHosts.contains(host) && cookiesFresh(host)

    fun open(url: String, wait: RsWait = RsWait.Ready, site: RsSite? = null): RsHit {
        val host = url.toHttpUrlOrNull()?.host ?: error("网关地址无效")
        val rsSite = site ?: RsSites.ofHost(host) ?: RsSite(host, "https://$host/", host, "token")
        synchronized(slot(host).lock) {
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
    ): RsHit {
        val host = url.toHttpUrlOrNull()?.host ?: error("网关地址无效")
        synchronized(slot(host).lock) {
            val skipDio = unlocked(host)
            if (!inPage && !skipDio) {
                val first = dioOnce(method, url, headers, body)
                if (first != null && first.usable) return first
                if (first != null && looksLikeRuishu(first.status, first.body)) rsHosts.add(host)
            }
            val s = ensureView(host)
            if (s.view != null && (inPage || cookiesFresh(host))) {
                try {
                    val hit = jsFetch(s, method, url, headers, body)
                    android.util.Log.d("swun", "[rs] call js $host ${hit.status} ${hit.body.length}b")
                    if (hit.usable) return hit
                    if (inPage) {
                        Thread.sleep(280)
                        val again = jsFetch(s, method, url, headers, body)
                        if (again.usable) return again
                        error("网关仍被拦截")
                    }
                } catch (e: Exception) {
                    if (inPage) throw e
                }
            }
            if (inPage) error("网关未就绪")
            refreshChallenge(url, headers["Referer"] ?: headers["referer"])
            var hit = jsFetch(ensureView(host), method, url, headers, body)
            if (!hit.usable) {
                Thread.sleep(280)
                hit = jsFetch(ensureView(host), method, url, headers, body)
            }
            if (!hit.usable) error("网关仍被拦截")
            return hit
        }
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
            if (looksLikeRuishu(r.code, body)) {
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
            } catch (_: Exception) {
            }
        }
    }

    fun warmup(url: String) {
        try {
            unlock(url)
        } catch (e: Exception) {
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
        } catch (_: Exception) {
            PageSnap("", 0, false, "")
        }
    }

    private fun waitOpen(site: RsSite, wait: RsWait): PageSnap {
        val host = site.host
        val until = System.currentTimeMillis() + if (wait == RsWait.Token) 18_000 else 16_000
        var last = PageSnap("", 0, false, "")
        while (System.currentTimeMillis() < until) {
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
        } catch (_: Exception) {
            ""
        }
    }

    fun writeLocalStorage(key: String, value: String, host: String) {
        evalJs(host, "try { localStorage.setItem(${JSONObject.quote(key)}, ${JSONObject.quote(value)}); } catch (e) {} return '1';")
    }

    fun cookieHeader(url: String): String = CookieManager.getInstance().getCookie(url).orEmpty()

    private fun slot(host: String): RsSlot = slots.getOrPut(host) { RsSlot() }

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

    private fun sslClient(slot: RsSlot, onFinished: (() -> Unit)? = null) = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            request?.url?.toString()?.let { slot.note(it) }
            return false
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
            url?.let { slot.note(it) }
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            url?.let { slot.note(it) }
            onFinished?.invoke()
        }

        override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
            handler?.proceed()
        }
    }

    private fun load(slot: RsSlot, url: String, waitMs: Long) {
        val latch = CountDownLatch(1)
        onMain(2_000) {
            slot.view?.webViewClient = sslClient(slot) { latch.countDown() }
            slot.view?.loadUrl(url)
        }
        latch.await(waitMs, TimeUnit.MILLISECONDS)
    }

    private fun refreshChallenge(url: String, keepUrl: String?) {
        val host = url.toHttpUrlOrNull()?.host ?: return
        val s = ensureView(host)
        var target = homeOf(host)
        val keep = keepUrl?.toHttpUrlOrNull()
        if (keep != null && keep.host == host) {
            target = keep.newBuilder().fragment(null).build().toString()
        }
        val before = wafSig(target)
        dropWafCookies(target)
        load(s, target, 2_800)
        waitWaf(target, before)
        Thread.sleep(250)
        pullCookies(target)
        s.cookieAt = System.currentTimeMillis()
        rsHosts.add(host)
    }

    private fun waitWaf(url: String, previous: String, timeoutMs: Long = 2_800) {
        val until = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < until) {
            val sig = wafSig(url)
            if (sig.isNotEmpty() && sig != previous) return
            Thread.sleep(40)
        }
        if (wafSig(url).isEmpty()) error("网关挑战超时 (瑞数)")
    }

    private fun wafSig(url: String): String {
        val header = CookieManager.getInstance().getCookie(url).orEmpty()
        val parts = header.split(';').map { it.trim() }.filter { part ->
            val name = part.substringBefore('=', "")
            CampusHttp.isWafCookieName(name)
        }.sorted()
        return parts.joinToString("|")
    }

    private fun dropWafCookies(url: String) {
        val cm = CookieManager.getInstance()
        val header = cm.getCookie(url).orEmpty()
        header.split(';').forEach { part ->
            val name = part.substringBefore('=').trim()
            if (CampusHttp.isWafCookieName(name)) {
                cm.setCookie(url, "$name=; Max-Age=0; Path=/")
            }
        }
        cm.flush()
    }

    fun pushCookies(url: String) {
        val httpUrl = url.toHttpUrlOrNull() ?: return
        val cm = CookieManager.getInstance()
        CampusHttp.cookies.loadForRequest(httpUrl).forEach { c ->
            if (c.value.isEmpty()) return@forEach
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
            if (c.value.isEmpty()) return@forEach
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
        } catch (_: Exception) {
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
                  var mark = '${'$'}_ts';
                  var jq = jsonish ? null : await viaJquery();
                  if (jq && jq.status && jq.status !== 412 && String(jq.body || '').indexOf(mark) < 0) { done(jq); return; }
                  var xhr = await viaXhr();
                  if (xhr.status && xhr.status !== 412 && String(xhr.body || '').indexOf(mark) < 0) { done(xhr); return; }
                  try {
                    var f = await viaFetch();
                    if (f.status && f.status !== 412) { done(f); return; }
                    if (xhr && xhr.status) { done(xhr); return; }
                    if (jq && jq.status) { done(jq); return; }
                    done(f);
                  } catch (e) {
                    if (xhr && xhr.status) { done(xhr); return; }
                    if (jq && jq.status) { done(jq); return; }
                    done(pack(0, '', url, String(e)));
                  }
                } catch (e) {
                  done({status: 0, body: '', url: url, error: String(e)});
                }
              })();
            })();
        """.trimIndent()
        val raw = jsCall(slot, script, 12_000)
        val v = JSONObject(raw.ifBlank { "{}" })
        val err = v.optString("error")
        if (err.isNotEmpty()) error("WebView 请求失败: $err")
        return RsHit(v.optInt("status"), v.optString("body"), v.optString("url").ifBlank { url })
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
        if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) error("WebView 请求超时")
        return slot.bridge.json
    }

    private fun decodeEval(value: String?): String {
        if (value.isNullOrBlank() || value == "null") return ""
        return try {
            org.json.JSONTokener(value).nextValue()?.toString() ?: value
        } catch (_: Exception) {
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
        main.post {
            try {
                result = fn()
            } catch (t: Throwable) {
                err = t
            } finally {
                latch.countDown()
            }
        }
        if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) error("主线程超时")
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
