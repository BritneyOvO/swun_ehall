import 'dart:async';
import 'dart:convert';
import 'dart:ui';

import 'package:cookie_jar/cookie_jar.dart';
import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_inappwebview/flutter_inappwebview.dart' as wv;

import 'httpx.dart';

const _tsMark = '\$_ts';

bool looksLikeRuishu({int? status, String? body}) {
  if (status == 412) return true;
  final s = body ?? '';
  if (s.contains(_tsMark) && (s.contains('nsd=') || s.contains('3AAFGrVH') || s.contains('FSSBBIl1'))) {
    return true;
  }
  return false;
}

bool _isWafName(String n) =>
    n.contains('3AAFGrVH') || n.contains('FSSBBIl1') || n.startsWith('FSSBB') || n.contains('3AAFG');

class RsHit {
  RsHit({required this.status, required this.body, this.url = ''});

  final int status;
  final String body;
  final String url;

  bool get looksLikeHtml {
    final s = body.trimLeft();
    return s.startsWith('<') || s.toLowerCase().startsWith('<!doctype');
  }

  bool get usable {
    if (status == 0 || status == 412) return false;
    if (looksLikeRuishu(status: status, body: body)) return false;
    if (looksLikeHtml) return false;
    return body.trim().isNotEmpty;
  }

  Map<String, dynamic> asJson() {
    var s = body.trim();
    if (s.startsWith('<') || s.toLowerCase().startsWith('<!doctype')) {
      throw Exception('非 JSON');
    }
    if (!s.startsWith('{') && !s.startsWith('[')) {
      final i = s.indexOf('{');
      final j = s.lastIndexOf('}');
      if (i >= 0 && j > i) s = s.substring(i, j + 1);
    }
    final d = jsonDecode(s);
    if (d is Map<String, dynamic>) return d;
    if (d is Map) return Map<String, dynamic>.from(d);
    throw Exception('非 JSON: ${s.length > 80 ? s.substring(0, 80) : s}');
  }
}

class _Slot {
  wv.HeadlessInAppWebView? view;
  wv.InAppWebViewController? ctl;
  Completer<void>? loadOnce;
  DateTime? originAt;
  DateTime? cookieAt;
  Future<void>? spawning;
  bool allowOffHost = false;
  Future<void> queue = Future<void>.value();
  Future<void> jsQueue = Future<void>.value();
}

/// 每站点一个后台 WebView. cookie 过关后走 dio, 不再等整站 SPA.
class RsGateway {
  RsGateway({required this.jar}) : dio = buildDio(jar) {
    dio.options.connectTimeout = const Duration(seconds: 8);
    dio.options.sendTimeout = const Duration(seconds: 8);
    dio.options.receiveTimeout = const Duration(seconds: 8);
  }

  final CookieJar jar;
  final Dio dio;

  final _slots = <String, _Slot>{};
  final _rsHosts = <String>{};
  final _dioOk = <String>{};

  static const _cookieTtl = Duration(minutes: 8);

  bool _cookiesFresh(_Slot s) =>
      s.ctl != null && s.cookieAt != null && DateTime.now().difference(s.cookieAt!) < _cookieTtl;

  _Slot _slot(String host) => _slots.putIfAbsent(host, _Slot.new);

  Future<T> _runQueue<T>({
    required Future<void> queue,
    required void Function(Future<void> next) setQueue,
    required Future<T> Function() fn,
    required Duration timeout,
    required String timeoutMsg,
  }) {
    final done = Completer<T>();
    final step = queue.catchError((_) {}).then((_) async {
      try {
        final r = await fn().timeout(timeout);
        if (!done.isCompleted) done.complete(r);
      } on TimeoutException {
        if (!done.isCompleted) done.completeError(Exception(timeoutMsg));
      } catch (e, st) {
        if (!done.isCompleted) done.completeError(e, st);
      }
    });
    setQueue(step);
    return done.future;
  }

  /// 超时在队列内部触发，避免上一次卡住后后面的请求永远排队。
  Future<T> _locked<T>(
    String host,
    Future<T> Function() fn, {
    Duration timeout = const Duration(seconds: 12),
  }) {
    final s = _slot(host);
    return _runQueue(
      queue: s.queue,
      setQueue: (next) => s.queue = next,
      fn: fn,
      timeout: timeout,
      timeoutMsg: '网关超时',
    );
  }

  Future<T> _jsLocked<T>(
    _Slot s,
    Future<T> Function() fn, {
    Duration timeout = const Duration(seconds: 6),
  }) {
    return _runQueue(
      queue: s.jsQueue,
      setQueue: (next) => s.jsQueue = next,
      fn: fn,
      timeout: timeout,
      timeoutMsg: 'WebView 请求超时',
    );
  }

  void remember(String host) => _rsHosts.add(host);

  String _homeOf(String host) {
    if (host == 'ktkq.swun.edu.cn') return 'https://ktkq.swun.edu.cn/';
    if (host == 'gyglxt.swun.edu.cn') return 'https://gyglxt.swun.edu.cn/';
    if (host == 'ykth5.swun.edu.cn') return 'https://ykth5.swun.edu.cn/';
    if (host == 'card.swun.edu.cn') return 'https://card.swun.edu.cn/';
    return 'https://$host/';
  }

  Future<void> warmup(String url) {
    final uri = Uri.parse(url);
    return _locked(uri.host, () async {
      final t0 = DateTime.now();
      final s = _slot(uri.host);
      if (s.ctl != null &&
          s.cookieAt != null &&
          DateTime.now().difference(s.cookieAt!) < _cookieTtl &&
          await _hasWafCookie(uri)) {
        debugPrint('[rs] warmup ${uri.host} skip ${DateTime.now().difference(t0).inMilliseconds}ms');
        return;
      }
      await _refreshChallenge(uri);
      debugPrint('[rs] warmup ${uri.host} ${DateTime.now().difference(t0).inMilliseconds}ms');
    }, timeout: const Duration(seconds: 10));
  }

  Future<void> warmupAll(Iterable<String> urls) => Future.wait(
        urls.map(
          (u) => warmup(u).catchError((Object e) {
            debugPrint('[rs] warmup $u $e');
          }),
        ),
      );

  Future<RsHit> request({
    required String method,
    required String url,
    Map<String, dynamic>? query,
    Object? data,
    Map<String, String>? headers,
  }) async {
    var uri = Uri.parse(url);
    if (query != null && query.isNotEmpty) {
      uri = uri.replace(queryParameters: {
        ...uri.queryParameters,
        for (final e in query.entries)
          if (e.value != null) e.key: '${e.value}',
      });
    }
    final host = uri.host;
    final keep = headers?['Referer'] ?? headers?['referer'];
    return _locked(host, () async {
      final t0 = DateTime.now();
      final s = _slot(host);
      // 已确认是瑞数站点且 cookie 还热时，跳过必定 412 的 dio。
      final skipDio = _rsHosts.contains(host) && _cookiesFresh(s);
      if (!skipDio) {
        final first = await _dioOnce(method, uri, data: data, headers: headers);
        if (first != null && first.usable) {
          _dioOk.add(host);
          debugPrint('[rs] $host dio ${DateTime.now().difference(t0).inMilliseconds}ms');
          return first;
        }
        if (first != null && looksLikeRuishu(status: first.status, body: first.body)) {
          _rsHosts.add(host);
          _dioOk.remove(host);
        }
      }
      if (s.ctl != null) {
        try {
          final hit = await _jsFetch(s, method, uri, data: data, headers: headers);
          if (hit.usable) {
            debugPrint('[rs] $host js ${DateTime.now().difference(t0).inMilliseconds}ms');
            return hit;
          }
        } catch (e) {
          debugPrint('[rs] $host js $e');
        }
      }
      await _refreshChallenge(uri, keepUrl: keep);
      if (s.ctl == null) throw Exception('网关未就绪');
      var hit = await _jsFetch(s, method, uri, data: data, headers: headers);
      if (!hit.usable) {
        await Future<void>.delayed(const Duration(milliseconds: 280));
        hit = await _jsFetch(s, method, uri, data: data, headers: headers);
      }
      debugPrint('[rs] $host refresh ${DateTime.now().difference(t0).inMilliseconds}ms ${hit.status}');
      if (!hit.usable) {
        throw Exception('网关仍被拦截');
      }
      return hit;
    });
  }

  Future<RsHit> navigate(String url) {
    final uri = Uri.parse(url);
    return _locked(uri.host, () async {
      await _ensureView(uri.host);
      final s = _slot(uri.host);
      s.allowOffHost = true;
      try {
        await _pushCookies(uri);
        await _pushHostCookies('authserver.swun.edu.cn');
        await _load(s, url);
        await _waitNavigated(s, uri);
        await _pullCookies(uri);
        s.originAt = DateTime.now();
        s.cookieAt = s.originAt;
        _rsHosts.add(uri.host);
        final snap = await _snapshot(s);
        return RsHit(status: 200, body: snap.text, url: snap.href);
      } finally {
        s.allowOffHost = false;
      }
    }, timeout: const Duration(seconds: 18));
  }

  Future<RsHit?> _dioOnce(String method, Uri uri, {Object? data, Map<String, String>? headers}) async {
    try {
      final r = await dio.requestUri(
        uri,
        data: data,
        options: Options(
          method: method,
          headers: headers,
          contentType: method == 'POST' && data is Map ? Headers.jsonContentType : null,
          sendTimeout: const Duration(seconds: 5),
          receiveTimeout: const Duration(seconds: 5),
          validateStatus: (s) => s != null && s < 500,
        ),
      );
      final body = r.data is String ? r.data as String : (r.data == null ? '' : jsonEncode(r.data));
      return RsHit(status: r.statusCode ?? 0, body: body, url: r.realUri.toString());
    } on DioException catch (e) {
      if (e.response?.statusCode == 412) {
        return RsHit(status: 412, body: '${e.response?.data ?? ''}', url: uri.toString());
      }
      return null;
    }
  }

  Future<void> _refreshChallenge(Uri uri, {String? keepUrl}) async {
    final s = _slot(uri.host);
    await _ensureView(uri.host);
    var target = _homeOf(uri.host);
    final keep = keepUrl == null ? null : Uri.tryParse(keepUrl);
    if (keep != null && keep.host == uri.host && keep.scheme.startsWith('http')) {
      target = keep.replace(fragment: '').toString();
    }
    final before = await _wafSig(uri);
    await _dropWafCookies(uri);
    await _load(s, target);
    await _waitWafCookie(uri, previous: before);
    await Future<void>.delayed(const Duration(milliseconds: 250));
    await _pullCookies(uri);
    s.cookieAt = DateTime.now();
    s.originAt = s.cookieAt;
  }

  Future<void> _ensureView(String host) async {
    final s = _slot(host);
    if (s.view != null && s.view!.isRunning() && s.ctl != null) return;
    final inflight = s.spawning;
    if (inflight != null) {
      await inflight;
      return;
    }
    final done = Completer<void>();
    s.spawning = done.future;
    try {
      if (s.view != null && s.view!.isRunning() && s.ctl != null) {
        done.complete();
        return;
      }
      await _spawn(host);
      done.complete();
    } catch (e, st) {
      done.completeError(e, st);
      rethrow;
    } finally {
      if (identical(s.spawning, done.future)) s.spawning = null;
    }
  }

  Future<void> _spawn(String host) async {
    final s = _slot(host);
    await s.view?.dispose();
    s.ctl = null;
    s.originAt = null;
    final ready = Completer<void>();
    s.view = wv.HeadlessInAppWebView(
      initialSize: const Size(360, 640),
      initialSettings: wv.InAppWebViewSettings(
        javaScriptEnabled: true,
        domStorageEnabled: true,
        databaseEnabled: true,
        thirdPartyCookiesEnabled: true,
        mixedContentMode: wv.MixedContentMode.MIXED_CONTENT_ALWAYS_ALLOW,
        userAgent: kUa,
        cacheEnabled: true,
        incognito: false,
        supportZoom: false,
        hardwareAcceleration: true,
        useHybridComposition: true,
        transparentBackground: true,
        blockNetworkImage: true,
        useShouldOverrideUrlLoading: true,
      ),
      onWebViewCreated: (c) {
        s.ctl = c;
        if (!ready.isCompleted) ready.complete();
      },
      onLoadStop: (c, url) {
        final w = s.loadOnce;
        if (w != null && !w.isCompleted) w.complete();
      },
      onReceivedError: (c, req, err) {
        final w = s.loadOnce;
        if (w != null && !w.isCompleted) w.complete();
      },
      shouldOverrideUrlLoading: (c, action) async {
        final next = action.request.url;
        if (next == null) return wv.NavigationActionPolicy.ALLOW;
        if (next.host.isEmpty || next.host == host) return wv.NavigationActionPolicy.ALLOW;
        if (s.allowOffHost) return wv.NavigationActionPolicy.ALLOW;
        return wv.NavigationActionPolicy.CANCEL;
      },
      onReceivedServerTrustAuthRequest: (c, challenge) async {
        return wv.ServerTrustAuthResponse(action: wv.ServerTrustAuthResponseAction.PROCEED);
      },
    );
    await s.view!.run().timeout(
          const Duration(seconds: 8),
          onTimeout: () => throw Exception('后台 WebView 启动超时'),
        );
    s.ctl ??= s.view!.webViewController;
    if (s.ctl == null) {
      await ready.future.timeout(const Duration(seconds: 6));
      s.ctl ??= s.view!.webViewController;
    }
    if (s.ctl == null) throw Exception('后台 WebView 启动失败');
  }

  Future<void> _load(_Slot s, String url) async {
    final ctl = s.ctl;
    if (ctl == null) return;
    final gate = Completer<void>();
    s.loadOnce = gate;
    try {
      await ctl
          .loadUrl(urlRequest: wv.URLRequest(url: wv.WebUri(url)))
          .timeout(const Duration(seconds: 3));
    } catch (_) {}
    await gate.future.timeout(const Duration(milliseconds: 1000), onTimeout: () {});
  }

  Future<List<wv.Cookie>> _cmCookies(Uri uri) async {
    try {
      final cm = wv.CookieManager.instance();
      return await cm
          .getCookies(url: wv.WebUri('${uri.scheme}://${uri.host}/'))
          .timeout(const Duration(seconds: 2), onTimeout: () => <wv.Cookie>[]);
    } catch (_) {
      return const [];
    }
  }

  Future<String> _wafSig(Uri uri) async {
    final parts = <String>[];
    for (final c in await _cmCookies(uri)) {
      if (_isWafName(c.name) && (c.value ?? '').isNotEmpty) parts.add('${c.name}=${c.value}');
    }
    try {
      for (final c in await jar.loadForRequest(uri).timeout(const Duration(seconds: 2))) {
        if (_isWafName(c.name) && c.value.isNotEmpty) parts.add('${c.name}=${c.value}');
      }
    } catch (_) {}
    parts.sort();
    return parts.join('|');
  }

  Future<void> _waitWafCookie(Uri uri, {String? previous}) async {
    final until = DateTime.now().add(const Duration(milliseconds: 900));
    while (DateTime.now().isBefore(until)) {
      final sig = await _wafSig(uri);
      if (sig.isNotEmpty && sig != previous) return;
      await Future<void>.delayed(const Duration(milliseconds: 40));
    }
    if ((await _wafSig(uri)).isNotEmpty) return;
    throw Exception('网关挑战超时 (瑞数)');
  }

  Future<void> _waitNavigated(_Slot s, Uri uri) async {
    final until = DateTime.now().add(const Duration(seconds: 10));
    while (DateTime.now().isBefore(until)) {
      final snap = await _snapshot(s);
      final h = Uri.tryParse(snap.href)?.host ?? '';
      if (h == uri.host && !snap.href.startsWith('about:')) {
        if (snap.rs) {
          await Future<void>.delayed(const Duration(milliseconds: 80));
          continue;
        }
        if (snap.emToken.isNotEmpty || snap.lsToken.isNotEmpty) return;
        if (snap.href.contains('token=')) return;
        if (snap.cookie.contains('Authorization=')) return;
        if (snap.htmlLen > 40) return;
      }
      await Future<void>.delayed(const Duration(milliseconds: 80));
    }
  }

  Future<bool> _hasWafCookie(Uri uri) async {
    if ((await _cmCookies(uri)).any((c) => _isWafName(c.name))) return true;
    try {
      final list = await jar.loadForRequest(uri).timeout(const Duration(seconds: 2));
      if (list.any((c) => _isWafName(c.name))) return true;
    } catch (_) {}
    return false;
  }

  Future<_Snap> _snapshot(_Slot s) {
    return _jsLocked(s, () async {
      final ctl = s.ctl;
      if (ctl == null) return _Snap();
      try {
        final r = await ctl.callAsyncJavaScript(
          functionBody: '''
          var h = document.documentElement ? document.documentElement.innerHTML : '';
          return {
            href: location.href || '',
            cookie: document.cookie || '',
            rs: h.indexOf('\$_ts') >= 0,
            htmlLen: h.length,
            text: document.body ? (document.body.innerText || '').slice(0, 200) : '',
            emToken: (function(){ try { return localStorage.getItem('EM_TOKEN') || ''; } catch(e){ return ''; } })(),
            lsToken: (function(){ try { return localStorage.getItem('token') || ''; } catch(e){ return ''; } })()
          };
        ''',
        );
        final v = r?.value;
        if (v is Map) {
          return _Snap(
            href: '${v['href'] ?? ''}',
            cookie: '${v['cookie'] ?? ''}',
            rs: v['rs'] == true,
            htmlLen: int.tryParse('${v['htmlLen'] ?? 0}') ?? 0,
            text: '${v['text'] ?? ''}',
            emToken: '${v['emToken'] ?? ''}',
            lsToken: '${v['lsToken'] ?? ''}',
          );
        }
      } catch (e) {
        debugPrint('[rs] snapshot $e');
      }
      return _Snap();
    }, timeout: const Duration(seconds: 2)).then(
      (v) => v,
      onError: (_, _) => _Snap(),
    );
  }

  Future<RsHit> _jsFetch(
    _Slot s,
    String method,
    Uri uri, {
    Object? data,
    Map<String, String>? headers,
  }) {
    final body = data == null ? '' : (data is String ? data : jsonEncode(data));
    return _jsLocked(s, () async {
      final ctl = s.ctl;
      if (ctl == null) throw Exception('网关未就绪');
      final r = await ctl.callAsyncJavaScript(
        functionBody: r'''
        try {
          const hdr = (typeof headerJson === 'string' && headerJson.length)
            ? JSON.parse(headerJson) : {};
          if (!hdr.Referer && !hdr.referer) {
            try { hdr.Referer = (location.href || '').split('#')[0] || (location.origin + '/'); } catch (e) {}
          }
          const opt = {
            method: method,
            credentials: 'include',
            redirect: 'follow',
            headers: hdr
          };
          if (body) opt.body = body;
          const ctrl = new AbortController();
          const timer = setTimeout(function(){ ctrl.abort(); }, 4000);
          opt.signal = ctrl.signal;
          try {
            const resp = await fetch(url, opt);
            const text = await resp.text();
            return {status: resp.status, body: text, url: resp.url, error: ''};
          } finally {
            clearTimeout(timer);
          }
        } catch (e) {
          return {status: 0, body: '', url: url, error: String(e)};
        }
      ''',
        arguments: {
          'url': uri.toString(),
          'method': method.toUpperCase(),
          'headerJson': jsonEncode(headers ?? <String, String>{}),
          'body': body,
        },
      );
      if (r?.error != null && '${r!.error}'.isNotEmpty) {
        throw Exception('WebView 请求失败: ${r.error}');
      }
      final v = r?.value;
      if (v is Map) {
        final err = '${v['error'] ?? ''}';
        if (err.isNotEmpty) throw Exception('WebView 请求失败: $err');
        return RsHit(
          status: int.tryParse('${v['status']}') ?? 0,
          body: '${v['body'] ?? ''}',
          url: '${v['url'] ?? uri}',
        );
      }
      throw Exception('WebView 无返回');
    });
  }

  Future<void> _pushHostCookies(String host) => _pushCookies(Uri.parse('https://$host/'));

  Future<void> _pushCookies(Uri uri) async {
    try {
      final cm = wv.CookieManager.instance();
      final list = await jar.loadForRequest(uri).timeout(const Duration(seconds: 2));
      final root = wv.WebUri('${uri.scheme}://${uri.host}/');
      await Future.wait([
        for (final c in list)
          if (c.value.isNotEmpty)
            cm.setCookie(
              url: root,
              name: c.name,
              value: c.value,
              domain: (c.domain == null || c.domain!.isEmpty) ? uri.host : c.domain,
              path: c.path ?? '/',
              isSecure: c.secure,
              isHttpOnly: c.httpOnly,
            ),
      ]).timeout(const Duration(seconds: 3));
    } catch (_) {}
  }

  Future<void> _dropWafCookies(Uri uri) async {
    final root = wv.WebUri('${uri.scheme}://${uri.host}/');
    try {
      final cm = wv.CookieManager.instance();
      final got = await _cmCookies(uri);
      await Future.wait([
        for (final c in got)
          if (_isWafName(c.name)) cm.deleteCookie(url: root, name: c.name),
      ]).timeout(const Duration(seconds: 2));
    } catch (_) {}
    try {
      final list = await jar.loadForRequest(uri).timeout(const Duration(seconds: 2));
      final expired = [
        for (final c in list)
          if (_isWafName(c.name))
            Cookie(c.name, '')
              ..domain = c.domain
              ..path = c.path ?? '/'
              ..expires = DateTime.fromMillisecondsSinceEpoch(0)
              ..maxAge = 0,
      ];
      if (expired.isNotEmpty) await jar.saveFromResponse(uri, expired);
    } catch (_) {}
  }

  Future<void> _pullCookies(Uri uri) async {
    final got = await _cmCookies(uri);
    if (got.isEmpty) return;
    final out = <Cookie>[];
    for (final c in got) {
      final v = '${c.value ?? ''}';
      if (v.isEmpty) continue;
      if ((c.name == 'Authorization' || c.name == 'token' || c.name == 'CASTGC') && v.length < 12) {
        continue;
      }
      out.add(
        Cookie(c.name, v)
          ..domain = c.domain ?? uri.host
          ..path = c.path ?? '/'
          ..httpOnly = c.isHttpOnly ?? false
          ..secure = c.isSecure ?? uri.scheme == 'https',
      );
    }
    if (out.isNotEmpty) {
      try {
        await jar.saveFromResponse(uri, out).timeout(const Duration(seconds: 2));
      } catch (_) {}
    }
  }

  Future<String> evalJs(String host, String functionBody) async {
    final s = _slot(host);
    if (s.ctl == null) return '';
    try {
      return await _jsLocked(s, () async {
        final ctl = s.ctl;
        if (ctl == null) return '';
        final r = await ctl.callAsyncJavaScript(functionBody: functionBody);
        final v = r?.value;
        if (v == null) return '';
        return '$v';
      }, timeout: const Duration(seconds: 2));
    } catch (_) {
      return '';
    }
  }

  Future<String?> readEmToken({String host = 'ktkq.swun.edu.cn'}) async {
    final s = _slot(host);
    if (s.ctl == null) return null;
    final snap = await _snapshot(s);
    if (snap.emToken.isNotEmpty) return snap.emToken;
    return RegExp(r'(?:^|;\s*)Authorization=([^;]+)').firstMatch(snap.cookie)?.group(1);
  }

  Future<String?> readToken({String host = 'gyglxt.swun.edu.cn'}) async {
    final s = _slot(host);
    if (s.ctl == null) return null;
    final snap = await _snapshot(s);
    if (snap.lsToken.isNotEmpty) return snap.lsToken;
    final hash = RegExp(r'[?&#]token=([A-Za-z0-9_-]+)').firstMatch(snap.href)?.group(1);
    if (hash != null && hash.isNotEmpty) return hash;
    return RegExp(r'(?:^|;\s*)token=([^;]+)').firstMatch(snap.cookie)?.group(1);
  }

  Future<void> writeLocalStorage(String key, String value, {String host = 'gyglxt.swun.edu.cn'}) async {
    final s = _slot(host);
    if (s.ctl == null) return;
    try {
      await _jsLocked(s, () async {
        final ctl = s.ctl;
        if (ctl == null) return;
        await ctl.callAsyncJavaScript(
          functionBody: r'''
        try { localStorage.setItem(key, value); } catch (e) {}
        return true;
      ''',
          arguments: {'key': key, 'value': value},
        );
      }, timeout: const Duration(seconds: 2));
    } catch (_) {}
  }

  Future<void> dispose() async {
    for (final s in _slots.values) {
      try {
        await s.view?.dispose();
      } catch (_) {}
    }
    _slots.clear();
    _rsHosts.clear();
    _dioOk.clear();
  }
}

class _Snap {
  _Snap({
    this.href = '',
    this.cookie = '',
    this.rs = false,
    this.htmlLen = 0,
    this.text = '',
    this.emToken = '',
    this.lsToken = '',
  });
  final String href;
  final String cookie;
  final bool rs;
  final int htmlLen;
  final String text;
  final String emToken;
  final String lsToken;
}
