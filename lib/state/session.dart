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
import '../api/zhcgm.dart';
import '../demo/demo_data.dart';
import '../models/credit.dart';
import '../models/lesson.dart';
import '../models/profile.dart';
import '../models/term.dart';
import 'accounts.dart';
import 'rooms.dart';

class Session extends ChangeNotifier {
  PersistCookieJar? _jar;
  CasClient? cas;
  EhallClient? ehall;
  JwxtClient? jwxt;
  KtkqClient? ktkq;
  GyglxtClient? gyglxt;
  YktClient? ykt;
  LantuClient? lantu;
  ZhcgmClient? zhcgm;
  final accounts = AccountStore();
  final rooms = RoomStore();
  Directory? _support;

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
  Future<Map<String, dynamic>>? _xkEntryFut;
  Map<String, Future<List<dynamic>>> _xkCourses = {};
  Completer<void>? _casGate;
  Future<void>? _ensuringKtkq;
  String? _teacherPath;
  final _teacherSlot = <String, String>{};
  final _teacherName = <String, String>{};

  Future<void> init() async {
    _support = await getApplicationSupportDirectory();
    await accounts.load(_support!);
    final current = accounts.currentId;
    if (current != null && current.isNotEmpty) {
      await _attachAccount(current, restore: true);
      loggedIn = lantu!.isLoggedIn || await cas!.hasTgt();
      if (loggedIn) _applyIdentity();
    } else {
      await _attachAccount('_scratch', restore: false);
    }
    ready = true;
    notifyListeners();
    if (loggedIn) {
      warmupGateways();
      unawaited(refreshProfile());
    }
  }

  Future<void> _attachAccount(String id, {required bool restore}) async {
    final support = _support ?? await getApplicationSupportDirectory();
    _support = support;
    await ktkq?.dispose();
    if (id != '_scratch') studentId = id;
    final dir = id == '_scratch'
        ? Directory('${support.path}/.scratch')
        : await accounts.ensureDir(id);
    if (!await dir.exists()) await dir.create(recursive: true);
    _jar = PersistCookieJar(
      storage: FileStorage('${dir.path}/cookies'),
      ignoreExpires: true,
    );
    cas = CasClient(_jar!);
    ehall = EhallClient(_jar!);
    jwxt = JwxtClient(_jar!)..attachCas(cas!);
    ktkq = KtkqClient(_jar!);
    gyglxt = GyglxtClient(_jar!, gateway: ktkq!.rs);
    ykt = YktClient(_jar!, gateway: ktkq!.rs);
    lantu = LantuClient(persistPath: '${dir.path}/lantu.json');
    zhcgm = ZhcgmClient(persistPath: '${dir.path}/zhcgm.json');
    _teacherPath = '${dir.path}/kb_teachers.json';
    await rooms.bind('${dir.path}/rooms.json');
    _clearCaches();
    ktkq?.token = null;
    gyglxt?.token = null;
    zhcgm?.token = null;
    if (!restore) {
      lantu!.token = '';
      return;
    }
    await lantu!.restore();
    await zhcgm!.restore();
    await _loadTeachers();
    await ktkq!.restoreToken();
    await gyglxt!.restoreToken();
  }

  void _clearCaches() {
    _kbFut = null;
    _cjCache.clear();
    _xfFut = null;
    _ksFut = null;
    _xkEntryFut = null;
    _xkCourses = {};
    _teacherSlot.clear();
    _teacherName.clear();
    _profileAt = null;
    _casGate = null;
    profileError = null;
    profileLoading = false;
  }

  void _applyIdentity() {
    if (lantu != null && lantu!.isLoggedIn) {
      profile = lantu!.profile();
      if (profile.studentId.isNotEmpty) studentId = profile.studentId;
      if (profile.hasName) displayName = profile.name;
    }
    if (studentId.isEmpty) studentId = accounts.currentId ?? '';
    if (displayName.isEmpty || displayName == '同学') {
      displayName =
          accounts.current?.label ?? (studentId.isEmpty ? '同学' : studentId);
    }
    gyglxt?.username = studentId.isEmpty ? null : studentId;
  }

  Future<void> _restorePrevious(String? prev) async {
    if (prev == null || prev.isEmpty || prev == '_scratch') return;
    try {
      await _attachAccount(prev, restore: true);
      loggedIn = lantu!.isLoggedIn || await cas!.hasTgt();
      if (loggedIn) {
        _applyIdentity();
        warmupGateways();
      }
    } catch (e) {
      debugPrint('[account] restore $e');
    }
  }

  Future<void> login(
    String username,
    String password, {
    bool attach = true,
    String? restoreId,
  }) async {
    busy = true;
    error = null;
    notifyListeners();
    final prev = restoreId ?? accounts.currentId;
    final prevLoggedIn = restoreId != null || (loggedIn && !demoMode);
    try {
      final user = username.trim();
      if (user.isEmpty || password.isEmpty) {
        throw Exception('请输入学号和密码');
      }
      demoMode = false;
      if (attach) {
        await _attachAccount(user, restore: false);
        await _jar?.deleteAll();
      }
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
      studentId = user;
      profile = lantu!.profile();
      if (profile.studentId.isNotEmpty) studentId = profile.studentId;
      displayName = profile.hasName ? profile.name : studentId;
      _clearCaches();
      await _loadTeachers();
      jwxt!.attachCas(cas!);
      await accounts.upsert(
        id: studentId,
        name: displayName,
        password: password,
      );
      gyglxt?.username = studentId;
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
      if (prevLoggedIn) await _restorePrevious(prev);
    } finally {
      busy = false;
      notifyListeners();
    }
  }

  Future<bool> switchTo(String id) async {
    final sid = id.trim();
    if (sid.isEmpty) return false;
    if (demoMode) {
      error = '预览模式不能切换账号';
      notifyListeners();
      return false;
    }
    if (sid == accounts.currentId && loggedIn) return true;
    busy = true;
    error = null;
    notifyListeners();
    final prev = accounts.currentId;
    final wasIn = loggedIn && !demoMode;
    try {
      await _attachAccount(sid, restore: true);
      loggedIn = lantu!.isLoggedIn || await cas!.hasTgt();
      if (loggedIn) {
        await accounts.setCurrent(sid);
        studentId = sid;
        _applyIdentity();
        warmupGateways();
        unawaited(refreshProfile(force: true));
        return true;
      }
      final pwd = await accounts.passwordOf(sid);
      if (pwd != null && pwd.isNotEmpty) {
        await _jar?.deleteAll();
        await login(sid, pwd, attach: false, restoreId: wasIn ? prev : null);
        return loggedIn && (studentId == sid || accounts.currentId == sid);
      }
      studentId = sid;
      error = '请输入该账号的密码';
      return false;
    } catch (e) {
      error = e.toString().replaceFirst('Exception: ', '');
      loggedIn = false;
      if (wasIn) await _restorePrevious(prev);
      return false;
    } finally {
      busy = false;
      notifyListeners();
    }
  }

  Future<void> rememberRoom({
    required String room,
    required double latitude,
    required double longitude,
    double accuracy = 0,
    String course = '',
  }) async {
    if (demoMode) return;
    await rooms.record(
      room: room,
      latitude: latitude,
      longitude: longitude,
      accuracy: accuracy,
      course: course,
    );
    notifyListeners();
  }

  Future<void> forgetRoom(RoomFix room) async {
    await rooms.remove(room.id);
    notifyListeners();
  }

  Future<void> removeAccount(String id) async {
    final sid = id.trim();
    if (sid.isEmpty) return;
    final wasCurrent = sid == accounts.currentId || sid == studentId;
    if (wasCurrent) {
      await logout(forget: false);
    }
    await accounts.remove(sid);
    if (wasCurrent) {
      final next = accounts.currentId;
      if (next != null) {
        await switchTo(next);
      } else {
        await _attachAccount('_scratch', restore: false);
      }
    }
    notifyListeners();
  }

  void warmupGateways() {
    if (demoMode) return;
    unawaited(AppLocator.warmup());
    unawaited(() async {
      await Future<void>.delayed(const Duration(seconds: 6));
      if (!loggedIn || demoMode) return;
      final rs = ktkq?.rs;
      if (rs == null) return;
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
            lantu!.userBaseInfo = Map<String, dynamic>.from(
              info['userBaseInfo'] as Map,
            );
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
      if (next.klass.isEmpty || StudentProfile.looksLikeCode(next.klass)) {
        try {
          await ensureJwxt();
          next = next.merge(await jwxt!.profile());
        } catch (_) {}
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

  Future<void> logout({bool forget = false}) async {
    final id = studentId.isEmpty ? accounts.currentId : studentId;
    await ktkq?.dispose();
    await lantu?.clear();
    await _jar?.deleteAll();
    loggedIn = false;
    demoMode = false;
    error = null;
    profileError = null;
    profileLoading = false;
    displayName = '同学';
    profile = const StudentProfile();
    _clearCaches();
    ktkq?.token = null;
    gyglxt?.token = null;
    await zhcgm?.clear();
    if (forget && id != null && id.isNotEmpty) {
      await accounts.remove(id);
      studentId = '';
      await _attachAccount('_scratch', restore: false);
    } else {
      studentId = id ?? '';
    }
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

  void invalidateGrades() => _cjCache.clear();

  void invalidateExams() => _ksFut = null;

  void invalidateSelection() {
    _xkEntryFut = null;
    _xkCourses = {};
  }

  /// 选课入口：轮次 + 学生画像（加密串 xkkz_xh 在轮次里）。
  Future<Map<String, dynamic>> loadSelectionEntry({bool force = false}) {
    if (demoMode) {
      return Future.value({
        'rounds': demoXkRounds,
        'profile': demoXkProfile,
      });
    }
    if (force) _xkEntryFut = null;
    final hit = _xkEntryFut;
    if (hit != null) return hit;
    late final Future<Map<String, dynamic>> fut;
    fut = () async {
      try {
        await ensureJwxt().timeout(const Duration(seconds: 25));
        final entry = await jwxt!.selectionEntry();
        if ((entry['rounds'] as List?)?.isEmpty ?? true) {
          throw Exception('当前没有开放的选课轮次');
        }
        return entry;
      } catch (e) {
        if (identical(_xkEntryFut, fut)) _xkEntryFut = null;
        rethrow;
      }
    }();
    _xkEntryFut = fut;
    return fut;
  }

  /// 某轮次的可选课程（含教学班行）。加密串会过期，失败自动刷一次入口。
  Future<List<dynamic>> loadSelectionCourses(
    Map<String, dynamic> round,
    Map<String, String> profile, {
    String keyword = '',
  }) {
    final key = '${round['xkkz_id']}|$keyword';
    final hit = _xkCourses[key];
    if (hit != null) return hit;
    late final Future<List<dynamic>> fut;
    fut = () async {
      if (demoMode) {
        return [
          for (final c in demoXkCourses)
            if (keyword.isEmpty || '${c['kcmc']}'.contains(keyword)) c,
        ];
      }
      try {
        await ensureJwxt().timeout(const Duration(seconds: 25));
        final query = buildXkQuery(round: round, profile: profile, kcmc: keyword);
        return await jwxt!.selectionCourses(query);
      } catch (e) {
        if (identical(_xkCourses[key], fut)) _xkCourses.remove(key);
        rethrow;
      }
    }();
    _xkCourses[key] = fut;
    return fut;
  }

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
      await File(path).writeAsString(
        jsonEncode({
          'xh': studentId,
          'slot': _teacherSlot,
          'name': _teacherName,
        }),
      );
    } catch (_) {}
  }

  void _applyKbTeachers(List<Map<String, dynamic>> kb) {
    for (final row in kb) {
      final cur = '${row['xm'] ?? ''}'.trim();
      if (cur.isNotEmpty && cur != '—') continue;
      final name = _normKc(row['kcmc']);
      row['xm'] =
          _teacherSlot['$name|${row['xqj'] ?? row['skxq']}|${_startOf(row)}'] ??
          _teacherName[name] ??
          '';
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

  Future<Map<String, dynamic>> loadSchedule({bool force = false}) {
    if (demoMode) {
      return Future.value({
        'kbList': demoSchedule,
        'curWeek': 1,
        'totalWeek': 16,
        'times': kDefaultPeriodTimes,
      });
    }
    if (force) _kbFut = null;
    final hit = _kbFut;
    if (hit != null) return hit;
    late final Future<Map<String, dynamic>> fut;
    fut = () async {
      try {
        if (lantu == null || !lantu!.isLoggedIn) {
          throw Exception('请重新登录后再看课表');
        }
        final raw = await lantu!.getCourse().timeout(
          const Duration(seconds: 12),
        );
        const times = kDefaultPeriodTimes;
        final kb = lantu!.mapKbList(raw);
        _applyKbTeachers(kb);
        unawaited(() async {
          try {
            final jw = await jwxt!.schedule().timeout(
              const Duration(seconds: 8),
            );
            _fillKbTeachers(kb, jw['kbList']);
            notifyListeners();
          } catch (_) {}
        }());
        debugPrint('[kb] lantu ${kb.length} lessons week=${raw['curWeek']}');
        return {
          'kbList': kb,
          'curWeek': raw['curWeek'] ?? lantu!.curWeek,
          'totalWeek': raw['totalWeek'] ?? 16,
          'times': times,
        };
      } catch (e) {
        debugPrint('[kb] $e');
        if (identical(_kbFut, fut)) _kbFut = null;
        rethrow;
      }
    }();
    _kbFut = fut;
    return fut;
  }

  Future<Map<String, dynamic>> loadGrades({
    String xnm = '',
    String xqm = '',
    bool force = false,
  }) {
    if (demoMode) {
      return Future.value({
        'items': [
          for (final e in demoGrades)
            if (matchesTerm(e, SchoolTerm(xnm: xnm, xqm: xqm))) e,
        ],
      });
    }
    final key = '$xnm|$xqm';
    if (force) _cjCache.remove(key);
    final hit = _cjCache[key];
    if (hit != null) return hit;
    late final Future<Map<String, dynamic>> fut;
    fut = () async {
      try {
        await ensureJwxt().timeout(const Duration(seconds: 20));
        return await jwxt!
            .grades(xnm: xnm, xqm: xqm)
            .timeout(const Duration(seconds: 25));
      } catch (e) {
        if (identical(_cjCache[key], fut)) _cjCache.remove(key);
        rethrow;
      }
    }();
    _cjCache[key] = fut;
    return fut;
  }

  void invalidateCredits() => _xfFut = null;

  Future<CreditProgress> loadCredits({bool force = false}) {
    if (demoMode) return Future.value(demoCreditProgress);
    if (force) _xfFut = null;
    final hit = _xfFut;
    if (hit != null) return hit;
    late final Future<CreditProgress> fut;
    fut = () async {
      try {
        await ensureJwxt().timeout(const Duration(seconds: 20));
        return await jwxt!.creditProgress().timeout(
          const Duration(seconds: 45),
        );
      } catch (e) {
        if (identical(_xfFut, fut)) _xfFut = null;
        rethrow;
      }
    }();
    _xfFut = fut;
    return fut;
  }

  Future<Map<String, dynamic>> loadExams({bool force = false}) {
    if (demoMode) return Future.value({'items': demoExams});
    if (force) _ksFut = null;
    final hit = _ksFut;
    if (hit != null) return hit;
    late final Future<Map<String, dynamic>> fut;
    fut = () async {
      try {
        await ensureJwxt();
        return await jwxt!.exams();
      } catch (e) {
        if (identical(_ksFut, fut)) _ksFut = null;
        rethrow;
      }
    }();
    _ksFut = fut;
    return fut;
  }

  Future<void> ensureZhcgm() async {
    if (demoMode) return;
    if (zhcgm == null) return;
    await _waitCas();
    await zhcgm!.ensure(cas!).timeout(const Duration(seconds: 25));
  }

  Future<void> ensureKtkq() async {
    if (demoMode) return;
    if (ktkq == null) return;
    while (_ensuringKtkq != null) {
      try {
        await _ensuringKtkq!.timeout(const Duration(seconds: 50));
      } catch (_) {}
      if (ktkq!.token != null && ktkq!.token!.isNotEmpty) return;
    }
    final done = _ensureKtkqBody();
    _ensuringKtkq = done;
    try {
      await done;
    } finally {
      if (identical(_ensuringKtkq, done)) _ensuringKtkq = null;
    }
  }

  Future<void> _ensureKtkqBody() async {
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
    await ktkq!.loginWithCas(cas!).timeout(const Duration(seconds: 45));
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
    if ((gyglxt!.username == null || gyglxt!.username!.isEmpty) &&
        studentId.isNotEmpty) {
      gyglxt!.username = studentId;
    }
  }
}
