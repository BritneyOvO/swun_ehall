import 'package:flutter/services.dart';

class AccountVault {
  static const _ch = MethodChannel('cn.edu.swun.swun_ehall/vault');

  static Future<({String iv, String ct})> encrypt(String plain) async {
    final raw = await _ch.invokeMethod<dynamic>('encrypt', {'plain': plain});
    if (raw is! Map) throw Exception('Keystore 加密失败');
    final iv = '${raw['iv'] ?? ''}'.trim();
    final ct = '${raw['ct'] ?? ''}'.trim();
    if (iv.isEmpty || ct.isEmpty) throw Exception('Keystore 加密失败');
    return (iv: iv, ct: ct);
  }

  static Future<String> decrypt(String iv, String ct) async {
    final plain = await _ch.invokeMethod<String>('decrypt', {'iv': iv, 'ct': ct});
    final out = (plain ?? '').trim();
    if (out.isEmpty) throw Exception('Keystore 解密失败');
    return out;
  }
}
