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

  Map<String, dynamic> asJson() {
    var s = body.trim();
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
  bool allowOffHost = false;
  Future<void> queue = Future<void>.value();
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
  Future<void> _creating = Future<void>.value();

  static const _originTtl = Duration(minutes: 10);

  _Slot _slot(String host) => _slots.putIfAbsent(host, _Slot.new);

  Future<T> _locked<T>(String host, Future<T> Function() fn) {
    final s = _slot(host);
    final done = Completer<T>();
    s.queue = s.queue.catchError((_) {}).then((_) async {
      try {
        done.complete(await fn());
      } catch (e, st) {
        done.completeError(e, st);
      }
    });
    return done.future;
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
      await _ensureOrigin(uri);
      debugPrint('[rs] warmup ${uri.host} ${DateTime.now().difference(t0).inMilliseconds}ms');
    });
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

    final first = await _dioOnce(method, uri, data: data, headers: headers);
    if (first != null && !looksLikeRuishu(status: first.status, body: first.body)) {
      _dioOk.add(host);
      return first;
    }
    if (first != null && looksLikeRuishu(status: first.status, body: first.body)) {
      _rsHosts.add(host);
      _dioOk.remove(host);
    }

    return _locked(host, () async {
      final again = await _dioOnce(method, uri, data: data, headers: headers);
      if (again != null && !looksLikeRuishu(status: again.status, body: again.body)) {
        _dioOk.add(host);
        return again;
      }
      await _ensureOrigin(uri);
      await _pushCookies(uri);
      await _pullCookies(uri);
      final third = await _dioOnce(method, uri, data: data, headers: headers);
      if (third != null && !looksLikeRuishu(status: third.status, body: third.body)) {
        _dioOk.add(host);
        debugPrint('[rs] $host dio 已通');
        return third;
      }
      await _pushCookies(uri);
      final hit = await _jsFetch(_slot(host).ctl!, method, uri, data: data, headers: headers);
      debugPrint('[rs] $method ${uri.path} -> ${hit.status} ${hit.body.length}B');
      if (looksLikeRuishu(status: hit.status, body: hit.body)) {
        _slot(host).originAt = null;
        await _ensureOrigin(uri);
        return _jsFetch(_slot(host).ctl!, method, uri, data: data, headers: headers);
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
        _rsHosts.add(uri.host);
        final snap = await _snapshot(s.ctl!);
        return RsHit(status: 200, body: snap.text, url: snap.href);
      } finally {
        s.allowOffHost = false;
      }
    });
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

  Future<void> _ensureOrigin(Uri uri) async {
    final s = _slot(uri.host);
    if (s.originAt != null && DateTime.now().difference(s.originAt!) < _originTtl && s.ctl != null) {
      if (await _hasWafCookie(uri)) {
        final snap = await _snapshot(s.ctl!);
        final h = Uri.tryParse(snap.href)?.host ?? '';
        if (h == uri.host || h.isEmpty) return;
      }
    }
    await _ensureView(uri.host);
    await _pushCookies(uri);
    unawaited(_load(s, _homeOf(uri.host)));
    await _waitPassed(s, uri);
    await _pullCookies(uri);
    s.originAt = DateTime.now();
  }

  Future<void> _ensureView(String host) {
    final s = _slot(host);
    if (s.view != null && s.view!.isRunning() && s.ctl != null) return Future<void>.value();
    final done = Completer<void>();
    _creating = _creating.catchError((_) {}).then((_) async {
      try {
        if (s.view != null && s.view!.isRunning() && s.ctl != null) {
          done.complete();
          return;
        }
        await _spawn(host);
        done.complete();
      } catch (e, st) {
        done.completeError(e, st);
      }
    });
    return done.future;
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
    await s.view!.run();
    s.ctl ??= s.view!.webViewController;
    if (s.ctl == null) {
      await ready.future.timeout(const Duration(seconds: 6));
      s.ctl ??= s.view!.webViewController;
    }
    if (s.ctl == null) throw Exception('后台 WebView 启动失败');
  }

  Future<void> _load(_Slot s, String url) async {
    final gate = Completer<void>();
    s.loadOnce = gate;
    await s.ctl!.loadUrl(urlRequest: wv.URLRequest(url: wv.WebUri(url)));
    await gate.future.timeout(const Duration(seconds: 8), onTimeout: () {});
  }

  Future<void> _waitPassed(_Slot s, Uri uri) async {
    final until = DateTime.now().add(const Duration(seconds: 6));
    while (DateTime.now().isBefore(until)) {
      if (await _hasWafCookie(uri)) return;
      final snap = await _snapshot(s.ctl!);
      if (_passed(snap, host: uri.host)) return;
      await Future<void>.delayed(const Duration(milliseconds: 80));
    }
    if (await _hasWafCookie(uri)) return;
    throw Exception('网关挑战超时 (瑞数)');
  }

  Future<void> _waitNavigated(_Slot s, Uri uri) async {
    final until = DateTime.now().add(const Duration(seconds: 10));
    while (DateTime.now().isBefore(until)) {
      final snap = await _snapshot(s.ctl!);
      final h = Uri.tryParse(snap.href)?.host ?? '';
      if (h == uri.host && !snap.href.startsWith('about:')) {
        if (snap.emToken.isNotEmpty || snap.lsToken.isNotEmpty) return;
        if (snap.href.contains('token=')) return;
        if (snap.cookie.contains('Authorization=')) return;
        if (!snap.rs && snap.htmlLen > 40) return;
        if (snap.htmlLen > 200 && await _hasWafCookie(uri)) return;
      }
      await Future<void>.delayed(const Duration(milliseconds: 80));
    }
  }

  bool _passed(_Snap s, {required String host}) {
    if (s.href.startsWith('about:')) return false;
    final h = Uri.tryParse(s.href)?.host ?? '';
    if (h.isNotEmpty && h != host) return false;
    if (s.cookie.contains('3AAFGrVH') || s.cookie.contains('FSSBBIl1') || s.cookie.contains('FSSBB')) {
      return true;
    }
    if (s.rs) return false;
    return s.htmlLen > 40 && h == host;
  }

  Future<bool> _hasWafCookie(Uri uri) async {
    try {
      final cm = wv.CookieManager.instance();
      final got = await cm.getCookies(url: wv.WebUri('${uri.scheme}://${uri.host}/'));
      if (got.any((c) => _isWafName(c.name))) return true;
    } catch (_) {}
    try {
      final list = await jar.loadForRequest(uri);
      if (list.any((c) => _isWafName(c.name))) return true;
    } catch (_) {}
    return false;
  }

  Future<_Snap> _snapshot(wv.InAppWebViewController ctl) async {
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
  }

  Future<RsHit> _jsFetch(
    wv.InAppWebViewController ctl,
    String method,
    Uri uri, {
    Object? data,
    Map<String, String>? headers,
  }) async {
    final body = data == null ? '' : (data is String ? data : jsonEncode(data));
    final r = await ctl.callAsyncJavaScript(
      functionBody: r'''
        try {
          const hdr = (typeof headerJson === 'string' && headerJson.length)
            ? JSON.parse(headerJson) : {};
          const opt = {
            method: method,
            credentials: 'include',
            redirect: 'follow',
            headers: hdr
          };
          if (body) opt.body = body;
          const ctrl = new AbortController();
          const timer = setTimeout(function(){ ctrl.abort(); }, 8000);
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
  }

  Future<void> _pushHostCookies(String host) => _pushCookies(Uri.parse('https://$host/'));

  Future<void> _pushCookies(Uri uri) async {
    final cm = wv.CookieManager.instance();
    final list = await jar.loadForRequest(uri);
    for (final c in list) {
      if (c.value.isEmpty) continue;
      await cm.setCookie(
        url: wv.WebUri('${uri.scheme}://${uri.host}/'),
        name: c.name,
        value: c.value,
        domain: (c.domain == null || c.domain!.isEmpty) ? uri.host : c.domain,
        path: c.path ?? '/',
        isSecure: c.secure,
        isHttpOnly: c.httpOnly,
      );
    }
  }

  Future<void> _pullCookies(Uri uri) async {
    final cm = wv.CookieManager.instance();
    final got = await cm.getCookies(url: wv.WebUri('${uri.scheme}://${uri.host}/'));
    if (got.isEmpty) return;
    final out = <Cookie>[];
    for (final c in got) {
      final v = '${c.value ?? ''}';
      if (v.isEmpty) continue;
      out.add(
        Cookie(c.name, v)
          ..domain = c.domain ?? uri.host
          ..path = c.path ?? '/'
          ..httpOnly = c.isHttpOnly ?? false
          ..secure = c.isSecure ?? uri.scheme == 'https',
      );
    }
    if (out.isNotEmpty) await jar.saveFromResponse(uri, out);
  }

  Future<String?> readEmToken({String host = 'ktkq.swun.edu.cn'}) async {
    final ctl = _slot(host).ctl;
    if (ctl == null) return null;
    final s = await _snapshot(ctl);
    if (s.emToken.isNotEmpty) return s.emToken;
    return RegExp(r'(?:^|;\s*)Authorization=([^;]+)').firstMatch(s.cookie)?.group(1);
  }

  Future<String?> readToken({String host = 'gyglxt.swun.edu.cn'}) async {
    final ctl = _slot(host).ctl;
    if (ctl == null) return null;
    final s = await _snapshot(ctl);
    if (s.lsToken.isNotEmpty) return s.lsToken;
    final hash = RegExp(r'[?&#]token=([A-Za-z0-9_-]+)').firstMatch(s.href)?.group(1);
    if (hash != null && hash.isNotEmpty) return hash;
    return RegExp(r'(?:^|;\s*)token=([^;]+)').firstMatch(s.cookie)?.group(1);
  }

  Future<void> writeLocalStorage(String key, String value, {String host = 'gyglxt.swun.edu.cn'}) async {
    final ctl = _slot(host).ctl;
    if (ctl == null) return;
    await ctl.callAsyncJavaScript(
      functionBody: r'''
        try { localStorage.setItem(key, value); } catch (e) {}
        return true;
      ''',
      arguments: {'key': key, 'value': value},
    );
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
