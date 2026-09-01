import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../api/ktkq.dart';
import '../demo/demo_data.dart';
import '../state/session.dart';
import '../theme.dart';
import '../widgets/async_body.dart';
import '../widgets/loader.dart';
import '../widgets/motion.dart';
import 'ktkq_sign_page.dart';

class KtkqPage extends StatefulWidget {
  const KtkqPage({super.key});

  @override
  State<KtkqPage> createState() => _KtkqPageState();
}

class _KtkqPageState extends State<KtkqPage> {
  Future<Map<String, dynamic>>? _future;
  var _started = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_started) return;
    _started = true;
    _future = _load();
  }

  Future<Map<String, dynamic>> _load({bool refresh = false}) async {
    final s = context.read<Session>();
    if (s.demoMode) return Map<String, dynamic>.from(demoKtkq);
    await s.ensureKtkq().timeout(const Duration(seconds: 20));
    return s.ktkq!.weekCourses(refresh: refresh).timeout(const Duration(seconds: 20));
  }

  void _reload() {
    setState(() => _future = _load(refresh: true));
  }

  @override
  Widget build(BuildContext context) {
    final future = _future;
    return Scaffold(
      appBar: AppBar(
        title: const Text('课堂考勤'),
        actions: [IconButton(onPressed: _reload, icon: const Icon(Icons.refresh_rounded))],
      ),
      body: future == null
          ? const Center(child: SwunLoader())
          : AsyncBody(
              future: future,
              builder: (context, data) {
                final meta = data['_meta'] is Map ? Map<String, dynamic>.from(data['_meta'] as Map) : {};
                final st = meta['schoolTime'] is Map ? Map<String, dynamic>.from(meta['schoolTime'] as Map) : {};
                final week = int.tryParse('${meta['skzc'] ?? st['todayWeekNum'] ?? 1}') ?? 1;
                final rows = data['data'];
                return ListView(
                  padding: const EdgeInsets.all(16),
                  children: [
                    Text(
                      '${st['xnxqmc'] ?? meta['xnxqdm'] ?? ''}  第 $week 周',
                      style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 16),
                    ),
                    const SizedBox(height: 6),
                    Text('点一门课进入签到。课表里点课程也可以。', style: TextStyle(color: context.muted, fontSize: 13)),
                    const SizedBox(height: 12),
                    if (data['code'] != 200 && data['code'] != 0)
                      Card(
                        child: Padding(
                          padding: const EdgeInsets.all(16),
                          child: Text('${data['msg'] ?? '无法拉取本周课程'}'),
                        ),
                      )
                    else if (rows is! List || rows.isEmpty)
                      const Card(child: ListTile(title: Text('本周暂无课程')))
                    else
                      for (final raw in rows)
                        if (raw is Map) _weekCard(Map<String, dynamic>.from(raw), week),
                  ],
                );
              },
            ),
    );
  }

  Widget _weekCard(Map<String, dynamic> c, int week) {
    final list = (c['list'] as List?) ?? [];
    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('${c['kcm'] ?? ''} (${c['kch'] ?? ''})', style: const TextStyle(fontWeight: FontWeight.w700)),
            if ('${c['xf'] ?? ''}${c['xs'] ?? ''}'.trim().isNotEmpty)
              Text(
                [
                  if ('${c['xf'] ?? ''}'.trim().isNotEmpty) '${c['xf']} 学分',
                  if ('${c['xs'] ?? ''}'.trim().isNotEmpty) '${c['xs']} 学时',
                ].join(' · '),
                style: TextStyle(color: context.muted),
              ),
            const Divider(),
            for (final it in list)
              if (it is Map)
                InkWell(
                  onTap: () {
                    final item = Map<String, dynamic>.from(it);
                    pushPage(
                      context,
                      KtkqSignPage(
                        lesson: ktkqSlotToLesson(c, item),
                        week: week,
                        slot: {
                          ...c,
                          ...item,
                          'list': null,
                        },
                      ),
                    );
                  },
                  child: Padding(
                    padding: const EdgeInsets.symmetric(vertical: 8),
                    child: Row(
                      children: [
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text('${it['jxbmc'] ?? ''}', style: const TextStyle(color: kCrimson)),
                              Text('${it['sksj'] ?? ''}  ${it['jasmc'] ?? ''}  节次 ${it['ksjc'] ?? ''}~${it['jsjc'] ?? ''}'),
                            ],
                          ),
                        ),
                        Icon(Icons.chevron_right_rounded, color: context.muted),
                      ],
                    ),
                  ),
                ),
          ],
        ),
      ),
    );
  }
}
