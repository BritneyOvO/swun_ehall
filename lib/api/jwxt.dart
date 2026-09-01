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

  bool _looksClosed(int? status, Object? data) {
    if (status == 403) return true;
    final t = data?.toString() ?? '';
    return t.contains('系统已关闭') ||
        t.contains('不在服务时间') ||
        t.contains('不在访问时间') ||
        t.contains('非服务时间') ||
        t.contains('服务已关闭');
  }

  Future<Response> _home() {
    return dio.get(
      '$kJwxt/jwglxt/xtgl/index_initMenu.html',
      queryParameters: {'jsdm': 'xs'},
    );
  }

  Future<void> loginWithCas(CasClient cas) async {
    portalClosed = false;
    var url = await cas.ticketFor(kJwxtService);
    for (var i = 0; i < 15; i++) {
      final r = await dio.get(url);
      if (r.statusCode == 200) break;
      if (_looksClosed(r.statusCode, r.data)) {
        portalClosed = true;
        return;
      }
      var next = loc(r);
      if (next.isEmpty) break;
      url = absUrl(kJwxt, next);
      if (url.contains('authserver') && !url.contains('ticket=')) {
        throw Exception('教务登录失败: 重定向回 CAS');
      }
    }
    final home = await _home();
    final body = home.data.toString();
    if (home.statusCode == 200 && !body.contains('login_slogin') && !_looksClosed(home.statusCode, body)) {
      portalClosed = false;
      return;
    }
    if (_looksClosed(home.statusCode, body)) {
      portalClosed = true;
      return;
    }
    if (body.contains('login_slogin')) {
      throw Exception('教务登录失败');
    }
    throw Exception('教务首页 HTTP ${home.statusCode}');
  }

  Future<bool> sessionAlive() async {
    try {
      final r = await _home();
      final body = r.data.toString();
      if (r.statusCode == 200 && !body.contains('login_slogin') && !_looksClosed(r.statusCode, body)) {
        portalClosed = false;
        return true;
      }
      if (_looksClosed(r.statusCode, body)) {
        portalClosed = true;
        return false;
      }
      return false;
    } catch (_) {
      return false;
    }
  }

  Future<Map<String, dynamic>> _post(
    String path,
    Map<String, dynamic> data, {
    Map<String, dynamic>? params,
  }) async {
    late Object? last;
    for (var i = 0; i < 2; i++) {
      try {
        final r = await dio.post(
          '$kJwxt$path',
          data: data,
          queryParameters: params,
          options: Options(contentType: Headers.formUrlEncodedContentType),
        );
        if (r.statusCode == 403 || _looksClosed(r.statusCode, r.data)) {
          portalClosed = true;
          throw Exception('教务门户当前关闭（夜间）');
        }
        if (r.statusCode != 200) throw Exception('HTTP ${r.statusCode}');
        final d = r.data;
        if (d is Map<String, dynamic>) return d;
        if (d is Map) return Map<String, dynamic>.from(d);
        throw Exception('非 JSON');
      } catch (e) {
        last = e;
        if (e.toString().contains('夜间') || e.toString().contains('403')) rethrow;
        if (i == 0) await Future<void>.delayed(const Duration(milliseconds: 400));
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
      total = int.tryParse('${last['totalResult'] ?? last['totalCount'] ?? items.length}') ?? items.length;
      if (items.length >= total || chunk is! List || chunk.isEmpty) break;
      page++;
    }
    last['items'] = items;
    last['totalResult'] = items.length;
    return last;
  }

  Future<Map<String, dynamic>> allGrades() => grades(xnm: '', xqm: '');

  Future<String> _getHtml(String path, {Map<String, dynamic>? params}) async {
    final r = await dio.get(
      '$kJwxt$path',
      queryParameters: params,
      options: Options(
        headers: {'Referer': '$kJwxt/jwglxt/xtgl/index_initMenu.html?jsdm=xs'},
        responseType: ResponseType.plain,
      ),
    );
    final body = r.data?.toString() ?? '';
    if (r.statusCode == 403 || _looksClosed(r.statusCode, body)) {
      portalClosed = true;
      throw Exception('教务门户当前关闭（夜间）');
    }
    if (r.statusCode != 200) throw Exception('HTTP ${r.statusCode}');
    if (body.contains('login_slogin') || body.contains('统一身份认证')) {
      throw Exception('教务登录已失效');
    }
    return body;
  }

  Future<CreditProgress> creditProgress() async {
    final html = await _getHtml(
      '/jwglxt/xsxy/xsxyqk_cxXsxyqkIndex.html',
      params: {'gnmkdm': 'N105515', 'layout': 'default', 'echarts': '1'},
    );
    if (html.contains('系统维护') || html.length < 200) {
      throw Exception('学业情况页暂不可用');
    }
    List<dynamic> items = const [];
    try {
      items = (await allGrades())['items'] as List? ?? const [];
    } catch (_) {}
    return parseCreditProgress(html, grades: items);
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
    final userName = _pick(m, const ['userName', 'username']);
    final xh = _pick(m, const ['xh', 'XH', 'xh_id', 'XH_ID', 'useraccount']);
    final xm = _pick(m, const ['xm', 'XM', 'userNameCn', 'realName', 'xm_id']);
    final userNameIsId = RegExp(r'^\d{8,}$').hasMatch(userName);
    return StudentProfile(
      studentId: xh.isNotEmpty ? xh : (userNameIsId ? userName : ''),
      name: xm.isNotEmpty ? xm : (userNameIsId ? '' : userName),
      gender: _gender(_pick(m, const ['xb', 'XB', 'xbm', 'XBM', 'xbmc'])),
      college: _pick(m, const ['xymc', 'XYMC', 'xy', 'jg_mc', 'dwmc', 'yxmc', 'YXMC']),
      major: _cleanMajor(_pick(m, const ['zymc', 'ZYMC', 'zy', 'zyh'])),
      klass: _pick(m, const ['bjmc', 'BJMC', 'bh', 'BH', 'xzb', 'bj']),
      grade: _pick(m, const ['njmc', 'NJMC', 'njdm_id', 'NJDM_ID', 'nj', 'xznj']),
      phone: _pick(m, const ['sjhm', 'SJHM', 'lxdh', 'yddh', 'phone', 'sjh']),
      campus: _pick(m, const ['xqmc', 'XQMC', 'xqh']),
      role: _role(_pick(m, const ['userType', 'usertype', 'jsdm'])),
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
    if (heading.contains('教师')) role = '教师';

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

  Future<Map<String, dynamic>> freeRooms({
    int? xnm,
    String? xqm,
    String xqj = '1,2,3,4,5',
    int zcd = 0,
    int jcd = 0,
  }) async {
    final t = currentTerm();
    return _post(
      '/jwglxt/cdjy/cdjy_cxKxcdlb.html',
      {
        'xqh_id': '',
        'xnm': '${xnm ?? t.$1}',
        'xqm': xqm ?? t.$2,
        'cdlb_id': '',
        'cdejlb_id': '',
        'qszws': '',
        'jszws': '',
        'cdmc': '',
        'cd_id': '',
        'lh': '',
        'jyfs': '0',
        'zcd': zcd,
        'xqj': xqj,
        'jcd': jcd,
        'cdjylx': '',
        'zysx': '',
        'sflb': '',
        'hbsl': '',
        'bbsl': '',
        'sfyzz': '',
        'sfjtjs': '',
        'tjsl': '',
        'tymbsl': '',
        'yczb': '',
        'zws': '',
        'sfbhkc': '',
        'kszws1': '',
      },
      params: {'doType': 'query'},
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

String _pick(Map<String, dynamic> m, List<String> keys) {
  final lower = <String, String>{};
  for (final e in m.entries) {
    final v = '${e.value}'.trim();
    if (v.isEmpty || v == 'null' || v == 'undefined') continue;
    lower[e.key.toString().toLowerCase()] = v;
  }
  for (final k in keys) {
    final v = lower[k.toLowerCase()];
    if (v != null && v.isNotEmpty) return v;
  }
  return '';
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

String _role(String v) {
  final s = v.toLowerCase();
  if (s.contains('xs') || s.contains('student') || v.contains('学生')) return '学生';
  if (s.contains('js') || s.contains('teacher') || v.contains('教师')) return '教师';
  return v;
}
