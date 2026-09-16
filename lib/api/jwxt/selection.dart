part of '../jwxt.dart';

mixin JwxtSelectionApi on JwxtSessionApi {
  Map<String, String>? _xkDisplayArgs;
  var _xkPriming = false;

  // ---------------- 自主选课 (xsxk, 逆向自 zzxkYzb.js) ----------------

  Options _xkOpts({bool html = false}) => Options(
        contentType: Headers.formUrlEncodedContentType,
        responseType: ResponseType.plain,
        receiveTimeout: const Duration(seconds: 12),
        sendTimeout: const Duration(seconds: 8),
        // 910/911 是选课模块门槛，不能让 Dio 在 <500 处直接丢掉。
        validateStatus: (s) => s != null && s < 1000,
        headers: {
          'Referer': kXkReferer,
          'X-Requested-With': 'XMLHttpRequest',
          'Accept': html
              ? 'text/html, */*; q=0.01'
              : 'application/json, text/javascript, */*; q=0.01',
        },
      );

  Future<Response> _xkRaw(
    String path,
    Map<String, dynamic> data, {
    bool html = false,
  }) async {
    Object? last;
    var relogged = false;
    var indexRetried = false;
    final form = <String, String>{
      for (final e in data.entries) e.key: '${e.value ?? ''}',
    };
    for (var i = 0; i < 3; i++) {
      try {
        final r = await dio.post(
          '$kJwxt$path',
          data: form,
          queryParameters: const {'gnmkdm': kXkGnmkdm},
          options: _xkOpts(html: html),
        );
        if (r.statusCode == 403 || _looksClosed(r.statusCode, r.data)) {
          portalClosed = true;
          throw Exception(nightClosedMessage('教务'));
        }
        // 910/911 必须先于“看起来像登录页”判断：910 的 JSON 壳有时带登录页字段。
        if (r.statusCode == 911 || r.statusCode == 910) {
          _xkTrace('$path HTTP ${r.statusCode}');
          if (_xkPriming) {
            throw Exception('选课会话已失效，请刷新重试');
          }
          if (!indexRetried) {
            indexRetried = true;
            await _primeXk();
            continue;
          }
          throw Exception('选课会话已失效，请刷新重试');
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
        return r;
      } catch (e) {
        last = e;
        final s = e.toString();
        if (s.contains('夜间') ||
            s.contains('过期') ||
            s.contains('重新登录') ||
            s.contains('刷新重试') ||
            s.contains('维护')) {
          rethrow;
        }
        if (i < 2) {
          await Future<void>.delayed(const Duration(milliseconds: 400));
        }
      }
    }
    throw Exception('选课请求失败 $path: $last');
  }

  Future<Object?> _xkPost(String path, Map<String, dynamic> data) async {
    final r = await _xkRaw(path, data);
    final raw = r.data;
    if (raw is List || raw is Map) {
      _xkTrace('$path typed ${raw.runtimeType}');
      return raw;
    }
    final s = raw?.toString() ?? '';
    _xkTrace('$path HTTP ${r.statusCode} ${s.length}b ${s.substring(0, s.length < 180 ? s.length : 180)}');
    if (s.contains('加密串错误')) throw Exception('选课加密串已过期，请刷新重试');
    if (s.contains('系统维护')) throw Exception('选课系统维护中');
    final d = _asJson(s);
    if (d is Map || d is List) return d;
    throw Exception('选课接口没有返回课程数据');
  }

  Future<String> _xkHtml(String path, Map<String, dynamic> data) async {
    final r = await _xkRaw(path, data, html: true);
    return r.data?.toString() ?? '';
  }

  Future<void> _primeXk() async {
    if (_xkPriming) return;
    _xkPriming = true;
    try {
      await dio.get(
        '$kJwxt/jwglxt/xsxk/zzxkyzb_cxZzxkYzbIndex.html',
        queryParameters: const {'gnmkdm': kXkGnmkdm, 'layout': 'default'},
        options: Options(headers: {'Referer': kXkReferer}),
      );
      final a = _xkDisplayArgs;
      if (a != null) {
        await _xkHtml('/jwglxt/xsxk/zzxkyzb_cxZzxkYzbDisplay.html', {
          'xkkz_id': a['xkkz_id'] ?? '',
          'kklxdm': a['kklxdm'] ?? '',
          'xszxzt': a['xszxzt'] ?? '1',
          'njdm_id': a['njdm_id'] ?? '',
          'zyh_id': a['zyh_id'] ?? '',
          'kspage': '0',
          'jspage': '0',
          'xkkz_xh': a['xkkz_xh'] ?? '',
        });
      }
    } catch (e) {
      _xkTrace('prime $e');
    } finally {
      _xkPriming = false;
    }
  }

  Map<String, dynamic> _xkActionQuery(
    Map<String, dynamic> query, {
    Map<String, dynamic> extra = const {},
  }) {
    final q = Map<String, dynamic>.from(query);
    for (final k in const [
      'kspage',
      'jspage',
      'globJsPage',
      'isEnd',
      'js_kcrow',
    ]) {
      q.remove(k);
    }
    extra.forEach((k, v) => q[k] = '${v ?? ''}');
    return q;
  }

  /// 选课入口页：返回轮次列表（每轮含 xkkz_id/xkkz_xh 加密串）与学生画像字段。
  Future<Map<String, dynamic>> selectionEntry() async {
    Object? last;
    for (var attempt = 0; attempt < 2; attempt++) {
      final r = await dio.get(
        '$kJwxt/jwglxt/xsxk/zzxkyzb_cxZzxkYzbIndex.html',
        queryParameters: const {'gnmkdm': kXkGnmkdm, 'layout': 'default'},
        options: Options(headers: {'Referer': _jwxtMenuReferer}),
      );
      if (_looksLoggedOut(r) || isRedirect(r)) {
        last = Exception('选课会话已刷新，请重试');
        await ensureSession(force: true);
        continue;
      }
      if (r.statusCode != 200) throw Exception('HTTP ${r.statusCode}');
      final html = r.data?.toString() ?? '';
      if (html.contains('系统维护页面')) throw Exception('选课系统维护中');
      final rounds = parseXkRounds(html);
      final profile = extractXkProfile(html);
      _xkTrace('entry rounds=${rounds.length} profile=${profile.length}');
      return {'rounds': rounds, 'profile': profile};
    }
    throw last ?? Exception('选课会话已刷新，请重试');
  }

  /// 点轮次 tab 后官网会 `.load` Display.html，把 rwlx/xklc 等 hidden 落到面板。
  Future<Map<String, String>> selectionDisplay({
    required String xkkzId,
    required String xkkzXh,
    required String kklxdm,
    required String njdmId,
    required String zyhId,
  }) async {
    _xkDisplayArgs = {
      'xkkz_id': xkkzId,
      'kklxdm': kklxdm,
      'xszxzt': '1',
      'njdm_id': njdmId,
      'zyh_id': zyhId,
      'xkkz_xh': xkkzXh,
    };
    final html = await _xkHtml('/jwglxt/xsxk/zzxkyzb_cxZzxkYzbDisplay.html', {
      'xkkz_id': xkkzId,
      'kklxdm': kklxdm,
      'xszxzt': '1',
      'njdm_id': njdmId,
      'zyh_id': zyhId,
      'kspage': 0,
      'jspage': 0,
      'xkkz_xh': xkkzXh,
    });
    if (html.contains('系统维护')) throw Exception('选课系统维护中');
    if (html.contains('加密串错误')) throw Exception('选课加密串已过期，请刷新重试');
    final panel = parseXkPanel(html);
    _xkTrace(
      'display panel=${panel.keys.length} rwlx=${panel['rwlx']} xklc=${panel['xklc']}',
    );
    return panel;
  }

  /// 可选课程列表。按官网分页一直拉到不足一页。
  Future<List<dynamic>> selectionCourses(Map<String, dynamic> query) async {
    const size = 10;
    final all = <dynamic>[];
    for (var page = 1; page <= 20; page++) {
      final range = xkPageRange(page, size: size);
      final q = Map<String, dynamic>.from(query)
        ..['kspage'] = '${range.$1}'
        ..['jspage'] = '${range.$2}';
      final d = await _xkPost(
        '/jwglxt/xsxk/zzxkyzb_cxZzxkYzbPartDisplay.html',
        q,
      );
      if (d == 0 || d == '0') {
        throw Exception('选课查询被拒绝，请刷新重试');
      }
      List<dynamic> rows = const [];
      if (d is Map) {
        if ('$d'.contains('加密串错误')) {
          throw Exception('选课加密串已过期，请刷新重试');
        }
        final flag = '${d['flag'] ?? ''}';
        if (flag == '0') throw Exception('${d['msg'] ?? '选课查询失败'}');
        rows = xkRowsOf(d);
      } else if (d is List) {
        rows = d;
      }
      _xkTrace(
        'PartDisplay page=$page kspage=${range.$1} jspage=${range.$2} rows=${rows.length} type=${d.runtimeType}',
      );
      all.addAll(rows);
      if (xkPartDisplayDone(rows, size)) break;
    }
    final collapsed = xkCollapseByCourse(all);
    _xkTrace(
      'courses total=${all.length} collapsed=${collapsed.length} first=${collapsed.isEmpty ? '' : (collapsed.first is Map ? collapsed.first['kcmc'] : collapsed.first)}',
    );
    return collapsed;
  }

  /// 某课程的教学班明细（含提交用的 do_jxb_id 加密串与实时余量）。
  Future<List<dynamic>> selectionJxbs(
    Map<String, dynamic> query,
    String kchId,
    String kcmc, {
    String cxbj = '',
    String fxbj = '',
  }) async {
    await _primeXk();
    final d = await _xkPost(
      '/jwglxt/xsxk/zzxkyzbjk_cxJxbWithKchZzxkYzb.html',
      _xkActionQuery(query, extra: {
        'kch_id': kchId,
        'kcmc': kcmc,
        'cxbj': cxbj,
        'fxbj': fxbj,
      }),
    );
    if (d == 0 || d == '0') {
      throw Exception('选课查询被拒绝，请刷新重试');
    }
    if (d is List) {
      _xkTrace('JxbWithKch $kchId n=${d.length}');
      return d;
    }
    final rows = xkRowsOf(d);
    _xkTrace('JxbWithKch $kchId n=${rows.length} type=${d.runtimeType}');
    return rows;
  }

  /// 提交选课。字段 1:1 对齐官网 `zzxkYzbChoosedZy.js` 的 `saveCourse`：
  /// POST `/xsxk/zzxkyzbjk_xkBcZyZzxkYzb.html`，`jxb_ids=do_jxb_id`。
  Future<Map<String, dynamic>> selectionSubmit({
    required String jxbIds,
    required String kchId,
    required String kcmc,
    required String rwlx,
    required String rlkz,
    required String cdrlkz,
    required String rlzlkz,
    required String sxbj,
    required String xxkbj,
    required String qz,
    required String cxbj,
    required String xkkzId,
    required String njdmId,
    required String zyhId,
    required String kklxdm,
    required String xklc,
    required String xkxnm,
    required String xkxqm,
    String jcxxId = '',
  }) async {
    final body = xkSaveCourseBody(
      jxbIds: jxbIds,
      kchId: kchId,
      kcmc: kcmc,
      rwlx: rwlx,
      rlkz: rlkz,
      cdrlkz: cdrlkz,
      rlzlkz: rlzlkz,
      sxbj: sxbj,
      xxkbj: xxkbj,
      qz: qz,
      cxbj: cxbj,
      xkkzId: xkkzId,
      njdmId: njdmId,
      zyhId: zyhId,
      kklxdm: kklxdm,
      xklc: xklc,
      xkxnm: xkxnm,
      xkxqm: xkxqm,
      jcxxId: jcxxId,
    );
    _xkTrace(
      'submit kch=$kchId sxbj=$sxbj cxbj=$cxbj qz=$qz xklc=$xklc '
      'rlzlkz=$rlzlkz kcmc=${kcmc.length}c keys=${body.keys.join(",")}',
    );
    final d = await _xkPost(
      '/jwglxt/xsxk/zzxkyzbjk_xkBcZyZzxkYzb.html',
      body,
    );
    if (d is Map<String, dynamic>) return d;
    if (d is Map) return Map<String, dynamic>.from(d);
    return {'flag': '-1', 'msg': '非 JSON 应答'};
  }

  /// 已选列表。官网右侧栏走 ChoosedDisplay JSON（kch_id / jxb_id / t_kch_id）。
  Future<List<dynamic>> selectionChoosed({
    Map<String, String> profile = const {},
    Map<String, String> panel = const {},
  }) async {
    try {
      final d = await _xkPost(
        '/jwglxt/xsxk/zzxkyzb_cxZzxkYzbChoosedDisplay.html',
        {
          'jg_id': panel['jg_id'] ?? profile['jg_id'] ?? profile['jg_id_1'] ?? '',
          'zyh_id': profile['zyh_id'] ?? panel['zyh_id'] ?? '',
          'njdm_id': profile['njdm_id'] ?? panel['njdm_id'] ?? '',
          'zyfx_id': profile['zyfx_id'] ?? panel['zyfx_id'] ?? '',
          'bh_id': profile['bh_id'] ?? panel['bh_id'] ?? '',
          'xz': profile['xz'] ?? panel['xz'] ?? '',
          'ccdm': profile['ccdm'] ?? panel['ccdm'] ?? '',
          'xqh_id': profile['xqh_id'] ?? panel['xqh_id'] ?? '',
          'xkxnm': profile['xkxnm'] ?? '',
          'xkxqm': profile['xkxqm'] ?? '',
          'xkly': panel['xkly'] ?? '',
        },
      );
      if (d is List) {
        _xkTrace('ChoosedDisplay n=${d.length}');
        return d;
      }
      final rows = xkRowsOf(d);
      if (rows.isNotEmpty) {
        _xkTrace('ChoosedDisplay n=${rows.length}');
        return rows;
      }
    } catch (e) {
      _xkTrace('ChoosedDisplay $e');
    }
    try {
      final html = await _xkHtml(
        '/jwglxt/xsxk/zzxkyzb_cxZzxkYzbChoosed.html',
        const {},
      );
      return xkParseChoosed(html);
    } catch (e) {
      _xkTrace('choosed $e');
      return const [];
    }
  }
}

void _xkTrace(String msg) {
  debugPrint('[xk] $msg');
  for (final p in const [
    '/data/data/cn.edu.swun.swun_ehall/files/xk.log',
    '/data/user/0/cn.edu.swun.swun_ehall/files/xk.log',
  ]) {
    try {
      File(p).writeAsStringSync(
        '${DateTime.now().toIso8601String()} [xk] $msg\n',
        mode: FileMode.append,
        flush: true,
      );
      return;
    } catch (_) {}
  }
}
