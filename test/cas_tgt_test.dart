import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:swun_ehall/api/cas.dart';

void main() {
  test('casCookieIsTgt matches CASTGC / TGC', () {
    expect(casCookieIsTgt(Cookie('CASTGC', 'TGT-1')), isTrue);
    expect(casCookieIsTgt(Cookie('TGC', 'TGT-1')), isTrue);
    expect(casCookieIsTgt(Cookie('CASTGC', '')), isFalse);
    expect(casCookieIsTgt(Cookie('JSESSIONID', 'abc')), isFalse);
  });
}
