import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../api/httpx.dart';
import '../api/jwxt.dart';
import '../state/session.dart';
import '../theme.dart';
import '../widgets/async_body.dart';
import '../widgets/loader.dart';
import '../widgets/motion.dart';

/// 自主选课：轮次 tab + 课程/教学班列表 + 选课提交。
class SelectionPage extends StatefulWidget {
  const SelectionPage({super.key});

  @override
  State<SelectionPage> createState() => _SelectionPageState();
}

class _SelectionPageState extends State<SelectionPage> {
  Future<Map<String, dynamic>>? _entry;
  var _busy = false;
  var _tab = 0;
  var _keyword = '';
  final _search = TextEditingController();
  final _choosed = <String>{}; // 已选 jxb_id（本轮）
  final _done = <String>{}; // 已完成目标 kch_id

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _entry ??= context.read<Session>().loadSelectionEntry();
  }

  void _reload() {
    if (_busy) return;
    final fut = context.read<Session>().loadSelectionEntry(force: true);
    setState(() {
      _busy = true;
      _entry = fut;
    });
    fut.whenComplete(() {
      if (mounted) setState(() => _busy = false);
    });
  }

  @override
  void dispose() {
    _search.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final entry = _entry;
    return Scaffold(
      appBar: AppBar(
        title: const Text('选课'),
        actions: [RefreshBusyButton(busy: _busy, onPressed: _reload)],
      ),
      body: entry == null
          ? const Center(child: SwunLoader())
          : AsyncBody<Map<String, dynamic>>(
              future: entry,
              onRetry: _busy ? null : _reload,
              builder: (context, data) => _body(data),
            ),
    );
  }

  Widget _body(Map<String, dynamic> data) {
    final rounds = [for (final r in (data['rounds'] as List?) ?? const []) Map<String, dynamic>.from(r as Map)];
    if (rounds.isEmpty) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Text('当前没有开放的选课轮次', style: TextStyle(color: context.muted, fontSize: 14)),
        ),
      );
    }
    if (_tab >= rounds.length) _tab = 0;
    final round = rounds[_tab];
    final profile = Map<String, String>.from(data['profile'] as Map? ?? {});
    return Column(
      children: [
        if (rounds.length > 1)
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 10, 16, 0),
            child: _roundTabs(rounds),
          ),
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 10, 16, 4),
          child: TextField(
            controller: _search,
            textInputAction: TextInputAction.search,
            decoration: InputDecoration(
              isDense: true,
              hintText: '搜索课程名',
              prefixIcon: const Icon(Icons.search_rounded, size: 20),
              border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
              contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
            ),
            onSubmitted: (v) => setState(() => _keyword = v.trim()),
          ),
        ),
        Expanded(
          child: RefreshIndicator(
            onRefresh: () async => _reload(),
            child: _CourseList(
              round: round,
              profile: profile,
              keyword: _keyword,
              choosed: _choosed,
              done: _done,
              onSubmit: _submit,
            ),
          ),
        ),
      ],
    );
  }

  Widget _roundTabs(List<Map<String, dynamic>> rounds) {
    return Row(
      children: [
        for (var i = 0; i < rounds.length; i++) ...[
          if (i > 0) const SizedBox(width: 8),
          Expanded(
            child: _roundTab(rounds[i], i == _tab, () => setState(() => _tab = i)),
          ),
        ],
      ],
    );
  }

  Widget _roundTab(Map<String, dynamic> r, bool active, VoidCallback onTap) {
    final label = '${r['kklxmc'] ?? r['kklxdm'] ?? '轮次'}';
    final bg = active ? context.primary : context.panel;
    final fg = active ? Colors.white : context.muted;
    return Pressable(
      radius: 12,
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 9),
        decoration: BoxDecoration(
          color: bg,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: context.line.withValues(alpha: 0.7)),
        ),
        child: Center(
          child: Text(
            label,
            overflow: TextOverflow.ellipsis,
            style: TextStyle(fontSize: 13, fontWeight: FontWeight.w600, color: fg),
          ),
        ),
      ),
    );
  }

  Future<void> _submit(
    Map<String, dynamic> round,
    Map<String, String> profile,
    Map<String, dynamic> jxb,
  ) async {
    final s = context.read<Session>();
    final name = '${jxb['kcmc'] ?? ''} ${jxb['jxbmc'] ?? jxb['jxb_id'] ?? ''}';
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('确认选课'),
        content: Text('要选择「$name」吗？'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('取消')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('选课')),
        ],
      ),
    );
    if (ok != true || !mounted) return;
    if (!mounted) return;
    showDialog<void>(
      context: context,
      barrierDismissible: false,
      builder: (ctx) => const Center(child: SwunLoader()),
    );
    try {
      final query = {
        ...buildXkQuery(round: round, profile: profile),
        'jxb_id': '${jxb['jxb_id'] ?? ''}',
        'do_jxb_id': '${jxb['do_jxb_id'] ?? ''}',
        'kch_id': '${jxb['kch_id'] ?? ''}',
        'jxbzls': '${jxb['jxbzls'] ?? ''}',
        'txbsfrl': '',
        'dsfrlxskg': '',
      };
      if (s.demoMode) {
        await Future<void>.delayed(const Duration(milliseconds: 600));
      } else {
        await s.ensureJwxt();
        final resp = await s.jwxt!.selectionSubmit(query);
        final flag = '${resp['flag'] ?? resp['jg'] ?? ''}';
        final msg = '${resp['msg'] ?? ''}';
        if (flag != '1' && flag != '6' && flag != '3') {
          throw Exception(msg.isEmpty ? '选课失败' : msg);
        }
      }
      if (!mounted) return;
      setState(() {
        _choosed.add('${jxb['jxb_id'] ?? ''}');
        _done.add('${jxb['kch_id'] ?? ''}');
      });
      ScaffoldMessenger.maybeOf(context)?.showSnackBar(
        SnackBar(content: Text('「${jxb['kcmc'] ?? ''}」选课成功'), duration: const Duration(seconds: 3)),
      );
    } catch (e) {
      if (!mounted) return;
      showDialog<void>(
        context: context,
        builder: (ctx) => AlertDialog(
          title: const Text('选课失败'),
          content: Text(publicError(e)),
          actions: [TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('知道了'))],
        ),
      );
    } finally {
      if (mounted) Navigator.of(context, rootNavigator: false).pop();
    }
  }
}

class _CourseList extends StatelessWidget {
  const _CourseList({
    required this.round,
    required this.profile,
    required this.keyword,
    required this.choosed,
    required this.done,
    required this.onSubmit,
  });

  final Map<String, dynamic> round;
  final Map<String, String> profile;
  final String keyword;
  final Set<String> choosed;
  final Set<String> done;
  final Future<void> Function(Map<String, dynamic>, Map<String, String>, Map<String, dynamic>) onSubmit;

  @override
  Widget build(BuildContext context) {
    final s = context.read<Session>();
    final fut = s.loadSelectionCourses(round, profile, keyword: keyword);
    return AsyncBody<List<dynamic>>(
      future: fut,
      onRetry: () => s.invalidateSelection(),
      builder: (context, rows) {
        if (rows.isEmpty) {
          return ListView(
            children: [
              Padding(
                padding: const EdgeInsets.all(32),
                child: Center(
                  child: Text('没有匹配的课程', style: TextStyle(color: context.muted, fontSize: 14)),
                ),
              ),
            ],
          );
        }
        return ListView.separated(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
          itemCount: rows.length,
          separatorBuilder: (_, _) => const SizedBox(height: 8),
          itemBuilder: (context, i) => _courseCard(context, Map<String, dynamic>.from(rows[i] as Map)),
        );
      },
    );
  }

  Widget _courseCard(BuildContext context, Map<String, dynamic> row) {
    final remain = xkRemain(row);
    final jxbId = '${row['jxb_id'] ?? ''}';
    final picked = choosed.contains(jxbId);
    final full = remain <= 0 && !picked;
    final accent = picked
        ? const Color(0xFF2E9E5B)
        : full
            ? context.muted
            : context.primary;
    return DecoratedBox(
      decoration: BoxDecoration(
        color: context.panel,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: context.line.withValues(alpha: 0.7)),
      ),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(14, 12, 14, 12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        '${row['kcmc'] ?? ''}',
                        style: TextStyle(fontSize: 15, fontWeight: FontWeight.w600, color: context.ink),
                      ),
                      const SizedBox(height: 3),
                      Text(
                        '${row['kch_id'] ?? ''} · ${row['jxbmc'] ?? ''} · ${row['xf'] ?? '?'}学分',
                        style: TextStyle(fontSize: 12, color: context.muted),
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 8),
                _remainBadge(context, remain, picked),
              ],
            ),
            const SizedBox(height: 10),
            Row(
              children: [
                Expanded(
                  child: Text(
                    [
                      if ('${row['jsmc'] ?? ''}'.isNotEmpty) '${row['jsmc']}',
                      if ('${row['sksj'] ?? ''}'.isNotEmpty) '${row['sksj']}',
                    ].join(' · '),
                    style: TextStyle(fontSize: 12, color: context.muted, height: 1.35),
                  ),
                ),
                const SizedBox(width: 8),
                SizedBox(
                  height: 30,
                  child: FilledButton(
                    style: FilledButton.styleFrom(
                      backgroundColor: accent,
                      padding: const EdgeInsets.symmetric(horizontal: 14),
                      textStyle: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600),
                    ),
                    onPressed: picked ? null : () => onSubmit(round, profile, row),
                    child: Text(picked ? '已选' : '选课'),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _remainBadge(BuildContext context, int remain, bool picked) {
    final color = picked
        ? const Color(0xFF2E9E5B)
        : remain > 0
            ? context.primary
            : kCrimson;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Text(
        picked ? '已选上' : (remain > 0 ? '余 $remain' : '已满'),
        style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: color),
      ),
    );
  }
}
