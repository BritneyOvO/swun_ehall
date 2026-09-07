import 'dart:async';
import 'dart:convert';

import 'package:cookie_jar/cookie_jar.dart';
import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';

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
  bool nightClosed = false;
  Future<void>? _loggingIn;

  void _auth() {
    if (token != null && token!.isNotEmpty) {
      dio.options.headers['Authorization'] = token;
    }
  }

  Map<String, String> _headers({bool json = false}) {
    return {
      'Accept': 'application/json, text/plain, */*',
      'Referer': '$kKtkq/jwmobile/index',
      'X-Requested-With': 'XMLHttpRequest',
      if (json) 'Content-Type': 'application/json',
      if (token != null && token!.isNotEmpty) 'Authorization': token!,
    };
  }

  Future<void> loginWithCas(CasClient cas) async {
    _cas = cas;
    while (_loggingIn != null) {
      try {
        await _loggingIn!;
      } catch (_) {}
      if (token != null && token!.isNotEmpty) return;
    }
    final done = Completer<void>();
    _loggingIn = done.future;
    try {
      Object? lastErr;
      for (var attempt = 0; attempt < 2; attempt++) {
        try {
          await _loginOnce(cas);
          return;
        } catch (e) {
          lastErr = e;
          final msg = e.toString();
          if (looksNightClosed(data: msg) ||
              msg.contains('夜间关闭') ||
              msg.contains('被踢回 CAS')) {
            rethrow;
          }
          token = null;
          debugPrint('[ktkq] login attempt ${attempt + 1} $e');
        }
      }
      throw lastErr ?? Exception('未拿到课堂考勤 token');
    } catch (e, st) {
      if (!done.isCompleted) done.completeError(e, st);
      rethrow;
    } finally {
      if (!done.isCompleted) done.complete();
      if (identical(_loggingIn, done.future)) _loggingIn = null;
    }
  }

  Future<void> _loginOnce(CasClient cas) async {
    var url = await cas.ticketFor(kKtkqService);
    var lastUrl = url;
    var webTried = false;
    for (var i = 0; i < 12; i++) {
      final r = await dio.get(url);
      lastUrl = r.realUri.toString().isNotEmpty ? r.realUri.toString() : url;
      if (looksNightClosed(status: r.statusCode, data: r.data)) {
        nightClosed = true;
        throw Exception(nightClosedMessage('课堂考勤'));
      }
      token = _extractToken(r);
      if (token != null) {
        debugPrint('[ktkq] token from dio hop len=${token!.length}');
        break;
      }
      if (looksLikeRuishu(status: r.statusCode, body: r.data?.toString())) {
        token = await _tokenViaWebView(url);
        webTried = true;
        break;
      }
      if (!isRedirect(r) || loc(r).isEmpty) break;
      url = absUrl(kKtkq, loc(r));
      lastUrl = url;
      if (url.contains('authserver') && !url.contains('ticket=')) {
        throw Exception('课堂考勤登录失败: 被踢回 CAS');
      }
    }
    token ??= await _tokenFromJar();
    if ((token == null || token!.isEmpty) && !webTried) {
      final nav = lastUrl.contains('ticket=')
          ? lastUrl
          : await cas.ticketFor(kKtkqService);
      token = await _tokenViaWebView(nav);
    }
    if (token == null || token!.isEmpty) {
      throw Exception('未拿到课堂考勤 token');
    }
    _auth();
    await jar.saveFromResponse(Uri.parse('$kKtkq/jwmobile/'), [
      Cookie('Authorization', token!)
        ..domain = 'ktkq.swun.edu.cn'
        ..path = '/jwmobile'
        ..httpOnly = true
        ..secure = true,
    ]);
  }

  Future<String?> _tokenViaWebView(String url) async {
    rs.remember(Uri.parse(kKtkq).host);
    final hit = await rs.navigate(url, waitForToken: true);
    var t = parseKtkqToken(urls: [hit.url], body: hit.body);
    t ??= await rs.readEmToken(wait: const Duration(milliseconds: 800));
    t ??= await _tokenFromJar();
    if (t != null) debugPrint('[ktkq] token from webview len=${t.length}');
    return t;
  }

  Future<String?> _tokenFromJar() async {
    for (final path in [
      '/jwmobile/auth/index',
      '/jwmobile/',
      '/jwmobile/index',
      '/',
    ]) {
      for (final c in await jar.loadForRequest(Uri.parse('$kKtkq$path'))) {
        if (c.name.toLowerCase() != 'authorization') continue;
        final t = cleanKtkqToken(c.value);
        if (t != null) return t;
      }
    }
    return null;
  }

  String? _extractToken(Response r) {
    return parseKtkqToken(
      setCookies: r.headers['set-cookie'] ?? const [],
      urls: [r.realUri.toString(), loc(r)],
      body: r.data,
    );
  }

  Future<Map<String, dynamic>> _api(
    String method,
    String path, {
    Map<String, dynamic>? params,
    Object? data,
    bool retry401 = true,
  }) async {
    _auth();
    final hit = await rs
        .request(
          method: method,
          url: '$kKtkq$path',
          query: params,
          data: data,
          headers: _headers(json: true),
        )
        .timeout(
          const Duration(seconds: 25),
          onTimeout: () {
            throw Exception('课堂考勤请求超时');
          },
        );
    if (looksNightClosed(status: hit.status, data: hit.body)) {
      nightClosed = true;
      throw Exception(nightClosedMessage('课堂考勤'));
    }
    Map<String, dynamic> map;
    try {
      map = hit.asJson();
    } catch (_) {
      if (looksLikeRuishu(status: hit.status, body: hit.body)) {
        throw Exception('课堂考勤仍被网关拦截');
      }
      throw Exception('$path 非 JSON');
    }
    nightClosed = false;
    if (map['code'] == 401 && retry401 && _cas != null) {
      await loginWithCas(_cas!);
      return _api(method, path, params: params, data: data, retry401: false);
    }
    return map;
  }

  Future<Map<String, dynamic>> _get(
    String path, [
    Map<String, dynamic>? params,
  ]) => _api('GET', path, params: params);

  Future<Map<String, dynamic>> _post(String path, Map<String, dynamic> body) =>
      _api('POST', path, data: body);

  Future<void> dispose() => rs.dispose();

  Future<Map<String, dynamic>> userInfo() => _get('/jwmobile/biz/user/info');

  Future<void> restoreToken() async {
    final t = await _tokenFromJar();
    if (t == null) return;
    token = t;
    _auth();
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
    var avatar = _str(merged, 'avatar');
    if (avatar.isNotEmpty && !avatar.startsWith('http')) {
      avatar = absUrl(kKtkq, avatar);
    }
    return StudentProfile(
      studentId: _str(merged, 'xh'),
      name: _str(merged, 'xm'),
      college: _str(merged, 'yxmc'),
      major: _str(merged, 'zymc'),
      klass: _str(merged, 'className'),
      grade: _str(merged, 'xznj'),
      phone: _str(merged, 'phonenumber'),
      role: '学生',
      avatar: avatar,
    );
  }

  Future<Map<String, dynamic>> termList() =>
      _get('/jwmobile/biz/v410/schedule/termList');

  Future<Map<String, dynamic>> schoolTime({String? xnxqdm}) => _get(
    '/jwmobile/biz/v410/schedule/school/time',
    xnxqdm == null ? null : {'xnxqdm': xnxqdm},
  );

  Future<Map<String, dynamic>> weekCourses({
    int? week,
    bool refresh = false,
  }) async {
    var data = <String, dynamic>{};
    var xnxqdm = '';
    try {
      final st = await schoolTime();
      debugPrint(
        '[ktkq] schoolTime code=${st['code']} keys=${st.keys.toList()} data=${_dataOf(st).keys.toList()}',
      );
      data = _dataOf(st);
      xnxqdm = pickKtkqXnxqdm(data, const []);
    } catch (e) {
      debugPrint('[ktkq] schoolTime $e');
    }
    if (xnxqdm.isEmpty) {
      try {
        final terms = await termList();
        final rows = _termRows(terms['data']);
        xnxqdm = pickKtkqXnxqdm(data, rows);
        if (xnxqdm.isNotEmpty) {
          try {
            final st = await schoolTime(xnxqdm: xnxqdm);
            data = _dataOf(st);
            final again = pickKtkqXnxqdm(data, const []);
            if (again.isNotEmpty) xnxqdm = again;
          } catch (e) {
            debugPrint('[ktkq] schoolTime($xnxqdm) $e');
          }
        }
      } catch (e) {
        debugPrint('[ktkq] termList $e');
      }
    }
    if (xnxqdm.isEmpty) {
      xnxqdm = ktkqXnxqdmNow();
      debugPrint(
        '[ktkq] calendar xnxqdm=$xnxqdm schoolTime=${data.keys.toList()}',
      );
    }
    if (data.isEmpty && xnxqdm.isNotEmpty) {
      try {
        data = _dataOf(await schoolTime(xnxqdm: xnxqdm));
      } catch (e) {
        debugPrint('[ktkq] schoolTime($xnxqdm) $e');
      }
    }
    final skzc =
        week ?? _asInt(data['todayWeekNum'], _asInt(data['skzc'], 1));
    final key = '$xnxqdm|$skzc';
    if (!refresh && _weekCache != null && _weekCacheKey == key) {
      return _weekCache!;
    }
    Map<String, dynamic> out;
    Future<Map<String, dynamic>> pull() => _post(
      '/jwmobile/biz/v410/schedule/querySchedule',
      {'xnxqdm': xnxqdm, 'skzc': skzc},
    );
    try {
      out = _normalizeWeek(await pull());
    } catch (e) {
      debugPrint('[ktkq] querySchedule $e');
      await Future<void>.delayed(const Duration(milliseconds: 400));
      try {
        out = _normalizeWeek(await pull());
      } catch (e2) {
        debugPrint('[ktkq] querySchedule retry $e2');
        rethrow;
      }
    }
    final n = _asMapList(out['data']).length;
    final sample = n == 0 ? <String, dynamic>{} : _flattenWeek(out).first;
    debugPrint(
      '[ktkq] week $xnxqdm skzc=$skzc courses=$n jxb=${_str(sample, 'jxbid')} kb=${_str(sample, 'kbid').length}',
    );
    out['_meta'] = {'xnxqdm': xnxqdm, 'skzc': skzc, 'schoolTime': data};
    if (_asMapList(out['data']).isNotEmpty) {
      _weekCacheKey = key;
      _weekCache = out;
    }
    return out;
  }

  Map<String, dynamic> _normalizeWeek(Map<String, dynamic> raw) {
    final data = raw['data'];
    if (data is List) return raw;
    final slots = <Map<String, dynamic>>[];
    if (data is Map) {
      for (final key in const [
        'theorySchedule',
        'practiceSchedule',
        'experimentSchedule',
        'changeSchedule',
        'list',
      ]) {
        slots.addAll(_asMapList(data[key]));
      }
    }
    final groups = <String, Map<String, dynamic>>{};
    for (final s in slots) {
      final name = _str(s, 'kcm', '课程');
      final code = _str(s, 'kch');
      final id = _str(s, 'jxbid');
      final gkey = id.isNotEmpty ? id : '$name|$code';
      final g = groups.putIfAbsent(gkey, () {
        return <String, dynamic>{
          'kcm': name,
          'kch': code,
          'jxbid': id,
          'jxblx': _str(s, 'jxblx'),
          'list': <Map<String, dynamic>>[],
        };
      });
      final day = _asInt(s['skxq']);
      if (_str(s, 'sksj').isEmpty && day >= 1 && day <= 7) {
        s['sksj'] = kWeekdayLabels[day];
      }
      s.putIfAbsent('kcm', () => name);
      (g['list'] as List).add(s);
    }
    return {...raw, 'code': raw['code'] ?? 200, 'data': groups.values.toList()};
  }

  Future<Map<String, dynamic>> queryCurrentLesson({
    required String teachClassId,
    required String teachClassType,
    required String scheduleId,
    required int week,
    required int weekDay,
    required int startNode,
    required int endNode,
  }) => _post('/jwmobile/biz/v410/lesson/queryCurrentLesson', {
    'teachClassId': teachClassId,
    'teachClassType': teachClassType,
    'scheduleId': scheduleId,
    'week': week,
    'weekDay': weekDay,
    'startNode': startNode,
    'endNode': endNode,
  });

  Future<Map<String, dynamic>> queryScheduleDetail({
    required String jxbid,
    required String kbid,
    required String jxblx,
  }) => _post('/jwmobile/biz/v410/schedule/queryScheduleDetail', {
    'jxbid': jxbid,
    'kbid': kbid,
    'jxblx': jxblx,
    'wid': '',
  });

  Future<Map<String, dynamic>> querySigninDetail(String activityId) => _post(
    '/jwmobile/biz/v410/signin/querySigninDetail',
    {'activityId': activityId},
  );

  Future<Map<String, dynamic>> signinDetail(String activityId) =>
      _post('/jwmobile/biz/v410/signin/detail', {'activityId': activityId});

  Future<Map<String, dynamic>> submitSign({
    required String activityId,
    String code = '',
    Object accuracy = 0,
    Object latitude = 0,
    Object longitude = 0,
  }) => _post('/jwmobile/biz/v410/signin/sign', {
    'activityId': activityId,
    'accuracy': accuracy,
    'latitude': latitude,
    'longitude': longitude,
    'code': code,
  });

  Future<Map<String, dynamic>> studentHistory(String teachClassId) => _post(
    '/jwmobile/biz/v410/signin/queryStudentHistory',
    {'teachClassId': teachClassId},
  );

  /// 用课表上的一节课，对上金智课堂考勤后再查签到活动。
  Future<Map<String, dynamic>> signForLesson(
    Lesson lesson, {
    int? week,
    Map<String, dynamic>? slot,
    bool refresh = false,
  }) async {
    var weekData = <String, dynamic>{};
    try {
      weekData = await weekCourses(week: week, refresh: refresh);
    } catch (e) {
      debugPrint('[ktkq] week for sign $e');
    }
    if (_flattenWeek(weekData).isEmpty) {
      try {
        weekData = await weekCourses(week: week, refresh: true);
      } catch (e) {
        debugPrint('[ktkq] week retry $e');
      }
    }
    final meta = _asMap(weekData['_meta']);
    final school = _asMap(meta['schoolTime']);
    final weekNum = _asInt(week ?? meta['skzc'] ?? school['todayWeekNum'], 1);
    final slots = _flattenWeek(weekData);
    var hit = <String, dynamic>{...lesson.raw, ...?slot};
    if (_str(hit, 'kcm').isEmpty) {
      hit['kcm'] = lesson.name;
    }
    if (_idsOf(hit).incomplete) {
      final matched = _matchSlot(lesson, slots);
      if (matched != null) hit = {...hit, ...matched};
    }
    if (_idsOf(hit).incomplete) {
      final filled = _fillIds(hit, slots);
      if (filled != null) hit = filled;
    }
    hit['ksjc'] ??= lesson.start;
    hit['jsjc'] ??= lesson.end;
    hit['skxq'] ??= lesson.weekday;
    debugPrint(
      '[ktkq] sign ${lesson.name} jxb=${_idsOf(hit).jxbid} kb=${_idsOf(hit).kbid.length} lx=${_idsOf(hit).jxblx}',
    );
    if (_idsOf(hit).incomplete) {
      return {
        'courseName': lesson.name,
        'classroom': lesson.room,
        'teacher': lesson.teacher,
        'timeText': '${kWeekdayLabels[lesson.weekday]}  ${lesson.periodLabel}',
        'week': weekNum,
        'weekDay': lesson.weekday,
        'startNode': lesson.start,
        'endNode': lesson.end,
        'status': 'not_in_ktkq',
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

  Future<Map<String, dynamic>> _probeSlot(
    Map<String, dynamic> item,
    int week,
    int weekDay,
  ) async {
    final ids = _idsOf(item);
    final teachClassId = ids.jxbid;
    final teachClassType = ids.jxblx;
    final scheduleId = ids.kbid;
    final startNode = _asInt(item['ksjc']);
    final endNode = _asInt(item['jsjc']);
    final day = _asInt(item['skxq'], weekDay);
    final entry = <String, dynamic>{
      'courseName': _str(item, 'kcm', '未知课程'),
      'courseCode': _str(item, 'kch'),
      'teacher': _str(item, 'skjs'),
      'classroom': _str(item, 'jasmc'),
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
    if (teachClassId.isEmpty || teachClassType.isEmpty || scheduleId.isEmpty) {
      return entry;
    }
    try {
      try {
        final detail = await queryScheduleDetail(
          jxbid: teachClassId,
          kbid: scheduleId,
          jxblx: teachClassType,
        );
        final teachers = _asMapList(_asMap(detail['data'])['teacherInfo']);
        if (teachers.isNotEmpty) entry['teacher'] = _str(teachers.first, 'xm');
      } catch (_) {}
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

  Future<Map<String, dynamic>> _probeActivity(
    Map<String, dynamic> activity,
  ) async {
    final id = _str(activity, 'activityId');
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
    var type = _str(detail, 'signinType');
    if (type.isEmpty) type = _str(activity, 'signType');
    var title = _str(activity, 'title');
    if (title.isEmpty) title = '课堂签到';
    return {
      'activityId': id,
      'title': title,
      'signType': type.toUpperCase(),
      'status': status,
      'message': ktkqStatusLabel(status),
      'startTime': _str(detail, 'startTime'),
      'endTime': _str(detail, 'endTime'),
      'signCode': _str(detail, 'code'),
      'leftSeconds': detail['leftSeconds'],
    };
  }

  List<Map<String, dynamic>> _flattenWeek(Map<String, dynamic> week) {
    final slots = <Map<String, dynamic>>[];
    for (final course in _asMapList(week['data'])) {
      final name = _str(course, 'kcm');
      final nested = _asMapList(course['list']);
      final rows = nested.isEmpty ? [course] : nested;
      for (final item in rows) {
        if (nested.isEmpty &&
            _str(item, 'kbid').isEmpty &&
            _str(item, 'jxbid').isEmpty &&
            item['ksjc'] == null) {
          continue;
        }
        item.putIfAbsent('kcm', () => name);
        item.putIfAbsent('kch', () => course['kch']);
        item.putIfAbsent('jxbid', () => course['jxbid']);
        item.putIfAbsent('jxblx', () => course['jxblx']);
        item.putIfAbsent('kbid', () => course['kbid']);
        slots.add(item);
      }
    }
    return slots;
  }

  Map<String, dynamic>? _fillIds(
    Map<String, dynamic> hit,
    List<Map<String, dynamic>> slots,
  ) {
    final jxb = _idsOf(hit).jxbid;
    if (jxb.isNotEmpty) {
      for (final s in slots) {
        if (_idsOf(s).jxbid == jxb && !_idsOf(s).incomplete) {
          return {...hit, ...s};
        }
      }
    }
    return _matchSlot(
      Lesson(
        weekday: _asInt(hit['skxq'], 1),
        start: _asInt(hit['ksjc'], 1),
        end: _asInt(hit['jsjc'], _asInt(hit['ksjc'], 1)),
        name: _str(hit, 'kcm'),
        room: _str(hit, 'jasmc'),
      ),
      slots,
    );
  }

  Map<String, dynamic>? _matchSlot(
    Lesson lesson,
    List<Map<String, dynamic>> slots,
  ) {
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
    final name = _normName(_str(item, 'kcm'));
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
    final room = _normName(_str(item, 'jasmc'));
    if (room.isNotEmpty && room == _normName(lesson.room)) s += 10;
    return s;
  }
}

Lesson ktkqSlotToLesson(
  Map<String, dynamic> course,
  Map<String, dynamic> item,
) {
  final merged = <String, dynamic>{...course, ...item};
  merged.remove('list');
  final start = _asInt(merged['ksjc'], 1);
  final end = _asInt(merged['jsjc'], start);
  return Lesson(
    weekday: parseWeekday(merged['skxq']) ?? DateTime.now().weekday,
    start: start,
    end: end < start ? start : end,
    name: _str(merged, 'kcm', '课程'),
    room: _str(merged, 'jasmc'),
    teacher: _str(merged, 'skjs'),
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
  'not_in_ktkq': '课堂考勤没有这节课',
  'expired': '已结束',
  'not_started': '未开始',
  'not_in_scope': '不在签到范围',
};

String ktkqStatusLabel(String s) => kKtkqStatusLabel[s] ?? s;

/// 从 Set-Cookie / URL / JSON / HTML 里抽出课堂考勤 JWT，字段与 ktkq_client._extract_token 一致。
String? parseKtkqToken({
  Iterable<String> setCookies = const [],
  Iterable<String> urls = const [],
  Object? body,
}) {
  for (final c in setCookies) {
    final m = RegExp(
      r'(?:^|[,;\s])Authorization=([^;]+)',
      caseSensitive: false,
    ).firstMatch(c);
    final t = cleanKtkqToken(m?.group(1));
    if (t != null) return t;
  }
  for (final u in urls) {
    final t = tokenFromKtkqUrl(u);
    if (t != null) return t;
  }
  return tokenFromKtkqBody(body);
}

String? tokenFromKtkqUrl(String u) {
  if (u.isEmpty || !u.contains('token=')) return null;
  final uri = Uri.tryParse(u);
  final q = uri?.queryParameters['token'];
  final fromQuery = cleanKtkqToken(q);
  if (fromQuery != null) return fromQuery;
  final frag = uri?.fragment ?? '';
  final fromFrag = cleanKtkqToken(
    RegExp(r'(?:^|[?&#])token=([^&\s#]+)').firstMatch(frag)?.group(1),
  );
  if (fromFrag != null) return fromFrag;
  return cleanKtkqToken(
    RegExp(r'[?&#]token=([^&\s#]+)').firstMatch(u)?.group(1),
  );
}

String? tokenFromKtkqBody(Object? data) {
  if (data == null) return null;
  if (data is Map) {
    final map = Map<Object?, Object?>.from(data);
    final direct = cleanKtkqToken(map['token']?.toString());
    if (direct != null) return direct;
    final inner = map['data'];
    if (inner is Map) {
      final nested = cleanKtkqToken(inner['token']?.toString());
      if (nested != null) return nested;
    }
  }
  final s = data.toString();
  if (s.isEmpty) return null;
  try {
    final decoded = jsonDecode(s);
    if (decoded is Map) return tokenFromKtkqBody(decoded);
  } catch (_) {}
  return cleanKtkqToken(
    RegExp(r'''["']token["']\s*[:=]\s*["']([^"']+)["']''')
        .firstMatch(s)
        ?.group(1),
  );
}

String? cleanKtkqToken(String? raw) {
  var s = (raw ?? '').trim();
  if (s.length >= 2 &&
      ((s.startsWith('"') && s.endsWith('"')) ||
          (s.startsWith("'") && s.endsWith("'")))) {
    s = s.substring(1, s.length - 1).trim();
  }
  try {
    s = Uri.decodeComponent(s);
  } catch (_) {}
  s = s.trim();
  if (s.toLowerCase().startsWith('bearer ')) s = s.substring(7).trim();
  if (s.isEmpty || s == 'null' || s == 'undefined' || s.length < 16) {
    return null;
  }
  return s;
}

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
  if (v is String) {
    final s = v.trim();
    if (s.startsWith('{')) {
      try {
        final d = jsonDecode(s);
        if (d is Map) return Map<String, dynamic>.from(d);
      } catch (_) {}
    }
  }
  return {};
}

Map<String, dynamic> _dataOf(Map<String, dynamic> resp) {
  final raw = resp['data'];
  if (raw is String) {
    final s = raw.trim();
    if (s.isNotEmpty && s != 'null' && !s.startsWith('{') && !s.startsWith('[')) {
      return {'xnxqdm': s};
    }
  }
  return _asMap(raw);
}

List<Map<String, dynamic>> _termRows(Object? data) {
  final direct = _asMapList(data);
  if (direct.isNotEmpty) return direct;
  if (data is Map) {
    final m = Map<String, dynamic>.from(data);
    for (final k in const ['list', 'records', 'termList', 'rows']) {
      final rows = _asMapList(m[k]);
      if (rows.isNotEmpty) return rows;
    }
  }
  return const [];
}

String _termCodeOf(Map<String, dynamic> m) {
  for (final k in const ['xnxqdm', 'termCode', 'dm', 'id']) {
    final s = _str(m, k);
    if (s.isNotEmpty) return s;
  }
  return '';
}

/// 8 月–次年 1 月为第 1 学期，其余为第 2 学期。GET school/time 空时兜底。
String ktkqXnxqdmNow([DateTime? now]) {
  final n = now ?? DateTime.now();
  if (n.month >= 8) return '${n.year}-${n.year + 1}-1';
  if (n.month == 1) return '${n.year - 1}-${n.year}-1';
  return '${n.year - 1}-${n.year}-2';
}

/// 学年学期代码：school/time 优先，否则 termList 当前项（currentFlag / sfdq），再否则第一项。
String pickKtkqXnxqdm(
  Map<String, dynamic> schoolTimeData,
  List<Map<String, dynamic>> terms,
) {
  final fromSchool = _termCodeOf(schoolTimeData);
  if (fromSchool.isNotEmpty) return fromSchool;
  for (final t in terms) {
    if (_truthyKq(t['currentFlag']) || _truthyKq(t['sfdq'])) {
      final code = _termCodeOf(t);
      if (code.isNotEmpty) return code;
    }
  }
  if (terms.isNotEmpty) return _termCodeOf(terms.first);
  return '';
}

/// 课班三件套，字段与 astrbot_plugin_ktqd._build_course_entry 一致。
class _KtkqIds {
  _KtkqIds(this.jxbid, this.jxblx, this.kbid);
  final String jxbid;
  final String jxblx;
  final String kbid;
  bool get incomplete => jxbid.isEmpty || jxblx.isEmpty || kbid.isEmpty;
}

_KtkqIds _idsOf(Map<String, dynamic> m) =>
    _KtkqIds(_str(m, 'jxbid'), _str(m, 'jxblx'), _str(m, 'kbid'));

List<Map<String, dynamic>> _asMapList(Object? v) {
  if (v is! List) return [];
  return [
    for (final e in v)
      if (e is Map) Map<String, dynamic>.from(e),
  ];
}

String _str(Map<String, dynamic> m, String key, [String fallback = '']) {
  final v = m[key];
  if (v == null) return fallback;
  final s = '$v'.trim();
  if (s.isEmpty || s == 'null') return fallback;
  return s;
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
  if (sp > 0 && !s.contains('T')) {
    s = '${s.substring(0, sp)}T${s.substring(sp + 1)}';
  }
  return DateTime.tryParse(s);
}

bool _alreadySigned(Map<String, dynamic> m) =>
    '${m['signStatus'] ?? ''}' == '1';

String _activityStatus(
  Map<String, dynamic> activity,
  Map<String, dynamic> queryDetail,
  Map<String, dynamic> signDetail,
) {
  if (_alreadySigned(queryDetail) || _alreadySigned(signDetail)) {
    return 'already_signed';
  }
  if (_truthyKq(activity['isEnd'])) return 'expired';
  final start = _parseDt(signDetail['startTime']);
  final end = _parseDt(signDetail['endTime']);
  final now = DateTime.now();
  if (start != null && now.isBefore(start)) return 'not_started';
  if (end != null && now.isAfter(end)) return 'expired';
  final left = signDetail['leftSeconds'];
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
  final sksj = _str(item, 'sksj');
  final ks = item['ksjc'];
  final js = item['jsjc'];
  final node = (ks != null && js != null) ? '第 $ks-$js 节' : '';
  return [sksj, node].where((e) => e.isNotEmpty).join('  ');
}

List<Map<String, dynamic>> _historyRows(
  Map<String, dynamic> raw,
  String courseName,
) {
  final src = raw['data'];
  if (src is! List) return [];
  final out = <Map<String, dynamic>>[];
  for (final e in src) {
    if (e is! Map) continue;
    final m = Map<String, dynamic>.from(e);
    var status = '';
    if ('${m['attendanceStatus'] ?? ''}' == '10') {
      status = '正常';
    } else if ('${m['signStatus'] ?? ''}' == '1') {
      status = '已签到';
    }
    out.add({
      'time': _str(m, 'startTime'),
      'course': courseName,
      'status': status,
      'activityId': _str(m, 'activityId'),
    });
  }
  return out;
}
