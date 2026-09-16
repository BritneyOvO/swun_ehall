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
}
