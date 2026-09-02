import 'dart:convert';
import 'dart:io';

import 'package:cookie_jar/cookie_jar.dart';
import 'package:dio/dio.dart';

import 'cas.dart';
import 'httpx.dart';

const kZhcgmHost = 'http://zhcgm.swun.edu.cn';
const kZhcgmGw = '$kZhcgmHost/onesports-gateway';
const kZhcgmDock = '$kZhcgmHost:8099/api/docking/getLoginUserInfo';

class ZhcgmException implements Exception {
  ZhcgmException(this.message);
  final String message;
  @override
  String toString() => message;
}

class ZhcgmClient {
  ZhcgmClient({this.persistPath}) : dio = buildDio(CookieJar()) {
    dio.options.baseUrl = kZhcgmGw;
    dio.options.headers['Accept'] = 'application/json, text/plain, */*';
    dio.options.headers['Content-Type'] = 'application/json';
  }

  final String? persistPath;
  final Dio dio;
  String? token;

  bool get isLoggedIn => token != null && token!.isNotEmpty;

  void _applyToken() {
    if (token != null && token!.isNotEmpty) {
      dio.options.headers['token'] = token;
    } else {
      dio.options.headers.remove('token');
    }
  }

  Future<void> restore() async {
    final path = persistPath;
    if (path == null) return;
    try {
      final f = File(path);
      if (!await f.exists()) return;
      final raw = jsonDecode(await f.readAsString());
      if (raw is! Map) return;
      final t = '${raw['token'] ?? ''}'.trim();
      if (t.isEmpty) return;
      token = t;
      _applyToken();
    } catch (_) {}
  }

  Future<void> _save() async {
    final path = persistPath;
    if (path == null) return;
    try {
      await File(path).writeAsString(jsonEncode({'token': token ?? ''}));
    } catch (_) {}
  }

  Future<void> clear() async {
    token = null;
    _applyToken();
    final path = persistPath;
    if (path == null) return;
    try {
      final f = File(path);
      if (await f.exists()) await f.delete();
    } catch (_) {}
  }

  String? _tokenOf(String url) {
    final u = Uri.tryParse(url);
    final t = u?.queryParameters['token']?.trim();
    if (t == null || t.isEmpty) return null;
    return t;
  }

  Future<void> loginWithCas(CasClient cas) async {
    var url = kZhcgmDock;
    for (var i = 0; i < 16; i++) {
      final hit = _tokenOf(url);
      if (hit != null) {
        token = hit;
        _applyToken();
        await _save();
        return;
      }
      final r = await cas.dio.get(url);
      final here = r.realUri.toString();
      final fromHere = _tokenOf(here);
      if (fromHere != null) {
        token = fromHere;
        _applyToken();
        await _save();
        return;
      }
      final next = loc(r);
      if (next.isEmpty) break;
      url = next.startsWith('http') ? next : Uri.parse(url).resolve(next).toString();
    }
    throw ZhcgmException('智慧场馆登录失败');
  }

  bool _needsLogin(Object data) {
    final t = data.toString();
    return t.contains('需要用户登录') || t.contains('用户需要登录');
  }

  Future<dynamic> _get(String path, {Map<String, dynamic>? query}) async {
    final r = await dio.get(path, queryParameters: query);
    return r.data;
  }

  Future<dynamic> _post(String path, Map<String, dynamic> body) async {
    final r = await dio.post(path, data: body);
    return r.data;
  }

  Future<void> ensure(CasClient cas) async {
    if (!isLoggedIn) await restore();
    if (isLoggedIn) {
      try {
        final raw = await _get(
          '/wechat-c/api/wechat/memberBookController/sessionsRuleList',
          query: {'soprtTypeId': '2'},
        );
        if (!_needsLogin(raw ?? '')) return;
      } catch (e) {
        if (!_needsLogin(e)) rethrow;
      }
    }
    token = null;
    _applyToken();
    await loginWithCas(cas);
  }

  Future<List<Map<String, dynamic>>> sportTypes() async {
    final raw = await _get('/wechat-c/api/wechat/indexController/sportTypeList');
    if (raw is! List) return [];
    return [
      for (final e in raw)
        if (e is Map) Map<String, dynamic>.from(e),
    ];
  }

  Future<({List<Map<String, dynamic>> fields, List<Map<String, dynamic>> placeTypes})> fields(String sportTypeId) async {
    final raw = await _get(
      '/wechat-c/api/wechat/memberBookController/fields',
      query: {'sportTypeId': sportTypeId},
    );
    if (raw is! Map) return (fields: <Map<String, dynamic>>[], placeTypes: <Map<String, dynamic>>[]);
    List<Map<String, dynamic>> maps(Object? v) => [
          for (final e in (v is List ? v : const []))
            if (e is Map) Map<String, dynamic>.from(e),
        ];
    return (fields: maps(raw['fieldList']), placeTypes: maps(raw['placeTypeList']));
  }

  Future<List<List<Map<String, dynamic>>>> sessions({
    required String fieldId,
    required String sportTypeId,
    required String placeTypeId,
    required String searchDate,
  }) async {
    final raw = await _post('/wechat-c/api/wechat/memberBookController/weChatSessionsList', {
      'fieldId': fieldId,
      'placeTypeId': placeTypeId,
      'searchDate': searchDate,
      'sportTypeId': sportTypeId,
    });
    if (_needsLogin(raw ?? '')) throw ZhcgmException('智慧场馆未登录');
    if (raw is Map && raw['msg'] != null && raw['code'] != null && '${raw['code']}' != '200') {
      throw ZhcgmException('${raw['msg']}');
    }
    if (raw is! List) return [];
    return [
      for (final col in raw)
        if (col is List)
          [
            for (final e in col)
              if (e is Map) Map<String, dynamic>.from(e),
          ],
    ];
  }

  Future<Map<String, dynamic>> reserve({
    required String fieldId,
    required String fieldName,
    required String sportTypeId,
    required String sportTypeName,
    required String siteName,
    required DateTime day,
    required List<String> sessionIds,
  }) async {
    final raw = await _post('/business-service/orders/weChatSessionsReserve', {
      'number': sessionIds.length,
      'orderUseDate': DateTime(day.year, day.month, day.day).millisecondsSinceEpoch,
      'requestsList': [
        for (final id in sessionIds) {'sessionsId': id},
      ],
      'fieldName': fieldName,
      'fieldId': fieldId,
      'siteName': siteName,
      'sportTypeName': sportTypeName,
      'sportTypeId': sportTypeId,
    });
    if (raw is! Map) throw ZhcgmException('预约失败');
    final map = Map<String, dynamic>.from(raw);
    if (_needsLogin(map)) throw ZhcgmException('智慧场馆未登录');
    final code = map['code'];
    if (code != null && code != 200 && code != '200') {
      throw ZhcgmException('${map['msg'] ?? '预约失败'}');
    }
    return map;
  }
}

bool zhcgmSlotTaken(Map<String, dynamic> s) {
  final status = '${s['sessionsStatus'] ?? ''}';
  if (status == 'EXPIRED' || status == 'FULLY_BOOKED') return true;
  if ('${s['isClose']}' == 'YES') return true;
  final book = int.tryParse('${s['bookOrder'] ?? 0}') ?? 0;
  final max = int.tryParse('${s['maxOrders'] ?? 0}') ?? 0;
  if (max > 0 && book >= max) return true;
  return false;
}

String zhcgmSlotLabel(Map<String, dynamic> s) {
  final a = '${s['openStartTime'] ?? ''}'.replaceFirst(RegExp(r':\d{2}$'), '');
  if (a.length >= 5) return a.substring(0, 5);
  return a;
}

String zhcgmSlotHint(Map<String, dynamic> s) {
  if ('${s['sessionsStatus']}' == 'EXPIRED') return '已过期';
  if ('${s['isClose']}' == 'YES') return '已关闭';
  if (zhcgmSlotTaken(s)) return '已预订';
  return '可约';
}
