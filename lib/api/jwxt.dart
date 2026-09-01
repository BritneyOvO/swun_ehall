import 'dart:convert';

import 'package:cookie_jar/cookie_jar.dart';
import 'package:dio/dio.dart';

import '../models/credit.dart';
import '../models/profile.dart';
import 'cas.dart';
import 'httpx.dart';

const kJwxt = 'https://jwxt.swun.edu.cn';
const kJwxtService = 'http://jwxt.swun.edu.cn/sso/jziotlogin';

(int, String) currentTerm() {
  final now = DateTime.now();
  if (now.month >= 8 || now.month <= 1) return (now.year, '3');
  return (now.year - 1, '12');
}

class JwxtClient {
  JwxtClient(this.jar) : dio = buildDio(jar);

  final CookieJar jar;
  final Dio dio;
  bool portalClosed = false;
  CasClient? _cas;
  Future<void>? _ensuring;

  void attachCas(CasClient cas) => _cas = cas;

  static const _referer = '$kJwxt/jwglxt/xtgl/index_initMenu.html?jsdm=xs';

  bool _looksClosed(int? status, Object? data) => looksNightClosed(status: status, data: data);

  bool _looksLoggedOut(Response r) {
    final t = r.data?.toString() ?? '';
    if (t.contains('login_slogin') || t.contains('统一身份认证')) return true;
    if (!isRedirect(r)) return false;
    final u = absUrl(kJwxt, loc(r)).toLowerCase();
    return u.contains('login_slogin') ||
        u.contains('/sso/login') ||
        u.contains('authserver') ||
        u.contains('/cas/');
  }

  Future<Response> _home() {
    return dio.get(
      '$kJwxt/jwglxt/xtgl/index_initMenu.html',
      queryParameters: {'jsdm': 'xs'},
      options: Options(headers: {'Referer': _referer}),
    );
  }

  Future<void> loginWithCas(CasClient cas) async {
    _cas = cas;
    portalClosed = false;
    var url = await cas.ticketFor(kJwxtService);
    for (var i = 0; i < 15; i++) {
      final r = await dio.get(
        url,
        options: Options(receiveTimeout: const Duration(seconds: 12)),
      );
      if (_looksClosed(r.statusCode, r.data)) {
        portalClosed = true;
        return;
      }
      if (r.statusCode == 200 && !_looksLoggedOut(r)) break;
      var next = loc(r);
      if (next.isEmpty) break;
      url = absUrl(kJwxt, next);
      if (url.contains('authserver') && !url.contains('ticket=')) {
        throw Exception('教务登录失败: 重定向回 CAS');
      }
    }
    final home = await _home();
    final body = home.data.toString();
    if (home.statusCode == 200 && !_looksLoggedOut(home) && !_looksClosed(home.statusCode, body)) {
      portalClosed = false;
      return;
    }
    if (_looksClosed(home.statusCode, body)) {
      portalClosed = true;
      return;
    }
    throw Exception('教务登录失败');
  }

  Future<bool> sessionAlive() async {
    try {
      final r = await _home();
      final body = r.data.toString();
      if (_looksClosed(r.statusCode, body)) {
        portalClosed = true;
        return false;
      }
      if (r.statusCode == 200 && !_looksLoggedOut(r)) {
        portalClosed = false;
        return true;
      }
      return false;
    } catch (_) {
      return false;
    }
  }

  Future<void> ensureSession({bool force = false}) async {
    if (!force && await sessionAlive()) return;
    while (_ensuring != null) {
      await _ensuring!.timeout(const Duration(seconds: 20));
      if (!force && await sessionAlive()) return;
      if (!force) return;
    }
    final done = _relogin();
    _ensuring = done;
    try {
      await done;
    } finally {
      if (identical(_ensuring, done)) _ensuring = null;
    }
  }

  Future<void> _relogin() async {
    final cas = _cas;
    if (cas == null) throw Exception('教务登录已过期，请重新登录');
    if (!await cas.tgtAlive()) throw Exception('统一身份已过期，请重新登录');
    await loginWithCas(cas).timeout(const Duration(seconds: 20));
    if (portalClosed) return;
    if (!await sessionAlive()) throw Exception('教务登录失败，请重新登录');
  }

  Options get _form => Options(
        contentType: Headers.formUrlEncodedContentType,
        headers: {'Referer': _referer, 'X-Requested-With': 'XMLHttpRequest'},
      );

  Future<Map<String, dynamic>> _post(
    String path,
    Map<String, dynamic> data, {
    Map<String, dynamic>? params,
  }) async {
    Object? last;
    var relogged = false;
    for (var i = 0; i < 3; i++) {
      try {
        final r = await dio.post(
          '$kJwxt$path',
          data: data,
          queryParameters: params,
          options: _form,
        );
        if (r.statusCode == 403 || _looksClosed(r.statusCode, r.data)) {
          portalClosed = true;
          throw Exception(nightClosedMessage('教务'));
        }
        if (_looksLoggedOut(r) || isRedirect(r)) {
          if (!relogged) {
            relogged = true;
            await ensureSession(force: true);
            continue;
          }
          throw Exception('教务登录已过期，请重新登录');
        }
        if (r.statusCode != 200) throw Exception('HTTP ${r.statusCode}');
        final d = r.data;
        if (d is Map<String, dynamic>) return d;
        if (d is Map) return Map<String, dynamic>.from(d);
        throw Exception('非 JSON');
      } catch (e) {
        last = e;
        final s = e.toString();
        if (s.contains('夜间') || s.contains('过期') || s.contains('重新登录')) rethrow;
        if (i < 2) await Future<void>.delayed(const Duration(milliseconds: 400));
      }
    }
    throw Exception('请求失败 $path: $last');
  }

  Future<Map<String, dynamic>> schedule({int? xnm, String? xqm}) async {
    final t = currentTerm();
    return _post('/jwglxt/kbcx/xskbcx_cxXsgrkb.html', {
      'xnm': '${xnm ?? t.$1}',
      'xqm': xqm ?? t.$2,
      'kzlx': 'ck',
      'xsdm': '',
      'kclbdm': '',
      'kclxdm': '',
    });
  }

  Future<Map<String, dynamic>> grades({String? xnm, String? xqm}) async {
    final t = currentTerm();
    final yn = xnm ?? '${t.$1}';
    final yq = xqm ?? t.$2;
    final items = <dynamic>[];
    var page = 1;
    var total = 1;
    Map<String, dynamic> last = {};
    while (page <= 20) {
      last = await _post(
        '/jwglxt/cjcx/cjcx_cxXsgrcj.html',
        {
          'xnm': yn,
          'xqm': yq,
          'queryModel.showAll': 'true',
          'queryModel.showCount': '200',
          'queryModel.currentPage': '$page',
        },
        params: {'doType': 'query'},
      );
      final chunk = last['items'];
      if (chunk is List) items.addAll(chunk);
      total = int.tryParse('${last['totalResult'] ?? items.length}') ?? items.length;
      if (items.length >= total || chunk is! List || chunk.isEmpty) break;
      page++;
    }
    last['items'] = items;
    last['totalResult'] = items.length;
    return last;
  }

  Future<Map<String, dynamic>> allGrades() => grades(xnm: '', xqm: '');

  static const _xyqkGnmkdm = 'N105515';
  static const _xyqkIndex = '/jwglxt/xsxy/xsxyqk_cxXsxyqkIndex.html';
  static const _xyqkKcxx = '/jwglxt/xsxy/xsxyqk_cxJxzxjhxfyqKcxx.html';
  static const _xyqkFKcxx = '/jwglxt/xsxy/xsxyqk_cxJxzxjhxfyqFKcxx.html';

  String get _xyqkReferer =>
      '$kJwxt$_xyqkIndex?echarts=1&gnmkdm=$_xyqkGnmkdm&layout=default';

  Future<CreditProgress> creditProgress() async {
    try {
      return await academicProgress().timeout(const Duration(seconds: 35));
    } catch (e) {
      final s = e.toString();
      if (s.contains('夜间') || s.contains('过期') || s.contains('重新登录') || s.contains('关闭')) {
        rethrow;
      }
    }
    final raw = await allGrades().timeout(const Duration(seconds: 20));
    final items = raw['items'] as List? ?? const [];
    if (items.isEmpty) throw Exception('暂无成绩，无法统计学分');
    return creditProgressFromGrades(items);
  }

  Future<CreditProgress> academicProgress() async {
    final html = await _xyqkHtml();
    final xh = _firstNonEmpty([
      _namedValue(html, 'xh_id'),
      _namedValue(html, 'sessionUserKey'),
    ]);
    if (xh.isEmpty) throw Exception('学业情况页未返回学号');
    final hidden = {
      'fromXh_id': '',
      'xh_id': xh,
      'cjlrxn': _namedValue(html, 'cjlrxn'),
      'cjlrxq': _namedValue(html, 'cjlrxq'),
      'bkcjlrxn': _namedValue(html, 'bkcjlrxn'),
      'bkcjlrxq': _namedValue(html, 'bkcjlrxq'),
      'xscjcxkz': _namedValue(html, 'xscjcxkz'),
      'cjcxkzzt': _namedValue(html, 'cjcxkzzt'),
      'cjztkz': _namedValue(html, 'cjztkz'),
      'cjzt': _namedValue(html, 'cjzt'),
    };
    final nodes = _xyqkNodes(html);
    if (nodes.isEmpty) throw Exception('学业情况未返回培养方案节点');
    final leaves = [
      for (final n in nodes)
        if (n.sfmjd == '1' && n.id != 'cxcyqkxfyq') n,
    ];
    final courses = await Future.wait([
      for (final n in leaves) _xyqkCourses(n, hidden),
    ]);
    final buckets = <CreditBucket>[];
    var extraPassed = 0;
    var extraFailed = 0;
    for (var i = 0; i < leaves.length; i++) {
      final n = leaves[i];
      final items = courses[i];
      if (n.id == 'qtkcxfyq' || n.id == 'cxcyqkxfyq') {
        extraPassed += items.where((c) => c.passed).length;
        extraFailed += items.where((c) => c.failed).length;
      }
      if (items.isEmpty && n.yxxf <= 0 && n.yqzdxf <= 0 && n.id == 'qtkcxfyq') {
        continue;
      }
      buckets.add(
        bucketFromXyqk(name: n.name, yxxf: n.yxxf, yqzdxf: n.yqzdxf, items: items),
      );
    }
    _XyqkNode? root;
    for (final n in nodes) {
      if (n.name == '主修') {
        root = n;
        break;
      }
      if (root == null && n.yqzdxf > 0) root = n;
    }
    var passed = 0, failed = 0, unstudied = 0, studying = 0, total = 0;
    for (final b in buckets) {
      if (b.name == '其他课程' || b.name == '创新创业情况') continue;
      passed += b.courses;
      failed += b.failedCourses;
      unstudied += b.unstudiedCourses;
      studying += b.studyingCourses;
      total += b.items.length;
    }
    return CreditProgress(
      taken: _xyqkLabelXf(html, '修读总学分'),
      required: _firstXf([
        _xyqkLabelXf(html, '要求最低学分'),
        root?.yqzdxf ?? 0,
      ]),
      earned: _firstXf([
        _xyqkLabelXf(html, '获得总学分'),
        root?.yxxf ?? 0,
      ]),
      gpa: _xyqkGpa(html),
      planTotal: total,
      planPassed: passed,
      planFailed: failed,
      planUnstudied: unstudied,
      planStudying: studying,
      extraPassed: extraPassed,
      extraFailed: extraFailed,
      buckets: buckets,
    );
  }

  Future<String> _xyqkHtml() async {
    Object? last;
    var relogged = false;
    for (var i = 0; i < 3; i++) {
      try {
        final r = await dio.get(
          '$kJwxt$_xyqkIndex',
          queryParameters: const {
            'echarts': '1',
            'gnmkdm': _xyqkGnmkdm,
            'layout': 'default',
          },
          options: Options(
            headers: {'Referer': _referer, 'Accept': 'text/html'},
            receiveTimeout: const Duration(seconds: 20),
          ),
        );
        if (r.statusCode == 403 || _looksClosed(r.statusCode, r.data)) {
          portalClosed = true;
          throw Exception(nightClosedMessage('教务'));
        }
        if (_looksLoggedOut(r) || isRedirect(r)) {
          if (!relogged) {
            relogged = true;
            await ensureSession(force: true);
            continue;
          }
          throw Exception('教务登录已过期，请重新登录');
        }
        if (r.statusCode != 200) throw Exception('HTTP ${r.statusCode}');
        final html = r.data.toString();
        if (!html.contains('xfyqjd_id') || !html.contains(_xyqkGnmkdm)) {
          throw Exception('学业情况页无培养方案');
        }
        return html;
      } catch (e) {
        last = e;
        final s = e.toString();
        if (s.contains('夜间') || s.contains('过期') || s.contains('重新登录') || s.contains('培养方案')) {
          rethrow;
        }
        if (i < 2) await Future<void>.delayed(const Duration(milliseconds: 400));
      }
    }
    throw Exception('学业情况页请求失败: $last');
  }

  Future<List<PlanCourse>> _xyqkCourses(_XyqkNode node, Map<String, String> hidden) async {
    final extra = node.id == 'qtkcxfyq' || node.id == 'cxcyqkxfyq';
    final path = (node.jdkcsx == '1' || extra) ? _xyqkKcxx : _xyqkFKcxx;
    final data = <String, dynamic>{
      'fromXh_id': hidden['fromXh_id'] ?? '',
      'xfyqjd_id': node.id,
      'xh_id': hidden['xh_id'] ?? '',
      if (extra) ...{
        'cjlrxn': hidden['cjlrxn'] ?? '',
        'cjlrxq': hidden['cjlrxq'] ?? '',
        'bkcjlrxn': hidden['bkcjlrxn'] ?? '',
        'bkcjlrxq': hidden['bkcjlrxq'] ?? '',
        'xscjcxkz': hidden['xscjcxkz'] ?? '',
        'cjcxkzzt': hidden['cjcxkzzt'] ?? '',
        'cjztkz': hidden['cjztkz'] ?? '',
        'cjzt': hidden['cjzt'] ?? '',
      },
    };
    try {
      final raw = await _postJson(path, data);
      if (raw is! List) return const [];
      return [
        for (final row in raw)
          if (row is Map) planCourseFromXyqk(row),
      ];
    } catch (_) {
      return const [];
    }
  }

  Future<Object?> _postJson(String path, Map<String, dynamic> data) async {
    Object? last;
    var relogged = false;
    for (var i = 0; i < 3; i++) {
      try {
        final r = await dio.post(
          '$kJwxt$path',
          data: data,
          queryParameters: const {'gnmkdm': _xyqkGnmkdm},
          options: Options(
            contentType: Headers.formUrlEncodedContentType,
            headers: {
              'Referer': _xyqkReferer,
              'X-Requested-With': 'XMLHttpRequest',
              'Accept': 'application/json, text/javascript, */*; q=0.01',
            },
          ),
        );
        if (r.statusCode == 403 || _looksClosed(r.statusCode, r.data)) {
          portalClosed = true;
          throw Exception(nightClosedMessage('教务'));
        }
        if (_looksLoggedOut(r) || isRedirect(r)) {
          if (!relogged) {
            relogged = true;
            await ensureSession(force: true);
            continue;
          }
          throw Exception('教务登录已过期，请重新登录');
        }
        if (r.statusCode != 200) throw Exception('HTTP ${r.statusCode}');
        return _asJson(r.data);
      } catch (e) {
        last = e;
        final s = e.toString();
        if (s.contains('夜间') || s.contains('过期') || s.contains('重新登录')) rethrow;
        if (i < 2) await Future<void>.delayed(const Duration(milliseconds: 400));
      }
    }
    throw Exception('请求失败 $path: $last');
  }

  Future<Map<String, dynamic>> exams({int? xnm, String? xqm}) async {
    final t = currentTerm();
    return _post(
      '/jwglxt/kwgl/kscx_cxXsksxxIndex.html',
      {'xnm': '${xnm ?? t.$1}', 'xqm': xqm ?? t.$2},
      params: {'doType': 'query'},
    );
  }

  Future<StudentProfile> profile() async {
    var p = const StudentProfile();
    p = p.merge(await _fromGrxx());
    p = p.merge(await _fromUserIndex());
    p = p.merge(await _fromSchedule());
    p = p.merge(await _fromMenu());
    return p;
  }

  Future<StudentProfile> _fromUserIndex() async {
    try {
      final r = await dio.post(
        '$kJwxt/jwglxt/xtgl/index_cxYhxxIndex.html',
        queryParameters: {
          'xt': 'jw',
          'localeKey': 'zh_CN',
          '_': '${DateTime.now().millisecondsSinceEpoch}',
        },
        options: Options(
          contentType: Headers.formUrlEncodedContentType,
          headers: {'Referer': '$kJwxt/jwglxt/xtgl/index_initMenu.html?jsdm=xs'},
        ),
      );
      final map = _asMap(r.data);
      if (map != null) return _fromMap(map);
      if (r.data is String) return _fromHtml(r.data.toString());
    } catch (_) {}
    return const StudentProfile();
  }

  Future<StudentProfile> _fromMenu() async {
    try {
      final r = await dio.get(
        '$kJwxt/jwglxt/xtgl/index_initMenu.html',
        queryParameters: {'jsdm': 'xs'},
      );
      if (r.statusCode != 200) return const StudentProfile();
      final html = r.data.toString();
      if (html.contains('login_slogin') || html.contains('统一身份认证平台')) {
        return const StudentProfile();
      }
      final welcome = RegExp(r'欢迎您[，,]\s*([^！!<]{1,20})').firstMatch(html)?.group(1)?.trim() ?? '';
      final sessionUser = RegExp(r'id="sessionUserKey"[^>]*>\s*([^<]+)').firstMatch(html)?.group(1)?.trim() ?? '';
      final name = _firstNonEmpty([welcome, sessionUser]);
      if (name.isEmpty) return const StudentProfile();
      return StudentProfile(name: name);
    } catch (_) {}
    return const StudentProfile();
  }

  Future<StudentProfile> _fromGrxx() async {
    try {
      final r = await dio.post(
        '$kJwxt/jwglxt/xsxxxggl/xsgrxxwh_cxXsgrxx.html',
        queryParameters: {'gnmkdm': 'N100801'},
        data: const <String, dynamic>{},
        options: Options(
          contentType: Headers.formUrlEncodedContentType,
          headers: {'Referer': '$kJwxt/jwglxt/xtgl/index_initMenu.html?jsdm=xs'},
        ),
      );
      final map = _asMap(r.data);
      if (map != null) return _fromMap(map);
      return _fromHtml(r.data.toString());
    } catch (_) {}
    return const StudentProfile();
  }

  Future<StudentProfile> _fromSchedule() async {
    try {
      final kb = await schedule();
      final xsxx = kb['xsxx'];
      if (xsxx is Map) return _fromMap(Map<String, dynamic>.from(xsxx));
      final list = kb['xsjbxxList'];
      if (list is List && list.isNotEmpty && list.first is Map) {
        return _fromMap(Map<String, dynamic>.from(list.first as Map));
      }
    } catch (_) {}
    return const StudentProfile();
  }

  StudentProfile _fromMap(Map<String, dynamic> m) {
    String at(String k) {
      final v = m[k] ?? m[k.toUpperCase()] ?? m[k.toLowerCase()];
      final s = '${v ?? ''}'.trim();
      return (s.isEmpty || s == 'null') ? '' : s;
    }

    final bj = at('bjmc');
    return StudentProfile(
      studentId: at('xh'),
      name: at('xm'),
      gender: _gender(at('xb')),
      college: at('jgmc'),
      major: _cleanMajor(at('zymc')),
      klass: bj.isNotEmpty ? bj : at('bj'),
      grade: at('njmc').isNotEmpty ? at('njmc') : at('njdm_id'),
      campus: at('xqmc'),
    );
  }

  StudentProfile _fromHtml(String html) {
    if (html.contains('login_slogin') || html.contains('统一身份认证平台')) {
      return const StudentProfile();
    }
    String named(List<String> ids) {
      for (final id in ids) {
        final v = _namedValue(html, id);
        if (_plausible(v)) return v;
      }
      return '';
    }

    String lab(List<String> labels) {
      for (final l in labels) {
        final v = _staticAfter(html, l);
        if (_plausible(v)) return v;
      }
      return '';
    }

    final heading = _clean(
      RegExp(r'class="media-heading">\s*([^<]+)').firstMatch(html)?.group(1) ?? '',
    );
    var nameFromHeading = heading.replaceAll(RegExp(r'\s*学生\s*$'), '').trim();
    var role = '';
    if (heading.contains('学生')) role = '学生';

    var college = lab(const ['学院名称', '学院']);
    var klass = lab(const ['班级名称', '班级']);
    final mediaLine = _clean(
      RegExp(r'class="media-heading">[\s\S]{0,400}?<p>\s*([^<]+)</p>').firstMatch(html)?.group(1) ?? '',
    );
    if (mediaLine.isNotEmpty) {
      final m = RegExp(r'^(.+学院)\s+(.+)$').firstMatch(mediaLine);
      if (m != null) {
        if (college.isEmpty) college = m.group(1)!.trim();
        if (klass.isEmpty) klass = m.group(2)!.trim();
      }
    }

    return StudentProfile(
      studentId: _firstNonEmpty([named(const ['xh', 'xh_id', 'XH']), lab(const ['学号'])]),
      name: _firstNonEmpty([
        lab(const ['姓名']),
        named(const ['xm', 'XM']),
        nameFromHeading,
      ]),
      gender: _gender(_firstNonEmpty([lab(const ['性别']), named(const ['xb', 'xbm'])])),
      college: college,
      major: _cleanMajor(_firstNonEmpty([lab(const ['专业名称', '专业']), named(const ['zymc', 'zy'])])),
      klass: klass,
      grade: _firstNonEmpty([lab(const ['年级']), named(const ['njmc', 'njdm_id', 'nj'])]),
      phone: _firstNonEmpty([lab(const ['手机号码', '手机', '联系电话']), named(const ['sjhm', 'lxdh', 'yddh'])]),
      campus: _firstNonEmpty([lab(const ['校区']), named(const ['xqmc'])]),
      role: role,
    );
  }
}

Map<String, dynamic>? _asMap(Object? data) {
  if (data is Map<String, dynamic>) return data;
  if (data is Map) return Map<String, dynamic>.from(data);
  if (data is String) {
    final s = data.trim();
    if (s.startsWith('{') && s.endsWith('}')) {
      try {
        final d = jsonDecode(s);
        if (d is Map) return Map<String, dynamic>.from(d);
      } catch (_) {}
    }
  }
  return null;
}

Object? _asJson(Object? data) {
  if (data is List || data is Map) return data;
  if (data is String) {
    final s = data.trim();
    if (s.isEmpty) return const [];
    try {
      return jsonDecode(s);
    } catch (_) {}
  }
  return data;
}

class _XyqkNode {
  const _XyqkNode({
    required this.id,
    required this.name,
    required this.jdkcsx,
    required this.sfmjd,
    required this.yxxf,
    required this.yqzdxf,
  });

  final String id;
  final String name;
  final String jdkcsx;
  final String sfmjd;
  final double yxxf;
  final double yqzdxf;
}

List<_XyqkNode> _xyqkNodes(String html) {
  final src = html.replaceAll(r"\'", "'");
  final seen = <String, _XyqkNode>{};
  final metaRe = RegExp(
    r"xfyqjd_id='([^']+)' jdkcsx='([^']*)' leaf='([^']*)' sfmjd='([^']*)'",
  );
  for (final m in metaRe.allMatches(src)) {
    final id = m.group(1)!;
    if (seen.containsKey(id)) continue;
    seen[id] = _XyqkNode(
      id: id,
      name: '',
      jdkcsx: m.group(2) ?? '',
      sfmjd: m.group(4) ?? '',
      yxxf: 0,
      yqzdxf: 0,
    );
  }
  final titleRe = RegExp(
    r"""id='p([^']+)' yxxf='([^']*)' yqzdxf='([^']*)' sftg='([^']*)'>"\s*\+\s*"([^"&<]+)""",
  );
  for (final m in titleRe.allMatches(src)) {
    final id = m.group(1)!;
    final prev = seen[id];
    if (prev == null) continue;
    seen[id] = _XyqkNode(
      id: id,
      name: prev.name.isNotEmpty ? prev.name : (m.group(5) ?? '').trim(),
      jdkcsx: prev.jdkcsx,
      sfmjd: prev.sfmjd,
      yxxf: double.tryParse(m.group(2) ?? '') ?? 0,
      yqzdxf: double.tryParse(m.group(3) ?? '') ?? 0,
    );
  }
  return seen.values.toList();
}

double _xyqkLabelXf(String html, String label) {
  final m = RegExp('$label[\\s\\S]{0,400}?>([0-9.]+)<').firstMatch(html);
  return double.tryParse(m?.group(1) ?? '') ?? 0;
}

double? _xyqkGpa(String html) {
  final i = html.indexOf('GPA');
  if (i < 0) return null;
  final end = i + 500 < html.length ? i + 500 : html.length;
  final m = RegExp(r'([0-9]+\.[0-9]+)').firstMatch(html.substring(i, end));
  return double.tryParse(m?.group(1) ?? '');
}

double _firstXf(List<double> xs) {
  for (final v in xs) {
    if (v > 0) return v;
  }
  return 0;
}


String _namedValue(String html, String name) {
  final n = RegExp.escape(name);
  final patterns = [
    RegExp('(?:name|id)=[\'"]$n[\'"][^>]*value=[\'"]([^\'"]+)[\'"]', caseSensitive: false),
    RegExp('value=[\'"]([^\'"]+)[\'"][^>]*(?:name|id)=[\'"]$n[\'"]', caseSensitive: false),
    RegExp('id=[\'"]col_$n[\'"][^>]*>\\s*([^<]+)', caseSensitive: false),
  ];
  for (final re in patterns) {
    final m = re.firstMatch(html);
    if (m != null) {
      final v = m.group(1)!.trim();
      if (v.isNotEmpty && v != 'null') return v;
    }
  }
  return '';
}

String _clean(String raw) {
  return raw
      .replaceAll('&nbsp;', ' ')
      .replaceAll(RegExp(r'\s+'), ' ')
      .trim();
}

bool _plausible(String v) {
  final t = _clean(v);
  if (t.isEmpty || t == 'null' || t == 'undefined') return false;
  if (t.endsWith('：') || t.endsWith(':')) return false;
  if (t.contains('请选择') || t.contains('请填写') || t.contains('请输入')) return false;
  if (RegExp(r'^(姓名|学号|性别|学院|专业|班级|年级|校区|手机|民族|系名称)名称?$').hasMatch(t)) return false;
  return true;
}

String _staticAfter(String html, String label) {
  final re = RegExp(
    '$label\\s*[:：]?\\s*</label>\\s*<div[^>]*>\\s*<p class="form-control-static">\\s*([^<]+)',
    caseSensitive: false,
  );
  final v = _clean(re.firstMatch(html)?.group(1) ?? '');
  return _plausible(v) ? v : '';
}

String _cleanMajor(String v) {
  return v.replaceFirst(RegExp(r'\(\d+\)$'), '').trim();
}

String _firstNonEmpty(List<String> xs) {
  for (final x in xs) {
    final v = x.trim();
    if (v.isNotEmpty) return v;
  }
  return '';
}

String _gender(String v) {
  if (v == '1' || v == '男') return '男';
  if (v == '2' || v == '女') return '女';
  return v;
}

