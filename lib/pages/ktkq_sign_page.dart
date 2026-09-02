import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../api/httpx.dart';
import '../api/ktkq.dart';
import '../api/locate.dart';
import '../demo/demo_data.dart';
import '../models/lesson.dart';
import '../state/session.dart';
import '../theme.dart';
import '../widgets/loader.dart';

class KtkqSignPage extends StatefulWidget {
  const KtkqSignPage({
    super.key,
    required this.lesson,
    this.week,
    this.slot,
  });

  final Lesson lesson;
  final int? week;
  final Map<String, dynamic>? slot;

  @override
  State<KtkqSignPage> createState() => _KtkqSignPageState();
}

class _KtkqSignPageState extends State<KtkqSignPage> {
  bool _loading = true;
  bool _locating = false;
  String? _error;
  Map<String, dynamic> _data = {};
  GeoFix? _pos;
  String _address = '';
  String? _locError;
  String? _punchingId;
  final _codes = <String, TextEditingController>{};

  Lesson get _lesson => widget.lesson;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _reload());
  }

  @override
  void dispose() {
    for (final c in _codes.values) {
      c.dispose();
    }
    super.dispose();
  }

  TextEditingController _codeOf(String id, [String initial = '']) {
    return _codes.putIfAbsent(id, () => TextEditingController(text: initial));
  }

  List<Map<String, dynamic>> get _activities {
    final raw = _data['activities'];
    if (raw is! List) return [];
    return [
      for (final e in raw)
        if (e is Map<String, dynamic>) e else if (e is Map) Map<String, dynamic>.from(e),
    ];
  }

  List<Map<String, dynamic>> get _history {
    final raw = _data['history'];
    if (raw is! List) return [];
    return [
      for (final e in raw)
        if (e is Map) Map<String, dynamic>.from(e),
    ];
  }

  Future<void> _reload({bool refresh = false}) async {
    setState(() {
      _loading = true;
      _error = null;
    });
    final s = context.read<Session>();
    try {
      if (s.demoMode) {
        _data = jsonDecode(jsonEncode(demoKtkqSign(_lesson))) as Map<String, dynamic>;
      } else {
        await s.ensureKtkq().timeout(const Duration(seconds: 25));
        _data = await s.ktkq!
            .signForLesson(_lesson, week: widget.week, slot: widget.slot, refresh: refresh)
            .timeout(const Duration(seconds: 30));
      }
    } catch (e) {
      _error = publicError(e);
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

  Future<void> _punch(Map<String, dynamic> act) async {
    final s = context.read<Session>();
    final activityId = '${act['activityId'] ?? ''}';
    if (activityId.isEmpty) {
      _toast('没有签到活动');
      return;
    }
    if (_pos == null) {
      await _locate();
      if (_pos == null) {
        _toast(_locError ?? '还没有定位');
        return;
      }
    }
    final type = '${act['signType'] ?? _data['signType'] ?? ''}';
    var code = _codeOf(activityId, '${act['signCode'] ?? ''}').text.trim();
    if (code.isEmpty) code = '${act['signCode'] ?? _data['signCode'] ?? ''}';
    if (!s.demoMode && ktkqNeedsCode(type) && code.isEmpty) {
      _toast('请输入教师展示的签到码');
      return;
    }

    setState(() => _punchingId = activityId);
    try {
      if (s.demoMode) {
        await Future<void>.delayed(const Duration(milliseconds: 500));
        act['status'] = 'already_signed';
        act['message'] = '已签到';
        _data['status'] = 'already_signed';
        _data['message'] = '已签到';
        _toast('示例模式：已模拟签到成功');
        return;
      }
      final lat = _pos!.latitude;
      final lng = _pos!.longitude;
      final acc = _pos!.accuracy.isFinite ? _pos!.accuracy.round() : 0;
      final r = await s.ktkq!.submitSign(
        activityId: activityId,
        code: code,
        accuracy: acc,
        latitude: lat,
        longitude: lng,
      );
      final rc = r['code'];
      final msg = '${r['msg'] ?? ''}'.trim();
      final ok = rc == 0 || rc == 200 || rc == '0' || rc == '200' || msg.contains('成功') || msg.contains('已签到');
      if (ok) {
        final room = '${_data['classroom'] ?? _lesson.room}'.trim();
        if (room.isNotEmpty) {
          await s.rememberRoom(
            room: room,
            latitude: lat,
            longitude: lng,
            accuracy: acc.toDouble(),
            course: _lesson.name,
          );
        }
        _toast(msg.isEmpty || msg == 'success' ? '签到成功' : msg);
        await _reload(refresh: true);
        return;
      }
      _toast(msg.isEmpty ? '签到失败' : msg);
    } catch (e) {
      _toast(e.toString().replaceFirst('Exception: ', ''));
    } finally {
      if (mounted) setState(() => _punchingId = null);
    }
  }

  void _toast(String m) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(m)));
  }

  Color _statusColor(String status) {
    switch (status) {
      case 'pending_signin':
      case 'not_in_scope':
        return kCrimson;
      case 'already_signed':
        return const Color(0xFF2E7D32);
      default:
        return context.muted;
    }
  }

  @override
  Widget build(BuildContext context) {
    final status = '${_data['status'] ?? ''}';
    return Scaffold(
      appBar: AppBar(title: const Text('课堂签到')),
      body: _loading
          ? const Center(child: SwunLoader())
          : RefreshIndicator(
              color: kCrimson,
              onRefresh: () => _reload(refresh: true),
              child: ListView(
                padding: const EdgeInsets.all(16),
                children: [
                  if (_error != null)
                    Card(
                      child: Padding(
                        padding: const EdgeInsets.all(16),
                        child: Text(_error!, style: const TextStyle(color: kCrimson)),
                      ),
                    ),
                  _courseCard(status),
                  const SizedBox(height: 12),
                  _locCard(),
                  const SizedBox(height: 20),
                  Text('签到活动', style: TextStyle(fontSize: 15, fontWeight: FontWeight.w600, color: context.ink)),
                  const SizedBox(height: 8),
                  if (_activities.isEmpty)
                    Card(
                      child: ListTile(
                        title: Text('${_data['message'] ?? '无签到活动'}'),
                      ),
                    )
                  else
                    for (final a in _activities) _activityCard(a),
                  const SizedBox(height: 20),
                  Text('本课记录', style: TextStyle(fontSize: 15, fontWeight: FontWeight.w600, color: context.ink)),
                  const SizedBox(height: 8),
                  if (_history.isEmpty)
                    const Card(child: ListTile(title: Text('暂无签到记录')))
                  else
                    ..._history.take(10).map(_historyTile),
                ],
              ),
            ),
    );
  }

  Widget _courseCard(String status) {
    final room = '${_data['classroom'] ?? _lesson.room}';
    final teacher = '${_data['teacher'] ?? _lesson.teacher}';
    final time = '${_data['timeText'] ?? '${kWeekdayLabels[_lesson.weekday]}  ${_lesson.periodLabel}'}';
    final week = _data['week'] ?? widget.week;
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
              Expanded(
                child: Text(_lesson.name, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w600)),
              ),
              if (status.isNotEmpty)
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                  decoration: BoxDecoration(
                    color: _statusColor(status).withValues(alpha: 0.12),
                    borderRadius: BorderRadius.circular(20),
                  ),
                  child: Text(
                    ktkqStatusLabel(status),
                    style: TextStyle(color: _statusColor(status), fontSize: 12, fontWeight: FontWeight.w600),
                  ),
                ),
            ],
          ),
          const SizedBox(height: 10),
          Text(time, style: TextStyle(color: context.muted, fontSize: 13)),
          if (room.isNotEmpty || teacher.isNotEmpty) ...[
            const SizedBox(height: 4),
            Text(
              [if (teacher.isNotEmpty) teacher, if (room.isNotEmpty) room].join('  ·  '),
              style: TextStyle(color: context.muted, fontSize: 13),
            ),
          ],
          if (week != null) ...[
            const SizedBox(height: 4),
            Text('第 $week 周', style: TextStyle(color: context.muted, fontSize: 12)),
          ],
        ],
      ),
    );
  }

  Widget _locCard() {
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
                '${_pos!.sourceLabel} · 精度 ${_pos!.accuracy.toStringAsFixed(0)} 米 · 签到会提交当前经纬度',
                style: TextStyle(color: context.muted, fontSize: 12),
              ),
            ],
          ],
        ),
      ),
    );
  }

  Widget _activityCard(Map<String, dynamic> act) {
    final id = '${act['activityId'] ?? ''}';
    final status = '${act['status'] ?? ''}';
    final type = '${act['signType'] ?? ''}';
    final pending = status == 'pending_signin';
    final punching = _punchingId == id;
    final range = ['${act['startTime'] ?? ''}', '${act['endTime'] ?? ''}'].where((e) => e.isNotEmpty).join(' ~ ');
    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(
                    '${act['title'] ?? '课堂签到'} · ${ktkqSignTypeLabel(type)}',
                    style: const TextStyle(fontWeight: FontWeight.w600),
                  ),
                ),
                Text(
                  ktkqStatusLabel(status),
                  style: TextStyle(color: _statusColor(status), fontSize: 12, fontWeight: FontWeight.w600),
                ),
              ],
            ),
            if (range.isNotEmpty)
              Padding(
                padding: const EdgeInsets.only(top: 4),
                child: Text(range, style: TextStyle(color: context.muted, fontSize: 12)),
              ),
            if (pending && ktkqNeedsCode(type)) ...[
              const SizedBox(height: 10),
              TextField(
                controller: _codeOf(id, '${act['signCode'] ?? ''}'),
                keyboardType: type.toUpperCase() == 'NUMBER' ? TextInputType.number : TextInputType.text,
                decoration: const InputDecoration(hintText: '教师口令 / 数字码'),
              ),
            ],
            if (pending) ...[
              const SizedBox(height: 10),
              FilledButton(
                onPressed: punching ? null : () => _punch(act),
                child: punching
                    ? SwunBusyDots(color: Theme.of(context).colorScheme.onPrimary, size: 5)
                    : const Text('立即签到'),
              ),
            ],
          ],
        ),
      ),
    );
  }

  Widget _historyTile(Map<String, dynamic> e) {
    return Card(
      margin: const EdgeInsets.only(bottom: 8),
      child: ListTile(
        title: Text('${e['time'] ?? ''}'),
        subtitle: Text('${e['course'] ?? ''}'),
        trailing: Text('${e['status'] ?? ''}'),
      ),
    );
  }
}
