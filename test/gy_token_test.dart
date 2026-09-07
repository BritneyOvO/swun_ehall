import 'package:flutter_test/flutter_test.dart';
import 'package:swun_ehall/api/gyglxt.dart';

void main() {
  test('gyTokenExpired matches 公寓 token失效 messages', () {
    expect(gyTokenExpired({'code': 401}), isTrue);
    expect(gyTokenExpired({'code': '403'}), isTrue);
    expect(gyTokenExpired({'code': 500, 'msg': 'token失效'}), isTrue);
    expect(gyTokenExpired({'code': 500, 'msg': 'Token已失效'}), isTrue);
    expect(gyTokenExpired({'code': 500, 'msg': '请重新登录'}), isTrue);
    expect(gyTokenExpired({'code': 0, 'msg': '打卡成功'}), isFalse);
    expect(gyTokenExpired({'code': 500, 'msg': '不在打卡范围'}), isFalse);
    expect(gyTokenExpired({}, httpStatus: 401), isTrue);
  });
}
