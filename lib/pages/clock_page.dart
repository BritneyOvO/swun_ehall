import 'dart:math';

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../api/locate.dart';
import '../demo/demo_data.dart';
import '../state/session.dart';
import '../theme.dart';
import '../widgets/loader.dart';

class ClockPage extends StatefulWidget {
  const ClockPage({super.key});

  @override
  State<ClockPage> createState() => _ClockPageState();
}

class _ClockPageState extends State<ClockPage> {
  bool _loading = true;
  bool _punching = false;
  String? _error;
  Map<String, dynamic> _data = {};
  GeoFix? _pos;
  bool _locating = false;
  String _address = '';
  String? _locError;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _reload());
  }

  Future<void> _reload() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    final s = context.read<Session>();
    try {
      if (s.demoMode) {
        _data = Map<String, dynamic>.from(demoClock);
      } else {
        if (s.gyglxt?.token == null || s.gyglxt!.token!.isEmpty) {
          await s.ensureGyglxt().timeout(const Duration(seconds: 25));
        }
        _data = await s.gyglxt!.dashboard().timeout(const Duration(seconds: 25));
        final st = _data['status'];
        if (st is! Map || st.isEmpty) {
          _error ??= '打卡状态未取到，点右上角刷新可重试';
        }
      }
    } catch (e) {
      _error = e.toString().replaceFirst('Exception: ', '');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
    if (mounted) {
      await _locate();
      if (mounted) setState(() {});
    }
  }

  void _applyPos(GeoFix p) {
    _pos = p;
    _address = p.source == 'demo' ? '西南民族大学（示例定位）' : '当前位置 ${p.coordText}';
    _locError = null;
  }

  Future<void> _locate({bool force = false}) async {
    setState(() => _locating = true);
    try {
      final fix = await AppLocator.current(
        demo: context.read<Session>().demoMode,
        force: force,
        onUpdate: (f) {
          if (!mounted) return;
          setState(() => _applyPos(f));
        },
      );
      if (mounted) setState(() => _applyPos(fix));
    } on LocateException catch (e) {
      _locError = e.message;
    } catch (_) {
      if (_pos == null) _locError = '定位失败，请打开系统定位后重试';
    } finally {
      if (mounted) setState(() => _locating = false);
    }
  }

  Map<String, dynamic> get _back {
    final st = _data['status'];
    if (st is Map && st['backMap'] is Map) return Map<String, dynamic>.from(st['backMap'] as Map);
    return {};
  }

  List<Map<String, dynamic>> get _tasks {
    final raw = _data['schedule'];
    final list = raw is Map ? raw['list'] : null;
    if (list is! List) return [];
    return [
      for (final e in list)
        if (e is Map) Map<String, dynamic>.from(e),
    ];
  }

  List<Map<String, dynamic>> get _fences {
    final raw = _data['positions'];
    final list = raw is Map ? raw['list'] : null;
    if (list is! List) return [];
    return [
      for (final e in list)
        if (e is Map) Map<String, dynamic>.from(e),
    ];
  }

  List<Map<String, dynamic>> get _records {
    final raw = _data['records'];
    if (raw is! Map) return [];
    final page = raw['page'];
    final list = page is Map ? page['list'] : null;
    if (list is! List) return [];
    return [
      for (final e in list)
        if (e is Map) Map<String, dynamic>.from(e),
    ];
  }

  double? get _nearestMeters {
    if (_pos == null || _fences.isEmpty) return null;
    double? best;
    for (final f in _fences) {
      final lat = double.tryParse('${f['lat'] ?? ''}');
      final lng = double.tryParse('${f['lng'] ?? ''}');
      if (lat == null || lng == null) continue;
      final d = _haversine(_pos!.latitude, _pos!.longitude, lat, lng);
      if (best == null || d < best) best = d;
    }
    return best;
  }

  String? get _openTaskId {
    for (final t in _tasks) {
      final open = t['isOpen'] == true || t['checked'] == false;
      if (open && '${t['id'] ?? ''}'.isNotEmpty) return '${t['id']}';
    }
    if (_tasks.isNotEmpty) return '${_tasks.first['id'] ?? ''}';
    return null;
  }

  Future<void> _punch() async {
    final s = context.read<Session>();
    if (_pos == null) {
      await _locate();
      if (_pos == null) {
        _toast(_locError ?? '还没有定位');
        return;
      }
    }
    setState(() => _punching = true);
    try {
      if (s.demoMode) {
        await Future<void>.delayed(const Duration(milliseconds: 600));
        _toast('示例模式：已模拟打卡成功');
        final st = Map<String, dynamic>.from(_data['status'] as Map? ?? {});
        final back = Map<String, dynamic>.from(st['backMap'] as Map? ?? {});
        back['isClock'] = true;
        st['backMap'] = back;
        _data['status'] = st;
      } else {
        final r = await s.gyglxt!.punch(
          lat: _pos!.latitude,
          lng: _pos!.longitude,
          address: _address,
          taskId: _openTaskId,
        );
        final code = r['code'];
        if (code == 0 || code == 200) {
          _toast('${r['msg'] ?? '打卡成功'}');
          await _reload();
          return;
        }
        _toast('${r['msg'] ?? '打卡失败'}');
      }
    } catch (e) {
      _toast(e.toString().replaceFirst('Exception: ', ''));
    } finally {
      if (mounted) setState(() => _punching = false);
    }
  }

  void _toast(String m) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(m)));
  }

  @override
  Widget build(BuildContext context) {
    final need = _truthy(_back['isNeedClock']);
    final done = _truthy(_back['isClock']);
    final near = _nearestMeters;
    return Scaffold(
      appBar: AppBar(
        title: const Text('公寓打卡'),
        actions: [
          RefreshBusyButton(busy: _loading, onPressed: _reload),
        ],
      ),
      body: _loading
          ? const Center(child: SwunLoader())
          : ListView(
                padding: const EdgeInsets.all(16),
                children: [
                  if (_error != null)
                    Card(
                      child: Padding(
                        padding: const EdgeInsets.all(16),
                        child: Text(_error!, style: const TextStyle(color: kCrimson)),
                      ),
                    ),
                  _statusCard(need, done),
                  const SizedBox(height: 12),
                  _timeCard(),
                  const SizedBox(height: 12),
                  _locCard(near),
                  const SizedBox(height: 16),
                  FilledButton(
                    onPressed: _punching ? null : _punch,
                    child: _punching
                        ? const SwunBusyDots(color: Colors.white, size: 5)
                        : Text(done ? '再次打卡' : '立即打卡'),
                  ),
                  const SizedBox(height: 20),
                  const Text('最近记录', style: TextStyle(fontSize: 15, fontWeight: FontWeight.w600)),
                  const SizedBox(height: 8),
                  if (_records.isEmpty)
                    const Card(child: ListTile(title: Text('暂无打卡记录')))
                  else
                    ..._records.take(8).map(_recordTile),
                ],
              ),
    );
  }

  Widget _statusCard(bool need, bool done) {
    final color = done ? const Color(0xFF2E7D32) : kCrimson;
    return Container(
      padding: const EdgeInsets.fromLTRB(16, 18, 16, 16),
      decoration: BoxDecoration(
        color: context.panel,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: context.line),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(width: 8, height: 8, decoration: BoxDecoration(color: color, shape: BoxShape.circle)),
              const SizedBox(width: 8),
              Text(
                done ? '今日已打卡' : (need ? '今日待打卡' : '今日无需打卡'),
                style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w600),
              ),
            ],
          ),
          const SizedBox(height: 8),
          Text(
            need ? '请在规定时段内到打卡范围完成定位打卡' : '当前账号未要求定位打卡',
            style: TextStyle(color: context.muted, fontSize: 13),
          ),
        ],
      ),
    );
  }

  Widget _timeCard() {
    if (_tasks.isEmpty) {
      return const Card(child: ListTile(title: Text('未获取到打卡时段')));
    }
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('打卡时段', style: TextStyle(fontWeight: FontWeight.w700)),
            const SizedBox(height: 8),
            for (final t in _tasks)
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 4),
                child: Text(
                  '${t['name'] ?? '打卡'}  ${t['startTime'] ?? ''} ~ ${t['endTime'] ?? ''}'
                  '${_truthy(t['isOpen']) ? '  · 进行中' : ''}',
                ),
              ),
          ],
        ),
      ),
    );
  }

  Widget _locCard(double? near) {
    final inRange = near != null && near <= 800;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Text('当前位置', style: TextStyle(fontWeight: FontWeight.w700)),
                const Spacer(),
                TextButton(
                  onPressed: _locating ? null : () => _locate(force: true),
                  child: Text(_locating ? '定位中' : '重新定位'),
                ),
              ],
            ),
            if (_locError != null) ...[
              Text(_locError!, style: const TextStyle(color: kCrimson)),
              TextButton(
                onPressed: () => AppLocator.openSettings(),
                child: const Text('打开系统定位设置'),
              ),
            ],
            if (_pos == null && _locError == null)
              Text(_locating ? '正在定位…' : '尚未定位', style: TextStyle(color: context.muted)),
            if (_pos != null) ...[
              Text(_address),
              Text(
                '${_pos!.sourceLabel} · 精度 ${_pos!.accuracy.toStringAsFixed(0)} 米',
                style: TextStyle(color: context.muted, fontSize: 12),
              ),
              const SizedBox(height: 6),
              Text(
                near == null ? '未获取到打卡围栏' : (inRange ? '在打卡范围内（约 ${near.toStringAsFixed(0)} 米）' : '可能不在范围内（约 ${near.toStringAsFixed(0)} 米）'),
                style: TextStyle(color: inRange ? const Color(0xFF2E7D32) : kCrimson, fontWeight: FontWeight.w600),
              ),
            ],
          ],
        ),
      ),
    );
  }

  Widget _recordTile(Map<String, dynamic> e) {
    return Card(
      margin: const EdgeInsets.only(bottom: 8),
      child: ListTile(
        title: Text('${e['clockTime'] ?? ''}'),
        subtitle: Text('${e['clockAddress'] ?? ''}'),
        trailing: Text('${e['dataStatus'] ?? ''}' == '1' ? '正常' : '${e['dataStatus'] ?? ''}'),
      ),
    );
  }
}

bool _truthy(Object? v) => v == true || v == 1 || v == '1' || v == 'true';

double _haversine(double lat1, double lng1, double lat2, double lng2) {
  const r = 6371000.0;
  final p1 = lat1 * pi / 180;
  final p2 = lat2 * pi / 180;
  final dp = (lat2 - lat1) * pi / 180;
  final dl = (lng2 - lng1) * pi / 180;
  final a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2);
  return 2 * r * atan2(sqrt(a), sqrt(1 - a));
}
