import 'dart:async';
import 'dart:io';

import 'package:cookie_jar/cookie_jar.dart';
import 'package:flutter/foundation.dart';
import 'package:path_provider/path_provider.dart';

import '../api/bugly.dart';
import '../api/cas.dart';
import '../api/locate.dart';
import '../api/ehall.dart';
import '../api/gyglxt.dart';
import '../api/jwxt.dart';
import '../api/ktkq.dart';
import '../api/lantu.dart';
import '../api/room_sync.dart';
import '../api/ykt.dart';
import '../api/zhcgm.dart';
import '../demo/demo_data.dart';
import '../models/credit.dart';
import '../models/profile.dart';
import 'academic_store.dart';
import 'accounts.dart';
import 'rooms.dart';
import 'schedule_store.dart';
import 'selection_store.dart';

/// 账号会话：登录 / CAS / 各站点客户端。课表、教务、选课缓存见三个 Store。
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
  late final schedule = ScheduleStore(onChange: notifyListeners);
  final academic = AcademicStore();
  final selection = SelectionStore();
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
  Completer<void>? _casGate;
  Future<void>? _casRelogin;
  Future<void>? _ensuringKtkq;

  Future<void> init() async {
    _support = await getApplicationSupportDirectory();
    await accounts.load(_support!);
    final current = accounts.currentId;
    if (current != null && current.isNotEmpty) {
      await _attachAccount(current, restore: true);
      loggedIn = lantu!.isLoggedIn || await cas!.hasTgt();
      if (loggedIn) {
        _applyIdentity();
        _wireStores();
      }
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
    await rooms.bind('${dir.path}/rooms.json');
    _clearCaches();
    _wireStores(teacherPath: '${dir.path}/kb_teachers.json');
    ktkq?.token = null;
    gyglxt?.token = null;
    zhcgm?.token = null;
    if (!restore) {
      lantu!.token = '';
      return;
    }
    await lantu!.restore();
    await zhcgm!.restore();
    await schedule.loadTeachers();
    await ktkq!.restoreToken();
    await gyglxt!.restoreToken();
  }

  void _wireStores({String? teacherPath}) {
    schedule.demoMode = demoMode;
    schedule.lantu = lantu;
    schedule.jwxt = jwxt;
    schedule.studentId = studentId;
    if (teacherPath != null) schedule.teacherPath = teacherPath;
    academic.demoMode = demoMode;
    academic.jwxt = jwxt;
    academic.ensureJwxt = ensureJwxt;
    selection.demoMode = demoMode;
    selection.jwxt = jwxt;
    selection.ensureJwxt = ensureJwxt;
  }

  void _clearCaches() {
    schedule.clear();
    academic.clear();
    selection.clear();
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
    unawaited(Bugly.setUserId(studentId));
  }

  Future<void> _restorePrevious(String? prev) async {
    if (prev == null || prev.isEmpty || prev == '_scratch') return;
    try {
      await _attachAccount(prev, restore: true);
      loggedIn = lantu!.isLoggedIn || await cas!.hasTgt();
      if (loggedIn) {
        _applyIdentity();
        _wireStores();
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
      await schedule.loadTeachers();
      jwxt!.attachCas(cas!);
      await accounts.upsert(
        id: studentId,
        name: displayName,
        password: password,
      );
      gyglxt?.username = studentId;
      _wireStores();
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
        _wireStores();
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
    final fix = await rooms.record(
      room: room,
      latitude: latitude,
      longitude: longitude,
      accuracy: accuracy,
      course: course,
    );
    notifyListeners();
    if (fix != null) {
      unawaited(pushRoomFix(fix));
    }
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
        await g.future.timeout(const Duration(seconds: 45));
      } catch (_) {}
      if (cas != null && await cas!.hasTgt()) return;
    }
    await _reloginCas();
    if (cas != null && await cas!.hasTgt()) return;
    throw Exception('统一身份已过期，请重新登录');
  }

  Future<void> _reloginCas() async {
    if (demoMode || cas == null) return;
    while (_casRelogin != null) {
      try {
        await _casRelogin;
      } catch (_) {}
      if (await cas!.hasTgt()) return;
    }
    final id = studentId.isNotEmpty ? studentId : accounts.currentId;
    if (id == null || id.isEmpty) return;
    final pwd = await accounts.passwordOf(id);
    if (pwd == null || pwd.isEmpty) return;
    final done = cas!.login(id, pwd);
    _casRelogin = done;
    try {
      await done.timeout(const Duration(seconds: 45));
    } catch (e) {
      debugPrint('[cas] relogin $e');
    } finally {
      if (identical(_casRelogin, done)) _casRelogin = null;
    }
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
    _wireStores();
    unawaited(Bugly.setUserId('demo'));
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
      _wireStores();
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
    _wireStores();
    unawaited(Bugly.setUserId(''));
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

  void invalidateSchedule() => schedule.invalidate();

  void invalidateGrades() => academic.invalidateGrades();

  void invalidateCredits() => academic.invalidateCredits();

  void invalidateExams() => academic.invalidateExams();

  void invalidateSelection() => selection.invalidate();

  Future<Map<String, dynamic>> loadSelectionEntry({bool force = false}) =>
      selection.loadEntry(force: force);

  Future<Map<String, String>> loadSelectionPanel(
    Map<String, dynamic> round,
    Map<String, String> profile,
  ) => selection.loadPanel(round, profile);

  Future<List<dynamic>> loadSelectionCourses(
    Map<String, dynamic> round,
    Map<String, String> profile, {
    String keyword = '',
    Map<String, String> panel = const {},
    bool force = false,
  }) => selection.loadCourses(
    round,
    profile,
    keyword: keyword,
    panel: panel,
    force: force,
  );

  Future<Map<String, dynamic>> loadSchedule({bool force = false}) =>
      schedule.load(force: force);

  Future<Map<String, dynamic>> loadGrades({
    String xnm = '',
    String xqm = '',
    bool force = false,
  }) => academic.loadGrades(xnm: xnm, xqm: xqm, force: force);

  Future<CreditProgress> loadCredits({bool force = false}) =>
      academic.loadCredits(force: force);

  Future<Map<String, dynamic>> loadExams({bool force = false}) =>
      academic.loadExams(force: force);

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
    await _waitCas();
    ktkq!.attachCas(cas!);
    await ktkq!.restoreToken();
    if (ktkq!.token != null && ktkq!.token!.isNotEmpty) {
      try {
        final info = await ktkq!
            .userInfo(retry401: false)
            .timeout(const Duration(seconds: 8));
        final code = info['code'];
        if (code == 200 || code == 0) return;
      } catch (_) {}
      ktkq!.token = null;
    }
    await ktkq!.loginWithCas(cas!).timeout(const Duration(seconds: 45));
  }

  Future<void> ensureGyglxt({bool force = false}) async {
    if (demoMode) return;
    if (gyglxt == null) return;
    if (studentId.isNotEmpty) {
      gyglxt!.username ??= studentId;
    }
    await _waitCas();
    gyglxt!.attachCas(cas!);
    if (!force && (gyglxt!.token == null || gyglxt!.token!.isEmpty)) {
      await gyglxt!.restoreToken();
    }
    if (force || gyglxt!.token == null || gyglxt!.token!.isEmpty) {
      await gyglxt!.loginWithCas(cas!).timeout(const Duration(seconds: 25));
    }
    if ((gyglxt!.username == null || gyglxt!.username!.isEmpty) &&
        studentId.isNotEmpty) {
      gyglxt!.username = studentId;
    }
  }
}
