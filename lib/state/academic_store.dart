import '../api/jwxt.dart';
import '../demo/demo_data.dart';
import '../models/credit.dart';
import '../models/term.dart';

/// 成绩、学分、考试。走教务，Future 去重。
class AcademicStore {
  bool demoMode = false;
  JwxtClient? jwxt;
  Future<void> Function()? ensureJwxt;

  final _cjCache = <String, Future<Map<String, dynamic>>>{};
  Future<CreditProgress>? _xfFut;
  Future<Map<String, dynamic>>? _ksFut;

  void clear() {
    _cjCache.clear();
    _xfFut = null;
    _ksFut = null;
  }

  void invalidateGrades() => _cjCache.clear();

  void invalidateCredits() => _xfFut = null;

  void invalidateExams() => _ksFut = null;

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
        await ensureJwxt!().timeout(const Duration(seconds: 20));
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

  Future<CreditProgress> loadCredits({bool force = false}) {
    if (demoMode) return Future.value(demoCreditProgress);
    if (force) _xfFut = null;
    final hit = _xfFut;
    if (hit != null) return hit;
    late final Future<CreditProgress> fut;
    fut = () async {
      try {
        await ensureJwxt!().timeout(const Duration(seconds: 20));
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
        await ensureJwxt!();
        return await jwxt!.exams();
      } catch (e) {
        if (identical(_ksFut, fut)) _ksFut = null;
        rethrow;
      }
    }();
    _ksFut = fut;
    return fut;
  }
}
