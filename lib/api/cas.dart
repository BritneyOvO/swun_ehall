import 'dart:convert';
import 'dart:math';
import 'dart:typed_data';

import 'package:cookie_jar/cookie_jar.dart';
import 'package:dio/dio.dart';
import 'package:pointycastle/export.dart';

import 'httpx.dart';

const kAuthBase = 'https://authserver.swun.edu.cn/authserver';
const kEhall = 'https://ehall.swun.edu.cn';
const kEhallService = '$kEhall/login';
const _randChars = 'ABCDEFGHJKMNPQRSTWXYZabcdefhijkmnprstwxyz2345678';

String _rand(int n) {
  final r = Random.secure();
  return String.fromCharCodes(
    List.generate(n, (_) => _randChars.codeUnitAt(r.nextInt(_randChars.length))),
  );
}

Uint8List _pkcs7(List<int> data, int block) {
  final n = block - (data.length % block);
  return Uint8List.fromList([...data, ...List.filled(n, n)]);
}

String encryptPassword(String password, String salt) {
  final key = Uint8List.fromList(utf8.encode(salt.trim()));
  final iv = Uint8List.fromList(utf8.encode(_rand(16)));
  final plain = _pkcs7(utf8.encode(_rand(64) + password), 16);
  final cbc = CBCBlockCipher(AESEngine())
    ..init(true, ParametersWithIV(KeyParameter(key), iv));
  final out = Uint8List(plain.length);
  var offset = 0;
  while (offset < plain.length) {
    offset += cbc.processBlock(plain, offset, out, offset);
  }
  return base64Encode(out);
}

class CasClient {
  CasClient(this.jar) : dio = buildDio(jar);

  final CookieJar jar;
  final Dio dio;

  Future<void> _dropTgt() async {
    for (final origin in ['https://authserver.swun.edu.cn', 'http://authserver.swun.edu.cn']) {
      final uri = Uri.parse('$origin/authserver/login');
      final cookies = await jar.loadForRequest(uri);
      final expired = <Cookie>[];
      for (final c in cookies) {
        if (c.name == 'CASTGC' || c.name == 'TGC') {
          expired.add(
            Cookie(c.name, '')
              ..domain = c.domain
              ..path = c.path ?? '/'
              ..expires = DateTime.fromMillisecondsSinceEpoch(0)
              ..maxAge = 0,
          );
        }
      }
      if (expired.isNotEmpty) await jar.saveFromResponse(uri, expired);
    }
  }

  Future<void> _consumeService(String url) async {
    var u = upgradeSwunHttps(url);
    for (var i = 0; i < 12; i++) {
      final r = await dio.get(u);
      if (r.statusCode == 200) return;
      if (!isRedirect(r) || loc(r).isEmpty) return;
      u = absUrl(u, loc(r));
    }
  }

  Future<Response> _loginPage() async {
    var url = '$kAuthBase/login';
    var params = <String, dynamic>{'service': kEhallService};
    for (var i = 0; i < 8; i++) {
      final page = await dio.get(url, queryParameters: params);
      if (page.statusCode == 200) return page;
      if (!isRedirect(page) || loc(page).isEmpty) {
        throw Exception('登录页 HTTP ${page.statusCode}');
      }
      final next = loc(page);
      if (next.contains('ticket=')) {
        return page;
      }
      final abs = absUrl(kAuthBase, next);
      url = abs;
      params = {};
    }
    throw Exception('登录页跳转过多');
  }

  Future<void> login(String username, String password) async {
    await _dropTgt();
    var page = await _loginPage();
    if (isRedirect(page) && loc(page).contains('ticket=')) {
      await _dropTgt();
      page = await _loginPage();
    }
    if (isRedirect(page) && loc(page).contains('ticket=')) {
      await _consumeService(loc(page));
      return;
    }
    if (page.statusCode != 200) {
      throw Exception('登录页 HTTP ${page.statusCode}');
    }
    final html = page.data.toString();
    final execution = RegExp(r'id="execution"[^>]*value="([^"]*)"').firstMatch(html)?.group(1);
    final salt = RegExp(r'id="pwdEncryptSalt"[^>]*value="([^"]*)"').firstMatch(html)?.group(1);
    if (execution == null || salt == null) {
      throw Exception('登录页解析失败');
    }

    final cap = await dio.post(
      '$kAuthBase/checkNeedCaptcha.htl',
      data: {'username': username},
      options: Options(contentType: Headers.formUrlEncodedContentType),
    );
    try {
      final m = cap.data is Map ? cap.data : jsonDecode(cap.data.toString());
      if (m is Map && m['isNeed'] == true) {
        throw Exception('该账号需要验证码, 请稍后再试');
      }
    } catch (e) {
      if (e is Exception && e.toString().contains('验证码')) rethrow;
    }

    final r = await dio.post(
      '$kAuthBase/login',
      queryParameters: {'service': kEhallService},
      data: {
        'username': username,
        'password': encryptPassword(password, salt),
        'captcha': '',
        'execution': execution,
        '_eventId': 'submit',
        'cllt': 'userNameLogin',
        'dllt': 'generalLogin',
        'lt': '',
      },
      options: Options(contentType: Headers.formUrlEncodedContentType),
    );
    if (r.statusCode == 200) {
      final msg = RegExp(r'id="showErrorTip"[^>]*>\s*(?:<p[^>]*>)?([^<]{2,200})')
          .firstMatch(r.data.toString())
          ?.group(1)
          ?.trim();
      throw Exception(msg ?? '登录失败');
    }
    if (!isRedirect(r) || !loc(r).contains('ticket=')) {
      throw Exception('登录异常: 未拿到 ticket');
    }
    await _consumeService(loc(r));
  }

  Future<bool> hasTgt() async {
    final cookies = await jar.loadForRequest(Uri.parse('$kAuthBase/login'));
    return cookies.any((c) => c.name == 'CASTGC' && c.value.isNotEmpty);
  }

  Future<bool> tgtAlive() async {
    if (await hasTgt()) return true;
    try {
      final r = await dio.get(
        '$kAuthBase/login',
        queryParameters: {'service': kEhallService},
      );
      return loc(r).contains('ticket=');
    } catch (_) {
      return false;
    }
  }

  /// 用已有 CASTGC 向任意 service 换 ST, 返回带 ticket 的 Location.
  Future<String> ticketFor(String service) async {
    var url = '$kAuthBase/login';
    var params = <String, dynamic>{'service': service};
    for (var i = 0; i < 6; i++) {
      final r = await dio.get(
        url,
        queryParameters: params,
        options: Options(receiveTimeout: const Duration(seconds: 8)),
      );
      final next = loc(r);
      if (next.contains('ticket=')) {
        return upgradeSwunHttps(next);
      }
      if (!isRedirect(r) || next.isEmpty) {
        throw Exception('未能换到 ticket (会话可能已过期)');
      }
      url = absUrl(kAuthBase, next);
      params = {};
    }
    throw Exception('未能换到 ticket (会话可能已过期)');
  }
}
