import 'dart:async';

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
  Completer<void>? _casGate;

  Future<void> init() async {
    final dir = await getApplicationSupportDirectory();
    _jar = PersistCookieJar(storage: FileStorage('${dir.path}/cookies'));
    cas = CasClient(_jar!);
    ehall = EhallClient(_jar!);
    jwxt = JwxtClient(_jar!);
    ktkq = KtkqClient(_jar!);
    gyglxt = GyglxtClient(_jar!, gateway: ktkq!.rs);
    ykt = YktClient(_jar!, gateway: ktkq!.rs);
    lantu = LantuClient(persistPath: '${dir.path}/lantu.json');
    await lantu!.restore();
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
        await jwxt!.sessionAlive();
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
      await lantu!.login(username.trim(), password);
      loggedIn = true;
      demoMode = false;
      studentId = username.trim();
      profile = lantu!.profile();
      if (profile.studentId.isNotEmpty) studentId = profile.studentId;
      displayName = profile.hasName ? profile.name : studentId;
      _kbFut = null;
      _cjCache.clear();
      _xfFut = null;
      _casGate = Completer<void>();
      unawaited(_finishLogin(username.trim(), password));
      warmupGateways();
    } catch (e) {
      error = e.toString().replaceFirst('Exception: ', '');
      loggedIn = false;
    } finally {
      busy = false;
      notifyListeners();
    }
  }

  Future<void> _finishLogin(String username, String password) async {
    try {
      await cas!.login(username, password);
    } catch (_) {}
    if (!(_casGate?.isCompleted ?? true)) _casGate!.complete();
    try {
      await jwxt!.loginWithCas(cas!);
    } catch (_) {}
    notifyListeners();
    await refreshProfile(force: true);
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
    _casGate = null;
    ktkq?.token = null;
    gyglxt?.token = null;
    notifyListeners();
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
      if (lantu != null && lantu!.isLoggedIn) {
        try {
          final raw = await lantu!.getCourse();
          List<PeriodTime> times = kDefaultPeriodTimes;
          try {
            times = parsePeriodTimes(await lantu!.getCourseTimeConfig());
          } catch (_) {}
          return {
            'kbList': lantu!.mapKbList(raw),
            'curWeek': raw['curWeek'] ?? lantu!.curWeek,
            'totalWeek': raw['totalWeek'] ?? 16,
            'times': times,
          };
        } catch (_) {}
      }
      final kb = await jwxt!.schedule();
      return {
        ...kb,
        'curWeek': kb['curWeek'] ?? 1,
        'totalWeek': kb['totalWeek'] ?? 16,
        'times': kDefaultPeriodTimes,
      };
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
    return _cjCache[key] ??= jwxt!.grades(xnm: xnm, xqm: xqm);
  }

  Future<CreditProgress> loadCredits() {
    if (demoMode) return Future.value(demoCreditProgress);
    return _xfFut ??= jwxt!.creditProgress();
  }

  Future<void> ensureKtkq() async {
    if (demoMode) return;
    if (ktkq == null) return;
    if (ktkq!.token != null && ktkq!.token!.isNotEmpty) return;
    await ktkq!.restoreToken();
    if (ktkq!.token != null && ktkq!.token!.isNotEmpty) return;
    await _waitCas();
    await ktkq!.loginWithCas(cas!);
  }

  Future<void> ensureGyglxt() async {
    if (demoMode) return;
    if (gyglxt == null) return;
    if (gyglxt!.token != null && gyglxt!.token!.isNotEmpty) return;
    await gyglxt!.restoreToken();
    if (gyglxt!.token != null && gyglxt!.token!.isNotEmpty) return;
    await _waitCas();
    await gyglxt!.loginWithCas(cas!);
  }
}
