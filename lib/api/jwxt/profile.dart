part of '../jwxt.dart';

mixin JwxtProfileApi on JwxtAcademicApi {
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
