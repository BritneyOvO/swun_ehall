import 'package:cookie_jar/cookie_jar.dart';
import 'package:dio/dio.dart';

import '../models/profile.dart';
import 'cas.dart';
import 'httpx.dart';

class EhallClient {
  EhallClient(this.jar) : dio = buildDio(jar);

  final CookieJar jar;
  final Dio dio;

  Options get _xhr => Options(
        headers: {
          'X-Requested-With': 'XMLHttpRequest',
          'Accept': 'application/json',
          'Referer': '$kEhall/index.html',
        },
      );

  Future<Map<String, dynamic>?> loginUser() async {
    final r = await dio.get('$kEhall/getLoginUser', options: _xhr);
    final raw = r.data;
    Map<String, dynamic>? m;
    if (raw is Map) m = Map<String, dynamic>.from(raw);
    if (m == null) return null;
    if ('${m['errcode']}' != '0') return null;
    final data = m['data'];
    if (data is Map && '${data['userAccount'] ?? ''}'.isNotEmpty) {
      return Map<String, dynamic>.from(data);
    }
    return null;
  }

  Future<Map<String, dynamic>> _ensureUser(CasClient cas) async {
    final hit = await loginUser();
    if (hit != null) return hit;
    var url = await cas.ticketFor(kEhallService);
    for (var i = 0; i < 12; i++) {
      final r = await dio.get(url);
      if (r.statusCode == 200) break;
      if (!isRedirect(r) || loc(r).isEmpty) break;
      url = absUrl(url, loc(r));
    }
    final again = await loginUser();
    if (again == null) throw Exception('办事大厅未登录');
    return again;
  }

  Future<StudentProfile> profile(CasClient cas) async {
    final d = await _ensureUser(cas);
    final dept = '${d['deptName'] ?? ''}';
    String college = '';
    String grade = '${d['enterSchoolDate'] ?? ''}';
    for (final p in dept.split('/')) {
      if (p.contains('学院') && college.isEmpty) college = p;
      if (RegExp(r'^\d{4}$').hasMatch(p) && grade.isEmpty) grade = p;
    }
    final cat = '${d['categoryName'] ?? ''}';
    var role = '';
    if (cat.contains('学生')) role = '学生';
    if (cat.contains('教师') || cat.contains('老师')) role = '教师';
    final sex = '${d['sexCode'] ?? ''}';
    var gender = '';
    if (sex == '1' || sex == '男') gender = '男';
    if (sex == '2' || sex == '女') gender = '女';
    var avatar = '${d['userIcon'] ?? d['headImageIcon'] ?? d['defaultUserAvatar'] ?? ''}';
    if (avatar.startsWith('http://gateway.swun.edu.cn')) {
      avatar = avatar.replaceFirst('http://', 'https://');
    }
    return StudentProfile(
      studentId: '${d['userAccount'] ?? ''}',
      name: '${d['userName'] ?? ''}',
      gender: gender,
      college: college,
      grade: grade,
      phone: '${d['phone'] ?? ''}',
      role: role,
      avatar: avatar,
    );
  }
}
