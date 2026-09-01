import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../models/credit.dart';
import '../state/session.dart';
import '../theme.dart';
import '../widgets/async_body.dart';

class CreditsPage extends StatefulWidget {
  const CreditsPage({super.key});

  @override
  State<CreditsPage> createState() => _CreditsPageState();
}

class _CreditsPageState extends State<CreditsPage> {
  Future<CreditProgress>? _future;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      setState(() => _future = context.read<Session>().loadCredits());
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('共修学分')),
      body: _future == null
          ? const Center(child: CircularProgressIndicator(color: kCrimson))
          : AsyncBody(
              future: _future!,
              builder: (context, p) => ListView(
                padding: const EdgeInsets.all(16),
                children: [
                  _hero(p),
                  const SizedBox(height: 12),
                  _planCard(p),
                  const SizedBox(height: 12),
                  const Text('学分详情', style: TextStyle(fontSize: 15, fontWeight: FontWeight.w600)),
                  const SizedBox(height: 8),
                  if (p.buckets.isEmpty)
                    const Card(child: ListTile(title: Text('暂无分类明细')))
                  else
                    for (final b in p.buckets) _bucket(b, p.taken),
                  const SizedBox(height: 12),
                  const Text(
                    '共修学分为教务「修读总学分」。还差学分 = 最低毕业学分 − 共修学分。计划内/计划外门数来自学生学业情况，仅供参考。',
                    style: TextStyle(color: Colors.black45, fontSize: 12, height: 1.4),
                  ),
                ],
              ),
            ),
    );
  }

  Widget _hero(CreditProgress p) {
    final done = p.met;
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
          Text(done ? '已修满' : '还差', style: TextStyle(color: context.muted, fontSize: 13)),
          const SizedBox(height: 4),
          TweenAnimationBuilder<double>(
            tween: Tween(begin: 0, end: done ? p.taken : p.remaining),
            duration: const Duration(milliseconds: 700),
            curve: Curves.easeOutCubic,
            builder: (context, v, _) {
              final s = v == v.roundToDouble() ? '${v.toInt()}' : v.toStringAsFixed(1);
              return Text(
                '$s 学分',
                style: TextStyle(fontSize: 32, fontWeight: FontWeight.w600, height: 1.1, color: context.ink),
              );
            },
          ),
          const SizedBox(height: 8),
          Text(
            '共修 ${p.takenText}  /  要求 ${p.required > 0 ? p.requiredText : '--'}'
            '${p.gpa != null ? '  ·  GPA ${p.gpa!.toStringAsFixed(3)}' : ''}',
            style: TextStyle(color: context.muted, fontSize: 13),
          ),
          if (p.required > 0) ...[
            const SizedBox(height: 14),
            TweenAnimationBuilder<double>(
              tween: Tween(begin: 0, end: (p.taken / p.required).clamp(0, 1)),
              duration: const Duration(milliseconds: 700),
              curve: Curves.easeOutCubic,
              builder: (context, v, _) => ClipRRect(
                borderRadius: BorderRadius.circular(4),
                child: LinearProgressIndicator(
                  value: v,
                  minHeight: 4,
                  backgroundColor: context.line,
                  color: kCrimson,
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }

  Widget _planCard(CreditProgress p) {
    if (p.planTotal == 0 && p.extraPassed == 0) {
      return const SizedBox.shrink();
    }
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('学业情况', style: TextStyle(fontWeight: FontWeight.w600)),
            const SizedBox(height: 8),
            if (p.planTotal > 0)
              Text(
                '计划内 ${p.planTotal} 门：通过 ${p.planPassed} · 在读 ${p.planStudying} · 未修 ${p.planUnstudied}'
                '${p.planFailed > 0 ? ' · 未通过 ${p.planFailed}' : ''}',
              ),
            if (p.extraPassed + p.extraFailed > 0)
              Text('计划外：通过 ${p.extraPassed}${p.extraFailed > 0 ? ' · 未通过 ${p.extraFailed}' : ''}'),
          ],
        ),
      ),
    );
  }

  Widget _bucket(CreditBucket b, double total) {
    final ratio = total > 0 ? (b.credits / total).clamp(0.0, 1.0) : 0.0;
    final xf = b.credits == b.credits.roundToDouble() ? '${b.credits.toInt()}' : b.credits.toStringAsFixed(1);
    return Card(
      margin: const EdgeInsets.only(bottom: 8),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(14, 12, 14, 12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(child: Text(b.name, style: const TextStyle(fontWeight: FontWeight.w700))),
                Text('$xf 学分 · ${b.courses} 门', style: const TextStyle(color: Colors.black54)),
              ],
            ),
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
        ),
      ),
    );
  }
}
