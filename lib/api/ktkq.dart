import 'package:cookie_jar/cookie_jar.dart';
import 'package:dio/dio.dart';

import '../models/lesson.dart';
import '../models/profile.dart';
import 'cas.dart';
import 'httpx.dart';
import 'rs_gateway.dart';

const kKtkq = 'https://ktkq.swun.edu.cn';
const kKtkqService = '$kKtkq/jwmobile/auth/index';

class KtkqClient {
  KtkqClient(this.jar, {RsGateway? gateway}) : dio = buildDio(jar) {
    rs = gateway ?? RsGateway(jar: jar);
  }

  final CookieJar jar;
  final Dio dio;
  late final RsGateway rs;
  CasClient? _cas;
  String? token;
  Object? _weekCacheKey;
  Map<String, dynamic>? _weekCache;

  void _auth() {
    if (token != null && token!.isNotEmpty) {
      dio.options.headers['Authorization'] = token;
    }
  }

  Map<String, String> _headers({bool json = false}) {
    return {
      'Accept': 'application/json, text/plain, */*',
      if (json) 'Content-Type': 'application/json',
      if (token != null && token!.isNotEmpty) 'Authorization': token!,
    };
  }

  Future<void> loginWithCas(CasClient cas) async {
    _cas = cas;
    var url = await cas.ticketFor(kKtkqService);
    for (var i = 0; i < 12; i++) {
      final r = await dio.get(url);
      token = _extractToken(r);
      if (token != null) break;
      if (looksLikeRuishu(status: r.statusCode, body: r.data?.toString())) {
        rs.remember(Uri.parse(kKtkq).host);
        await rs.navigate(url);
        token = await rs.readEmToken();
        break;
      }
      if (!isRedirect(r) || loc(r).isEmpty) break;
      url = absUrl(kKtkq, loc(r));
      if (url.contains('authserver') && !url.contains('ticket=')) {
        throw Exception('课堂考勤登录失败: 被踢回 CAS');
      }
    }
    if (token == null || token!.isEmpty) {
      for (final c in await jar.loadForRequest(Uri.parse(kKtkq))) {
        if (c.name == 'Authorization' && c.value.isNotEmpty) {
          token = c.value;
          break;
        }
      }
    }
    if (token == null || token!.isEmpty) {
      throw Exception('未拿到课堂考勤 token');
    }
    _auth();
    await jar.saveFromResponse(Uri.parse(kKtkq), [
      Cookie('Authorization', token!)..domain = 'ktkq.swun.edu.cn',
    ]);
  }

  String? _extractToken(Response r) {
    final cookies = r.headers['set-cookie'] ?? [];
    for (final c in cookies) {
      final m = RegExp(r'Authorization=([^;]+)').firstMatch(c);
      if (m != null) return m.group(1);
    }
    for (final u in [r.realUri.toString(), loc(r)]) {
      final q = Uri.tryParse(u)?.queryParameters['token'];
      if (q != null && q.isNotEmpty) return q;
    }
    return null;
  }

  Future<Map<String, dynamic>> _api(
    String method,
    String path, {
    Map<String, dynamic>? params,
    Object? data,
    bool retry401 = true,
  }) async {
    _auth();
    final hit = await rs.request(
      method: method,
      url: '$kKtkq$path',
      query: params,
      data: data,
      headers: _headers(json: method.toUpperCase() == 'POST'),
    );
    Map<String, dynamic> map;
    try {
      map = hit.asJson();
    } catch (_) {
      if (looksLikeRuishu(status: hit.status, body: hit.body)) {
        throw Exception('课堂考勤仍被网关拦截');
      }
      throw Exception('$path 非 JSON');
    }
    if (map['code'] == 401 && retry401 && _cas != null) {
      await loginWithCas(_cas!);
      return _api(method, path, params: params, data: data, retry401: false);
    }
    return map;
  }

  Future<Map<String, dynamic>> _get(String path, [Map<String, dynamic>? params]) =>
      _api('GET', path, params: params);

  Future<Map<String, dynamic>> _post(String path, Map<String, dynamic> body) =>
      _api('POST', path, data: body);

  Future<void> dispose() => rs.dispose();

  Future<Map<String, dynamic>> userInfo() => _get('/jwmobile/biz/user/info');

  Future<void> restoreToken() async {
    for (final c in await jar.loadForRequest(Uri.parse(kKtkq))) {
      if (c.name == 'Authorization' && c.value.isNotEmpty) {
        token = c.value;
        _auth();
        return;
      }
    }
  }

  Future<StudentProfile> profile() async {
    final raw = await userInfo();
    final merged = Map<String, dynamic>.from(raw);
    if (raw['data'] is Map) {
      merged.addAll(Map<String, dynamic>.from(raw['data'] as Map));
    }
    final code = merged['code'];
    if (code != null && code != 200 && code != 0) {
      throw Exception(merged['msg']?.toString() ?? '课堂考勤未登录');
    }
    String pick(List<String> keys) {
      for (final k in keys) {
        for (final e in merged.entries) {
          if (e.key.toString().toLowerCase() == k.toLowerCase()) {
            final v = '${e.value}'.trim();
            if (v.isNotEmpty && v != 'null') return v;
          }
        }
      }
      return '';
    }

    var avatar = pick(const ['avatar', 'headImage', 'photourl', 'photo']);
    if (avatar.isNotEmpty && !avatar.startsWith('http')) {
      avatar = absUrl(kKtkq, avatar);
    }
    final usertype = pick(const ['usertype', 'userType', 'userTypeName']);
    return StudentProfile(
      studentId: pick(const ['username', 'userName', 'xh', 'loginName', 'userId']),
      name: pick(const ['realName', 'xm', 'nickName', 'name', 'userNameCn']),
      college: pick(const ['xy', 'xymc', 'deptName', 'yxmc']),
      major: pick(const ['zymc', 'zy', 'major']),
      klass: pick(const ['className', 'bjmc', 'bj', 'xzb']),
      grade: pick(const ['xznj', 'njmc', 'nj']),
      phone: pick(const ['phonenumber', 'phone', 'mobile', 'sjhm']),
      role: usertype.contains('teacher') || usertype.contains('教师')
          ? '教师'
          : (usertype.isEmpty ? '学生' : (usertype.contains('student') || usertype.contains('xs') ? '学生' : usertype)),
      avatar: avatar,
    );
  }

  Future<Map<String, dynamic>> termList() => _get('/jwmobile/biz/v410/schedule/termList');

  Future<Map<String, dynamic>> schoolTime({String? xnxqdm}) =>
      _get('/jwmobile/biz/v410/schedule/school/time', xnxqdm == null ? null : {'xnxqdm': xnxqdm});

  Future<Map<String, dynamic>> weekCourses({int? week, bool refresh = false}) async {
    var st = await schoolTime();
    var data = (st['data'] is Map) ? Map<String, dynamic>.from(st['data'] as Map) : <String, dynamic>{};
    var xnxqdm = data['xnxqdm']?.toString() ?? data['termCode']?.toString();
    if (xnxqdm == null || xnxqdm.isEmpty) {
      final terms = await termList();
      final list = terms['data'];
      if (list is List && list.isNotEmpty) {
        Map<String, dynamic>? cur;
        for (final t in list) {
          if (t is Map && (t['currentFlag'] == true || t['currentFlag'] == 1)) {
            cur = Map<String, dynamic>.from(t);
            break;
          }
        }
        cur ??= Map<String, dynamic>.from(list.first as Map);
        xnxqdm = (cur['termCode'] ?? cur['xnxqdm'])?.toString();
        if (xnxqdm != null) {
          st = await schoolTime(xnxqdm: xnxqdm);
          data = (st['data'] is Map) ? Map<String, dynamic>.from(st['data'] as Map) : data;
          xnxqdm = data['xnxqdm']?.toString() ?? xnxqdm;
        }
      }
    }
    final skzc = week ?? data['todayWeekNum'] ?? 1;
    if (xnxqdm == null) throw Exception('无法确定学年学期');
    final key = '$xnxqdm|$skzc';
    if (!refresh && _weekCache != null && _weekCacheKey == key) return _weekCache!;
    final out = await _post('/jwmobile/biz/v410/schedule/queryCourseInfo', {
      'xnxqdm': xnxqdm,
      'skzc': skzc,
      'page': 'course',
    });
    out['_meta'] = {'xnxqdm': xnxqdm, 'skzc': skzc, 'schoolTime': data};
    _weekCacheKey = key;
    _weekCache = out;
    return out;
  }

  Future<Map<String, dynamic>> queryCurrentLesson({
    required String teachClassId,
    required String teachClassType,
    required String scheduleId,
    required int week,
    required int weekDay,
    required int startNode,
    required int endNode,
  }) =>
      _post('/jwmobile/biz/v410/lesson/queryCurrentLesson', {
        'teachClassId': teachClassId,
        'teachClassType': teachClassType,
        'scheduleId': scheduleId,
        'week': week,
        'weekDay': weekDay,
        'startNode': startNode,
        'endNode': endNode,
      });

  Future<Map<String, dynamic>> checkAllowSign(Map<String, dynamic> params) =>
      _get('/jwmobile/biz/v410/signin/checkAllowSign', params);

  Future<Map<String, dynamic>> querySigninDetail(String activityId) =>
      _post('/jwmobile/biz/v410/signin/querySigninDetail', {'activityId': activityId});

  Future<Map<String, dynamic>> signinDetail(String activityId) =>
      _post('/jwmobile/biz/v410/signin/detail', {'activityId': activityId});

  Future<Map<String, dynamic>> submitSign({
    required String activityId,
    String code = '',
    Object accuracy = 0,
    Object latitude = 0,
    Object longitude = 0,
  }) =>
      _post('/jwmobile/biz/v410/signin/sign', {
        'activityId': activityId,
        'accuracy': accuracy,
        'latitude': latitude,
        'longitude': longitude,
        'code': code,
      });

  Future<Map<String, dynamic>> studentHistory(String teachClassId) =>
      _post('/jwmobile/biz/v410/signin/queryStudentHistory', {'teachClassId': teachClassId});

  /// 用课表上的一节课，对上金智课堂考勤后再查签到活动。
  Future<Map<String, dynamic>> signForLesson(
    Lesson lesson, {
    int? week,
    Map<String, dynamic>? slot,
    bool refresh = false,
  }) async {
    final weekData = await weekCourses(week: week, refresh: refresh);
    final meta = _asMap(weekData['_meta']);
    final school = _asMap(meta['schoolTime']);
    final weekNum = _asInt(week ?? meta['skzc'] ?? school['todayWeekNum'], 1);
    var hit = slot == null ? null : Map<String, dynamic>.from(slot);
    if (hit != null && _pick(hit, const ['kcm', 'courseName', 'kcmc']).isEmpty) {
      hit['kcm'] = lesson.name;
    }
    if (hit == null || _pick(hit, const ['jxbid', 'teachClassId']).isEmpty) {
      hit = _matchSlot(lesson, _flattenWeek(weekData));
    }
    if (hit == null) {
      return {
        'courseName': lesson.name,
        'classroom': lesson.room,
        'teacher': lesson.teacher,
        'timeText': '${kWeekdayLabels[lesson.weekday]}  ${lesson.periodLabel}',
        'week': weekNum,
        'weekDay': lesson.weekday,
        'startNode': lesson.start,
        'endNode': lesson.end,
        'status': 'missing_schedule',
        'message': '课堂考勤里没有对应这节课',
        'activities': <Map<String, dynamic>>[],
        'history': <Map<String, dynamic>>[],
        '_meta': meta,
      };
    }
    final probed = await _probeSlot(hit, weekNum, lesson.weekday);
    probed['_meta'] = meta;
    final id = '${probed['teachClassId'] ?? ''}';
    if (id.isNotEmpty) {
      try {
        probed['history'] = _historyRows(
          await studentHistory(id),
          '${probed['courseName'] ?? lesson.name}',
        );
      } catch (_) {
        probed['history'] = <Map<String, dynamic>>[];
      }
    } else {
      probed['history'] = <Map<String, dynamic>>[];
    }
    return probed;
  }

  Future<Map<String, dynamic>> _probeSlot(Map<String, dynamic> item, int week, int weekDay) async {
    final teachClassId = _pick(item, const ['jxbid', 'teachClassId']);
    final teachClassType = _pick(item, const ['jxblx', 'teachClassType']);
    final scheduleId = _pick(item, const ['kbid', 'scheduleId']);
    final startNode = _asInt(item['ksjc']);
    final endNode = _asInt(item['jsjc']);
    final day = _asInt(item['skxq'], weekDay);
    final entry = <String, dynamic>{
      'courseName': _pick(item, const ['kcm', 'courseName', 'kcmc'], '未知课程'),
      'courseCode': _pick(item, const ['kch', 'courseCode']),
      'teacher': _pick(item, const ['skjs', 'jsxm', 'teacherName']),
      'classroom': _pick(item, const ['jasmc', 'jsmc', 'cdmc', 'classroomName']),
      'timeText': _slotTime(item),
      'teachClassId': teachClassId,
      'teachClassType': teachClassType,
      'scheduleId': scheduleId,
      'week': week,
      'weekDay': day,
      'startNode': startNode,
      'endNode': endNode,
      'activities': <Map<String, dynamic>>[],
      'status': 'missing_schedule',
      'message': ktkqStatusLabel('missing_schedule'),
    };
    if (teachClassId.isEmpty || teachClassType.isEmpty || scheduleId.isEmpty) return entry;
    try {
      final current = await queryCurrentLesson(
        teachClassId: teachClassId,
        teachClassType: teachClassType,
        scheduleId: scheduleId,
        week: week,
        weekDay: day,
        startNode: startNode,
        endNode: endNode,
      );
      final acts = _asMapList(_asMap(current['data'])['activityList']);
      if (acts.isEmpty) {
        entry['status'] = 'no_activity';
        entry['message'] = ktkqStatusLabel('no_activity');
        return entry;
      }
      final detailed = await Future.wait(acts.map(_probeActivity));
      entry['activities'] = detailed;
      entry['status'] = _rollup(detailed);
      entry['message'] = ktkqStatusLabel('${entry['status']}');
      for (final a in detailed) {
        if (a['status'] == 'pending_signin') {
          entry['activityId'] = a['activityId'];
          entry['signType'] = a['signType'];
          entry['signCode'] = a['signCode'];
          break;
        }
      }
      return entry;
    } catch (e) {
      entry['status'] = 'inactive';
      entry['message'] = e.toString().replaceFirst('Exception: ', '');
      return entry;
    }
  }

  Future<Map<String, dynamic>> _probeActivity(Map<String, dynamic> activity) async {
    final id = _pick(activity, const ['activityId', 'id']);
    var query = <String, dynamic>{};
    var detail = <String, dynamic>{};
    if (id.isNotEmpty) {
      try {
        query = _asMap((await querySigninDetail(id))['data']);
      } catch (_) {}
      try {
        detail = _asMap((await signinDetail(id))['data']);
      } catch (_) {}
    }
    final status = _activityStatus(activity, query, detail);
    var type = _pick(detail, const ['signinType', 'signType']);
    if (type.isEmpty) type = _pick(activity, const ['signType', 'signinType'], 'GENERAL');
    return {
      'activityId': id,
      'title': _pick(activity, const ['title', 'name'], _pick(detail, const ['title', 'name'], '课堂签到')),
      'signType': type.toUpperCase(),
      'status': status,
      'message': ktkqStatusLabel(status),
      'startTime': _pick(activity, const ['startTime'], _pick(detail, const ['startTime'])),
      'endTime': _pick(activity, const ['endTime'], _pick(detail, const ['endTime'])),
      'signCode': _pick(detail, const ['code', 'numcode', 'signinCode']),
      'leftSeconds': detail['leftSeconds'] ?? activity['leftSeconds'],
    };
  }

  List<Map<String, dynamic>> _flattenWeek(Map<String, dynamic> week) {
    final slots = <Map<String, dynamic>>[];
    for (final course in _asMapList(week['data'])) {
      final name = _pick(course, const ['kcm', 'courseName', 'kcmc']);
      for (final item in _asMapList(course['list'])) {
        item.putIfAbsent('kcm', () => name);
        item.putIfAbsent('kch', () => course['kch']);
        if (_asInt(item['skxq'], 0) == 0) {
          final d = parseWeekday(item['sksj'] ?? item['rqmc']);
          if (d != null) item['skxq'] = d;
        }
        slots.add(item);
      }
    }
    return slots;
  }

  Map<String, dynamic>? _matchSlot(Lesson lesson, List<Map<String, dynamic>> slots) {
    Map<String, dynamic>? best;
    var bestScore = 0;
    for (final item in slots) {
      final score = _slotScore(lesson, item);
      if (score > bestScore) {
        bestScore = score;
        best = item;
      }
    }
    if (bestScore < 40) return null;
    return best;
  }

  int _slotScore(Lesson lesson, Map<String, dynamic> item) {
    final day = _asInt(item['skxq'], 0);
    if (day != 0 && day != lesson.weekday) return 0;
    var s = day == lesson.weekday ? 20 : 0;
    final name = _normName(_pick(item, const ['kcm', 'courseName', 'kcmc']));
    final lname = _normName(lesson.name);
    if (name.isEmpty || lname.isEmpty) return 0;
    if (name == lname) {
      s += 50;
    } else if (name.contains(lname) || lname.contains(name)) {
      s += 30;
    } else {
      final a = _coreName(name);
      final b = _coreName(lname);
      if (a.length >= 2 && a == b) {
        s += 28;
      } else {
        return 0;
      }
    }
    final ks = _asInt(item['ksjc']);
    final js = _asInt(item['jsjc'], ks);
    if (ks == lesson.start && js == lesson.end) {
      s += 20;
    } else if (ks <= lesson.end && js >= lesson.start) {
      s += 10;
    }
    final room = _normName(_pick(item, const ['jasmc', 'jsmc', 'cdmc', 'classroomName']));
    if (room.isNotEmpty && room == _normName(lesson.room)) s += 10;
    return s;
  }
}

Lesson ktkqSlotToLesson(Map<String, dynamic> course, Map<String, dynamic> item) {
  final merged = <String, dynamic>{...course, ...item};
  final name = _pick(merged, const ['kcm', 'courseName', 'kcmc'], '课程');
  final day = parseWeekday(merged['skxq'] ?? merged['sksj'] ?? merged['weekDay']) ?? DateTime.now().weekday;
  final start = _asInt(merged['ksjc'] ?? merged['startNode'], 1);
  final end = _asInt(merged['jsjc'] ?? merged['endNode'], start);
  return Lesson(
    weekday: day,
    start: start,
    end: end < start ? start : end,
    name: name,
    room: _pick(merged, const ['jasmc', 'jsmc', 'cdmc', 'classroomName']),
    teacher: _pick(merged, const ['skjs', 'jsxm', 'teacherName']),
    raw: merged,
  );
}

const kKtkqStatusLabel = {
  'pending_signin': '待签到',
  'already_signed': '已签到',
  'outside_time': '不在签到时间',
  'no_activity': '无签到活动',
  'inactive': '当前无进行中签到',
  'missing_schedule': '课程信息不完整',
  'expired': '已结束',
  'not_started': '未开始',
  'not_in_scope': '不在签到范围',
};

String ktkqStatusLabel(String s) => kKtkqStatusLabel[s] ?? s;

String ktkqSignTypeLabel(String t) {
  switch (t.toUpperCase()) {
    case 'NUMBER':
      return '数字签到';
    case 'LOCATION':
      return '定位签到';
    case 'SCAN':
    case 'QR':
      return '扫码签到';
    case 'GESTURE':
      return '手势签到';
    case 'GENERAL':
      return '课堂签到';
    default:
      return t.isEmpty ? '课堂签到' : t;
  }
}

bool ktkqNeedsCode(String type) {
  final t = type.toUpperCase();
  return t == 'NUMBER' || t == 'SCAN' || t == 'QR';
}

Map<String, dynamic> _asMap(Object? v) {
  if (v is Map<String, dynamic>) return v;
  if (v is Map) return Map<String, dynamic>.from(v);
  return {};
}

List<Map<String, dynamic>> _asMapList(Object? v) {
  if (v is! List) return [];
  return [
    for (final e in v)
      if (e is Map) Map<String, dynamic>.from(e),
  ];
}

String _pick(Map<String, dynamic> m, List<String> keys, [String fallback = '']) {
  for (final k in keys) {
    final v = m[k];
    if (v == null) continue;
    final s = '$v'.trim();
    if (s.isNotEmpty && s != 'null') return s;
  }
  return fallback;
}

int _asInt(Object? v, [int fallback = 0]) {
  if (v is int) return v;
  return int.tryParse('$v') ?? fallback;
}

bool _truthyKq(Object? v) => v == true || v == 1 || v == '1' || v == 'true';

DateTime? _parseDt(Object? v) {
  var s = '${v ?? ''}'.trim();
  if (s.isEmpty) return null;
  s = s.replaceAll('/', '-');
  final sp = s.indexOf(' ');
  if (sp > 0 && !s.contains('T')) s = '${s.substring(0, sp)}T${s.substring(sp + 1)}';
  return DateTime.tryParse(s);
}

bool _alreadySigned(Map<String, dynamic> m) =>
    '${m['signStatus'] ?? ''}' == '1' || '${m['attendanceStatus'] ?? ''}' == '10';

String _activityStatus(
  Map<String, dynamic> activity,
  Map<String, dynamic> queryDetail,
  Map<String, dynamic> signDetail,
) {
  if (_alreadySigned(queryDetail) || _alreadySigned(signDetail)) return 'already_signed';
  if (_truthyKq(activity['isEnd'])) return 'expired';
  final start = _parseDt(signDetail['startTime'] ?? activity['startTime']);
  final end = _parseDt(signDetail['endTime'] ?? activity['endTime']);
  final now = DateTime.now();
  if (start != null && now.isBefore(start)) return 'not_started';
  if (end != null && now.isAfter(end)) return 'expired';
  final left = signDetail['leftSeconds'] ?? activity['leftSeconds'];
  if (left != null && _asInt(left, 1) <= 0) return 'expired';
  final st = '${activity['status'] ?? ''}';
  if (st.isNotEmpty && st != '1') return 'inactive';
  return 'pending_signin';
}

String _rollup(List<Map<String, dynamic>> acts) {
  if (acts.any((a) => a['status'] == 'pending_signin')) return 'pending_signin';
  if (acts.any((a) => a['status'] == 'already_signed')) return 'already_signed';
  if (acts.any((a) => a['status'] == 'expired')) return 'expired';
  if (acts.any((a) => a['status'] == 'outside_time')) return 'outside_time';
  if (acts.any((a) => a['status'] == 'not_started')) return 'not_started';
  if (acts.any((a) => a['status'] == 'no_activity')) return 'no_activity';
  return 'inactive';
}

String _normName(String s) => s
    .toLowerCase()
    .replaceAll(RegExp(r'\s+'), '')
    .replaceAll('（', '(')
    .replaceAll('）', ')')
    .replaceAll('【', '[')
    .replaceAll('】', ']');

String _coreName(String s) => s.replaceAll(RegExp(r'\([^)]*\)'), '');

String _slotTime(Map<String, dynamic> item) {
  final sksj = _pick(item, const ['sksj', 'rqmc']);
  final jc = _pick(item, const ['jc']);
  final ks = item['ksjc'];
  final js = item['jsjc'];
  final node = (ks != null && js != null) ? '第 $ks-$js 节' : '';
  return [sksj, jc, node].where((e) => e.isNotEmpty).join('  ');
}

String _fmtTime(Object? v) {
  if (v is int && v > 1000000000) {
    final ms = v > 100000000000 ? v : v * 1000;
    final d = DateTime.fromMillisecondsSinceEpoch(ms);
    String two(int n) => n.toString().padLeft(2, '0');
    return '${two(d.month)}-${two(d.day)} ${two(d.hour)}:${two(d.minute)}';
  }
  final s = '${v ?? ''}'.trim();
  return s == 'null' ? '' : s;
}

List<Map<String, dynamic>> _historyRows(Map<String, dynamic> raw, String courseName) {
  Object? src = raw['data'] ?? raw['list'] ?? raw['page'];
  List list = const [];
  if (src is List) {
    list = src;
  } else if (src is Map) {
    final inner = src['list'] ?? src['records'] ?? src['rows'] ?? src['data'];
    if (inner is List) list = inner;
  }
  final out = <Map<String, dynamic>>[];
  for (final e in list) {
    if (e is! Map) continue;
    final m = Map<String, dynamic>.from(e);
    final course = _pick(m, const ['kcm', 'courseName', 'kcmc']);
    var status = _pick(m, const ['clockStatus', 'statusName', 'result', 'status']);
    if ('${m['attendanceStatus'] ?? ''}' == '10') status = '正常';
    if ('${m['signStatus'] ?? ''}' == '1' && status.isEmpty) status = '已签到';
    out.add({
      'time': _fmtTime(m['signTime'] ?? m['signinTime'] ?? m['createTime'] ?? m['qdsj'] ?? m['kqsj']),
      'course': course.isEmpty ? courseName : course,
      'status': status,
      'place': _pick(m, const ['jasmc', 'classroom', 'address', 'location', 'cdmc']),
    });
  }
  return out;
}
