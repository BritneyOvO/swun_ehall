import 'dart:convert';

import 'package:cookie_jar/cookie_jar.dart';
import 'package:dio/dio.dart';

import 'httpx.dart';
import 'rs_gateway.dart';

const kYktH5 = 'https://ykth5.swun.edu.cn';
const kYktMenu = '$kYktH5/menu/menu.do?menu=qrcode';
const kYktQrApi = '$kYktH5/qrcode/queryCardInfo.do';

class YktQr {
  const YktQr({required this.payload, this.expire});

  final String payload;
  final String? expire;
}

class YktClient {
  YktClient(this.jar, {RsGateway? gateway}) : dio = buildDio(jar) {
    rs = gateway ?? RsGateway(jar: jar);
  }

  final CookieJar jar;
  final Dio dio;
  late final RsGateway rs;

  Future<YktQr> fetchQr({required String studentId, String schoolId = '187'}) async {
    if (studentId.trim().isEmpty) throw Exception('没有学号，无法打开一卡通');
    rs.remember(Uri.parse(kYktH5).host);
    final menu = await rs.request(method: 'GET', url: kYktMenu);
    if (looksLikeRuishu(status: menu.status, body: menu.body)) {
      throw Exception('一卡通入口仍被网关拦截');
    }
    final expire = RegExp(r"timestamp\s*=\s*'([^']+)'").firstMatch(menu.body)?.group(1);
    if (expire == null || expire.isEmpty) {
      throw Exception('未拿到一卡通入口参数');
    }
    final fn =
        '$kYktH5/menu/function.do?expire=$expire&stu_code=$studentId&acco_id=$studentId&school_id=$schoolId&menu=qrcode';
    await rs.navigate(fn);
    final hit = await rs.request(
      method: 'POST',
      url: kYktQrApi,
      headers: {
        'Accept': 'application/json, text/javascript, */*; q=0.01',
        'X-Requested-With': 'XMLHttpRequest',
        'Referer': fn,
      },
    );
    if (looksLikeRuishu(status: hit.status, body: hit.body)) {
      throw Exception('一卡通二维码仍被网关拦截');
    }
    final payload = _payloadOf(hit.body);
    if (payload == null || payload.isEmpty) {
      throw Exception('用户无卡片，无法生成二维码');
    }
    return YktQr(payload: payload, expire: expire);
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
        for (final k in const ['qrcode', 'qrCode', 'code', 'data', 'barCode', 'barcode', 'msg']) {
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
