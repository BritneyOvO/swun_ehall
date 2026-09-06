import 'dart:convert';

import 'package:cookie_jar/cookie_jar.dart';
import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';

import '../models/credit.dart';
import '../models/profile.dart';
import 'cas.dart';
import 'httpx.dart';

const kJwxt = 'https://jwxt.swun.edu.cn';
const kJwxtService = 'http://jwxt.swun.edu.cn/sso/jziotlogin';

/// 自主选课功能码（jwglxt 所有 xsxk 请求都要带 ?gnmkdm=N253512）。
const kXkGnmkdm = 'N253512';
const kXkReferer = '$kJwxt/jwglxt/xsxk/zzxkyzb_cxZzxkYzbIndex.html?gnmkdm=$kXkGnmkdm';

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

  // ---------------- 自主选课 (xsxk, 逆向自 zzxkYzb.js) ----------------

  Options get _xkForm => Options(
        contentType: Headers.formUrlEncodedContentType,
        headers: const {
          'Referer': kXkReferer,
          'X-Requested-With': 'XMLHttpRequest',
          'Accept': 'application/json, text/javascript, */*; q=0.01',
        },
      );

  Future<Object?> _xkPost(String path, Map<String, dynamic> data) async {
    Object? last;
    var relogged = false;
    for (var i = 0; i < 3; i++) {
      try {
        final r = await dio.post(
          '$kJwxt$path',
          data: data,
          queryParameters: const {'gnmkdm': kXkGnmkdm},
          options: _xkForm,
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
        // 910/911 = 选课模块自定义状态（会话/权限门槛），911 空壳可在重新进入口后恢复。
        if (r.statusCode == 911 || r.statusCode == 910) {
          throw Exception('选课会话已失效，请刷新重试');
        }
        if (r.statusCode != 200) throw Exception('HTTP ${r.statusCode}');
        return _asJson(r.data);
      } catch (e) {
        last = e;
        final s = e.toString();
        if (s.contains('夜间') || s.contains('过期') || s.contains('重新登录') || s.contains('刷新重试')) {
          rethrow;
        }
        if (i < 2) await Future<void>.delayed(const Duration(milliseconds: 400));
      }
    }
    throw Exception('选课请求失败 $path: $last');
  }

  /// 选课入口页：返回轮次列表（每轮含 xkkz_id/xkkz_xh 加密串）与学生画像字段。
  /// 逆向：入口 GET zzxkyzb_cxZzxkYzbIndex.html?gnmkdm=N253512&layout=default，
  /// 每个轮次 tab 的 onclick=queryCourse(this,kklxdm,xkkz_id,njdm_id,zyh_id,xkkz_xh)。
  Future<Map<String, dynamic>> selectionEntry() async {
    final r = await dio.get(
      '$kJwxt/jwglxt/xsxk/zzxkyzb_cxZzxkYzbIndex.html',
      queryParameters: const {'gnmkdm': kXkGnmkdm, 'layout': 'default'},
      options: Options(headers: {'Referer': _referer}),
    );
    if (_looksLoggedOut(r) || isRedirect(r)) {
      await ensureSession(force: true);
      throw Exception('选课会话已刷新，请重试');
    }
    if (r.statusCode != 200) throw Exception('HTTP ${r.statusCode}');
    final html = r.data?.toString() ?? '';
    if (html.contains('系统维护页面')) throw Exception('选课系统维护中');
    final rounds = parseXkRounds(html);
    final profile = extractXkProfile(html);
    debugPrint('[xk] entry rounds=${rounds.length} profile=${profile.length}');
    return {'rounds': rounds, 'profile': profile};
  }

  /// 可选课程列表（含教学班行）。[query] 由 [buildXkQuery] 构造。
  Future<List<dynamic>> selectionCourses(Map<String, dynamic> query) async {
    final d = await _xkPost('/jwglxt/xsxk/zzxkyzb_cxZzxkYzbPartDisplay.html', query);
    if (d is Map) {
      if ('$d'.contains('加密串错误')) throw Exception('选课加密串已过期，请刷新重试');
      final flag = '${d['flag'] ?? ''}';
      if (flag == '0') throw Exception('${d['msg'] ?? '选课查询失败'}');
      final rows = (d['tmpList'] as List?) ?? const [];
      debugPrint('[xk] PartDisplay rows=${rows.length}');
      return rows;
    }
    debugPrint('[xk] PartDisplay raw ${d.runtimeType}');
    return d is List ? d : const [];
  }

  /// 某课程的教学班明细（含提交用的 do_jxb_id 加密串与实时余量）。
  Future<List<dynamic>> selectionJxbs(Map<String, dynamic> query, String kchId, String kcmc) async {
    final d = await _xkPost('/jwglxt/xsxk/zzxkyzbjk_cxJxbWithKchZzxkYzb.html', {
      ...query,
      'kch_id': kchId,
      'kcmc': kcmc,
    });
    return d is List ? d : const [];
  }

  /// 提交选课。返回服务端应答（flag=1/6/3 成功，其余 msg 为失败原因）。
  Future<Map<String, dynamic>> selectionSubmit(Map<String, dynamic> query) async {
    final d = await _xkPost('/jwglxt/xsxk/zzxkyzb_xkZzxk.html', query);
    if (d is Map<String, dynamic>) return d;
    if (d is Map) return Map<String, dynamic>.from(d);
    return {'flag': '-1', 'msg': '非 JSON 应答'};
  }

  /// 已选列表。选课会话门槛高，失败时返回空（页面按「暂无已选」处理）。
  Future<List<dynamic>> selectionChoosed(Map<String, dynamic> query) async {
    final d = await _xkPost('/jwglxt/xsxk/zzxkyzb_cxZzxkYzbChoosed.html', query);
    if (d is Map) return (d['items'] as List?) ?? const [];
    return d is List ? d : const [];
  }

  /// 教务全量已选课程（xklb=01 主修）；用于选课页展示本学期已选。
  Future<Map<String, dynamic>> selectionIndex({
    required String xkkzId,
    required String xkkzXh,
    required String kklxdm,
    required String njdmId,
    required String zyhId,
    Map<String, String> profile = const {},
  }) {
    return _xkPost('/jwglxt/xsxk/zzxkyzb_cxZzxkYzbDisplay.html', {
      'xkkz_id': xkkzId,
      'kklxdm': kklxdm,
      'xszxzt': '1',
      'njdm_id': njdmId,
      'zyh_id': zyhId,
      'kspage': 0,
      'jspage': 0,
      'xkkz_xh': xkkzXh,
    }).then((d) => d is Map<String, dynamic> ? d : <String, dynamic>{});
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

// ---------------- 选课纯函数（可单测） ----------------

String _xkAttr(String html, String name) {
  final m = RegExp('name="$name"[^>]*value="([^"]*)"').firstMatch(html);
  return m?.group(1) ?? '';
}

/// 从选课入口页 HTML 解析轮次列表。
/// 每个 tab: `queryCourse(this,'01','<xkkz_id>','<njdm>','<zyh>','<xkkz_xh RSA串>')`
List<Map<String, dynamic>> parseXkRounds(String html) {
  final out = <Map<String, dynamic>>[];
  final re = RegExp(
    r"queryCourse\(this,'([^']+)','([^']+)','([^']+)','([^']+)','([^']*)'\)",
  );
  for (final m in re.allMatches(html)) {
    out.add({
      'kklxdm': m.group(1) ?? '',
      'xkkz_id': m.group(2) ?? '',
      'njdm_id': m.group(3) ?? '',
      'zyh_id': m.group(4) ?? '',
      'xkkz_xh': m.group(5) ?? '',
      // tab 文本在同一个 <li> 里，向前找最近的 >xxx<
      'kklxmc': _xkTabName(html, m.start),
    });
  }
  return out;
}

String _xkTabName(String html, int at) {
  // tab 文本在 onclick 所在 <a> 标签闭合与 </a> 之间：
  // …queryCourse(…)"> 主修课程</a>
  final open = html.indexOf('">', at);
  final gt = open < 0 ? -1 : html.indexOf('>', open);
  final stop = gt < 0 ? -1 : html.indexOf('</a>', gt);
  if (gt < 0 || stop < 0) return '';
  final name = html.substring(gt + 1, stop).trim();
  if (name.isEmpty || name.contains('input')) return '';
  return name;
}

/// 入口页学生画像隐藏字段（PartDisplay 必须原样回传）。
Map<String, String> extractXkProfile(String html) {
  const keys = [
    'xh_id', 'xqh_id', 'jg_id_1', 'zyh_id', 'zyfx_id', 'njdm_id', 'bh_id',
    'xbm', 'xslbdm', 'mzm', 'xz', 'ccdm', 'xsbj', 'njdm_id_1', 'zyh_id_1',
    'xkxnm', 'xkxqm', 'xkkz_xh', 'jxbzbkg', 'jxbzhkg', 'qzz', 'xkxfqzfs',
  ];
  final out = <String, String>{};
  for (final k in keys) {
    final v = _xkAttr(html, k);
    if (v.isNotEmpty) out[k] = v;
  }
  return out;
}

/// PartDisplay 查询体：轮次 + 画像 + 展开态字段 + 分页。
/// 校方口径（zzxkYzb.js）：kspage = jspage + 1，jspage = 已展示行数；
/// 即 kspage 从 1 开始、jspage 从 0 开始，两者相差 1。
Map<String, dynamic> buildXkQuery({
    required Map<String, dynamic> round,
    required Map<String, String> profile,
    String kchId = '',
    String kcmc = '',
    int page = 1,
    int size = 30,
    Map<String, String> panel = const {},
  }) {
    return {
      'xkkz_id': round['xkkz_id'],
      'xkkz_xh': round['xkkz_xh'],
      'kklxdm': round['kklxdm'],
      'rwlx': panel['rwlx'] ?? '1',
      'xklc': panel['xklc'] ?? '',
      'xkly': panel['xkly'] ?? '0',
      'bklx_id': '', 'sfkkjyxdnxq': '', 'sfkkjyxdxnxq': '', 'kzkcgs': '0',
      'jg_id': '', 'gnjkxdnj': '', 'bjgkczxbbjwcx': '',
      'njdm_id_1': round['njdm_id'], 'zyh_id_1': round['zyh_id'],
      'zyh_id': round['zyh_id'], 'njdm_id': round['njdm_id'],
      'zyfx_id': profile['zyfx_id'] ?? 'wfx',
      'xqh_id': profile['xqh_id'] ?? '',
      'bh_id': profile['bh_id'] ?? '',
      'xbm': profile['xbm'] ?? '', 'xslbdm': profile['xslbdm'] ?? '',
      'mzm': profile['mzm'] ?? '', 'xz': profile['xz'] ?? '',
      'ccdm': profile['ccdm'] ?? '', 'xsbj': profile['xsbj'] ?? '',
      'sfkknj': '', 'sfkkzy': '', 'kzybkxy': '', 'sfznkx': '', 'zdkxms': '',
      'sfkxq': '', 'bhbcyxkjxb': '', 'sfkcfx': '', 'kkbk': '', 'kkbkdj': '',
      'bklbkcj': '', 'sfkgbcx': '', 'sfrxtgkcxd': '', 'tykczgxdcs': '',
      'xkxnm': profile['xkxnm'] ?? '', 'xkxqm': profile['xkxqm'] ?? '',
      'bbhzxjxb': '', 'zxgbxkkg': '', 'xkzgbj': '0', 'rlkz': '0',
      'jxbzcxskg': '', 'zh': '', 'jxbzb': '',
      'kch_id': kchId, 'kcmc': kcmc,
      'kspage': (page - 1) * size + 1,
      'jspage': (page - 1) * size,
    };
  }

/// 余量解析：blzyl(本轮余量) → blyxrs(补选余量) → jxbrl - yxzrs(容量-已选)。
int xkRemain(Map<String, dynamic> row) {
  var v = _xkInt(row['blzyl']) ?? _xkInt(row['blyxrs']);
  if (v == null) {
    final rl = _xkInt(row['jxbrl']);
    final yx = _xkInt(row['yxzrs']);
    if (rl != null && yx != null) v = rl - yx;
  }
  return v ?? 0;
}

int? _xkInt(Object? v) {
  if (v == null) return null;
  if (v is num) return v.toInt();
  final parsed = int.tryParse('$v'.trim());
  return parsed;
}

/// 已选教学班 id 集合（Choosed items / 本轮已选标记）。
Set<String> xkChoosedIds(List<dynamic> items) {
  return {
    for (final it in items)
      if (it is Map && '${it['jxb_id'] ?? ''}'.isNotEmpty) '${it['jxb_id']}',
  };
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

