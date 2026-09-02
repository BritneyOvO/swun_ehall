import 'dart:io';

import 'package:cookie_jar/cookie_jar.dart';
import 'package:dio/dio.dart';
import 'package:dio/io.dart';
import 'package:dio_cookie_manager/dio_cookie_manager.dart';

const kUa =
    'Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36';

void attachHttpClient(Dio dio) {
  dio.httpClientAdapter = IOHttpClientAdapter(
    createHttpClient: () {
      final client = HttpClient();
      client.badCertificateCallback = (cert, host, port) => true;
      client.connectionTimeout = const Duration(seconds: 8);
      client.idleTimeout = const Duration(seconds: 60);
      // Prefer IPv4 to skip campus AAAA blackholes. HttpClient will not wrap
      // this socket in TLS, so HTTPS must return a SecureSocket with SNI=host.
      client.connectionFactory = (uri, proxyHost, proxyPort) async {
        if (proxyHost != null && proxyPort != null) {
          return Socket.startConnect(proxyHost, proxyPort);
        }
        final port = uri.hasPort
            ? uri.port
            : (uri.isScheme('https') ? 443 : 80);
        Object host = uri.host;
        try {
          final addrs = await InternetAddress.lookup(
            uri.host,
            type: InternetAddressType.IPv4,
          ).timeout(const Duration(seconds: 3));
          if (addrs.isNotEmpty) host = addrs.first;
        } catch (_) {}
        if (!uri.isScheme('https')) {
          return Socket.startConnect(host, port);
        }
        final raw = await Socket.startConnect(host, port);
        return ConnectionTask.fromSocket(
          raw.socket.then(
            (socket) => SecureSocket.secure(
              socket,
              host: uri.host,
              onBadCertificate: (_) => true,
            ),
          ),
          raw.cancel,
        );
      };
      return client;
    },
  );
}

Dio buildDio(CookieJar jar) {
  final dio = Dio(
    BaseOptions(
      connectTimeout: const Duration(seconds: 8),
      receiveTimeout: const Duration(seconds: 15),
      followRedirects: false,
      validateStatus: (s) => s != null && s < 500,
      headers: {'User-Agent': kUa, 'Accept-Language': 'zh-CN,zh;q=0.9'},
    ),
  );
  attachHttpClient(dio);
  dio.interceptors.add(CookieManager(jar));
  return dio;
}

String loc(Response r) => r.headers.value('location') ?? '';

bool isRedirect(Response r) =>
    r.statusCode == 301 ||
    r.statusCode == 302 ||
    r.statusCode == 303 ||
    r.statusCode == 307 ||
    r.statusCode == 308;

String absUrl(String base, String next) {
  if (next.startsWith('http')) return upgradeSwunHttps(next);
  return upgradeSwunHttps(Uri.parse(base).resolve(next).toString());
}

bool looksNightClosed({int? status, Object? data}) {
  final t = data?.toString() ?? '';
  if (t.contains('没有权限访问该网站') ||
      t.contains('没有权限访问相应的资源') ||
      (t.contains('出错啦') && t.contains('403')) ||
      t.contains('错误代码：403') ||
      t.contains('错误代码:403')) {
    return true;
  }
  if (status == 403) return true;
  return t.contains('系统已关闭') ||
      t.contains('不在服务时间') ||
      t.contains('不在访问时间') ||
      t.contains('非服务时间') ||
      t.contains('服务已关闭') ||
      t.contains('当前不在服务时间');
}

String nightClosedMessage(String name) => '$name夜间关闭';

String publicError(Object e) {
  var s = e.toString().replaceFirst('Exception: ', '');
  if (looksNightClosed(data: s) || s.contains('夜间关闭')) {
    return RegExp(r'[^。\s]{2,8}夜间关闭').firstMatch(s)?.group(0) ?? '该功能夜间关闭';
  }
  if (s.contains('<html') || s.contains('<!DOCTYPE') || s.contains('<HTML')) {
    return '请求失败，请稍后重试';
  }
  return s;
}

/// CAS 有时仍回 http Location; 站点本身是 https.
String upgradeSwunHttps(String url) {
  return url
      .replaceFirst('http://ehall.swun.edu.cn', 'https://ehall.swun.edu.cn')
      .replaceFirst('http://jwxt.swun.edu.cn', 'https://jwxt.swun.edu.cn');
}
