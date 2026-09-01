import 'package:cookie_jar/cookie_jar.dart';
import 'package:dio/dio.dart';

import 'cas.dart';
import 'httpx.dart';
import 'rs_gateway.dart';

const kGy = 'https://gyglxt.swun.edu.cn';
const kGyService = '$kGy/appcas/ssoLogin.jsp';

class GyglxtClient {
  GyglxtClient(this.jar, {RsGateway? gateway}) : dio = buildDio(jar) {
    rs = gateway ?? RsGateway(jar: jar);
  }

  final CookieJar jar;
  final Dio dio;
  late final RsGateway rs;
  CasClient? _cas;
  String? token;

  Map<String, String> _headers({bool json = false}) {
    return {
      'Accept': 'application/json, text/plain, */*',
      if (json) 'Content-Type': 'application/json; charset=utf-8',
      if (token != null && token!.isNotEmpty) 'token': token!,
    };
  }

  Future<void> restoreToken() async {
    for (final c in await jar.loadForRequest(Uri.parse(kGy))) {
      if (c.name == 'token' && c.value.isNotEmpty && c.value.length >= 16) {
        token = c.value;
        return;
      }
    }
  }

  Future<void> loginWithCas(CasClient cas) async {
    _cas = cas;
    var url = await cas.ticketFor(kGyService);
    String? fromResp;
    for (var i = 0; i < 12; i++) {
      final r = await dio.get(url);
      fromResp = _tokenFromText('${r.realUri} ${loc(r)} ${r.data}');
      if (fromResp != null) break;
      if (looksLikeRuishu(status: r.statusCode, body: r.data?.toString())) {
        rs.remember(Uri.parse(kGy).host);
        final hit = await rs.navigate(url);
        fromResp = _tokenFromText(hit.url) ?? await rs.readToken();
        break;
      }
      if (!isRedirect(r) || loc(r).isEmpty) break;
      url = absUrl(kGy, loc(r));
      if (url.contains('authserver') && !url.contains('ticket=')) {
        throw Exception('公寓系统登录失败: 被踢回 CAS');
      }
    }
    token = fromResp ?? await rs.readToken();
    if (token == null || token!.isEmpty) {
      await restoreToken();
    }
    if (token == null || token!.isEmpty) {
      throw Exception('未拿到公寓系统 token');
    }
    await rs.writeLocalStorage('token', token!);
    await jar.saveFromResponse(Uri.parse(kGy), [
      Cookie('token', token!)..domain = 'gyglxt.swun.edu.cn',
    ]);
  }

  String? _tokenFromText(String raw) {
    return RegExp(r'[?&#/]token=([A-Za-z0-9_-]{16,})').firstMatch(raw)?.group(1) ??
        RegExp(r'casredirectsvc\?token=([A-Za-z0-9_-]{16,})').firstMatch(raw)?.group(1);
  }

  int get _t => DateTime.now().millisecondsSinceEpoch;

  Future<Map<String, dynamic>> _api(
    String method,
    String path, {
    Map<String, dynamic>? query,
    Object? data,
    bool retry401 = true,
  }) async {
    final q = {'t': '$_t', ...?query};
    final hit = await rs.request(
      method: method,
      url: '$kGy$path',
      query: q,
      data: data,
      headers: _headers(json: method.toUpperCase() == 'POST' && data != null),
    );
    Map<String, dynamic> map;
    try {
      map = hit.asJson();
    } catch (_) {
      if (looksLikeRuishu(status: hit.status, body: hit.body)) {
        throw Exception('公寓系统仍被网关拦截');
      }
      throw Exception('$path 非 JSON');
    }
    final code = map['code'];
    if ((code == 401 || code == '401') && retry401 && _cas != null) {
      await loginWithCas(_cas!);
      return _api(method, path, query: query, data: data, retry401: false);
    }
    return map;
  }

  Future<Map<String, dynamic>> _post(String path, {Map<String, dynamic>? query, Object? data}) =>
      _api('POST', path, query: query, data: data ?? {'t': _t});

  Future<Map<String, dynamic>> _get(String path, [Map<String, dynamic>? query]) =>
      _api('GET', path, query: query);

  Future<Map<String, dynamic>> userInfo() => _get('/appsys/sys/user/info');

  Future<Map<String, dynamic>> _postOrGet(String path, {Object? data}) async {
    try {
      final r = await _post(path, data: data);
      if (r['code'] == 0 || r['code'] == 200) return r;
      final g = await _get(path);
      if (g['code'] == 0 || g['code'] == 200) return g;
      return r;
    } catch (_) {
      return _get(path);
    }
  }

  Future<Map<String, dynamic>> clockStatus() => _postOrGet('/appao/appApi/getNeedClockStatus');

  Future<Map<String, dynamic>> scheduleToday() => _postOrGet('/appao/appApi/getMobileScheduleByDate');

  Future<Map<String, dynamic>> clockRange() => _postOrGet('/appao/appApi/getClockTimeRange');

  Future<Map<String, dynamic>> positions() => _postOrGet('/appao/appApi/getPositionListByParams');

  Future<Map<String, dynamic>> records() => _post(
        '/appao/appApi/getStudentClockRecordListByParams',
        data: {'t': _t, 'currPage': 1, 'pageSize': 20, 'page': 1, 'limit': 20},
      );

  Future<Map<String, dynamic>> startConfig() => _post('/appao/appApi/getStartConfig');

  Future<Map<String, dynamic>> punch({
    required double lat,
    required double lng,
    required String address,
    String? taskId,
  }) {
    final body = <String, dynamic>{
      't': _t,
      'latitude': lat,
      'longitude': lng,
      'lat': lat,
      'lng': lng,
      'address': address,
      if (taskId != null && taskId.isNotEmpty) 'taskId': taskId,
    };
    return _post('/appao/appApi/saveClockRecordByMobileAction', data: body);
  }

  Future<Map<String, dynamic>> _safe(Future<Map<String, dynamic>> fn) async {
    try {
      return await fn.timeout(const Duration(seconds: 10));
    } catch (_) {
      return const {};
    }
  }

  Future<Map<String, dynamic>> dashboard() async {
    final parts = await Future.wait([
      _safe(clockStatus()),
      _safe(scheduleToday()),
      _safe(positions()),
      _safe(records()),
    ]);
    return {
      'status': parts[0],
      'schedule': parts[1],
      'positions': parts[2],
      'records': parts[3],
    };
  }
}
