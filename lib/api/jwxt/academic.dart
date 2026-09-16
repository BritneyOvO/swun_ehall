part of '../jwxt.dart';

mixin JwxtAcademicApi on JwxtSessionApi {
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
            headers: {'Referer': _jwxtMenuReferer, 'Accept': 'text/html'},
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
}
