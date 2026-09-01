import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:cookie_jar/cookie_jar.dart';
import 'package:flutter/foundation.dart';
import 'package:path_provider/path_provider.dart';

import '../api/cas.dart';
import '../api/locate.dart';
import '../api/ehall.dart';
import '../api/gyglxt.dart';
import '../api/jwxt.dart';
import '../api/ktkq.dart';
import '../api/lantu.dart';
import '../api/ykt.dart';
import '../demo/demo_data.dart';
import '../models/credit.dart';
import '../models/lesson.dart';
import '../models/profile.dart';
import '../models/term.dart';

class Session extends ChangeNotifier {
  PersistCookieJar? _jar;
  CasClient? cas;
  EhallClient? ehall;
  JwxtClient? jwxt;
  KtkqClient? ktkq;
  GyglxtClient? gyglxt;
  YktClient? ykt;
  LantuClient? lantu;

  bool ready = false;
  bool loggedIn = false;
  bool demoMode = false;
  bool busy = false;
  bool profileLoading = false;
  String? error;
  String? profileError;
  String displayName = '同学';
  String studentId = '';
  StudentProfile profile = const StudentProfile();

  DateTime? _profileAt;
  bool _profileBusy = false;
  Future<Map<String, dynamic>>? _kbFut;
  final _cjCache = <String, Future<Map<String, dynamic>>>{};
  Future<CreditProgress>? _xfFut;
  Future<Map<String, dynamic>>? _ksFut;
  Completer<void>? _casGate;
  String? _teacherPath;
  final _teacherSlot = <String, String>{};
  final _teacherName = <String, String>{};

  Future<void> init() async {
    final dir = await getApplicationSupportDirectory();
    _jar = PersistCookieJar(storage: FileStorage('${dir.path}/cookies'), ignoreExpires: true);
    cas = CasClient(_jar!);
    ehall = EhallClient(_jar!);
    jwxt = JwxtClient(_jar!)..attachCas(cas!);
    ktkq = KtkqClient(_jar!);
    gyglxt = GyglxtClient(_jar!, gateway: ktkq!.rs);
    ykt = YktClient(_jar!, gateway: ktkq!.rs);
    lantu = LantuClient(persistPath: '${dir.path}/lantu.json');
    _teacherPath = '${dir.path}/kb_teachers.json';
    await lantu!.restore();
    await _loadTeachers();
    await ktkq!.restoreToken();
    await gyglxt!.restoreToken();
    loggedIn = lantu!.isLoggedIn || await cas!.hasTgt();
    if (lantu!.isLoggedIn) {
      profile = lantu!.profile();
      if (profile.studentId.isNotEmpty) studentId = profile.studentId;
      if (profile.hasName) displayName = profile.name;
    }
    ready = true;
    notifyListeners();
    if (loggedIn) {
      warmupGateways();
      unawaited(() async {
        try {
          if (await cas!.tgtAlive()) {
            await jwxt!.ensureSession();
          }
        } catch (e) {
          debugPrint('[jwxt] restore $e');
        }
        notifyListeners();
        await refreshProfile();
      }());
    }
  }

  Future<void> login(String username, String password) async {
    busy = true;
    error = null;
    notifyListeners();
    try {
      final user = username.trim();
      await lantu!.login(user, password);
      _casGate = Completer<void>();
      try {
        await cas!.login(user, password);
      } catch (e) {
        await lantu!.clear();
        throw Exception('统一身份登录失败，请重试');
      } finally {
        if (!(_casGate?.isCompleted ?? true)) _casGate!.complete();
      }
      loggedIn = true;
      demoMode = false;
      studentId = user;
      profile = lantu!.profile();
      if (profile.studentId.isNotEmpty) studentId = profile.studentId;
      displayName = profile.hasName ? profile.name : studentId;
      _kbFut = null;
      _cjCache.clear();
      _xfFut = null;
      _ksFut = null;
      _teacherSlot.clear();
      _teacherName.clear();
      await _loadTeachers();
      jwxt!.attachCas(cas!);
      unawaited(() async {
        try {
          await jwxt!.ensureSession(force: true);
        } catch (e) {
          debugPrint('[jwxt] $e');
        }
        notifyListeners();
        await refreshProfile(force: true);
      }());
      warmupGateways();
    } catch (e) {
      error = e.toString().replaceFirst('Exception: ', '');
      loggedIn = false;
    } finally {
      busy = false;
      notifyListeners();
    }
  }

  void warmupGateways() {
    final rs = ktkq?.rs;
    if (rs == null || demoMode) return;
    unawaited(AppLocator.warmup());
    unawaited(() async {
      try {
        await rs.warmupAll(const [
          'https://ktkq.swun.edu.cn/',
          'https://gyglxt.swun.edu.cn/',
          'https://ykth5.swun.edu.cn/',
        ]);
      } catch (e) {
        debugPrint('[rs] warmup $e');
      }
    }());
  }

  Future<void> _waitCas() async {
    if (cas != null && await cas!.hasTgt()) return;
    final g = _casGate;
    if (g != null) {
      try {
        await g.future.timeout(const Duration(seconds: 20));
      } catch (_) {}
      if (cas != null && await cas!.hasTgt()) return;
    }
    throw Exception('尚未登录统一身份');
  }

  void enterDemo() {
    demoMode = true;
    loggedIn = true;
    displayName = '${demoProfile['name']}';
    studentId = '${demoProfile['studentId']}';
    profile = StudentProfile(
      studentId: studentId,
      name: displayName,
      gender: '${demoProfile['gender']}',
      college: '${demoProfile['college']}',
      major: '${demoProfile['major']}',
      klass: '${demoProfile['klass']}',
      grade: '${demoProfile['grade']}',
      phone: '${demoProfile['phone']}',
      campus: '${demoProfile['campus']}',
      role: '${demoProfile['role']}',
    );
    profileLoading = false;
    profileError = null;
    error = null;
    notifyListeners();
  }

  Future<void> refreshProfile({bool force = false}) async {
    if (demoMode) {
      enterDemo();
      return;
    }
    if (!loggedIn) return;
    if (_profileBusy) return;
    if (!force &&
        _profileAt != null &&
        DateTime.now().difference(_profileAt!) < const Duration(minutes: 10) &&
        (profile.hasName || profile.hasDetails)) {
      return;
    }
    _profileBusy = true;
    profileLoading = true;
    profileError = null;
    notifyListeners();
    try {
      var next = StudentProfile(studentId: studentId);
      if (lantu != null && lantu!.isLoggedIn) {
        next = next.merge(lantu!.profile());
        try {
          final info = await lantu!.getUserInfo();
          if (info['userBaseInfo'] is Map) {
            lantu!.userBaseInfo = Map<String, dynamic>.from(info['userBaseInfo'] as Map);
            next = next.merge(lantu!.profile());
          }
        } catch (_) {}
      }
      if (!next.hasName) {
        try {
          next = next.merge(await ehall!.profile(cas!));
        } catch (e) {
          profileError = e.toString().replaceFirst('Exception: ', '');
        }
      }
      profile = next;
      if (profile.studentId.isNotEmpty) studentId = profile.studentId;
      if (profile.hasName) {
        displayName = profile.name;
      } else if (studentId.isNotEmpty) {
        displayName = studentId;
      }
      if (!profile.hasName && !profile.hasDetails) {
        profileError ??= '未能读取个人信息';
      } else {
        profileError = null;
      }
      _profileAt = DateTime.now();
    } finally {
      _profileBusy = false;
      profileLoading = false;
      notifyListeners();
    }
  }

  Future<void> logout() async {
    await ktkq?.dispose();
    await lantu?.clear();
    await _jar?.deleteAll();
    loggedIn = false;
    demoMode = false;
    error = null;
    profileError = null;
    profileLoading = false;
    displayName = '同学';
    studentId = '';
    profile = const StudentProfile();
    _profileAt = null;
    _kbFut = null;
    _cjCache.clear();
    _xfFut = null;
    _ksFut = null;
    _teacherSlot.clear();
    _teacherName.clear();
    _casGate = null;
    ktkq?.token = null;
    gyglxt?.token = null;
    notifyListeners();
  }

  Future<void> ensureJwxt() async {
    if (demoMode) return;
    jwxt?.attachCas(cas!);
    if (cas != null && await cas!.tgtAlive()) {
      await jwxt!.ensureSession().timeout(const Duration(seconds: 25));
      return;
    }
    await _waitCas();
    if (cas != null && await cas!.tgtAlive()) {
      await jwxt!.ensureSession().timeout(const Duration(seconds: 25));
      return;
    }
    throw Exception('统一身份已过期，请重新登录');
  }

  void invalidateSchedule() => _kbFut = null;

  String _normKc(Object? s) => '$s'.replaceAll(RegExp(r'\s+'), '');

  String _startOf(Map<String, dynamic> m) {
    final sk = '${m['skjc'] ?? ''}';
    if (sk.isNotEmpty && sk != 'null') return sk;
    final jcs = '${m['jcs'] ?? ''}';
    return jcs.split(RegExp(r'[-~]')).first;
  }

  Future<void> _loadTeachers() async {
    final path = _teacherPath;
    if (path == null) return;
    try {
      final f = File(path);
      if (!await f.exists()) return;
      final raw = jsonDecode(await f.readAsString());
      if (raw is! Map) return;
      final xh = '${raw['xh'] ?? ''}';
      if (xh.isNotEmpty && studentId.isNotEmpty && xh != studentId) return;
      final slot = raw['slot'];
      final name = raw['name'];
      if (slot is Map) {
        slot.forEach((k, v) {
          final s = '$v'.trim();
          if (s.isNotEmpty) _teacherSlot['$k'] = s;
        });
      }
      if (name is Map) {
        name.forEach((k, v) {
          final s = '$v'.trim();
          if (s.isNotEmpty) _teacherName['$k'] = s;
        });
      }
    } catch (_) {}
  }

  Future<void> _saveTeachers() async {
    final path = _teacherPath;
    if (path == null) return;
    try {
      await File(path).writeAsString(jsonEncode({
        'xh': studentId,
        'slot': _teacherSlot,
        'name': _teacherName,
      }));
    } catch (_) {}
  }

  void _applyKbTeachers(List<Map<String, dynamic>> kb) {
    for (final row in kb) {
      final cur = '${row['xm'] ?? ''}'.trim();
      if (cur.isNotEmpty && cur != '—') continue;
      final name = _normKc(row['kcmc']);
      row['xm'] = _teacherSlot['$name|${row['xqj'] ?? row['skxq']}|${_startOf(row)}'] ?? _teacherName[name] ?? '';
    }
  }

  void _fillKbTeachers(List<Map<String, dynamic>> kb, Object? jwList) {
    if (jwList is List) {
      for (final e in jwList) {
        if (e is! Map) continue;
        final m = Map<String, dynamic>.from(e);
        final xm = '${m['xm'] ?? m['jsxm'] ?? ''}'.trim();
        if (xm.isEmpty || xm == '—') continue;
        final name = _normKc(m['kcmc']);
        _teacherSlot['$name|${m['xqj']}|${_startOf(m)}'] = xm;
        _teacherName.putIfAbsent(name, () => xm);
      }
      unawaited(_saveTeachers());
    }
    _applyKbTeachers(kb);
  }

  Future<Map<String, dynamic>> loadSchedule() {
    if (demoMode) {
      return Future.value({
        'kbList': demoSchedule,
        'curWeek': 1,
        'totalWeek': 16,
        'times': kDefaultPeriodTimes,
      });
    }
    return _kbFut ??= () async {
      try {
        if (lantu == null || !lantu!.isLoggedIn) {
          throw Exception('请重新登录后再看课表');
        }
        final raw = await lantu!.getCourse().timeout(const Duration(seconds: 12));
        const times = kDefaultPeriodTimes;
        final kb = lantu!.mapKbList(raw);
        _applyKbTeachers(kb);
        try {
          final jw = await jwxt!.schedule().timeout(const Duration(seconds: 6));
          _fillKbTeachers(kb, jw['kbList']);
        } catch (_) {}
        debugPrint('[kb] lantu ${kb.length} lessons week=${raw['curWeek']}');
        return {
          'kbList': kb,
          'curWeek': raw['curWeek'] ?? lantu!.curWeek,
          'totalWeek': raw['totalWeek'] ?? 16,
          'times': times,
        };
      } catch (e) {
        debugPrint('[kb] $e');
        _kbFut = null;
        rethrow;
      }
    }();
  }

  Future<Map<String, dynamic>> loadGrades({String xnm = '', String xqm = ''}) {
    if (demoMode) {
      return Future.value({
        'items': [
          for (final e in demoGrades)
            if (matchesTerm(e, SchoolTerm(xnm: xnm, xqm: xqm))) e,
        ],
      });
    }
    final key = '$xnm|$xqm';
    return _cjCache[key] ??= () async {
      try {
        await ensureJwxt().timeout(const Duration(seconds: 20));
        return await jwxt!.grades(xnm: xnm, xqm: xqm).timeout(const Duration(seconds: 25));
      } catch (e) {
        _cjCache.remove(key);
        rethrow;
      }
    }();
  }

  void invalidateCredits() => _xfFut = null;

  Future<CreditProgress> loadCredits() {
    if (demoMode) return Future.value(demoCreditProgress);
    return _xfFut ??= () async {
      try {
        await ensureJwxt().timeout(const Duration(seconds: 20));
        return await jwxt!.creditProgress().timeout(const Duration(seconds: 45));
      } catch (e) {
        _xfFut = null;
        rethrow;
      }
    }();
  }

  Future<Map<String, dynamic>> loadExams() {
    if (demoMode) return Future.value({'items': demoExams});
    return _ksFut ??= () async {
      try {
        await ensureJwxt();
        return await jwxt!.exams();
      } catch (e) {
        _ksFut = null;
        rethrow;
      }
    }();
  }

  Future<void> ensureKtkq() async {
    if (demoMode) return;
    if (ktkq == null) return;
    await ktkq!.restoreToken();
    if (ktkq!.token != null && ktkq!.token!.isNotEmpty) {
      try {
        final info = await ktkq!.userInfo().timeout(const Duration(seconds: 8));
        final code = info['code'];
        if (code == 200 || code == 0) return;
      } catch (_) {}
      ktkq!.token = null;
    }
    await _waitCas();
    await ktkq!.loginWithCas(cas!).timeout(const Duration(seconds: 20));
  }

  Future<void> ensureGyglxt() async {
    if (demoMode) return;
    if (gyglxt == null) return;
    if (studentId.isNotEmpty) {
      gyglxt!.username ??= studentId;
    }
    if (gyglxt!.token == null || gyglxt!.token!.isEmpty) {
      await gyglxt!.restoreToken();
    }
    if (gyglxt!.token == null || gyglxt!.token!.isEmpty) {
      await _waitCas();
      await gyglxt!.loginWithCas(cas!).timeout(const Duration(seconds: 20));
    }
    if ((gyglxt!.username == null || gyglxt!.username!.isEmpty) && studentId.isNotEmpty) {
      gyglxt!.username = studentId;
    }
  }
}
