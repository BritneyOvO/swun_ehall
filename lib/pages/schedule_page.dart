import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../models/lesson.dart';
import '../state/session.dart';
import '../theme.dart';
import '../widgets/async_body.dart';
import '../widgets/course_table.dart';
import '../widgets/motion.dart';
import 'ktkq_sign_page.dart';

class SchedulePage extends StatefulWidget {
  const SchedulePage({super.key});

  @override
  State<SchedulePage> createState() => _SchedulePageState();
}

class _SchedulePageState extends State<SchedulePage> {
  Future<Map<String, dynamic>>? _future;
  int? _week;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      setState(() => _future = context.read<Session>().loadSchedule());
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('课表'), automaticallyImplyLeading: false),
      body: _future == null
          ? const Center(child: CircularProgressIndicator(color: kCrimson))
          : AsyncBody(
              future: _future!,
              builder: (context, data) {
                final lessons = lessonsFromKb((data['kbList'] as List?) ?? const []);
                if (lessons.isEmpty) return const Center(child: Text('本学期暂无课表'));
                final cur = int.tryParse('${data['curWeek'] ?? 1}') ?? 1;
                final total = int.tryParse('${data['totalWeek'] ?? 16}') ?? 16;
                final maxW = [
                  total,
                  16,
                  for (final l in lessons) if (l.weeks.isNotEmpty) l.weeks.reduce((a, b) => a > b ? a : b),
                ].reduce((a, b) => a > b ? a : b);
                final week = (_week ?? cur).clamp(1, maxW);
                final times = data['times'] is List<PeriodTime>
                    ? data['times'] as List<PeriodTime>
                    : parsePeriodTimes(data['times']);
                return Column(
                  children: [
                    _WeekBar(
                      week: week,
                      maxWeek: maxW,
                      isCurrent: week == cur,
                      onPrev: week > 1 ? () => setState(() => _week = week - 1) : null,
                      onNext: week < maxW ? () => setState(() => _week = week + 1) : null,
                      onCurrent: week == cur ? null : () => setState(() => _week = cur),
                    ),
                    Expanded(
                      child: AnimatedSwitcher(
                        duration: const Duration(milliseconds: 280),
                        switchInCurve: Curves.easeOutCubic,
                        child: CourseTable(
                          key: ValueKey(week),
                          lessons: lessons,
                          times: times,
                          week: week,
                          onLessonTap: (l) => pushPage(context, KtkqSignPage(lesson: l, week: week)),
                        ),
                      ),
                    ),
                  ],
                );
              },
            ),
    );
  }
}

class _WeekBar extends StatelessWidget {
  const _WeekBar({
    required this.week,
    required this.maxWeek,
    required this.isCurrent,
    this.onPrev,
    this.onNext,
    this.onCurrent,
  });

  final int week;
  final int maxWeek;
  final bool isCurrent;
  final VoidCallback? onPrev;
  final VoidCallback? onNext;
  final VoidCallback? onCurrent;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(4, 6, 8, 2),
      child: Row(
        children: [
          IconButton(
            onPressed: onPrev,
            icon: const Icon(Icons.chevron_left),
            color: kCrimson,
          ),
          Text('第$week周', style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 16)),
          IconButton(
            onPressed: onNext,
            icon: const Icon(Icons.chevron_right),
            color: kCrimson,
          ),
          Text('/ $maxWeek', style: const TextStyle(color: Colors.black45, fontSize: 12)),
          const Spacer(),
          if (!isCurrent)
            TextButton(onPressed: onCurrent, child: const Text('回本周'))
          else
            const Text('本周', style: TextStyle(color: Colors.black45, fontSize: 13)),
        ],
      ),
    );
  }
}
