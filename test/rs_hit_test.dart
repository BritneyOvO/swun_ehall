import 'package:flutter_test/flutter_test.dart';
import 'package:swun_ehall/api/rs_gateway.dart';

void main() {
  test('empty 200 is usable, ruishu and HTML are not', () {
    expect(RsHit(status: 200, body: '').usable, isTrue);
    expect(RsHit(status: 200, body: '"QRCODEPAYLOAD"').usable, isTrue);
    expect(RsHit(status: 412, body: '<html>').usable, isFalse);
    expect(RsHit(status: 200, body: '<!DOCTYPE html>x').usable, isFalse);
    expect(looksLikeRuishu(status: 412), isTrue);
  });
}
