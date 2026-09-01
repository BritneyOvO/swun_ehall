import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../demo/demo_data.dart';
import '../state/session.dart';
import '../theme.dart';
import '../widgets/loader.dart';
import '../widgets/motion.dart';

class VenuePage extends StatefulWidget {
  const VenuePage({super.key});

  @override
  State<VenuePage> createState() => _VenuePageState();
}

class _VenuePageState extends State<VenuePage> with SingleTickerProviderStateMixin {
  late final TabController _tabs = TabController(length: 2, vsync: this);
  bool _loading = true;
  bool _busy = false;
  String? _error;
  List<Map<String, dynamic>> _venues = [];
  List<Map<String, dynamic>> _mine = [];
  DateTime _day = DateTime.now();
  String _type = '羽毛球';

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _reload());
  }

  @override
  void dispose() {
    _tabs.dispose();
    super.dispose();
  }

  List<String> get _types {
    final s = <String>{};
    for (final v in _venues) {
      final t = _typeOf(v);
      if (t.isNotEmpty) s.add(t);
    }
    if (s.isEmpty) return const ['羽毛球', '篮球', '乒乓球'];
    final list = s.toList()..sort();
    if (list.contains('羽毛球')) {
      list.remove('羽毛球');
      list.insert(0, '羽毛球');
    }
    return list;
  }

  List<Map<String, dynamic>> get _filtered {
    return [
      for (final v in _venues)
        if (_typeOf(v) == _type || (_type.isEmpty && _typeOf(v).isEmpty)) v,
    ];
  }

  Future<void> _reload() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    final s = context.read<Session>();
    try {
      if (s.demoMode) {
        _venues = [
          for (final e in demoVenues) Map<String, dynamic>.from(e)..['taken'] = [...(e['taken'] as List? ?? const [])],
        ];
        _mine = [for (final e in demoVenueBookings) Map<String, dynamic>.from(e)];
        _type = '羽毛球';
      } else {
        final lantu = s.lantu;
        if (lantu == null || !lantu.isLoggedIn) {
          throw Exception('请先登录后再预约场地');
        }
        final list = await lantu.getPlaceList();
        final mine = await lantu.getMyPlaceList();
        _venues = _asMaps(list['placeList']);
        _mine = _asMaps(mine['placeList']);
        final types = _types;
        if (types.isNotEmpty && !types.contains(_type)) _type = types.first;
      }
    } catch (e) {
      _error = e.toString().replaceFirst('Exception: ', '').replaceFirst('LantuException: ', '');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _bookSlot(Map<String, dynamic> court, String start) async {
    final end = _nextHour(start);
    final ok = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('确认预约'),
        content: Text('${_typeOf(court)} ${_nameOf(court)}\n${_dayText(_day)}  $start–$end'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('取消')),
          TextButton(onPressed: () => Navigator.pop(context, true), child: const Text('预约')),
        ],
      ),
    );
    if (ok != true || !mounted) return;
    final s = context.read<Session>();
    setState(() => _busy = true);
    try {
      if (s.demoMode) {
        final taken = (court['taken'] as List?) ?? [];
        taken.add(start);
        court['taken'] = taken;
        _mine.insert(0, {
          'id': 'demo-${court['id']}-$start-${_ymd(_day)}',
          'placeName': _nameOf(court),
          'placeType': _typeOf(court),
          'campusName': _campusOf(court),
          'bookDate': _dayText(_day),
          'startTime': start,
          'endTime': end,
          'status': '已预约',
        });
        _toast('已预约 ${_nameOf(court)} $start');
        return;
      }
      final lantu = s.lantu!;
      final id = court['id'] ?? court['placeId'];
      final date = _ymd(_day);
      final r = await lantu.addPlaceBooking({
        'id': id,
        'placeId': id,
        'placeName': _nameOf(court),
        'startTime': start,
        'endTime': end,
        'bookDate': date,
        'useDate': date,
        'date': date,
        'userName': s.studentId,
        'userId': lantu.userLoginInfo['userId'],
        'holdUnit': '个人',
        'activityName': '场地预约',
        'peopleNum': 2,
        'peopleNumber': 2,
        'phone': s.profile.phone,
      });
      _toast('${r['msg'] ?? '预约成功'}');
      await _reload();
    } catch (e) {
      _toast(e.toString().replaceFirst('Exception: ', '').replaceFirst('LantuException: ', ''));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _cancel(Map<String, dynamic> row) async {
    final id = row['id'] ?? row['placeId'];
    if (id == null) {
      _toast('没有预约编号');
      return;
    }
    final s = context.read<Session>();
    try {
      if (s.demoMode) {
        _mine.removeWhere((e) => '${e['id']}' == '$id');
        for (final v in _venues) {
          if (_nameOf(v) == _nameOf(row)) {
            final taken = [...((v['taken'] as List?) ?? const [])];
            taken.remove('${row['startTime']}');
            v['taken'] = taken;
          }
        }
        _toast('已取消');
        setState(() {});
        return;
      }
      final r = await s.lantu!.delPlaceInfo(id);
      _toast('${r['msg'] ?? '已取消'}');
      await _reload();
    } catch (e) {
      _toast(e.toString().replaceFirst('Exception: ', '').replaceFirst('LantuException: ', ''));
    }
  }

  bool _taken(Map<String, dynamic> court, String hour) {
    final taken = court['taken'];
    if (taken is List && taken.map((e) => '$e').contains(hour)) return true;
    final date = _ymd(_day);
    for (final b in _mine) {
      if (_nameOf(b) == _nameOf(court) && '${b['startTime']}' == hour) {
        final bd = '${b['bookDate'] ?? b['useDate'] ?? b['date'] ?? ''}';
        if (bd == date || bd == _dayText(_day) || bd.contains(_day.month.toString())) return true;
      }
    }
    return false;
  }

  void _toast(String m) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(m)));
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('预约场馆')),
      body: Column(
        children: [
          _segment(),
          Expanded(
            child: _loading
                ? const Center(child: SwunLoader())
                : TabBarView(
                    controller: _tabs,
                    children: [
                      RefreshIndicator(color: kCrimson, onRefresh: _reload, child: _bookTab()),
                      RefreshIndicator(color: kCrimson, onRefresh: _reload, child: _mineTab()),
                    ],
                  ),
          ),
        ],
      ),
    );
  }

  Widget _segment() {
    const labels = ['订场地', '我的预约'];
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 4),
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: context.panel,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: context.line),
        ),
        child: Padding(
          padding: const EdgeInsets.all(4),
          child: AnimatedBuilder(
            animation: _tabs,
            builder: (context, _) {
              return Row(
                children: [
                  for (var i = 0; i < labels.length; i++)
                    Expanded(
                      child: Pressable(
                        radius: 10,
                        onTap: () {
                          if (_tabs.index != i) _tabs.animateTo(i);
                        },
                        child: AnimatedContainer(
                          duration: const Duration(milliseconds: 180),
                          curve: Curves.easeOutCubic,
                          padding: const EdgeInsets.symmetric(vertical: 10),
                          alignment: Alignment.center,
                          decoration: BoxDecoration(
                            color: _tabs.index == i
                                ? Theme.of(context).colorScheme.primary.withValues(alpha: 0.12)
                                : Colors.transparent,
                            borderRadius: BorderRadius.circular(10),
                          ),
                          child: Text(
                            labels[i],
                            style: TextStyle(
                              fontSize: 14,
                              fontWeight: _tabs.index == i ? FontWeight.w600 : FontWeight.w400,
                              color: _tabs.index == i ? context.primary : context.muted,
                            ),
                          ),
                        ),
                      ),
                    ),
                ],
              );
            },
          ),
        ),
      ),
    );
  }

  Widget _bookTab() {
    final hours = _hoursFor(_filtered);
    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 24),
      children: [
        if (_error != null)
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Text(_error!, style: const TextStyle(color: kCrimson)),
            ),
          ),
        SizedBox(
          height: 36,
          child: ListView(
            scrollDirection: Axis.horizontal,
            children: [
              for (var i = 0; i < 7; i++)
                Padding(
                  padding: const EdgeInsets.only(right: 8),
                  child: ChoiceChip(
                    label: Text(_dayText(DateTime.now().add(Duration(days: i)))),
                    selected: _ymd(_day) == _ymd(DateTime.now().add(Duration(days: i))),
                    onSelected: (_) => setState(() => _day = DateTime.now().add(Duration(days: i))),
                  ),
                ),
            ],
          ),
        ),
        const SizedBox(height: 10),
        Wrap(
          spacing: 8,
          children: [
            for (final t in _types)
              ChoiceChip(
                label: Text(t),
                selected: _type == t,
                onSelected: (_) => setState(() => _type = t),
              ),
          ],
        ),
        const SizedBox(height: 8),
        Text('点绿色时段即可预约，每次 1 小时', style: TextStyle(color: context.muted, fontSize: 12)),
        const SizedBox(height: 12),
        if (_filtered.isEmpty)
          const Card(
            child: Padding(
              padding: EdgeInsets.all(16),
              child: Text('当前没有可预约的场地。预览模式可体验羽毛球场预约。'),
            ),
          )
        else
          for (final court in _filtered) _courtCard(court, hours),
        if (_busy)
          const Padding(
            padding: EdgeInsets.only(top: 12),
            child: Center(child: SwunLoader(compact: true)),
          ),
      ],
    );
  }

  Widget _courtCard(Map<String, dynamic> court, List<String> hours) {
    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(12, 12, 12, 10),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Text(_nameOf(court), style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 16)),
                const Spacer(),
                Text(_campusOf(court), style: TextStyle(color: context.muted, fontSize: 12)),
              ],
            ),
            const SizedBox(height: 10),
            Wrap(
              spacing: 6,
              runSpacing: 6,
              children: [
                for (final h in hours)
                  _slotChip(court, h),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _slotChip(Map<String, dynamic> court, String hour) {
    final taken = _taken(court, hour);
    final mine = _mine.any(
      (b) => _nameOf(b) == _nameOf(court) && '${b['startTime']}' == hour && '${b['bookDate']}' == _dayText(_day),
    );
    Color bg;
    Color fg;
    if (mine) {
      bg = kCrimson.withValues(alpha: 0.14);
      fg = kCrimson;
    } else if (taken) {
      bg = context.line;
      fg = context.muted;
    } else {
      bg = const Color(0xFF2E7D32).withValues(alpha: 0.12);
      fg = const Color(0xFF2E7D32);
    }
    return InkWell(
      onTap: taken || _busy ? null : () => _bookSlot(court, hour),
      borderRadius: BorderRadius.circular(8),
      child: Container(
        width: 64,
        padding: const EdgeInsets.symmetric(vertical: 8),
        decoration: BoxDecoration(color: bg, borderRadius: BorderRadius.circular(8)),
        child: Text(
          hour,
          textAlign: TextAlign.center,
          style: TextStyle(color: fg, fontSize: 12, fontWeight: FontWeight.w600),
        ),
      ),
    );
  }

  Widget _mineTab() {
    if (_mine.isEmpty) {
      return ListView(
        children: const [
          SizedBox(height: 80),
          Center(child: Text('还没有场地预约')),
        ],
      );
    }
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        for (final e in _mine)
          Card(
            margin: const EdgeInsets.only(bottom: 10),
            child: ListTile(
              title: Text('${_typeOf(e)} ${_nameOf(e)}'.trim()),
              subtitle: Text(
                '${e['bookDate'] ?? e['useDate'] ?? e['date'] ?? ''}  ${e['startTime'] ?? ''}–${e['endTime'] ?? ''}\n'
                '${_campusOf(e)}',
              ),
              isThreeLine: true,
              trailing: TextButton(onPressed: () => _cancel(e), child: const Text('取消')),
            ),
          ),
      ],
    );
  }
}

List<Map<String, dynamic>> _asMaps(Object? raw) {
  if (raw is! List) return [];
  return [
    for (final e in raw)
      if (e is Map) Map<String, dynamic>.from(e),
  ];
}

String _pick(Map<String, dynamic> m, List<String> keys) {
  for (final k in keys) {
    final v = '${m[k] ?? ''}'.trim();
    if (v.isNotEmpty && v != 'null') return v;
  }
  return '';
}

String _nameOf(Map<String, dynamic> m) => _pick(m, const ['placeName', 'name', 'title']);
String _typeOf(Map<String, dynamic> m) => _pick(m, const ['placeType', 'type']);
String _campusOf(Map<String, dynamic> m) => _pick(m, const ['campusName', 'campus', 'xqmc']);

String _ymd(DateTime d) =>
    '${d.year.toString().padLeft(4, '0')}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';

String _dayText(DateTime d) {
  final now = DateTime.now();
  final a = DateTime(now.year, now.month, now.day);
  final b = DateTime(d.year, d.month, d.day);
  final diff = b.difference(a).inDays;
  if (diff == 0) return '今天';
  if (diff == 1) return '明天';
  const week = ['一', '二', '三', '四', '五', '六', '日'];
  return '${d.month}/${d.day} 周${week[d.weekday - 1]}';
}

int _toMin(String hhmm) {
  final p = hhmm.split(':');
  if (p.length < 2) return 0;
  return (int.tryParse(p[0]) ?? 0) * 60 + (int.tryParse(p[1]) ?? 0);
}

String _fmtMin(int m) {
  final h = (m ~/ 60).clamp(0, 23);
  final mm = m % 60;
  return '${h.toString().padLeft(2, '0')}:${mm.toString().padLeft(2, '0')}';
}

List<String> _hoursFor(List<Map<String, dynamic>> courts) {
  var a = 8 * 60;
  var b = 21 * 60;
  for (final v in courts) {
    final oa = _toMin('${v['openTime'] ?? v['startTime'] ?? '08:00'}');
    final ob = _toMin('${v['closeTime'] ?? v['endTime'] ?? '21:00'}');
    if (oa > 0 && oa < a) a = oa;
    if (ob > b) b = ob;
  }
  final out = <String>[];
  for (var t = a; t < b; t += 60) {
    out.add(_fmtMin(t));
  }
  return out.isEmpty ? const ['08:00', '09:00', '10:00', '11:00'] : out;
}

String _nextHour(String start) => _fmtMin(_toMin(start) + 60);
