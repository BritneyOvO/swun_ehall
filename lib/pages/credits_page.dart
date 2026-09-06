import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../models/credit.dart';
import '../state/session.dart';
import '../theme.dart';
import '../widgets/async_body.dart';
import '../widgets/loader.dart';
import '../widgets/motion.dart';

class CreditsPage extends StatefulWidget {
  const CreditsPage({super.key});

  @override
  State<CreditsPage> createState() => _CreditsPageState();
}

class _CreditsPageState extends State<CreditsPage> {
  Future<CreditProgress>? _future;
  var _started = false;
  var _epoch = 0;
  var _busy = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_started) return;
    _started = true;
    _future = context.read<Session>().loadCredits();
  }

  void _reload() {
    if (_busy) return;
    final fut = context.read<Session>().loadCredits(force: true);
    setState(() {
      _epoch++;
      _busy = true;
      _future = fut;
    });
    fut.whenComplete(() {
      if (mounted) setState(() => _busy = false);
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('共修学分'),
        actions: [RefreshBusyButton(busy: _busy, onPressed: _reload)],
      ),
      body: _future == null
          ? const Center(child: SwunLoader())
          : AsyncBody(
              key: ValueKey(_epoch),
              future: _future!,
              onRetry: _busy ? null : _reload,
              builder: (context, p) => ListView(
                padding: const EdgeInsets.all(16),
                children: [
                  _hero(p),
                  const SizedBox(height: 12),
                  _planCard(p),
                  const SizedBox(height: 12),
                  const Text('学分详情', style: TextStyle(fontSize: 15, fontWeight: FontWeight.w600)),
                  const SizedBox(height: 4),
                  Text('点开各方向查看已修、在修、未修课程', style: TextStyle(color: context.muted, fontSize: 12)),
                  const SizedBox(height: 8),
                  if (p.buckets.isEmpty)
                    const Card(child: ListTile(title: Text('暂无分类明细')))
                  else
                    for (final b in p.buckets) _bucket(b),
                ],
              ),
            ),
    );
  }

  Widget _hero(CreditProgress p) {
    final bar = p.required > 0 ? (p.taken / p.required).clamp(0.0, 1.0) : 0.0;
    return Container(
      padding: const EdgeInsets.fromLTRB(18, 20, 18, 16),
      decoration: BoxDecoration(
        color: context.panel,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: context.line),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('已修学分', style: TextStyle(color: context.muted, fontSize: 13)),
          const SizedBox(height: 4),
          TweenAnimationBuilder<double>(
            tween: Tween(begin: 0, end: p.taken),
            duration: const Duration(milliseconds: 700),
            curve: Curves.easeOutCubic,
            builder: (context, v, _) {
              return Text(
                '${formatXf(v)} 学分',
                style: TextStyle(fontSize: 32, fontWeight: FontWeight.w600, height: 1.1, color: context.ink),
              );
            },
          ),
          const SizedBox(height: 10),
          Wrap(
            spacing: 12,
            runSpacing: 4,
            children: [
              if (p.required > 0) Text('要求 ${p.requiredText}', style: TextStyle(color: context.ink, fontSize: 13)),
              if (p.earned > 0) Text('获得 ${p.earnedText}', style: TextStyle(color: context.muted, fontSize: 13)),
              if (p.gpa != null) Text('GPA ${p.gpa!.toStringAsFixed(3)}', style: TextStyle(color: context.muted, fontSize: 13)),
            ],
          ),
          if (p.required > 0) ...[
            const SizedBox(height: 10),
            ClipRRect(
              borderRadius: BorderRadius.circular(6),
              child: LinearProgressIndicator(
                value: bar,
                minHeight: 6,
                backgroundColor: const Color(0x11000000),
                color: kCrimson,
              ),
            ),
            const SizedBox(height: 8),
            Text(
              p.met ? '已达毕业要求 ${p.requiredText} 学分' : '还差 ${p.remainingText} 学分',
              style: TextStyle(color: context.muted, fontSize: 12),
            ),
          ],
        ],
      ),
    );
  }

  Widget _planCard(CreditProgress p) {
    if (p.planPassed + p.planFailed + p.planUnstudied + p.planStudying == 0) {
      return const SizedBox.shrink();
    }
    final bits = <String>[
      if (p.planPassed > 0) '已修 ${p.planPassed} 门',
      if (p.planStudying > 0) '在修 ${p.planStudying} 门',
      if (p.planUnstudied > 0) '未修 ${p.planUnstudied} 门',
      if (p.planFailed > 0) '未过 ${p.planFailed} 门',
    ];
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('培养方案', style: TextStyle(fontWeight: FontWeight.w600)),
            const SizedBox(height: 8),
            Text(bits.join(' · ')),
          ],
        ),
      ),
    );
  }

  Widget _bucket(CreditBucket b) {
    final bits = <String>[
      '已修 ${formatXf(b.credits)} 学分${b.courses > 0 ? ' · ${b.courses} 门' : ''}',
      if (b.unstudied > 0 || b.unstudiedCourses > 0)
        '未修 ${formatXf(b.unstudied)} 学分${b.unstudiedCourses > 0 ? ' · ${b.unstudiedCourses} 门' : ''}',
      if (b.studying > 0 || b.studyingCourses > 0)
        '在修 ${formatXf(b.studying)} 学分${b.studyingCourses > 0 ? ' · ${b.studyingCourses} 门' : ''}',
      if (b.failed > 0 || b.failedCourses > 0)
        '未过 ${formatXf(b.failed)} 学分${b.failedCourses > 0 ? ' · ${b.failedCourses} 门' : ''}',
      if (b.required > 0) '要求 ${formatXf(b.required)} 学分',
    ];
    final denom = b.scope;
    final ratio = denom > 0 ? (b.credits / denom).clamp(0.0, 1.0) : (b.credits > 0 ? 1.0 : 0.0);
    return Card(
      margin: const EdgeInsets.only(bottom: 8),
      child: InkWell(
        borderRadius: BorderRadius.circular(12),
        onTap: () => pushPage(context, _BucketCoursesPage(bucket: b)),
        child: Padding(
          padding: const EdgeInsets.fromLTRB(14, 12, 10, 12),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Expanded(child: Text(b.name, style: const TextStyle(fontWeight: FontWeight.w700))),
                  Icon(Icons.chevron_right_rounded, color: context.muted, size: 20),
                ],
              ),
              const SizedBox(height: 6),
              Text(bits.join('    '), style: TextStyle(color: context.muted, fontSize: 13, height: 1.4)),
              if (denom > 0 && (b.unstudied > 0 || b.studying > 0 || b.failed > 0 || b.required > 0)) ...[
                const SizedBox(height: 8),
                ClipRRect(
                  borderRadius: BorderRadius.circular(6),
                  child: LinearProgressIndicator(
                    value: ratio,
                    minHeight: 6,
                    backgroundColor: const Color(0x11000000),
                    color: kCrimson,
                  ),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}

class _BucketCoursesPage extends StatelessWidget {
  const _BucketCoursesPage({required this.bucket});

  final CreditBucket bucket;

  @override
  Widget build(BuildContext context) {
    final groups = <String, List<PlanCourse>>{
      '在修': [for (final c in bucket.items) if (c.studying) c],
      '未修': [for (final c in bucket.items) if (c.unstudied) c],
      '未过': [for (final c in bucket.items) if (c.failed) c],
      '已修': [for (final c in bucket.items) if (c.passed) c],
    };
    final other = [for (final c in bucket.items) if (!c.studying && !c.unstudied && !c.failed && !c.passed) c];
    if (other.isNotEmpty) groups['其他'] = other;

    return Scaffold(
      appBar: AppBar(title: Text(bucket.name)),
      body: bucket.items.isEmpty
          ? Center(
              child: Text(
                bucket.credits > 0 ? '该方向暂无课程明细' : '该方向暂无课程',
                style: TextStyle(color: context.muted),
              ),
            )
          : ListView(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
              children: [
                Padding(
                  padding: const EdgeInsets.fromLTRB(4, 0, 4, 12),
                  child: Text(
                    [
                      '已修 ${formatXf(bucket.credits)}',
                      if (bucket.unstudied > 0) '未修 ${formatXf(bucket.unstudied)}',
                      if (bucket.studying > 0) '在修 ${formatXf(bucket.studying)}',
                      if (bucket.required > 0) '要求 ${formatXf(bucket.required)}',
                    ].join(' · '),
                    style: TextStyle(color: context.muted, fontSize: 13),
                  ),
                ),
                for (final e in groups.entries)
                  if (e.value.isNotEmpty) ...[
                    Padding(
                      padding: const EdgeInsets.fromLTRB(4, 8, 4, 6),
                      child: Text(
                        '${e.key} ${e.value.length} 门',
                        style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 14),
                      ),
                    ),
                    for (final c in e.value) _courseTile(context, c),
                  ],
              ],
            ),
    );
  }

  Widget _courseTile(BuildContext context, PlanCourse c) {
    final bits = <String>[
      if (c.code.isNotEmpty) c.code,
      if (c.credits > 0) '${formatXf(c.credits)} 学分',
      if (c.term.isNotEmpty) c.term,
      if (c.nature.isNotEmpty) c.nature,
    ];
    return Card(
      margin: const EdgeInsets.only(bottom: 8),
      child: ListTile(
        title: Text(c.name),
        subtitle: bits.isEmpty ? null : Text(bits.join(' · ')),
        trailing: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          crossAxisAlignment: CrossAxisAlignment.end,
          children: [
            Text(c.status, style: TextStyle(color: context.muted, fontSize: 12)),
            if (c.score.isNotEmpty)
              Text(c.score, style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 16, color: kCrimson)),
          ],
        ),
      ),
    );
  }
}
