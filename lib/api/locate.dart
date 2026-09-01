import 'package:flutter/services.dart';
import 'package:geolocator/geolocator.dart';

/// 校园打卡 H5（金智/蓝图入口）使用的高德 JS Key。
const kDefaultAmapKey = '7bf909742712a9eca7c5e18efa431f9a';

class LocateException implements Exception {
  LocateException(this.message);
  final String message;
  @override
  String toString() => message;
}

class GeoFix {
  const GeoFix({
    required this.latitude,
    required this.longitude,
    this.accuracy = 0,
    this.source = '',
    this.at,
  });

  final double latitude;
  final double longitude;
  final double accuracy;
  final String source;
  final DateTime? at;

  String get sourceLabel {
    switch (source) {
      case 'amap':
        return '高德';
      case 'network':
        return '网络';
      case 'gps':
        return 'GPS';
      case 'last':
        return '缓存';
      case 'demo':
        return '示例';
      default:
        return source.isEmpty ? '定位' : source;
    }
  }

  String get coordText => '${latitude.toStringAsFixed(5)}, ${longitude.toStringAsFixed(5)}';
}

class AppLocator {
  static const _ch = MethodChannel('cn.edu.swun.swun_ehall/locate');
  static const _ttl = Duration(seconds: 25);
  static const _fresh = Duration(seconds: 12);

  static GeoFix? cache;
  static DateTime? _cacheAt;
  static const demoFix = GeoFix(
    latitude: 30.58120,
    longitude: 103.97048,
    accuracy: 12,
    source: 'demo',
  );

  static Future<void> setKey(String key) async {
    try {
      final k = key.trim().isEmpty ? kDefaultAmapKey : key.trim();
      await _ch.invokeMethod('setKey', k);
    } catch (_) {}
  }

  static Future<Map<String, String>> info() async {
    try {
      final m = await _ch.invokeMethod('info');
      if (m is Map) {
        return {for (final e in m.entries) '${e.key}': '${e.value}'};
      }
    } catch (_) {}
    return {};
  }

  static Future<void> warmup() async {
    try {
      await setKey(kDefaultAmapKey);
      if (!await ensurePermission(request: false)) return;
      await _ch.invokeMethod('warmup');
    } catch (_) {}
  }

  static Future<void> openSettings() => Geolocator.openLocationSettings();

  static Future<bool> ensurePermission({bool request = true}) async {
    var perm = await Geolocator.checkPermission();
    if (perm == LocationPermission.denied && request) {
      perm = await Geolocator.requestPermission();
    }
    return perm != LocationPermission.denied && perm != LocationPermission.deniedForever;
  }

  static Future<GeoFix> current({
    bool demo = false,
    bool force = false,
    Duration timeout = const Duration(seconds: 8),
    void Function(GeoFix)? onUpdate,
  }) async {
    if (demo) {
      onUpdate?.call(demoFix);
      return demoFix;
    }
    final now = DateTime.now();
    final hit = cache;
    final at = _cacheAt;
    if (!force && hit != null && at != null && now.difference(at) < _ttl) {
      onUpdate?.call(hit);
      if (now.difference(at) < _fresh && hit.accuracy > 0 && hit.accuracy <= 80) {
        return hit;
      }
    } else if (hit != null) {
      onUpdate?.call(hit);
    }

    if (!await Geolocator.isLocationServiceEnabled()) {
      throw LocateException('系统定位已关闭，请打开后再试');
    }
    if (!await ensurePermission()) {
      throw LocateException('需要定位权限');
    }

    try {
      final raw = await _ch.invokeMethod('getFix', {
        'timeoutMs': timeout.inMilliseconds,
        'force': force,
      }).timeout(timeout + const Duration(seconds: 2));
      final fix = _parse(raw);
      _store(fix);
      onUpdate?.call(fix);
      return fix;
    } on LocateException {
      rethrow;
    } on PlatformException catch (e) {
      if (e.code == 'PERMISSION') throw LocateException('需要定位权限');
      return _fallback(onUpdate: onUpdate);
    } catch (_) {
      return _fallback(onUpdate: onUpdate);
    }
  }

  static Future<GeoFix> _fallback({void Function(GeoFix)? onUpdate}) async {
    try {
      final last = await Geolocator.getLastKnownPosition(forceAndroidLocationManager: true);
      if (last != null) {
        final fix = GeoFix(
          latitude: last.latitude,
          longitude: last.longitude,
          accuracy: last.accuracy,
          source: 'last',
          at: last.timestamp,
        );
        _store(fix);
        onUpdate?.call(fix);
      }
    } catch (_) {}
    try {
      final p = await Geolocator.getCurrentPosition(
        locationSettings: AndroidSettings(
          accuracy: LocationAccuracy.low,
          forceLocationManager: true,
          intervalDuration: const Duration(milliseconds: 400),
          timeLimit: const Duration(seconds: 5),
        ),
      );
      final fix = GeoFix(
        latitude: p.latitude,
        longitude: p.longitude,
        accuracy: p.accuracy,
        source: 'network',
        at: p.timestamp,
      );
      _store(fix);
      onUpdate?.call(fix);
      return fix;
    } catch (_) {}
    final hit = cache;
    if (hit != null) return hit;
    throw LocateException('无法获取定位，请打开系统定位后重试');
  }

  static void _store(GeoFix fix) {
    cache = fix;
    _cacheAt = DateTime.now();
  }

  static GeoFix _parse(Object? raw) {
    if (raw is! Map) throw LocateException('无法获取定位');
    final lat = (raw['latitude'] as num?)?.toDouble();
    final lng = (raw['longitude'] as num?)?.toDouble();
    if (lat == null || lng == null) throw LocateException('无法获取定位');
    final ms = (raw['time'] as num?)?.toInt();
    return GeoFix(
      latitude: lat,
      longitude: lng,
      accuracy: (raw['accuracy'] as num?)?.toDouble() ?? 0,
      source: '${raw['source'] ?? ''}',
      at: ms == null || ms <= 0 ? DateTime.now() : DateTime.fromMillisecondsSinceEpoch(ms),
    );
  }
}
