import 'package:flutter_test/flutter_test.dart';
import 'package:swun_ehall/api/ykt.dart';

void main() {
  test('parses 一卡通个人档案账户余额', () {
    expect(parseYktYuan('3.45元'), 3.45);
    expect(parseYktYuan('账户余额：3.45元'), 3.45);
    expect(parseYktYuan(''), isNull);
  });
}
