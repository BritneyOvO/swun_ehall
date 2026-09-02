import 'package:flutter_test/flutter_test.dart';
import 'package:swun_ehall/api/ktkq.dart';

const _jwt =
    'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJzdHVkZW50Iiwicm9sZSI6InhzIn0.signaturepart';

void main() {
  test('parses Authorization Set-Cookie', () {
    expect(
      parseKtkqToken(
        setCookies: ['Authorization=$_jwt; Path=/jwmobile; HttpOnly'],
      ),
      _jwt,
    );
    expect(
      parseKtkqToken(
        setCookies: ['other=1; Authorization=$_jwt; Path=/jwmobile'],
      ),
      _jwt,
    );
  });

  test('parses hash and query token', () {
    expect(
      parseKtkqToken(
        urls: [
          'https://ktkq.swun.edu.cn/jwmobile/index#/index/kb/course/list?token=$_jwt',
        ],
      ),
      _jwt,
    );
    expect(
      parseKtkqToken(
        urls: ['https://ktkq.swun.edu.cn/jwmobile/auth/index?token=$_jwt'],
      ),
      _jwt,
    );
  });

  test('parses JSON body token like Python client', () {
    expect(parseKtkqToken(body: {'token': _jwt}), _jwt);
    expect(
      parseKtkqToken(
        body: {
          'code': 200,
          'data': {'token': _jwt},
        },
      ),
      _jwt,
    );
    expect(parseKtkqToken(body: '{"code":200,"data":{"token":"$_jwt"}}'), _jwt);
    expect(parseKtkqToken(body: 'var cfg = {"token": "$_jwt"};'), _jwt);
  });

  test('cleans encoding, quotes and bearer', () {
    expect(cleanKtkqToken('Bearer $_jwt'), _jwt);
    expect(cleanKtkqToken('"$_jwt"'), _jwt);
    expect(cleanKtkqToken(Uri.encodeComponent(_jwt)), _jwt);
    expect(cleanKtkqToken('null'), isNull);
    expect(cleanKtkqToken('short'), isNull);
  });
}
