import 'dart:async';

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:url_launcher/url_launcher.dart';

import '../api/httpx.dart';
import '../api/zhcgm.dart';
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

class _VenuePageState extends State<VenuePage> {
  bool _loading = true;
  String? _error;
  String _typeId = '';
  String _typeName = '';
  String _placeTypeId = '';
  List<Map<String, dynamic>> _types = [];
  List<Map<String, dynamic>> _fields = [];

  ZhcgmClient? get _zhcgm => context.read<Session>().zhcgm;

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
        _types = [
          for (final t in const ['羽毛球', '篮球', '乒乓球']) {'id': t, 'name': t},
        ];
        _typeId = '羽毛球';
        _typeName = '羽毛球';
        _fields = [
          for (final e in demoVenues)
            if ('${e['placeType']}' == _typeName)
              {'id': e['id'], 'dataName': e['placeName'], 'dataAddress': e['campusName']},
        ];
      } else {
        final z = s.zhcgm ?? ZhcgmClient();
        unawaited(s.ensureZhcgm());
        final types = await z.sportTypes();
        _types = [for (final t in types) if ('${t['name'] ?? ''}'.trim().isNotEmpty) t];
        if (_types.isEmpty) {
          _fields = [];
        } else {
          if (_typeId.isEmpty || !_types.any((t) => '${t['id']}' == _typeId)) {
            var prefer = _types.first;
            for (final t in _types) {
              if ('${t['name']}' == '羽毛球') {
                prefer = t;
                break;
              }
            }
            _typeId = '${prefer['id']}';
            _typeName = '${prefer['name']}';
          }
          await _loadFields();
        }
      }
    } catch (e) {
      _error = publicError(e);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _loadFields() async {
    final z = _zhcgm ?? ZhcgmClient();
    final pack = await z.fields(_typeId);
    _fields = pack.fields;
    _placeTypeId = pack.placeTypes.isEmpty ? '' : '${pack.placeTypes.first['id'] ?? ''}';
    for (final t in _types) {
      if ('${t['id']}' == _typeId) _typeName = '${t['name']}';
    }
  }

  Future<void> _selectType(Map<String, dynamic> t) async {
    final id = '${t['id']}';
    if (id == _typeId) return;
    setState(() {
      _typeId = id;
      _typeName = '${t['name']}';
      _loading = true;
    });
    try {
      if (context.read<Session>().demoMode) {
        _fields = [
          for (final e in demoVenues)
            if ('${e['placeType']}' == _typeName)
              {'id': e['id'], 'dataName': e['placeName'], 'dataAddress': e['campusName']},
        ];
      } else {
        await _loadFields();
      }
    } catch (e) {
      _error = publicError(e);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _openOfficial() async {
    final ok = await launchUrl(Uri.parse(kZhcgmHost), mode: LaunchMode.externalApplication);
    if (!ok && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('无法打开智慧场馆')));
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('预约场馆'),
        actions: [
          RefreshBusyButton(busy: _loading, onPressed: _reload),
          TextButton(onPressed: _openOfficial, child: const Text('智慧场馆')),
        ],
      ),
      body: _loading
          ? const Center(child: SwunLoader())
          : ListView(
                padding: const EdgeInsets.fromLTRB(16, 8, 16, 28),
                children: [
                  if (_error != null)
                    Padding(
                      padding: const EdgeInsets.only(bottom: 12),
                      child: Text(_error!, style: const TextStyle(color: kCrimson, fontSize: 13)),
                    ),
                  if (_types.isNotEmpty)
                    Wrap(
                      spacing: 8,
                      runSpacing: 8,
                      children: [
                        for (final t in _types)
                          ChoiceChip(
                            label: Text('${t['name']}'),
                            selected: '${t['id']}' == _typeId,
                            onSelected: (_) => _selectType(t),
                          ),
                      ],
                    ),
                  const SizedBox(height: 12),
                  if (_fields.isEmpty)
                    const Padding(
                      padding: EdgeInsets.symmetric(vertical: 28),
                      child: Center(child: Text('这个项目暂时没有场地')),
                    )
                  else
                    for (final f in _fields) _card(f),
                ],
              ),
    );
  }

  Widget _card(Map<String, dynamic> f) {
    final name = '${f['dataName'] ?? f['name'] ?? ''}'.trim();
    final addr = '${f['dataAddress'] ?? f['dataAddr'] ?? ''}'.trim();
    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: Pressable(
        onTap: () => pushPage(
          context,
          VenueFieldPage(
            field: f,
            sportTypeId: _typeId,
            sportTypeName: _typeName,
            placeTypeId: _placeTypeId,
          ),
        ),
        child: DecoratedBox(
          decoration: BoxDecoration(
            color: context.panel,
            borderRadius: BorderRadius.circular(12),
            border: Border.all(color: context.line),
          ),
          child: Padding(
            padding: const EdgeInsets.fromLTRB(16, 14, 12, 14),
            child: Row(
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(name.isEmpty ? '场地' : name, style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600)),
                      if (addr.isNotEmpty) ...[
                        const SizedBox(height: 4),
                        Text(addr, style: TextStyle(color: context.muted, fontSize: 13)),
                      ],
                    ],
                  ),
                ),
                Icon(Icons.chevron_right_rounded, color: context.muted, size: 18),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class VenueFieldPage extends StatefulWidget {
  const VenueFieldPage({
    super.key,
    required this.field,
    required this.sportTypeId,
    required this.sportTypeName,
    required this.placeTypeId,
  });

  final Map<String, dynamic> field;
  final String sportTypeId;
  final String sportTypeName;
  final String placeTypeId;

  @override
  State<VenueFieldPage> createState() => _VenueFieldPageState();
}

class _VenueFieldPageState extends State<VenueFieldPage> {
  bool _loading = true;
  bool _busy = false;
  String? _error;
  List<List<Map<String, dynamic>>> _cols = [];
  final _day = DateTime.now();

  String get _fieldName => '${widget.field['dataName'] ?? widget.field['name'] ?? ''}'.trim();
  String get _addr => '${widget.field['dataAddress'] ?? ''}'.trim();
  String get _fieldId => '${widget.field['id'] ?? ''}';

  String _ymd(DateTime d) =>
      '${d.year.toString().padLeft(4, '0')}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';

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
        _cols = [];
      } else {
        await s.ensureZhcgm();
        final z = s.zhcgm!;
        _cols = await z.sessions(
          fieldId: _fieldId,
          sportTypeId: widget.sportTypeId,
          placeTypeId: widget.placeTypeId,
          searchDate: _ymd(_day),
        );
      }
    } catch (e) {
      _error = publicError(e);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _book(Map<String, dynamic> slot) async {
    if (zhcgmSlotTaken(slot) || _busy) return;
    final time = '${zhcgmSlotLabel(slot)}–${zhcgmSlotLabel({'openStartTime': slot['openEndTime']})}';
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('确认预约'),
        content: Text('${slot['placeName'] ?? ''}\n今天  $time'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('取消')),
          TextButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('预约')),
        ],
      ),
    );
    if (ok != true || !mounted) return;
    final s = context.read<Session>();
    setState(() => _busy = true);
    try {
      await s.ensureZhcgm();
      final r = await s.zhcgm!.reserve(
        fieldId: _fieldId,
        fieldName: _fieldName,
        sportTypeId: widget.sportTypeId,
        sportTypeName: widget.sportTypeName,
        siteName: _addr,
        day: _day,
        sessionIds: ['${slot['id']}'],
      );
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('${r['msg'] ?? '预约成功'}')));
      await _reload();
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(publicError(e))));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(_fieldName.isEmpty ? '场地' : _fieldName),
        actions: [
          RefreshBusyButton(busy: _loading, onPressed: _reload),
        ],
      ),
      body: _loading
          ? const Center(child: SwunLoader())
          : ListView(
                padding: const EdgeInsets.fromLTRB(16, 8, 16, 28),
                children: [
                  if (_addr.isNotEmpty)
                    Padding(
                      padding: const EdgeInsets.only(bottom: 8),
                      child: Text(_addr, style: TextStyle(color: context.muted, fontSize: 13)),
                    ),
                  Text('今天 · 灰色为已订满', style: TextStyle(color: context.muted, fontSize: 12)),
                  const SizedBox(height: 12),
                  if (_error != null)
                    Padding(
                      padding: const EdgeInsets.only(bottom: 12),
                      child: Text(_error!, style: const TextStyle(color: kCrimson, fontSize: 13)),
                    ),
                  if (_cols.isEmpty && _error == null)
                    const Padding(
                      padding: EdgeInsets.symmetric(vertical: 32),
                      child: Center(child: Text('今天没有可显示的时段')),
                    )
                  else
                    for (final col in _cols) _court(col),
                  if (_busy)
                    const Padding(
                      padding: EdgeInsets.only(top: 12),
                      child: Center(child: SwunLoader(compact: true)),
                    ),
                ],
              ),
    );
  }

  Widget _court(List<Map<String, dynamic>> col) {
    if (col.isEmpty) return const SizedBox.shrink();
    final name = '${col.first['placeName'] ?? ''}'.trim();
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: context.panel,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: context.line),
        ),
        child: Padding(
          padding: const EdgeInsets.fromLTRB(12, 12, 12, 10),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(name, style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 15)),
              const SizedBox(height: 8),
              Wrap(
                spacing: 6,
                runSpacing: 6,
                children: [
                  for (final s in col) _chip(s),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _chip(Map<String, dynamic> s) {
    final taken = zhcgmSlotTaken(s);
    final label = zhcgmSlotLabel(s);
    final hint = zhcgmSlotHint(s);
    final bg = taken ? context.line : const Color(0xFF2E7D32).withValues(alpha: 0.12);
    final fg = taken ? context.muted : const Color(0xFF2E7D32);
    return InkWell(
      onTap: taken || _busy ? null : () => _book(s),
      borderRadius: BorderRadius.circular(8),
      child: Container(
        width: 72,
        padding: const EdgeInsets.symmetric(vertical: 8),
        decoration: BoxDecoration(color: bg, borderRadius: BorderRadius.circular(8)),
        child: Column(
          children: [
            Text(label, style: TextStyle(color: fg, fontSize: 12, fontWeight: FontWeight.w600)),
            Text(hint, style: TextStyle(color: fg, fontSize: 10)),
          ],
        ),
      ),
    );
  }
}

