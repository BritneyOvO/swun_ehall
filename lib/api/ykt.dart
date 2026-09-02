import 'dart:convert';

import 'package:cookie_jar/cookie_jar.dart';
import 'package:dio/dio.dart';

import 'httpx.dart';
import 'rs_gateway.dart';

const kYktH5 = 'https://ykth5.swun.edu.cn';
const kYktMenu = '$kYktH5/menu/menu.do?menu=qrcode';
const kYktQrApi = '$kYktH5/qrcode/queryCardInfo.do';

class YktQr {
  const YktQr({required this.payload, this.expire, this.balanceYuan});

  final String payload;
  final String? expire;
  final double? balanceYuan;
}

class YktClient {
  YktClient(this.jar, {RsGateway? gateway}) : dio = buildDio(jar) {
    rs = gateway ?? RsGateway(jar: jar);
  }

  final CookieJar jar;
  final Dio dio;
  late final RsGateway rs;
  bool nightClosed = false;

  Future<RsHit> _postQr(String referer) {
    return rs.request(
      method: 'POST',
      url: kYktQrApi,
      headers: {
        'Accept': 'application/json, text/javascript, */*; q=0.01',
        'X-Requested-With': 'XMLHttpRequest',
        'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8',
        'Referer': referer,
      },
    );
  }

  Future<YktQr> fetchQr({required String studentId, String schoolId = '187'}) {
    return _fetchQr(studentId: studentId, schoolId: schoolId).timeout(
      const Duration(seconds: 50),
      onTimeout: () => throw Exception('一卡通请求超时'),
    );
  }

  Future<String> _openFunction({
    required String studentId,
    required String schoolId,
    required String menu,
  }) async {
    final host = Uri.parse(kYktH5).host;
    await rs.navigate(kYktMenu);
    final ts = await _menuTimestamp(host);
    if (ts.isEmpty) throw Exception('一卡通页面未就绪');
    final fn =
        '$kYktH5/menu/function.do?expire=$ts&stu_code=$studentId&acco_id=$studentId&school_id=$schoolId&menu=$menu';
    await rs.navigate(fn);
    return fn;
  }

  Future<double?> _readBalanceYuan() async {
    final host = Uri.parse(kYktH5).host;
    final raw = (await rs.evalJs(host, r'''
      try {
        var ps = document.getElementsByTagName('p');
        for (var i = 0; i < ps.length; i++) {
          var t = ps[i].innerText || '';
          if (t.indexOf('账户余额') >= 0) {
            var inp = ps[i].querySelector('input');
            if (inp && inp.value) return String(inp.value);
            return t;
          }
        }
        return '';
      } catch (e) { return ''; }
    ''')).trim();
    return parseYktYuan(raw);
  }

  Future<String> _menuTimestamp(String host) async {
    for (var i = 0; i < 8; i++) {
      final ts = (await rs.evalJs(host, r'''
        try {
          if (typeof timestamp === 'string' && timestamp.length) return timestamp;
          if (window.timestamp) return String(window.timestamp);
          var h = document.documentElement ? document.documentElement.innerHTML : '';
          var m = h.match(/var\s+timestamp\s*=\s*'([^']+)'/);
          return m ? m[1] : '';
        } catch (e) { return ''; }
      ''')).trim();
      if (ts.isNotEmpty) return ts;
      await Future<void>.delayed(const Duration(milliseconds: 200));
    }
    return '';
  }

  Future<YktQr> _fetchQr({
    required String studentId,
    String schoolId = '187',
  }) async {
    if (studentId.trim().isEmpty) throw Exception('没有学号，无法打开一卡通');
    final host = Uri.parse(kYktH5).host;
    rs.remember(host);
    double? balance;
    try {
      await _openFunction(
        studentId: studentId,
        schoolId: schoolId,
        menu: 'data',
      );
      await Future<void>.delayed(const Duration(milliseconds: 200));
      balance = await _readBalanceYuan();
    } catch (_) {}
    final fn = await _openFunction(
      studentId: studentId,
      schoolId: schoolId,
      menu: 'qrcode',
    );
    await Future<void>.delayed(const Duration(milliseconds: 280));
    final hit = await _postQr(fn);
    if (looksNightClosed(status: hit.status, data: hit.body)) {
      nightClosed = true;
      throw Exception(nightClosedMessage('一卡通'));
    }
    if (looksLikeRuishu(status: hit.status, body: hit.body)) {
      throw Exception('一卡通二维码仍被网关拦截');
    }
    final payload = _payloadOf(hit.body);
    if (payload == null || payload.isEmpty) {
      throw Exception('用户无卡片，无法生成二维码');
    }
    nightClosed = false;
    return YktQr(payload: payload, balanceYuan: balance);
  }

  String? _payloadOf(String body) {
    final t = body.trim();
    if (t.isEmpty || t == 'null') return null;
    try {
      final d = jsonDecode(t);
      if (d == null) return null;
      if (d is String) return d.trim().isEmpty ? null : d.trim();
      if (d is num) return '$d';
      if (d is Map) {
        for (final k in const [
          'qrcode',
          'qrCode',
          'code',
          'data',
          'barCode',
          'barcode',
          'msg',
        ]) {
          final v = '${d[k] ?? ''}'.trim();
          if (v.isNotEmpty && v != 'null') return v;
        }
      }
    } catch (_) {}
    if (t.startsWith('"') && t.endsWith('"') && t.length > 2) {
      return t.substring(1, t.length - 1);
    }
    if (t.startsWith('<')) return null;
    return t;
  }
}

/// 一卡通个人档案「账户余额：3.45元」.
double? parseYktYuan(String raw) {
  final m = RegExp(r'(\d+(?:\.\d+)?)').firstMatch(raw.replaceAll(',', ''));
  if (m == null) return null;
  return double.tryParse(m.group(1)!);
}
