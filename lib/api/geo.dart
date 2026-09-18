import 'dart:math';

/// 中国测绘局加密坐标。校内课堂考勤 / 公寓打卡的围栏都是 GCJ-02。
/// GPS 芯片和 AOSP fused 给出 WGS-84，不转换会在成都偏大约 300–500 米。
/// 高德点和国产 ROM 的 `network` 已经是 GCJ-02，不能再转。

const _a = 6378245.0;
const _ee = 0.00669342162296594323;
const _earth = 6371000.0;

bool geoOutOfChina(double lat, double lng) {
  return lng < 72.004 || lng > 137.8347 || lat < 0.8293 || lat > 55.8271;
}

/// 签到 / 打卡提交前：WGS-84 转 GCJ-02，已经是 GCJ 的点原样返回。
(double lat, double lng) campusGcj02(
  double lat,
  double lng, {
  required String source,
  String? datum,
  String? provider,
}) {
  if (!geoIsWgs84(source: source, datum: datum, provider: provider)) {
    return (lat, lng);
  }
  return wgs84ToGcj02(lat, lng);
}

/// [datum] 优先。否则 GPS / fused / 名字里带 fused 的系统点当 WGS-84。
bool geoIsWgs84({required String source, String? datum, String? provider}) {
  final d = datum?.trim().toLowerCase();
  if (d == 'gcj02') return false;
  if (d == 'wgs84') return true;
  final p = provider?.trim().toLowerCase() ?? '';
  if (p == 'network') return false;
  if (p == 'gps' ||
      p == 'passive' ||
      p.contains('fused') ||
      (p.contains('gps') && !p.contains('network'))) {
    return true;
  }
  switch (source) {
    case 'amap':
    case 'network':
    case 'demo':
      return false;
    case 'gps':
    case 'fused':
      return true;
    default:
      return false;
  }
}

/// 迭代反解。教室 GCJ 用来模拟芯片 GPS 时用。
(double lat, double lng) gcj02ToWgs84(double lat, double lng) {
  if (!lat.isFinite || !lng.isFinite) return (lat, lng);
  if (geoOutOfChina(lat, lng)) return (lat, lng);
  var wgsLat = lat;
  var wgsLng = lng;
  for (var i = 0; i < 8; i++) {
    final g = wgs84ToGcj02(wgsLat, wgsLng);
    wgsLat -= g.$1 - lat;
    wgsLng -= g.$2 - lng;
  }
  return (wgsLat, wgsLng);
}

(double lat, double lng) wgs84ToGcj02(double lat, double lng) {
  if (!lat.isFinite || !lng.isFinite) return (lat, lng);
  if (geoOutOfChina(lat, lng)) return (lat, lng);
  final dLat = _transformLat(lng - 105.0, lat - 35.0);
  final dLng = _transformLng(lng - 105.0, lat - 35.0);
  final rad = lat / 180.0 * pi;
  var magic = sin(rad);
  magic = 1 - _ee * magic * magic;
  final sqrtMagic = sqrt(magic);
  final nlat = (dLat * 180.0) / ((_a * (1 - _ee)) / (magic * sqrtMagic) * pi);
  final nlng = (dLng * 180.0) / (_a / sqrtMagic * cos(rad) * pi);
  return (lat + nlat, lng + nlng);
}

double geoMeters(double lat1, double lng1, double lat2, double lng2) {
  final p1 = lat1 * pi / 180;
  final p2 = lat2 * pi / 180;
  final dP = (lat2 - lat1) * pi / 180;
  final dL = (lng2 - lng1) * pi / 180;
  final a =
      sin(dP / 2) * sin(dP / 2) + cos(p1) * cos(p2) * sin(dL / 2) * sin(dL / 2);
  return 2 * _earth * atan2(sqrt(a), sqrt(1 - a));
}

double _transformLat(double x, double y) {
  var ret =
      -100.0 +
      2.0 * x +
      3.0 * y +
      0.2 * y * y +
      0.1 * x * y +
      0.2 * sqrt(x.abs());
  ret += (20.0 * sin(6.0 * x * pi) + 20.0 * sin(2.0 * x * pi)) * 2.0 / 3.0;
  ret += (20.0 * sin(y * pi) + 40.0 * sin(y / 3.0 * pi)) * 2.0 / 3.0;
  ret += (160.0 * sin(y / 12.0 * pi) + 320 * sin(y * pi / 30.0)) * 2.0 / 3.0;
  return ret;
}

double _transformLng(double x, double y) {
  var ret =
      300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(x.abs());
  ret += (20.0 * sin(6.0 * x * pi) + 20.0 * sin(2.0 * x * pi)) * 2.0 / 3.0;
  ret += (20.0 * sin(x * pi) + 40.0 * sin(x / 3.0 * pi)) * 2.0 / 3.0;
  ret += (150.0 * sin(x / 12.0 * pi) + 300.0 * sin(x / 30.0 * pi)) * 2.0 / 3.0;
  return ret;
}
