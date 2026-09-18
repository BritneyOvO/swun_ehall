import 'package:flutter_test/flutter_test.dart';
import 'package:swun_ehall/api/geo.dart';

void main() {
  test('leaves coordinates outside China unchanged', () {
    expect(wgs84ToGcj02(37.39, -122.08), (37.39, -122.08));
  });

  test('Chengdu WGS-84 shifts several hundred meters to GCJ-02', () {
    // 武侯校区附近一个 WGS-84 点。
    const lat = 30.6370;
    const lng = 104.0430;
    final gcj = wgs84ToGcj02(lat, lng);
    final d = geoMeters(lat, lng, gcj.$1, gcj.$2);
    expect(d, greaterThan(300));
    expect(d, lessThan(700));
    expect(gcj.$1, isNot(lat));
    expect(gcj.$2, isNot(lng));
  });

  test('geoMeters of identical points is 0', () {
    expect(geoMeters(30.64, 104.04, 30.64, 104.04), 0);
  });

  test('campusGcj02 converts gps and fused, not amap or network', () {
    const lat = 30.6370;
    const lng = 104.0430;
    final gps = campusGcj02(lat, lng, source: 'gps');
    final fused = campusGcj02(lat, lng, source: 'fused');
    final fusedName = campusGcj02(lat, lng, source: 'last', provider: 'fused');
    final amap = campusGcj02(lat, lng, source: 'amap');
    final net = campusGcj02(lat, lng, source: 'network');
    expect(gps, isNot((lat, lng)));
    expect(fused, gps);
    expect(fusedName, gps);
    expect(amap, (lat, lng));
    expect(net, (lat, lng));
  });

  test('datum gcj02 wins over gps source so plugin does not double-shift', () {
    const lat = 30.6370;
    const lng = 104.0430;
    final raw = campusGcj02(lat, lng, source: 'gps', datum: 'gcj02');
    expect(raw, (lat, lng));
  });

  test('gcj02ToWgs84 round-trips Chengdu classroom coords', () {
    const lat = 30.57137;
    const lng = 103.97029;
    final wgs = gcj02ToWgs84(lat, lng);
    final back = wgs84ToGcj02(wgs.$1, wgs.$2);
    expect(geoMeters(lat, lng, back.$1, back.$2), lessThan(1));
    expect(geoMeters(lat, lng, wgs.$1, wgs.$2), greaterThan(300));
  });

  test('geoIsWgs84 treats OEM fused aliases as WGS', () {
    expect(geoIsWgs84(source: 'last', provider: 'gps_fused'), isTrue);
    expect(geoIsWgs84(source: 'last', provider: 'network'), isFalse);
    expect(geoIsWgs84(source: 'gps', datum: 'wgs84'), isTrue);
  });
}
