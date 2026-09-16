import 'dart:math';

/// 中国测绘局加密坐标。校内课堂考勤 / 公寓打卡的围栏都是 GCJ-02。
/// GPS 芯片给出 WGS-84，不转换会在成都偏大约 300–500 米。

const _a = 6378245.0;
const _ee = 0.00669342162296594323;
const _earth = 6371000.0;

bool geoOutOfChina(double lat, double lng) {
  return lng < 72.004 || lng > 137.8347 || lat < 0.8293 || lat > 55.8271;
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
  final a = sin(dP / 2) * sin(dP / 2) +
      cos(p1) * cos(p2) * sin(dL / 2) * sin(dL / 2);
  return 2 * _earth * atan2(sqrt(a), sqrt(1 - a));
}

double _transformLat(double x, double y) {
  var ret = -100.0 +
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
  var ret = 300.0 +
      x +
      2.0 * y +
      0.1 * x * x +
      0.1 * x * y +
      0.1 * sqrt(x.abs());
  ret += (20.0 * sin(6.0 * x * pi) + 20.0 * sin(2.0 * x * pi)) * 2.0 / 3.0;
  ret += (20.0 * sin(x * pi) + 40.0 * sin(x / 3.0 * pi)) * 2.0 / 3.0;
  ret += (150.0 * sin(x / 12.0 * pi) + 300.0 * sin(x / 30.0 * pi)) * 2.0 / 3.0;
  return ret;
}
