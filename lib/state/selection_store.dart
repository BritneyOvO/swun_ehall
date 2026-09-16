import '../api/jwxt.dart';
import '../demo/demo_data.dart';

/// 自主选课入口 / 面板 / 课程列表缓存。
class SelectionStore {
  bool demoMode = false;
  JwxtClient? jwxt;
  Future<void> Function()? ensureJwxt;

  Future<Map<String, dynamic>>? _xkEntryFut;
  Map<String, Future<List<dynamic>>> _xkCourses = {};

  void clear() {
    _xkEntryFut = null;
    _xkCourses = {};
  }

  void invalidate() => clear();

  /// 选课入口：轮次 + 学生画像（加密串 xkkz_xh 在轮次里）。
  Future<Map<String, dynamic>> loadEntry({bool force = false}) {
    if (demoMode) {
      return Future.value({
        'rounds': demoXkRounds,
        'profile': demoXkProfile,
      });
    }
    if (force) {
      _xkEntryFut = null;
      _xkCourses = {};
    }
    final hit = _xkEntryFut;
    if (hit != null) return hit;
    late final Future<Map<String, dynamic>> fut;
    fut = () async {
      try {
        await ensureJwxt!().timeout(const Duration(seconds: 25));
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

  /// 点轮次后官网先 load Display.html，拿到 rwlx/xklc。
  Future<Map<String, String>> loadPanel(
    Map<String, dynamic> round,
    Map<String, String> profile,
  ) async {
    if (demoMode) {
      return {'rwlx': '1', 'xklc': '1', 'xkly': '0'};
    }
    await ensureJwxt!().timeout(const Duration(seconds: 25));
    return jwxt!.selectionDisplay(
      xkkzId: '${round['xkkz_id'] ?? ''}',
      xkkzXh: '${round['xkkz_xh'] ?? ''}',
      kklxdm: '${round['kklxdm'] ?? ''}',
      njdmId: '${round['njdm_id'] ?? profile['njdm_id'] ?? ''}',
      zyhId: '${round['zyh_id'] ?? profile['zyh_id'] ?? ''}',
    );
  }

  /// 某轮次的可选课程。必须先 await [loadPanel]。
  Future<List<dynamic>> loadCourses(
    Map<String, dynamic> round,
    Map<String, String> profile, {
    String keyword = '',
    Map<String, String> panel = const {},
    bool force = false,
  }) {
    final key = '${round['xkkz_id']}|$keyword|${panel['xklc'] ?? ''}';
    if (force) _xkCourses.remove(key);
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
        await ensureJwxt!().timeout(const Duration(seconds: 25));
        final query = buildXkQuery(
          round: round,
          profile: profile,
          kcmc: keyword,
          panel: panel,
        );
        return await jwxt!.selectionCourses(query);
      } catch (e) {
        if (identical(_xkCourses[key], fut)) _xkCourses.remove(key);
        rethrow;
      }
    }();
    _xkCourses[key] = fut;
    return fut;
  }
}
