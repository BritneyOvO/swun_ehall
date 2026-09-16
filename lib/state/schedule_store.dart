import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/foundation.dart';

import '../api/jwxt.dart';
import '../api/lantu.dart';
import '../demo/demo_data.dart';
import '../models/lesson.dart';

/// 课表：蓝图优先，教务补教师名。
class ScheduleStore {
  ScheduleStore({required this.onChange});

  final VoidCallback onChange;

  bool demoMode = false;
  LantuClient? lantu;
  JwxtClient? jwxt;
  String studentId = '';
  String? teacherPath;

  Future<Map<String, dynamic>>? _kbFut;
  final _teacherSlot = <String, String>{};
  final _teacherName = <String, String>{};

  void clear() {
    _kbFut = null;
    _teacherSlot.clear();
    _teacherName.clear();
  }

  void invalidate() => _kbFut = null;

  Future<void> loadTeachers() async {
    final path = teacherPath;
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
    final path = teacherPath;
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

  String _normKc(Object? s) => '$s'.replaceAll(RegExp(r'\s+'), '');

  String _startOf(Map<String, dynamic> m) {
    final sk = '${m['skjc'] ?? ''}';
    if (sk.isNotEmpty && sk != 'null') return sk;
    final jcs = '${m['jcs'] ?? ''}';
    return jcs.split(RegExp(r'[-~]')).first;
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

  Future<Map<String, dynamic>> load({bool force = false}) {
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
            onChange();
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
}
