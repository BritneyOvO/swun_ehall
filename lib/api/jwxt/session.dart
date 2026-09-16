part of '../jwxt.dart';

mixin JwxtSessionApi {
  CookieJar get jar;
  Dio get dio;

  bool portalClosed = false;
  CasClient? _cas;
  Future<void>? _ensuring;

  void attachCas(CasClient cas) => _cas = cas;

  bool _looksClosed(int? status, Object? data) => looksNightClosed(status: status, data: data);

  bool _looksLoggedOut(Response r) {
    final t = r.data?.toString() ?? '';
    if (t.contains('login_slogin') || t.contains('统一身份认证')) return true;
    if (!isRedirect(r)) return false;
    final u = absUrl(kJwxt, loc(r)).toLowerCase();
    return u.contains('login_slogin') ||
        u.contains('/sso/login') ||
        u.contains('authserver') ||
        u.contains('/cas/');
  }

  Future<Response> _home() {
    return dio.get(
      '$kJwxt/jwglxt/xtgl/index_initMenu.html',
      queryParameters: {'jsdm': 'xs'},
      options: Options(headers: {'Referer': _jwxtMenuReferer}),
    );
  }

  Future<void> loginWithCas(CasClient cas) async {
    _cas = cas;
    portalClosed = false;
    var url = await cas.ticketFor(kJwxtService);
    for (var i = 0; i < 15; i++) {
      final r = await dio.get(
        url,
        options: Options(receiveTimeout: const Duration(seconds: 12)),
      );
      if (_looksClosed(r.statusCode, r.data)) {
        portalClosed = true;
        return;
      }
      if (r.statusCode == 200 && !_looksLoggedOut(r)) break;
      var next = loc(r);
      if (next.isEmpty) break;
      url = absUrl(kJwxt, next);
      if (url.contains('authserver') && !url.contains('ticket=')) {
        throw Exception('教务登录失败: 重定向回 CAS');
      }
    }
    final home = await _home();
    final body = home.data.toString();
    if (home.statusCode == 200 && !_looksLoggedOut(home) && !_looksClosed(home.statusCode, body)) {
      portalClosed = false;
      return;
    }
    if (_looksClosed(home.statusCode, body)) {
      portalClosed = true;
      return;
    }
    throw Exception('教务登录失败');
  }

  Future<bool> sessionAlive() async {
    try {
      final r = await _home();
      final body = r.data.toString();
      if (_looksClosed(r.statusCode, body)) {
        portalClosed = true;
        return false;
      }
      if (r.statusCode == 200 && !_looksLoggedOut(r)) {
        portalClosed = false;
        return true;
      }
      return false;
    } catch (_) {
      return false;
    }
  }

  Future<void> ensureSession({bool force = false}) async {
    if (!force && await sessionAlive()) return;
    while (_ensuring != null) {
      await _ensuring!.timeout(const Duration(seconds: 20));
      if (!force && await sessionAlive()) return;
      if (!force) return;
    }
    final done = _relogin();
    _ensuring = done;
    try {
      await done;
    } finally {
      if (identical(_ensuring, done)) _ensuring = null;
    }
  }

  Future<void> _relogin() async {
    final cas = _cas;
    if (cas == null) throw Exception('教务登录已过期，请重新登录');
    if (!await cas.tgtAlive()) throw Exception('统一身份已过期，请重新登录');
    await loginWithCas(cas).timeout(const Duration(seconds: 20));
    if (portalClosed) return;
    if (!await sessionAlive()) throw Exception('教务登录失败，请重新登录');
  }

  Options get _form => Options(
        contentType: Headers.formUrlEncodedContentType,
        headers: {'Referer': _jwxtMenuReferer, 'X-Requested-With': 'XMLHttpRequest'},
      );

  Future<Map<String, dynamic>> _post(
    String path,
    Map<String, dynamic> data, {
    Map<String, dynamic>? params,
  }) async {
    Object? last;
    var relogged = false;
    for (var i = 0; i < 3; i++) {
      try {
        final r = await dio.post(
          '$kJwxt$path',
          data: data,
          queryParameters: params,
          options: _form,
        );
        if (r.statusCode == 403 || _looksClosed(r.statusCode, r.data)) {
          portalClosed = true;
          throw Exception(nightClosedMessage('教务'));
        }
        if (_looksLoggedOut(r) || isRedirect(r)) {
          if (!relogged) {
            relogged = true;
            await ensureSession(force: true);
            continue;
          }
          throw Exception('教务登录已过期，请重新登录');
        }
        if (r.statusCode != 200) throw Exception('HTTP ${r.statusCode}');
        final d = r.data;
        if (d is Map<String, dynamic>) return d;
        if (d is Map) return Map<String, dynamic>.from(d);
        throw Exception('非 JSON');
      } catch (e) {
        last = e;
        final s = e.toString();
        if (s.contains('夜间') || s.contains('过期') || s.contains('重新登录')) rethrow;
        if (i < 2) await Future<void>.delayed(const Duration(milliseconds: 400));
      }
    }
    throw Exception('请求失败 $path: $last');
  }
}
