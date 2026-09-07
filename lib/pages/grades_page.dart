import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../models/credit.dart';
import '../models/term.dart';
import '../state/session.dart';
import '../theme.dart';
import '../widgets/async_body.dart';
import '../widgets/loader.dart';
import '../widgets/motion.dart';
import 'credits_page.dart';

class GradesPage extends StatefulWidget {
  const GradesPage({super.key});

  @override
  State<GradesPage> createState() => _GradesPageState();
}

class _GradesPageState extends State<GradesPage> {
  late SchoolTerm _term;
  Future<Map<String, dynamic>>? _future;
  Future<CreditProgress>? _credits;
  var _busy = false;

  @override
  void initState() {
    super.initState();
    final cur = currentSchoolTerm();
    _term = SchoolTerm(xnm: '${cur.$1}', xqm: cur.$2);
    WidgetsBinding.instance.addPostFrameCallback((_) => _load());
  }

  void _load({bool force = false}) {
    if (!mounted) return;
    final s = context.read<Session>();
    final g = s.loadGrades(xnm: _term.xnm, xqm: _term.xqm, force: force);
    final c = force || _credits == null
        ? s.loadCredits(force: force)
        : _credits!;
    setState(() {
      _busy = true;
      _future = g;
      _credits = c;
    });
    g.whenComplete(() {
      if (mounted) setState(() => _busy = false);
    });
  }

  void _reload() {
    if (_busy) return;
    _load(force: true);
  }

  void _onTerm(SchoolTerm? next) {
    if (next == null || next == _term) return;
    setState(() => _term = next);
    _load();
  }

  @override
  Widget build(BuildContext context) {
    final session = context.watch<Session>();
    final terms = buildGradeTerms(studentId: session.studentId, grade: session.profile.grade);

    return Scaffold(
      appBar: AppBar(
        title: const Text('成绩'),
        automaticallyImplyLeading: false,
        actions: [
          RefreshBusyButton(busy: _busy, onPressed: _reload),
        ],
      ),
      body: Column(
        children: [
          Align(
            alignment: Alignment.centerLeft,
            child: Padding(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 4),
              child: PopupMenuButton<SchoolTerm>(
                tooltip: '选择学期',
                offset: const Offset(0, 8),
                position: PopupMenuPosition.under,
                color: context.panel,
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(12),
                  side: BorderSide(color: context.line),
                ),
                constraints: const BoxConstraints(minWidth: 200, maxWidth: 280),
                onSelected: (t) => _onTerm(t),
                itemBuilder: (context) => [
                  for (final t in terms)
                    PopupMenuItem(
                      value: t,
                      height: 40,
                      child: Text(
                        t.label,
                        style: TextStyle(
                          fontSize: 14,
                          fontWeight: t == _term ? FontWeight.w600 : FontWeight.w400,
                          color: t == _term ? context.primary : context.ink,
                        ),
                      ),
                    ),
                ],
                child: DecoratedBox(
                  decoration: BoxDecoration(
                    color: context.panel,
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(color: context.line),
                  ),
                  child: Padding(
                    padding: const EdgeInsets.fromLTRB(12, 8, 8, 8),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Text(
                          _term.label,
                          style: TextStyle(fontSize: 14, fontWeight: FontWeight.w600, color: context.ink),
                        ),
                        const SizedBox(width: 2),
                        Icon(Icons.expand_more_rounded, size: 20, color: context.muted),
                      ],
                    ),
                  ),
                ),
              ),
            ),
          ),
          if (_credits != null)
            FutureBuilder<CreditProgress>(
              future: _credits,
              builder: (context, snap) {
                final p = snap.data;
                if (p == null || p.taken <= 0 && p.required <= 0) return const SizedBox.shrink();
                return Padding(
                  padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
                  child: Material(
                    color: context.panel,
                    borderRadius: BorderRadius.circular(16),
                    child: InkWell(
                      borderRadius: BorderRadius.circular(16),
                      onTap: () => pushPage(context, const CreditsPage()),
                      child: Padding(
                        padding: const EdgeInsets.fromLTRB(14, 12, 10, 12),
                        child: Row(
                          children: [
                            Expanded(
                              child: Text(
                                p.required > 0
                                    ? (p.met
                                        ? '共修 ${p.takenText} 学分，已达毕业要求 ${p.requiredText}'
                                        : '共修 ${p.takenText} / ${p.requiredText}，还差 ${p.remainingText} 学分')
                                    : '共修 ${p.takenText} 学分',
                                style: const TextStyle(fontWeight: FontWeight.w600),
                              ),
                            ),
                            Icon(Icons.chevron_right_rounded, color: context.muted),
                          ],
                        ),
                      ),
                    ),
                  ),
                );
              },
            ),
          Expanded(
            child: _future == null
                ? const Center(child: SwunLoader())
                : AsyncBody(
                    future: _future!,
                    onRetry: _busy ? null : _reload,
                    builder: (context, data) {
                      final items = (data['items'] as List?) ?? [];
                      if (items.isEmpty) {
                        return Center(
                          child: Padding(
                            padding: const EdgeInsets.all(24),
                            child: Text(
                              _term.isAll ? '暂无成绩' : '该学期暂无成绩，可切换其他学期',
                              textAlign: TextAlign.center,
                            ),
                          ),
                        );
                      }
                      return Column(
                        children: [
                          _SummaryBar(items: items),
                          Expanded(
                            child: ListView.separated(
                              padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
                              itemCount: items.length,
                              separatorBuilder: (_, _) => const SizedBox(height: 8),
                              itemBuilder: (context, i) {
                                final e = Map<String, dynamic>.from(items[i] as Map);
                                final score = '${e['bfzcj'] ?? ''}';
                                final bits = <String>[
                                  if (_term.isAll) termLabelOfItem(e),
                                  if ('${e['xf'] ?? ''}'.isNotEmpty) '${e['xf']} 学分',
                                  if ('${e['kclbmc'] ?? ''}'.isNotEmpty) '${e['kclbmc']}',
                                  '绩点 ${e['jd'] ?? '-'}',
                                ];
                                return Card(
                                  child: ListTile(
                                    title: Text('${e['kcmc'] ?? ''}'),
                                    subtitle: Text(bits.where((s) => s.trim().isNotEmpty).join(' · ')),
                                    trailing: Text(
                                      score,
                                      style: const TextStyle(
                                        fontSize: 20,
                                        fontWeight: FontWeight.w700,
                                        color: kCrimson,
                                      ),
                                    ),
                                  ),
                                );
                              },
                            ),
                          ),
                        ],
                      );
                    },
                  ),
          ),
        ],
      ),
    );
  }
}

class _SummaryBar extends StatelessWidget {
  const _SummaryBar({required this.items});

  final List<dynamic> items;

  @override
  Widget build(BuildContext context) {
    var credit = 0.0;
    var gpaNum = 0.0;
    var gpaDen = 0.0;
    for (final raw in items) {
      if (raw is! Map) continue;
      final xf = double.tryParse('${raw['xf'] ?? ''}');
      final jd = double.tryParse('${raw['jd'] ?? ''}');
      if (xf != null) credit += xf;
      if (xf != null && jd != null) {
        gpaNum += jd * xf;
        gpaDen += xf;
      }
    }
    final creditText = credit == credit.roundToDouble() ? '${credit.toInt()}' : credit.toStringAsFixed(1);
    final gpaText = gpaDen > 0 ? (gpaNum / gpaDen).toStringAsFixed(2) : '-';
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 0, 20, 8),
      child: Align(
        alignment: Alignment.centerLeft,
        child: Text(
          '共 ${items.length} 门 · $creditText 学分 · 平均绩点 $gpaText',
          style: TextStyle(color: context.muted),
        ),
      ),
    );
  }
}
