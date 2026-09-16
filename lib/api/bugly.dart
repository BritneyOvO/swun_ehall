import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

/// 腾讯 Bugly：原生崩溃 / ANR 在 Application 里初始化；Dart 异常走这个通道。
class Bugly {
  static const _ch = MethodChannel('cn.edu.swun.swun_ehall/bugly');

  static void installHooks() {
    final prev = FlutterError.onError;
    FlutterError.onError = (details) {
      prev?.call(details);
      unawaited(
        postError(
          details.exception,
          details.stack ?? StackTrace.empty,
          details.library,
        ),
      );
    };
    PlatformDispatcher.instance.onError = (error, stack) {
      unawaited(postError(error, stack));
      return false;
    };
  }

  static Future<void> setUserId(String id) async {
    try {
      await _ch.invokeMethod('setUserId', id.trim());
    } catch (_) {}
  }

  static Future<Map<String, String>> info() async {
    try {
      final raw = await _ch.invokeMethod('info');
      if (raw is Map) {
        return {for (final e in raw.entries) '${e.key}': '${e.value}'};
      }
    } catch (_) {}
    return {};
  }

  static Future<void> postError(
    Object error,
    StackTrace stack, [
    String? library,
  ]) async {
    try {
      await _ch.invokeMethod('postError', {
        'name': error.runtimeType.toString(),
        'message': [
          if (library != null && library.isNotEmpty) library,
          error.toString(),
        ].join('\n'),
        'stack': stack.toString(),
      });
    } catch (_) {}
  }
}
