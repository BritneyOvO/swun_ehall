import 'dart:convert';
import 'dart:io';
import 'dart:math';
import 'dart:typed_data';

import 'package:crypto/crypto.dart';
import 'package:dio/dio.dart';
import 'package:pointycastle/asn1.dart';
import 'package:pointycastle/export.dart';

import '../models/profile.dart';

const kLantuAppKey = 'GiITvn';
const kLantuSchoolId = 187;
const kLantuBase = 'https://app.swun.edu.cn/baseCampus/';
const kLantuJw = 'https://app.swun.edu.cn/jwCampus/';
const kLantuInfo = 'https://app.swun.edu.cn/infoCampus/';
const kLantuOa = 'https://app.swun.edu.cn/oaCampus/';
const kLantuUa =
    'Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/107.0.0.0 Mobile Safari/537.36 lantuMobilecampus lantuMC';

class LantuException implements Exception {
  LantuException(this.message);
  final String message;
  @override
  String toString() => message;
}

class LantuClient {
  LantuClient({this.persistPath})
      : dio = Dio(
          BaseOptions(
            connectTimeout: const Duration(seconds: 12),
            receiveTimeout: const Duration(seconds: 12),
            headers: {
              'Content-Type': 'application/json;charset=UTF-8',
              'Accept-Language': 'zh-CN',
              'User-Agent': kLantuUa,
            },
          ),
        );

  final String? persistPath;
  final Dio dio;

  String uuid = _genUuid();
  String token = '';
  String? publicKey;
  int schoolId = kLantuSchoolId;
  Map<String, dynamic> userBaseInfo = {};
  Map<String, dynamic> userLoginInfo = {};
  int curWeek = 1;

  bool get isLoggedIn => token.isNotEmpty;

  static String _genUuid() {
    String seg() {
      final frac = Random().nextDouble().toString().split('.')[1].padRight(8, '0').substring(0, 8);
      final ms = DateTime.now().millisecondsSinceEpoch.toString();
      final tail = ms.substring(ms.length - 10);
      return BigInt.parse(frac + tail).toRadixString(16).padLeft(8, '0').substring(0, 8);
    }
    return 'web$seg$seg';
  }

  static String _sign(Map<String, dynamic> obj) {
    final keys = obj.keys.toList()..sort();
    final chain = keys.map((k) => '$k=${obj[k]}').join('&');
    return md5.convert(utf8.encode(chain)).toString();
  }

  RSAPublicKey _parseSpki(String b64) {
    final der = base64Decode(b64.replaceAll(RegExp(r'\s'), ''));
    final top = ASN1Parser(der).nextObject() as ASN1Sequence;
    final bit = top.elements![1] as ASN1BitString;
    var bytes = bit.valueBytes!;
    if (bytes.isNotEmpty && bytes.first == 0) bytes = bytes.sublist(1);
    final pk = ASN1Parser(bytes).nextObject() as ASN1Sequence;
    final n = pk.elements![0] as ASN1Integer;
    final e = pk.elements![1] as ASN1Integer;
    return RSAPublicKey(n.integer!, e.integer!);
  }

  String _rsaEncrypt(String plain) {
    final pub = _parseSpki(publicKey!);
    final modulusBytes = (pub.modulus!.bitLength + 7) >> 3;
    final maxLen = modulusBytes - 11;
    final src = utf8.encode(plain);
    final out = <int>[];
    for (var i = 0; i < src.length; i += maxLen) {
      final end = min(i + maxLen, src.length);
      final cipher = PKCS1Encoding(RSAEngine())
        ..init(true, PublicKeyParameter<RSAPublicKey>(pub));
      out.addAll(cipher.process(Uint8List.fromList(src.sublist(i, end))));
    }
    return base64Encode(out);
  }

  Map<String, dynamic> _tron(Map<String, dynamic> data, {required bool secure}) {
    var param = jsonEncode(data);
    final a = <String, dynamic>{
      'appKey': kLantuAppKey,
      'param': param,
      'time': DateTime.now().millisecondsSinceEpoch,
      'secure': secure ? 1 : 0,
    };
    if (secure) a['schoolId'] = schoolId;
    a['sign'] = _sign(a);
    if (secure) a['param'] = _rsaEncrypt(param);
    return a;
  }

  Future<Map<String, dynamic>> _post(String url, Map<String, dynamic> data, {bool secure = false}) async {
    final body = Map<String, dynamic>.from(data)
      ..['campusType'] = 1
      ..['wxCode'] = null
      ..['mcClient'] = null
      ..['openId'] = null;
    final wrapped = _tron(body, secure: secure);
    final r = await dio.post(
      url,
      data: jsonEncode(wrapped),
      options: Options(headers: {'token': token}),
    );
    final raw = r.data;
    final result = raw is Map ? Map<String, dynamic>.from(raw) : jsonDecode(raw.toString()) as Map<String, dynamic>;
    final state = result['msgState'];
    if (state != null && int.tryParse('$state') != 1) {
      throw LantuException(result['msg']?.toString() ?? '请求失败');
    }
    return result;
  }

  Future<void> _ensureRsa() async {
    if (publicKey != null && publicKey!.isNotEmpty) return;
    final r = await _post('${kLantuBase}login/getRsa.do', {'schoolId': schoolId});
    final pk = r['publicKey']?.toString();
    if (pk == null || pk.isEmpty) throw LantuException('未拿到登录公钥');
    publicKey = pk;
  }

  Future<void> login(String username, String password) async {
    await _ensureRsa();
    final r = await _post(
      '${kLantuBase}login/login.do',
      {
        'userName': username,
        'password': password,
        'uuId': uuid,
        'schoolId': schoolId,
      },
      secure: true,
    );
    _applyLogin(r);
    await _save();
  }

  void _applyLogin(Map<String, dynamic> r) {
    final tok = r['token'];
    if (tok is List) {
      token = tok.map((e) => '$e').join('_');
    } else {
      token = '${tok ?? ''}';
    }
    userBaseInfo = r['userBaseInfo'] is Map ? Map<String, dynamic>.from(r['userBaseInfo'] as Map) : {};
    userLoginInfo = r['userLoginInfo'] is Map ? Map<String, dynamic>.from(r['userLoginInfo'] as Map) : {};
    final sid = userLoginInfo['schoolId'];
    if (sid != null) schoolId = int.tryParse('$sid') ?? schoolId;
  }

  Future<Map<String, dynamic>> getUserInfo() => _post('${kLantuBase}user/getUserInfo.do', {});

  Future<Map<String, dynamic>> getCourse({int? curTime}) => _post(
        '${kLantuJw}course/getCourse.do',
        {'curTime': curTime ?? DateTime.now().millisecondsSinceEpoch},
      );

  Future<Map<String, dynamic>> getCardBrief() =>
      _post('${kLantuInfo}playCampus/getCardBreifInfo.do', {});

  Future<Map<String, dynamic>> getCourseTimeConfig() =>
      _post('${kLantuJw}course/getCourseTimeConfig.do', {});

  Future<Map<String, dynamic>> getPlaceList({int limit = 50, int offset = 0, Map<String, dynamic>? placeInfo}) {
    return _post('${kLantuOa}place/getPlaceList.do', {
      'limit': limit,
      'offset': offset,
      'schoolId': schoolId,
      'placeInfo': {
        'schoolId': schoolId,
        ...?placeInfo,
      },
    });
  }

  Future<Map<String, dynamic>> getPlaceInfo(Object id) =>
      _post('${kLantuOa}place/getPlaceInfo.do', {'placeInfo': {'id': id}});

  Future<Map<String, dynamic>> getMyPlaceList({int limit = 50, int offset = 0}) {
    final uid = userLoginInfo['userId'];
    final uname = userLoginInfo['userName'];
    return _post('${kLantuOa}place/getMyPlaceList.do', {
      'limit': limit,
      'offset': offset,
      'userId': uid,
      'placeInfo': {
        'userId': ?uid,
        'userName': ?uname,
      },
    });
  }

  Future<Map<String, dynamic>> addPlaceBooking(Map<String, dynamic> placeInfo) {
    final uid = userLoginInfo['userId'];
    final uname = userLoginInfo['userName'];
    final body = <String, dynamic>{
      'userId': ?uid,
      'userName': ?uname,
      ...placeInfo,
    };
    return _post('${kLantuOa}place/addPlaceBooking.do', {'placeInfo': body});
  }

  Future<Map<String, dynamic>> delPlaceInfo(Object id) =>
      _post('${kLantuOa}place/delPlaceInfo.do', {'placeInfo': {'id': id}});

  StudentProfile profile() {
    final b = userBaseInfo;
    final l = userLoginInfo;
    final sex = b['sex'] ?? l['sex'];
    return StudentProfile(
      studentId: '${l['userName'] ?? ''}',
      name: '${b['realName'] ?? ''}',
      gender: sex == 1 || sex == '1' ? '男' : (sex == 2 || sex == '2' ? '女' : ''),
      college: '${b['collegeName'] ?? ''}',
      major: '${b['majorId'] ?? ''}',
      klass: '${b['classId'] ?? ''}',
      grade: '${b['sznj'] ?? ''}',
      phone: '${b['tel'] ?? ''}',
      role: '学生',
      avatar: '${b['headImage'] ?? ''}',
    );
  }

  List<Map<String, dynamic>> mapKbList(Map<String, dynamic> course) {
    curWeek = int.tryParse('${course['curWeek'] ?? 1}') ?? 1;
    final list = course['courseList'];
    if (list is! List) return [];
    final out = <Map<String, dynamic>>[];
    for (final row in list) {
      if (row is! Map) continue;
      final m = Map<String, dynamic>.from(row);
      final start = int.tryParse('${m['skjc'] ?? 1}') ?? 1;
      final dur = int.tryParse('${m['cxjc'] ?? 1}') ?? 1;
      final xqj = int.tryParse('${m['skxq'] ?? ''}') ?? 0;
      if (xqj < 1 || xqj > 7) continue;
      out.add({
        'xqj': xqj,
        'skxq': xqj,
        'jcs': dur <= 1 ? '$start' : '$start-${start + dur - 1}',
        'kcmc': '${m['kcmc'] ?? ''}',
        'cdmc': '${m['jash'] ?? ''}',
        'xm': '',
        'skzc': '${m['skzc'] ?? ''}',
        'skjc': start,
        'cxjc': dur,
        'jsjc': start + (dur > 0 ? dur : 1) - 1,
      });
    }
    return out;
  }

  Future<void> restore() async {
    final path = persistPath;
    if (path == null) return;
    final f = File(path);
    if (!await f.exists()) return;
    try {
      final m = jsonDecode(await f.readAsString());
      if (m is! Map) return;
      token = '${m['token'] ?? ''}';
      uuid = '${m['uuid'] ?? uuid}';
      schoolId = int.tryParse('${m['schoolId'] ?? schoolId}') ?? schoolId;
      if (m['userBaseInfo'] is Map) userBaseInfo = Map<String, dynamic>.from(m['userBaseInfo'] as Map);
      if (m['userLoginInfo'] is Map) userLoginInfo = Map<String, dynamic>.from(m['userLoginInfo'] as Map);
      if (token.isEmpty) return;
      final info = await getUserInfo();
      if (info['userBaseInfo'] is Map) {
        userBaseInfo = Map<String, dynamic>.from(info['userBaseInfo'] as Map);
      }
      if (info['userLoginInfo'] is Map) {
        userLoginInfo = Map<String, dynamic>.from(info['userLoginInfo'] as Map);
      }
    } catch (_) {
      token = '';
    }
  }

  Future<void> _save() async {
    final path = persistPath;
    if (path == null) return;
    final safeBase = Map<String, dynamic>.from(userBaseInfo)
      ..remove('pid')
      ..remove('rfid')
      ..remove('inviteCode');
    final safeLogin = Map<String, dynamic>.from(userLoginInfo)
      ..remove('pid')
      ..remove('securityPassword')
      ..remove('password');
    await File(path).writeAsString(jsonEncode({
      'token': token,
      'uuid': uuid,
      'schoolId': schoolId,
      'userBaseInfo': safeBase,
      'userLoginInfo': safeLogin,
    }));
  }

  Future<void> clear() async {
    token = '';
    userBaseInfo = {};
    userLoginInfo = {};
    final path = persistPath;
    if (path != null) {
      final f = File(path);
      if (await f.exists()) await f.delete();
    }
  }
}
